# P5 / P5.1 — Android home-screen widget

P5 adds a read-only, offline Jetpack Glance home-screen widget to My OJT Companion. It displays completed and remaining OJT hours, a capped percentage/progress bar, the Manila attendance date/status, and Attendance/Activities navigation shortcuts. Application ID, version metadata (0.4.0/code 4), Room database name/schema version 4, migrations, and attendance write rules are unchanged. No installation, device changes, commit, push, or deployment was performed during implementation.

## Files

Created (Kotlin paths relative to `app/src/main/java/com/lazarus/personalojtdtr`):

- `domain/model/WidgetSnapshot.kt`
- `widget/OjtProgressWidget.kt` and `widget/WidgetUpdates.kt`
- `app/src/main/res/xml/ojt_widget_info.xml`, `layout/widget_loading.xml`, `drawable/widget_background.xml`, `drawable/widget_button.xml`, `values/widget.xml`, and `values-night/widget.xml`
- `app/src/test/java/com/lazarus/personalojtdtr/domain/usecase/WidgetSnapshotTest.kt`
- This guide.

Modified: `MainActivity.kt`, `OjtApplication.kt`, `data/repository/OjtRepository.kt`, `ui/navigation/OjtNavigation.kt`, `util/FutureIntegrations.kt`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `tools/check_cached_kotlin.py`, and `README.md`.

## Architecture and business rules

- `domain/model/WidgetSnapshot.kt` defines immutable widget inputs, presentation formatting, status mapping, the next Manila midnight, and an allowlist of shortcut destinations. It calls `DailyAttendance.sessions(events, resolutions)` and `AttendanceCalculations.summary(legacy + derivedSessions, requiredHours)`, exactly as OjtViewModel and Dashboard do. It uses the existing `hoursLabel` formatter. There is no second attendance calculator.
- `OjtRepository.widgetInputs()` reads profile, legacy sessions, photo-confirmed events, and resolution actions inside **one Room read transaction**. A widget cannot observe half of a committed REPLACE restore. Missing profile means zero required hours. No database schema or DAO changes are needed.
- `OjtRepository.widgetChanges` uses Room invalidation for profile, legacy attendance, events, resolutions, and the restore marker. Duplicate snapshots are suppressed. An application-scope observer requests persistent refresh work; a live Glance composition also observes these snapshots while its session is running.
- `widget/OjtProgressWidget.kt` provides responsive Glance content and `OjtWidgetReceiver`. Every fresh session reads Room. Only a refresh revision is stored in Glance preferences; attendance data is not copied into a second database or preferences cache.
- `widget/WidgetUpdates.kt` implements the existing `WidgetPublisher` boundary as `OfflineWidgetPublisher`, a serialized WorkManager refresh worker, an inexact midnight alarm, and a boot/time-change receiver. Glance 1.1.1 was already present. WorkManager 2.10.1 is now an explicit dependency because the app uses its APIs directly.

Only completed actual intervals receive hours. Active intervals contribute zero; P1 break deductions, overlap guards, P2 pair rules, and P3 incomplete/skipped resolutions retain their existing effects. Remaining hours clamp to zero, progress caps at 100%, and zero/unset required hours display 0% safely while retaining any genuinely completed hours. Large layouts explain how to set a missing goal.

Normal status sequence is Not Started → Morning In → Morning Completed → Afternoon In → Completed. Skipped mornings and finished days with skips are labeled explicitly. Unresolved prior-day time-ins show Recovery needed. P1 sessions have separate legacy timed-in/open/completed labels. Status presentation uses `DailyAttendance.next` and `AttendanceRecovery.unfinishedBefore`; it does not invent events or resolve sessions.

## P5.1 minimalist visual redesign

P5 was subsequently built and installed successfully on the Samsung Galaxy A06, as confirmed by the user. P5.1 changes only the widget presentation and resources. Existing Room inputs, domain mapping, calculations, receivers, WorkManager refreshes, midnight scheduling, and explicit Attendance/Activities intent actions remain unchanged. No application screens or dependencies were changed for this redesign.

P5.1 files: `widget/OjtProgressWidget.kt`, `res/values/widget.xml`, `res/values-night/widget.xml`, `res/layout/widget_loading.xml`, and this guide were updated. Added `res/layout/widget_progress.xml`, `res/drawable/widget_progress_track.xml`, `res/drawable/widget_ic_attendance.xml`, and `res/drawable/widget_ic_activities.xml`. Resource paths are relative to `app/src/main`.

