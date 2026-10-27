# Attendance tab UI refinement

## Scope

The separate Attendance destination now uses a compact, theme-aware layout that matches the restrained surfaces and maroon accents of Dashboard V2. Dashboard sections, shared navigation, attendance calculations, database records, camera screens and recovery rules are outside this change. Dashboard received only small maroon, gold and status-color accents; its layout and Today's Attendance arrangement are unchanged.

## Screen structure

- A short Attendance header shows the live Manila date and a compact state: Not started, In progress, Complete or Recovery.
- A single summary surface shows total completed hours with separate morning and afternoon totals. Values come from `DailyAttendance.daily` exactly as before; the lunch interval is excluded by the existing calculation.
- Four event cards show Morning Time In/Out and Afternoon Time In/Out. Recorded time, pending/available/resolved state and the existing timestamped photo evidence are visible in each card.
- The next available capture action remains primary. It still uses the existing next-event sequence, prior-session and legacy-session gates and opens the existing CameraX capture flow.
- Historical incomplete sessions, legacy P1 closure, session skips, photo viewing/sharing, confirmations, busy-state handling and audit warnings remain available below the event section when applicable.
- The full screen scrolls inside the existing Scaffold content area; the shared bottom navigation remains fixed.

## Color and accessibility

The Dashboard retains its V2 structure. A small gold progress marker and warm percentage chip add brand color; event times and recorded/pending states use restrained maroon, green and gold status colors. Attendance uses neutral cards, maroon for the next action, green for recorded events, gold for pending states, and the theme error role for recovery. Colors derive from the app's Material theme where possible and adapt to Light, Dark and System Default.

Event cards reflow into two columns at the target width and allow labels and content to grow with font scaling. The page remains vertically scrollable. Existing photo descriptions and action labels are preserved for accessibility; warning and confirmation copy remains available at the point of recovery actions.

## Validation

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — successful; 218 JVM tests passed (cached/up-to-date where applicable).
- `./gradlew :app:assembleDebugAndroidTest` — successful.
- Samsung Galaxy A06 instrumentation: 3 Attendance screen tests cover the next capture callback/date, all four recorded events and calculated daily total, and Dark theme recovery content. They render inside the fixed shared bottom bar. The 6 Dashboard visual tests and 14 navigation/controller/touch tests also passed in the final run, **23 device tests total**.
- Debug and Android test APKs installed in place with `adb install -r`; both reported Success. App data was retained.
- Attendance test screenshots are saved in the app cache as `attendance-v2-light.png` and `attendance-v2-dark-recovery.png`; they contain fixture data only.

## Remaining device review

Review the Attendance tab with existing records and photos in Light, Dark and System Default. Confirm recovery and legacy-session messages against real scenarios when they occur. Instrumentation uses in-memory fixture records and does not create or alter actual attendance.
