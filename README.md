# My OJT Companion — P4

P1–P3 are retained, with P4 activity documentation and UI improvements implemented. App version is **0.4.0** (`com.lazarus.personalojtdtr`).

P4 adds required titles/descriptions, selectable dates and optional times, five ordered camera/picker photos, a state-preserving editor, confirmed deletion, detail/full-screen photo views, manual text/photo sharing, recent Dashboard activities and consistent navigation icons. No new dependency or permission was added.

See [P4_ACTIVITY_JOURNAL.md](docs/P4_ACTIVITY_JOURNAL.md) for the file map, behavior, storage/revision safety, compatibility and remaining device checks. [BACKUP_FORMAT.md](docs/BACKUP_FORMAT.md) documents format 2/schema 4 and P3 format-1 import compatibility.

Validation: **137 JVM tests** and **48 host SQLite tests** passed, including preserved prior coverage (backup-version fixtures updated for format 2). Real Room/KSP schema-4 generation, Kotlin/Compose and generated-source compilation passed. All 15 navigation/action icon paths parsed successfully. SDK resource compilation/linking also passed as a supplemental check. Full Gradle P4 results remain subject to the sandbox restriction described below.

User-confirmed P1–P3 builds and Samsung tests are respected. Actual restoration has not been confirmed on the device. P4 phone testing, installation and device modifications were not performed.

Normal checks:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
python3 tools/test_database_guards.py
python3 tools/test_event_migration.py
python3 tools/test_recovery_migration.py
python3 tools/test_activity_migration.py
```

`tools/check_cached_kotlin.py` provides supplemental host/JUnit, Compose, KSP and generated-source checks using installed artifacts. It does not replace Gradle/D8 packaging, Android runtime migration validation, or real-device testing. Logs are under ignored `app/build/local-validation`.

Both P4 Gradle tasks were attempted with a temporary writable project-local Gradle cache. The known sandbox failure occurs before configuration:

```text
Could not create service of type FileLockContentionHandler
Could not determine a usable wildcard IP for this machine.
```

No system networking was changed. No full P4 APK build success is claimed. No emulator, app/device launch, installation, connected-device modification, Git commit/push or deployment occurred. The original DTR repositories remain untouched.

## Attendance and recovery

The four standard photo-backed events remain Morning Time In → Morning Time Out → Afternoon Time In → Afternoon Time Out. Morning/afternoon are labels without AM/PM clock cutoffs. Each genuine event still requires a new CameraX capture, preview and explicit confirmation. Retake/cancel, lifecycle cleanup, original preservation, a separate EXIF-aware timestamped JPEG, and optional manual Sharesheet sharing remain implemented.

Timestamps are UTC instants, displayed in Asia/Manila. The confirmed device timestamp is shared by the event record, overlay and history. It is not independently verified. Only completed actual intervals count; the lunch gap is excluded without another break deduction. Remaining hours clamp at zero.

P3 adds an explicit audit layer without changing captured events:

- An unfinished previous-day morning or afternoon is shown in Attendance with its actual events and photos. Confirm **Incomplete / unverified** to resolve the open interval and allow a new day. The original Time In/time/photo remains. No Time Out/photo is invented and the incomplete interval adds zero hours.
- An unstarted session on the current Manila day can be confirmed **Skipped / not worked**. Afternoon skipping requires the morning to be finished or explicitly skipped. Afternoon-only days first skip morning; morning-only days finish morning and skip afternoon. Early departure uses a genuine earlier Time Out capture.
- Started current-day sessions cannot be silently skipped, and completed sessions cannot be changed through recovery. Actions have unique IDs, date/session uniqueness and actual UTC audit timestamps. Duplicate actions and accidental edits/deletes are rejected.
- Dashboard and History use the same resolution-aware calculations. History distinguishes normal events, incomplete/unverified sessions, and skipped sessions while retaining photos and real timestamps.

P1 legacy records, break settings and their original hours remain preserved. The existing legacy closing flow remains available for a legacy open session; P3's labeled morning/afternoon recovery applies to the four-event model. There is no backdating, fake attendance, or one-tap undo of audit actions.

## Offline backup and REPLACE restore

Profile/Settings now provides **Export Backup** and **Restore Backup** using Android's document pickers with the local-only hint. No broad storage permission is needed ([Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)).

Export includes profile/settings, required hours, P1 attendance, P2/P3 events, recovery/skip actions, activities, and all referenced original/timestamped/activity photos. It streams a ZIP with a strict JSON manifest and generated asset names, verifies photo integrity, validates the completed private archive, then writes and reads back the selected document before reporting success. Missing/corrupt referenced photos cause export failure rather than a false complete-backup claim.

Restore validates a selected P3 archive entirely in private staging before showing its date, profile/settings and record/photo/action counts. **Replace all local data** requires explicit confirmation and explains that current records will be replaced. No automatic merge is attempted. Cancel before apply removes staging and does not change current records or the source backup.

For safe apply, photos are copied into a fresh private generation and synced. One Room transaction switches all records/photo references and a restore marker together, rebuilding the fixed guard triggers within the transaction. Existing live photos are never overwritten or renamed. Failure before commit rolls back records/triggers; uncertain commit delivery keeps both photo generations and checks the marker. Startup reconciles committed references and removes abandoned staging/unreferenced generations. Final apply is non-cancellable so it finishes commit/rollback; process-death safety relies on SQLite and the immutable generations.

Backups are **not automatically encrypted**. UI warnings ask the user to store archives securely. The source archive is not deleted after restoration. Backup contents and private paths are not logged or included in error messages/sharing intents. The app does not upload; choose a secure local destination in the system picker.

See [BACKUP_FORMAT.md](docs/BACKUP_FORMAT.md) for the exact version-2 format and P3 compatibility, limits, validation, portability, cancellation and crash-recovery design.

## Room architecture and migrations

The simple P1/P2 structure remains: `data/local` entities/DAOs/database, repositories, Android-independent domain models/use cases, Compose screens/navigation/ViewModel, photo handling and utilities.

Room is now **version 4**:

- Existing explicit `1 → 2` migration remains unchanged.
- `RecoverySchema.MIGRATION_2_3` adds `session_resolutions` and `restore_marker`, a unique date/session index, immutable audit guards, and resolution-aware attendance/legacy overlap guards.
- `ActivitySchema.MIGRATION_3_4` adds title, optional time and edit revision with a legacy-title backfill. P1/P2/P3 attendance tables are not rewritten or deleted by migration. Genuine timestamps/photo paths remain intact.
- All four schema JSON files under `app/schemas` were generated by the real Room processor. No destructive migration is configured.
- Recovery and writes use transactions and a shared write gate. Backup snapshots/file reads and restore apply are serialized against mutations.
- Confirmed event and resolution deletion is available only inside the explicitly confirmed, validated REPLACE transaction. Ordinary DAOs remain protected by immutable triggers.

## Project notes

P3 recovery and restore architecture is retained. P4 files, limits, tests and next milestone are described in the P4 implementation document linked above. The existing legacy P1 closing flow remains unchanged; recovery/skip actions target the four-event sessions.