### Palette and themes

| Role | Light | Dark |
| --- | --- | --- |
| Background / quiet shortcut surface | `#F8F6F4` | `#211B1D` |
| Primary text | `#202020` | `#FFFFFF` |
| Secondary text | `#757575` | `#B8AFB1` |
| Progress / small outline icons | `#8B1717` | `#C94B50` |
| Muted progress track | `#E7DEDB` | `#443237` |

Night-qualified Android resources select the dark palette. Both themes use the same hierarchy, rounding, and layout. The restrained maroon accent provides an academic identity without logos, gradients, shadows, decorative borders, gold, or additional accent colors. No custom font or icon dependency is added.

### Hierarchy and responsive behavior

| Responsive breakpoint | Intended launcher size | Content |
| --- | --- | --- |
| 250 × 148 dp | Approximately 4 × 2 | Small title, emphasized completed hours with a secondary goal when it fits, progress/percent, dated attendance status, two accessible icon shortcuts |
| 250 × 224 dp | Approximately 4 × 3 | Larger completed hours and secondary goal, remaining hours, progress/percent, Today's Attendance with date/status, labeled outline-icon shortcuts |
| 320 × 280 dp | Approximately 4 × 4 | Large layout plus a Completed OJT hours caption and more section spacing |

The provider's existing sizes, minimum dimensions, cell requests, and resize behavior are unchanged. The large content activates at 224 dp height; the more spacious variant activates at 280 dp. Grid spans are launcher-dependent, including One UI grid/display zoom settings.

The completed value has the strongest emphasis (24/30/36 sp); the required value is smaller and secondary. All values come from the existing snapshot. Only redundant zero minutes are hidden (`240h 0m` becomes `240h`); nonzero minutes remain visible. When long values cannot share one row, the goal is omitted from the compact row and remains available in the large layout's secondary line. Zero-goal guidance remains in larger layouts. No percentage, duration, or attendance business rule changes.

Progress is a thin **4 dp** rounded track with the existing percentage beside it. A small XML-backed Android `ProgressBar` is embedded through Glance `AndroidRemoteViews` to provide rounded ends on API 26+, rather than relying on API 31-only clipping. The scale drawable sizes the existing progress fraction, and `setProgressBar` updates without requesting animation. The visible percentage supplies the accessible progress text.

Shortcuts use matching 16 dp outline calendar/check and journal icons. Compact shortcuts show just the icons with Attendance/Activities accessibility descriptions; larger shortcuts include text. Both keep 48 dp touch targets and the existing direct Activity intents. Shortcut backgrounds match the widget surface for a quiet appearance. The title stays 12 sp, medium weight; status is visually subordinate to hours. The snapshot date remains visible if Android delays a refresh.

Spacing uses 8 dp for related content and 12 dp between sections when height permits. Large layouts use 16 dp padding; compact layouts use 12 dp horizontally and 8 dp vertically to protect legible values and usable shortcuts. Existing rounded backgrounds are retained. Secondary details are reduced in compact layouts instead of shrinking all text. At unusually large accessibility font/display scaling, enlarge the widget to avoid clipping; verify this on the A06.

### P5.1 validation and Samsung A06 checks

