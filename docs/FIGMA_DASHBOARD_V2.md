# Dashboard refinement V2

## Direction and scope

The supplied Figma arrangement is an information-layout reference, not a pixel-perfect styling target. V2 retains the header arrangement (avatar/profile shortcut, name, BSIT logo), progress, four attendance slots, recent activities, and five destinations in the same order. The local design source contains a personal display name and prototype attendance values and remains excluded from version control.

Only Dashboard, its local visual helpers, and the shared bottom bar changed. `OjtNavigation.kt` and the P9.2 `selectTopLevel` navigation policy were not edited for V2. No global theme, other screen, PDF export, Room schema, stored record, calculation, backup, widget or preference changes.

## Visual improvements

- Neutral theme-aware page and surfaces replace maroon panel backgrounds and nested red cards.
- One subtly elevated progress card, a plain two-column metrics row, four outlined attendance slots, and a single recent-activity list with light separators.
- Completed hours are now a prominent visible number alongside required/remaining hours and percentage; all values still come from the existing calculator.
- Consistent 20dp page padding, 24dp section gaps, readable Material typography, and restrained rounded corners.
- Compact header avatar icon and 68dp BSIT logo maintain identity without dominating the page.
- Maroon accents are limited to progress, actions and active navigation. Dark mode uses a lighter contrast-safe brand ink. Gold is primarily retained in the BSIT logo rather than repeated as decorative borders.
- Attendance slots explicitly distinguish Morning in/out and Afternoon in/out, displaying saved time and Recorded/Not recorded status.
- Recent activity rows use actual thumbnails when available, otherwise a simple document icon; full details remain a tap away.
- Standard Material 3 NavigationBar on a neutral surface. Only the active tab has a tonal indicator. All labels remain visible and each tab retains Material touch/selection semantics.

## Functionality and themes

The existing AttendanceCalculations.summary and DailyAttendance.daily calls are unchanged. No example records are inserted. Header and required/remaining metric shortcuts still open Profile; attendance slots and View open Attendance; recent View/rows open Activities or the saved entry.

Thumbnail decoding remains read-only, EXIF-aware, aspect-preserving, off the main thread and bounded to 240 pixels. Missing/corrupt images display a compact camera fallback with an unavailable accessibility description, avoiding clipped error text in a thumbnail. The four attendance records and two recent rows bound the number of displayed thumbnails.

Light, Dark and System Default use the existing theme selection and system-night behavior. No preference is changed automatically. Large text and long names wrap; the page scrolls instead of compressing everything into one viewport. One-line journal previews use ellipsis while keeping full text in details. Bottom labels use one fitted size based on the longest destination name and available tab width, keeping every name on one line. At larger font settings only these space-constrained labels stop growing once the available width is used; Dashboard text continues to follow the requested font scale.

## Files changed for V2

- `app/src/main/java/com/lazarus/personalojtdtr/ui/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/lazarus/personalojtdtr/ui/dashboard/DashboardDesign.kt`
- `app/src/main/java/com/lazarus/personalojtdtr/ui/navigation/DashboardBottomBar.kt`
- `app/src/androidTest/java/com/lazarus/personalojtdtr/ui/dashboard/DashboardVisualTest.kt`
- `docs/FIGMA_DASHBOARD_V2.md` (new)

Existing uncommitted P8, P9, P9.1, P9.2 and V1 work outside these scoped files is preserved. No uninstall, data clearing, commit or push.

## Validation

Six Dashboard instrumentation tests use in-memory fixtures only. Five cover: Light, Dark/empty, 1.5× text/long name, System Default/progress values, and a saved attendance time with an unavailable photo. Screenshots are written only to app cache with the `dashboard-v2-` prefix. Existing seven controller and seven real-touch tests cover all five tabs, shortcuts/Home, rapid/repeated navigation, Back, nested screens, recreation and theme recomposition.

### Resume validation — 2026-10-27

The visual implementation, real-data bindings, theme support, vertical scrolling, fixed Scaffold bottom bar and P9.2 navigation policy were already complete before resuming. The final fitted bottom-label implementation had compiled but still needed final device verification. Inspection found Gradle idle and no active instrumentation run before starting validation. No production source changes were needed during this resume.

Added `testCompactLargeTextScrollKeepsNavigationFixed`: renders the existing Dashboard at 320dp width and 1.5× font scale, scrolls to the final journal entry, verifies the selected Home tab's screen bounds remain unchanged, and verifies the final entry is visible above the bottom bar. Fixtures remain entirely in memory.

Results on the connected Samsung Galaxy A06 (SM-A065F):

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — **BUILD SUCCESSFUL**, 2 seconds; all 45 tasks up-to-date. Existing JVM result XML confirms **218 tests, zero failures/errors/skips**; Gradle reused those valid results because production/JVM inputs were unchanged.
- `./gradlew :app:assembleDebugAndroidTest` — **BUILD SUCCESSFUL**, 9 seconds; 5 tasks executed, 45 up-to-date.
- `DashboardVisualTest`, `BottomNavigationTest` and `BottomNavigationTouchTest` via Android instrumentation — **20 tests passed**, 80.611 seconds. This includes Light/Dark/System appearance, real calculator output from fixtures, all five tabs, repeated/rapid switching, Home return, nested screens, Back and recreation.
- Fresh large-text and compact-width bottom-of-scroll screenshots were inspected. All five navigation labels fit on one line, the active Home pill remains clear, and the final activity is reachable above the fixed bar. The compact test's unused strip outside its 320dp container is test framing, not a production layout change.
- App and test APK installation with `adb install -r` both returned **Success**. The current debug app was installed in place; no uninstall, storage clearing, database migration or preference changes were performed.
- `git diff --check` passed. Resume changes are limited to the Dashboard visual test and this document; production source and all other preexisting work remain unchanged.

Logs: `/tmp/dashboard-v2-resume-build.log`, `/tmp/dashboard-v2-resume-device-build.log`, `/tmp/dashboard-v2-resume-device.log`. Screenshots: `/tmp/dashboard-v2-large-text.png` and `/tmp/dashboard-v2-scroll-bottom.png` (synthetic fixtures only).

## Limitations and review

- Readable typography and whitespace mean recent activities can fall below the first viewport; scrolling is intentional.
- No new profile-photo field was introduced; the avatar is a profile shortcut icon.
- Thumbnail failures are represented by a small fallback; opening the existing detail/attendance screen provides the fuller photo view.
- Test screenshots contain synthetic in-memory fixtures; the installed Dashboard displays only the user's actual saved data.
- System Default is tested against the device's current system appearance, with explicit Light/Dark tested separately; the tests do not modify system settings or stored preferences.
- Review the installed app in both themes, switch all five tabs, and compare real counts/photos with the existing detail screens. Large-text previews remain available in test cache for inspection.
