# Figma Dashboard v1

## Reference and scope

The user-supplied 360 × 800 design reference was inspected directly. Its local PNG and Figma source contain a personal display name and attendance-style prototype values, so both reference files remain local and are excluded from version control.

Only Dashboard UI and the shared bottom navigation were changed. The P9.2 `selectTopLevel` implementation, route IDs, saved-state behavior, selected destination hierarchy, and busy-state guard are retained. Attendance calculations, CameraX, activity records, Room, exports, backup/restore, widget behavior, and preferences were not changed. Existing uncommitted P8/P9/P9.1/P9.2 work is preserved.

## Design implementation

- Off-white page with brand label, circular gray avatar placeholder, welcome/name text, and the existing BSIT logo.
- Three maroon panels in the supplied order: OJT progress, today's attendance, recent activities.
- Exact sampled colors: page `#F8F6F4`, panel `#5A1018`, inset `#8B1717`, gold `#D4A43A`, placeholder `#D9D9D9`, progress track `#997478`.
- 17dp left / 16dp right page margins, 10dp panel gaps and rounded corners, thin gold bottom accents, compact typography and View chips.
- Required/remaining metrics side by side, four attendance tiles in a 2 × 2 grid, and two recent activity rows with date/title/description, photo preview and arrow.
- Dashboard-specific outlined vector glyphs follow the reference shapes. Other screens' icon resources are untouched.
- Shared maroon bottom bar with gold top edge, red icon tiles, white outlined icons, and reference labels Home / Attendance / Activities / History / Profile. Whole tab regions remain at least 48dp tall and use selectable Tab semantics; selection also controls label/underline emphasis.

## Real data and behavior

The student's saved name, calculated hours/percentage, today's saved events, and two most recent saved activities populate the design. Descriptions are never fabricated. Empty values use “Welcome!”, “—”, “No photo”, and an empty-journal prompt. The screenshot's sample name/times/descriptions are not inserted into the database.

Photo previews read existing private paths through ActivityPhotoStore and use bounded decoding via the existing LocalPhoto component. Attendance tiles use only their saved event photo; journal rows use only the first actual journal attachment. No original photograph is modified.

The avatar/name area and required/remaining metrics open Profile. The attendance View chip and tiles (including camera artwork) open the existing Attendance screen, preserving the established capture/confirmation workflow. Recent View opens Activities; saved rows open the existing activity details. No new direct camera/shutter behavior was invented.

## Deliberate practical differences

- Native status/gesture/navigation bars consume space that is absent from the Figma artboard. The A06's logical width depends on its display settings, so flexible card widths differ slightly from the 360px reference while dp spacing is retained.
- The smallest reference captions are approximately 6px. App captions/navigation labels use 8sp or more, and full action regions are usable touch targets. The attendance heading/action region is slightly taller to accommodate the 48dp View target.
- Actual data differs from the sample. Missing evidence is explicitly labeled; the gray avatar remains a placeholder because the current profile has no avatar field.
- At enlarged font settings or with long names, text wraps and Dashboard scrolls. Recent titles/descriptions remain single-line previews with ellipsis; full saved text remains in activity details.
- Image previews fit without distortion rather than using the reference's placeholder artwork.
- Only a Light design was supplied. Dark mode retains the same layout and maroon/red/gold panels, with the existing dark page and appropriate header text color; saved theme preferences are unchanged.
- Icons are code-native vector reconstructions and Android uses its system sans-serif font. Glyph rasterization/line metrics can differ slightly from Figma.

## Files

Modified:
- `app/src/main/java/com/lazarus/personalojtdtr/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/lazarus/personalojtdtr/ui/navigation/OjtNavigation.kt` (bottom bar appearance and Attendance label only, on top of preserved P9.2 changes)
- `app/src/androidTest/java/com/lazarus/personalojtdtr/ui/navigation/BottomNavigationTouchTest.kt` (reference label and profile shortcut semantics)

Added:
- `app/src/main/java/com/lazarus/personalojtdtr/ui/dashboard/DashboardDesign.kt`
- `app/src/main/java/com/lazarus/personalojtdtr/ui/navigation/DashboardBottomBar.kt`
- `app/src/androidTest/java/com/lazarus/personalojtdtr/ui/dashboard/DashboardVisualTest.kt`
- This document.

## Validation

DashboardVisualTest uses only in-memory reference fixtures, captures Light/Dark/large-text screenshots in the app cache, and checks rendered content. It does not persist sample records or modify preferences. Its scroll helper permits a fully visible Dashboard at normal A06 display settings; large text remains scrollable.

The existing seven controller and seven physical-touch navigation tests cover all five tabs, shortcut return to Home, repeated/rapid switching, Back, nested navigation/export dialogs, selection, recreation and Light/Dark recomposition.

Actual validation on 2026-10-27:

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`: BUILD SUCCESSFUL (final build, 5 seconds).
- JVM results: 218 tests, zero failures/errors/skips.
- Samsung Galaxy A06 SM-A065F: `OK (17 tests)`, 65.458 seconds: three Dashboard visual/content tests, seven controller tests and seven actual-touch navigation tests.
- Final Light/Dark device screenshots inspected against the PNG, plus a 1.5× font-scale/long-name fixture. The normal-size fixture fits this device; the large-text fixture scrolls and reaches recent activities.
- `git diff --check`: passed.
- App and test APK installed in place with `adb install -r`. No uninstall, application-data clearing, database reset, commit, or push.
- One transient USB disconnect occurred after install; the device reconnected and the test suite completed successfully.
- The first two visual-fixture errors came from assuming a scrollable area must exist even when all content fits. That test-only assumption was corrected; final tests passed.

Synthetic screenshots remain in app cache as `dashboard-v1-light.png`, `dashboard-v1-light-lower.png`, `dashboard-v1-dark.png`, and `dashboard-v1-large-text.png`. These contain in-memory fixtures, not inserted journal records.

Device test command:

```sh
adb -s R8YY411LE3T shell am instrument -w -e class com.lazarus.personalojtdtr.ui.dashboard.DashboardVisualTest,com.lazarus.personalojtdtr.ui.navigation.BottomNavigationTest,com.lazarus.personalojtdtr.ui.navigation.BottomNavigationTouchTest com.lazarus.personalojtdtr.test/android.test.InstrumentationTestRunner
```

## A06 review

1. Open the installed app and select Home. Use Light mode for comparison with the supplied reference; no preference was changed automatically.
2. Compare the header/logo, progress metrics, four attendance tiles, recent rows, and bottom bar with the PNG.
3. Confirm values/photos match saved data. Use View/camera artwork to enter Attendance, and recent rows to open details.
4. Switch among all five tabs and return Home using its icon and label; confirm selected highlighting and Back behavior.
5. Check Dark mode and larger font size if desired. Scroll to the final recent-activity row when content exceeds the viewport.
6. Review Dashboard before proceeding with any other screen styling.
