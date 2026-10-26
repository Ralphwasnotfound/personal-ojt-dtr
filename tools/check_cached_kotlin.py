"""Supplemental offline source/JUnit check; does not replace Gradle, KSP, or Android builds.
Reads installed cache artifacts; writes only app/build/local-validation inside the project.
"""
from pathlib import Path
import os
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
CACHE = Path(os.environ.get("OJT_GRADLE_CACHE", str(Path.home() / ".gradle/caches/modules-2/files-2.1")))
OUT = ROOT / "app/build/local-validation"
OUT.mkdir(parents=True, exist_ok=True)
(OUT / "tmp").mkdir(exist_ok=True)


def checked(args):
    result = subprocess.run(args)
    if result.returncode:
        raise SystemExit(result.returncode)


def jar(group, artifact, version="*"):
    return str(next((CACHE / group / artifact).glob(version + "/*/*.jar")))


compiler = [jar("org.jetbrains.kotlin", name, "2.2.21") for name in
            ("kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-reflect")]
compiler += [jar("org.jetbrains", "annotations", "23.0.0"), jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")]
gson = jar("com.google.code.gson", "gson", "2.11.0")
stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.21")
junit = [jar("junit", "junit", "4.13.2"), jar("org.hamcrest", "hamcrest-core", "1.3")]
coroutines = jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")
# DataStore is already provided by Glance; theme persistence tests exercise its real file store.
sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
preference_classpath = [str(sdk / "platforms/android-36/android.jar")]
for artifact in (CACHE / "androidx.datastore").iterdir():
    for cached in (artifact / "1.0.0").glob("*/*"):
        if cached.suffix == ".jar":
            preference_classpath.append(str(cached))
        elif cached.suffix == ".aar":
            with zipfile.ZipFile(cached) as archive:
                target = OUT / ("androidx.datastore-" + artifact.name + ".jar")
                target.write_bytes(archive.read("classes.jar"))
                preference_classpath.append(str(target))
base = ROOT / "app/src/main/java/com/lazarus/personalojtdtr"
sources = [base / "data/backup" / (name + ".kt") for name in ["BackupRecords", "BackupJson", "BackupArchive", "AtomicRestore"]] + list((base / "domain").rglob("*.kt")) + [base / "data/photos/LocalAttendancePhotos.kt", base / "util/AttendanceTimestamp.kt", base / "util/Display.kt", base / "data/photos/ActivityPhotoStore.kt", base / "data/preferences/ThemePreferences.kt"]
tests = list((ROOT / "app/src/test").rglob("*.kt"))
command = ["java", "-Djava.io.tmpdir=" + str(OUT / "tmp"), "-Xmx2g", "-cp", ":".join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17"]
test_jar = str(OUT / "tests.jar")
checked(command + ["-classpath", ":".join([stdlib, coroutines, gson] + junit + preference_classpath), "-d", test_jar] + list(map(str, sources + tests)))
classes = [".".join(p.relative_to(ROOT / "app/src/test/java").with_suffix("").parts) for p in tests]
checked(["java", "-Djava.io.tmpdir=" + str(OUT / "tmp"), "-cp", ":".join([test_jar, stdlib, coroutines, gson] + junit + preference_classpath), "org.junit.runner.JUnitCore"] + classes)

if "--tests-only" in sys.argv:
    raise SystemExit(0)

classpath = []
for group in CACHE.iterdir():
    if not (group.name.startswith("androidx.") or group.name in ["org.jetbrains.kotlinx", "org.jetbrains", "org.jetbrains.kotlin", "com.google.guava", "com.google.guava.failureaccess", "com.google.code.gson"]):
        continue
    for artifact in group.iterdir():
        if group.name == "org.jetbrains.kotlin" and artifact.name not in ["kotlin-stdlib", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8"]:
            continue
        versions = sorted(artifact.iterdir(), key=lambda p: tuple(int(x) if x.isdigit() else x for x in re.split(r"(\d+)", p.name)))
        if not versions:
            continue
        for f in versions[-1].glob("*/*"):
            if f.suffix == ".jar":
                classpath.append(str(f))
            elif f.suffix == ".aar":
                with zipfile.ZipFile(f) as archive:
                    if "classes.jar" in archive.namelist():
                        target = OUT / (group.name + "-" + artifact.name + ".jar")
                        target.write_bytes(archive.read("classes.jar"))
                        classpath.append(str(target))
sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
classpath.append(str(sdk / "platforms/android-36/android.jar"))
# Generate a real application R class for sources that reference widget resources.
# This validates our resources, not the merged dependency manifest/resources or APK.
resources = OUT / "resource-check"
resources.mkdir(exist_ok=True)
resource_java = resources / "java"
resource_classes = resources / "classes"
resource_java.mkdir(exist_ok=True)
resource_classes.mkdir(exist_ok=True)
aapt = str(sdk / "build-tools/36.0.0/aapt2")
compiled = resources / "resources.zip"
checked([aapt, "compile", "--dir", str(ROOT / "app/src/main/res"), "-o", str(compiled)])
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text().replace("${applicationId}", "com.lazarus.personalojtdtr")
manifest = manifest.replace("<manifest ", '<manifest package="com.lazarus.personalojtdtr" ', 1)
manifest_file = resources / "AndroidManifest.xml"
manifest_file.write_text(manifest)
checked([aapt, "link", "-I", str(sdk / "platforms/android-36/android.jar"), "--manifest", str(manifest_file),
         "--java", str(resource_java), "--min-sdk-version", "26", "--target-sdk-version", "36",
         "-o", str(resources / "resources.apk"), str(compiled)])
checked(["javac", "--release", "17", "-d", str(resource_classes)] + list(map(str, resource_java.rglob("*.java"))))
resource_jar = resources / "resources.jar"
checked(["jar", "--create", "--file", str(resource_jar), "-C", str(resource_classes), "."])
classpath.append(str(resource_jar))
print("Application resource compilation/linking and R generation passed (not merged APK validation).")
plugin = jar("org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", "2.2.21")
checked(command + ["-Xplugin=" + plugin, "-classpath", ":".join(classpath), "-d", str(OUT / "sources.jar")] + list(map(str, base.rglob("*.kt"))))
print("Supplemental Kotlin/Compose source compilation passed (no KSP, resources, or APK validation).")

# Run the real cached KSP2 + Room processor independently of Gradle's blocked socket services.
ksp_version = "2.2.21-2.0.4"
ksp_runtime = [jar("com.google.devtools.ksp", name, ksp_version) for name in
               ("symbol-processing-aa-embeddable", "symbol-processing-api", "symbol-processing-common-deps")]
ksp_runtime += [stdlib, coroutines]
processor = []
for group in CACHE.iterdir():
    if group.name not in ["androidx.room", "com.squareup", "com.google.auto", "com.google.auto.value", "com.google.guava", "org.xerial", "commons-codec", "com.intellij", "org.jetbrains", "org.slf4j", "com.google.code.gson", "org.jetbrains.kotlinx"]:
        continue
    for artifact in group.iterdir():
        versions = sorted(artifact.iterdir(), key=lambda p: tuple(int(x) if x.isdigit() else x for x in re.split(r"(\d+)", p.name)))
        if versions:
            processor += [str(p) for p in versions[-1].glob("*/*.jar")]
ksp_out = OUT / "ksp"
for directory in ["java", "kotlin", "resources", "classes", "caches", "tmp"]:
    (ksp_out / directory).mkdir(parents=True, exist_ok=True)
args = {
    "java-output-dir": ksp_out / "java",
    "jvm-target": "17",
    "module-name": "ojt-room-validation",
    "source-roots": base / "data/local",
    "libraries": ":".join(classpath),
    "processor-options": "room.schemaLocation=" + str(ROOT / "app/schemas") + ":room.generateKotlin=true",
    "project-base-dir": ROOT,
    "output-base-dir": ksp_out,
    "caches-dir": ksp_out / "caches",
    "class-output-dir": ksp_out / "classes",
    "kotlin-output-dir": ksp_out / "kotlin",
    "resource-output-dir": ksp_out / "resources",
    "language-version": "2.2",
    "api-version": "2.2",
    "incremental": "false",
    "jdk-home": os.environ.get("JAVA_HOME", "/usr/lib/jvm/java-17-openjdk"),
}
checked(["java", "-Xmx2g", "-Djava.io.tmpdir=" + str(ksp_out / "tmp"), "-Dsqlite.tmpdir=" + str(ksp_out / "tmp"), "-cp", ":".join(ksp_runtime), "com.google.devtools.ksp.cmdline.KSPJvmMain"] + ["-" + key + "=" + str(value) for key, value in args.items()] + [":".join(processor)])
generated = list((ksp_out / "kotlin").rglob("*.kt"))
if not generated:
    raise RuntimeError("Room KSP did not generate implementation files")
checked(command + ["-Xplugin=" + plugin, "-classpath", ":".join(classpath), "-d", str(OUT / "sources-with-room.jar")] + list(map(str, list(base.rglob("*.kt")) + generated)))
print("Real Room 2.8.3 KSP generation, v4 schema export, and generated Kotlin compilation passed.")