P5.1 does not add business-rule tests for visual-only changes. The existing supplemental suite covers snapshot values/statuses. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` and use the existing supplemental compiler/resource checker if sandbox execution is blocked. The user-confirmed successful P5 build does not establish that the P5.1 redesign has been built or visually verified.

P5.1 implementation-session checks passed: **161 supplemental JVM tests**, application resource compilation/linking and R generation, Kotlin/Compose/Glance compilation, and real Room/KSP generated-source compilation. The requested Gradle command was blocked by a read-only wrapper cache; an installed-Gradle retry with writable caches was blocked before configuration by the sandbox wildcard-IP restriction. No fresh P5.1 APK build or device visual verification is claimed. A file-hash comparison confirmed the pre-redesign functional widget code, state/calculations, navigation, manifest, provider sizes, build configuration, and Room schemas were preserved.

After an in-place update with the same signing key (do not uninstall or clear app data):

- [ ] Open the app and return Home to request a widget refresh. Verify the maroon/neutral palette in both light and dark mode, including the thin progress track and icons.
- [ ] Resize the existing widget through roughly 4 × 2, 4 × 3 and 4 × 4. Confirm completed hours dominate, the required value is secondary, the percent is readable, and the dated status fits without overlapping the shortcuts.
- [ ] On compact widgets, tap each icon and verify Attendance/Activities opens correctly; enable TalkBack and confirm each icon is named. Larger widgets must show both shortcut labels and remaining hours.
- [ ] Compare completed, required, remaining, percentage, and status with the existing app. Verify zero required hours and completed hours above the goal still display safely. Check minute precision on actual records with nonzero minutes.
- [ ] Check the smallest supported size, larger accessibility fonts, display zoom, 0%/partial/100% progress, and long recovery/legacy status labels. Enlarge the widget if the launcher/font configuration requires more room.
- [ ] Confirm normal attendance/profile changes still refresh all installed widget instances. No extra polling, network work, or photo display should appear.

## Data update behavior

1. Confirmed morning/afternoon time-in/time-out, legacy time-out, recovery/skip actions, required-hours changes, and relevant REPLACE restore commits invalidate Room tables. The application observer requests unique WorkManager work. This does not depend on a Dashboard or Activity remaining visible.
2. Work requests use `APPEND_OR_REPLACE` so a change arriving while a refresh is running gets a subsequent refresh rather than being dropped. Each job invalidates the refresh revision and updates every installed instance. Failed dispatches retry up to three times; no row data or exception details are put into worker output/logs.
3. Widget creation, system widget updates, and app foreground entry request fresh state. Reads come from Room, including after process recreation. WorkManager persists enqueued work across process death/reboot.
4. A single `AlarmManager.RTC` **non-wakeup, inexact** alarm requests an update at the next **Asia/Manila** midnight and schedules the following midnight. This is the attendance calendar even when the device time zone differs. It does not require exact-alarm permission or wake a sleeping phone solely to refresh the widget.
5. Boot completed, app package replacement, and system clock/time-zone changes rebuild the midnight schedule and request refresh. `RECEIVE_BOOT_COMPLETED` is the only new app-declared permission.
6. The platform's widget update period is once per day as reconciliation if a process dies between a database commit and observer delivery, or another trigger is lost. There is no minute/hour polling or continuously running service. Removing the last widget cancels the midnight alarm and pending refresh chain; no widget jobs are requested when none are installed.

Android may defer WorkManager, widget sessions, and inexact alarms under Doze, battery saver, One UI sleeping-app restrictions, or background execution limits. A midnight refresh is **not guaranteed at exactly 00:00**; the visible date remains part of the status to distinguish older content. A commit immediately followed by process termination before its invalidation is delivered is reconciled on the next app/widget/system refresh. Force-stopping an app prevents background delivery until it is launched again. Open the app and return home to request a fresh read. No device timezone or clock changes are made by the app.

## Navigation behavior

Both shortcuts use Glance `actionStartActivity` with explicit intents to MainActivity. Distinct allowlisted intent actions prevent Attendance and Activities PendingIntents from sharing an identity. No broadcast/service trampoline launches the Activity.

Cold starts and `onNewIntent` warm starts are handled. Navigation waits for local data loading and active save/restore operations, then selects the existing `attendance` or `activities` route. A consumed request is cleared to avoid replay on recreation. Repeated shortcuts are supported and the usual Home/back-stack behavior remains. Activities clears an old selected detail while retaining any existing editor draft. Navigation itself does not open a new camera or save/discard attendance or activity records.

## Integrity and privacy

The widget has no time-in/time-out, skip, restore, or edit actions. It never writes attendance, captures a photo, requests camera permission, performs cloud/network calls, or reads photo bytes. Widget UI contains no display name, journal description, photo, private path, or individual attendance timestamp. Work input/preferences contain no personal records. The fallback says to open the app if local progress cannot be read; it does not show fabricated zero totals on read failure. Hours/status are visible to anyone who can see the home screen, as expected for this widget.

## Validation

Run the normal checks from the project directory:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
python3 tools/test_database_guards.py
python3 tools/test_event_migration.py
python3 tools/test_recovery_migration.py
python3 tools/test_activity_migration.py
```

`WidgetSnapshotTest.kt` adds 24 host/JVM tests for shared-calculator parity, progress/remaining caps, zero/unset goals, formatting, all normal statuses, skip and recovery states, legacy sessions, overlapping intervals, midnight/year rollover, shortcut action identity/allowlisting, and changed/restored inputs. Existing P1–P4 tests remain.

Implementation-session results: 161 supplemental JVM tests and 48 SQLite tests passed. Application resource compilation/linking and real R generation, Kotlin/Compose/Glance source compilation, and real Room 2.8.3 KSP/generated-source compilation also passed. Exported Room schemas remain unchanged. The Gradle wrapper was blocked opening a lock in the read-only user cache. Direct installed Gradle 8.13 with writable temporary caches was blocked before configuration by `FileLockContentionHandler: Could not determine a usable wildcard IP for this machine`. This is an environment limitation; no fresh Gradle APK build or Gradle test success is claimed for P5.

The supplemental `tools/check_cached_kotlin.py` can check the installed cached dependencies, application resource compilation/linking and generated R, Kotlin/Compose/Glance source compilation, and real Room/KSP generated source compilation. It does **not** validate merged dependency resources/manifests, D8/R8 packaging, WorkManager delivery, launcher rendering, PendingIntent navigation, or real-device Room behavior. These require the normal build and device checklist.

## Manual Samsung A06 checklist

Use the same application ID and signing key for an in-place debug update after a successful local build. Do not uninstall, clear data, or reset the database. Keep a secure offline backup before any optional restore test.

- [ ] Build and run unit tests with the normal Gradle command above. Confirm P1–P4 attendance, activity editing/camera/picker, history and profile still work and existing records remain.
- [ ] After installing the update normally, long-press empty Home space → Widgets → My OJT Companion. Add the widget. Check initial progress matches Dashboard exactly; add a second instance and confirm both agree.
- [ ] Resize through roughly 4 × 2, 4 × 3 and 4 × 4. Verify title, large hour values, dated status, progress and both 48 dp buttons fit at the A06's usual grid/font/display zoom. Test portrait, larger fonts and light/dark mode. No personal photo/name should appear.
- [ ] Enable airplane mode and verify initial rendering, updates, and both shortcuts. Test cold start, backgrounded app, repeated Attendance/Activities taps, Back, rotation, and tapping while a save/restore finishes. Confirm distinct destinations and retained unsaved journal drafts. Opening Attendance must not start a new capture automatically.
- [ ] Observe Not Started, genuine photo-confirmed Morning In, Morning Completed, Afternoon In and Completed over normal attendance use. Return Home after each action; compare completed/remaining hours against Dashboard. An open session must not continuously accumulate credited hours.
- [ ] On appropriate genuine attendance days, verify morning/afternoon skip labels and prior-day incomplete recovery. Confirm recovery changes update both widget instances and do not add hours for incomplete intervals. Verify any retained legacy open/closed sessions display appropriately.
- [ ] Change required hours in Profile: test zero, below already completed hours, and above completed hours. Expect safe 0% at zero; otherwise no negative remaining hours or percentage above 100%.
- [ ] Check a natural Manila midnight: yesterday's Completed becomes the new day's Not Started; an unresolved time-in becomes Recovery needed. Sleeping/battery saver may delay the update. Reopen the app to reconcile. Prefer observing a real rollover rather than changing a phone clock containing genuine attendance data.
- [ ] Remove the app from Recents, allow ordinary process recreation, and reboot. Confirm records persist and the widget eventually rereads Room with the right date. Force-stop is a separate Android restriction: reopen the app afterward and verify reconciliation.
- [ ] Only on a separate/safe installation or with explicit readiness to replace current data, verify a validated backup restore refreshes totals and status for both widgets. Restore replaces local data; widget validation does not itself authorize replacing valuable attendance records.
- [ ] Remove one instance and confirm the other still updates. Remove the last instance; re-add later and confirm a fresh Room read and resumed day refresh.

P5 is ready for local build and Samsung A06 validation. Launcher layout, background delivery/reboot behavior, and actual shortcut intents have not been exercised on a device during implementation.

## Android references

- [Glance state and updates](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)
- [Responsive Glance layouts](https://developer.android.com/develop/ui/compose/glance/build-ui)
- [Direct Activity actions](https://developer.android.com/develop/ui/compose/glance/user-interaction)
- [Inexact alarm delivery](https://developer.android.com/develop/background-work/services/alarms)
