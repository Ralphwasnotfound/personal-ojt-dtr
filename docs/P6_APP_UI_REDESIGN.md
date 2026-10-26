# P6 — Complete Android app UI/UX redesign

P6 replaces the app's repeated outlined cards with typography-led sections, compact journal rows, subtle dividers, and a shared attendance timeline. It is a presentation update to the existing offline Android app. Room remains schema 4, application ID/version metadata are unchanged, and P5.1 widget source/resources remain untouched.

## Files

Created:

- `app/src/main/java/com/lazarus/personalojtdtr/ui/theme/DesignTokens.kt`
- This guide.

Modified under `app/src/main/java/com/lazarus/personalojtdtr/ui`:

- `theme/Theme.kt`, `Components.kt`, `AppIcons.kt`
- `navigation/OjtNavigation.kt`
- `dashboard/DashboardScreen.kt`, `attendance/AttendanceScreen.kt`
- `activity/ActivitiesScreen.kt`, `history/HistoryScreen.kt`, `profile/ProfileScreen.kt`
- Supporting `attendance/AttendancePhoto.kt`, `attendance/CameraCaptureScreen.kt`, `activity/ActivityCameraScreen.kt`, and `activity/ActivityPhotoViewer.kt`

README links to this guide. No dependencies, manifests, ViewModels, repositories, domain calculators, database definitions, migrations, or widget implementations were changed for P6.

## Design system

Palette primitives live in `DesignTokens.kt`. `Theme.kt` maps them to Material 3 semantic roles so screens do not hardcode colors. Dynamic wallpaper colors do not replace the approved palette.

| Role | Light | Dark |
| --- | --- | --- |
| Primary maroon | `#8B1717` | `#C94B50` |
| Dark maroon / primary container ink | `#5A1018` | Dark maroon container |
| Background | `#F8F6F4` | `#171517` |
| Surface | `#FFFFFF` | `#252124` |
| Primary text | `#202020` | `#FFFFFF` |
| Secondary palette | `#757575` | `#B8AFB1` |
| Divider | `#E7E1DE` | `#40383C` |
| Optional gold | `#D4A43A` | `#D4A43A` |

Maroon is reserved for primary actions, progress, active navigation and selected controls. Neutral surface/container roles avoid Material's default colored surface casts. Surface tint is transparent; navigation has zero tonal elevation. Gold is available as a tertiary accent, not used to decorate ordinary statistics or rows. There are no gradients, added shadows, font libraries or icon dependencies.

Contrast-specific semantic variants are intentional: light small secondary text uses `#6B6266` rather than the softer `#757575` on the warm background; dark small action labels and focused field labels use `#E0787E` maroon ink. Filled dark primary buttons retain `#C94B50` with white text. Checked contrast ratios include white on light maroon **9.41:1**, white on dark accent **4.56:1**, light secondary ink on the warm background **5.47:1**, and dark secondary text on the dark background **8.48:1**. Standard Material disabled/interaction state treatments remain; device accessibility checks are still needed.

### Typography, shape and spacing

System sans-serif fonts are used. Prominent progress uses 36 sp / 44 sp line height; page headings 26/34; section headings 16/24; body 16/24 or 14/22; supporting copy 12/18. Headings use medium/semibold weight and semantic heading markers. Text sizes remain in sp and follow system font scaling.

`AppSpacing` defines 4, 8, 12, 16, 24 and 32 dp. Standard page padding is 16 dp; related controls use 8 dp and major sections 24 dp. Camera and supporting dialogs follow 12/16 dp padding. Material shapes use 8/12/16/24 dp rounding. No fixed-height container surrounds long text; main forms/pages scroll and lists remain lazy. Native Material touch targets remain available.

## Shared components

- `PageHeader`: page title, concise supporting copy and heading semantics.
- `Page`: scrolling, keyboard-aware page padding and consistent section spacing.
- `SectionHeading` / `Section`: borderless groups with optional heading actions.
- `EmptyState`: concise copy and an optional clear next action.
- `TimelineRow`: small completed/available/pending indicator, subtle connecting line, event name, explicit status, recorded timestamp and optional evidence controls. Color is never the only status cue.
- `SettingsActionRow`: text-led settings actions with small outline icons and usable row targets.
- `QuietButton`, `SecondaryButton`, `appTextFieldColors`: centralized readable maroon action/focus colors using the existing Material 3 components.
- `ActivityListRow`: clickable, divided journal row with date, title, description preview and optional 56 dp thumbnail.

The obsolete `InfoCard` and `ActivityCard` presentation functions were removed after their references were replaced. Existing stroke icons are reused; small check/export/restore paths were added. No bitmap asset or decorative logo is introduced.

## Dashboard

There is one progress section: prominent completed hours, a thin horizontal progress indicator, percentage, then required/remaining hours. The same `AttendanceCalculations.summary` inputs and calculation are retained. A missing goal offers a Profile shortcut.

Today's Attendance uses the existing P5 domain status mapping, shows recorded events/times and resolution labels, retains morning/afternoon completed totals and active-session detail, and offers an Attendance shortcut. Recent activities use compact divided journal rows with a View all shortcut and a concise empty-state action. Statistics are not split into separate cards.

## Attendance

The four genuine events remain Morning Time In → Morning Time Out → Afternoon Time In → Afternoon Time Out. They appear as a connected vertical timeline. Each event has a textual status: Completed, Available, Pending, Recovery required, or the existing session resolution label. Recorded times and compact evidence previews remain available with View photos and manual Share photo actions.

One primary capture button represents only the existing `DailyAttendance.next` result. It retains the busy, earlier-open-session and legacy-session gates. No event can be captured by tapping an arbitrary pending row. Legacy closure keeps its actual-break validation and original callback. Previous-day recovery, skip controls, immutable audit confirmation and zero-hour explanations remain. Reducing preview sizes does not change, overwrite or delete the stored photographs.

## Activity journal and editor

The list uses date headings and divider-separated rows instead of outlined cards. Rows retain actual dates, optional times, titles, description previews, photo counts and optional thumbnails. Lazy loading and existing ordering are preserved.

The editor uses filled fields, a separate attachment section, wrapping Camera/Choose photos controls, and a single persistent Save action in its header. Validation limits, required title/description, date range, optional time, five-photo limit, picker selection behavior and ViewModel/SavedStateHandle ownership remain. Attachment viewing, ordering/removal, confirmed discard, confirmed deletion, revision checks and all sharing paths remain. Attachment wrappers no longer use cards. Detail, camera and full-screen photo dialogs inherit the new background/typography without changing their lifecycle or storage behavior.

## History

History remains chronological, grouped by the original Manila dates. Each date has a compact completed-hours summary, preserved legacy hours, resolution audit details, and event timeline rows. Compact previews retain View photos and Share photo actions. Existing daily/legacy calculations and explicit no-fabrication explanations remain. A first-use empty state links to Attendance. There is no new filtering, paging or conversion of old records.

## Profile and backup

Settings are grouped into Personal information, OJT requirements, Data & backup, and Application information. Fields remain limited to the existing name, required hours, optional start date and legacy break setting. Save validation is unchanged; fields and backup actions disable during a busy operation.

Export/restore are settings rows. Secure local storage warnings, validation preview, explicit REPLACE confirmation, cancellation and all existing record/photo counts remain. The old statement that widgets are planned is replaced with accurate application information. No account, cloud, unsupported personal field or new persistence is introduced.

## Navigation and supporting dialogs

All five routes (`dashboard`, `attendance`, `activities`, `history`, `profile`) remain unchanged. Existing outline icons, short labels, selection semantics, single-top behavior and restored tab state remain. The bottom bar now has a neutral surface, thin divider, maroon selection and zero tonal elevation. Scaffold applies safe drawing insets; child content consumes its padding to avoid duplicate inset handling. Pages/forms retain IME handling.

Widget shortcut request handling and direct Activity intents are unchanged. Dashboard and History add ordinary shortcuts into the existing Attendance route.

Attendance photo preview has visibly selected Original/Timestamped chips and scrollable dialog content; source selection and timestamped-photo sharing remain unchanged. Restore preview is spaced and scrollable. Existing confirmation/cancel controls, save/restore blocking, manual sharing, CameraX permission/retry/retake/confirm callbacks and full-screen photo navigation remain available.

## Validation and limitations

Run:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
python3 tools/check_cached_kotlin.py
python3 tools/test_database_guards.py
python3 tools/test_event_migration.py
python3 tools/test_recovery_migration.py
python3 tools/test_activity_migration.py
```

The requested Gradle command is blocked in this session by the read-only wrapper cache. Direct installed Gradle 8.13 with writable temporary caches is blocked before configuration by `FileLockContentionHandler: Could not determine a usable wildcard IP for this machine`. This does not establish an Android source failure, and no fresh P6 Gradle APK build is claimed.

Supplemental checks cover the existing JVM tests, Kotlin/Compose/Material 3/Glance source compilation, application resource linking/R generation, real Room/KSP generated source compilation, and SQLite guards/migrations. They do not replace merged manifest/dependency resource checks, D8/R8 packaging, runtime navigation, actual launcher rendering, permission UI, TalkBack, camera/gallery excursions or Android lifecycle validation. No device testing was performed for P6. A local build and Samsung A06 validation remain required.

Implementation-session results: **161 supplemental JVM tests and 48 SQLite tests passed**. Application resource linking/R generation, Kotlin/Compose/Material 3/Glance source compilation, and real Room/KSP generated-source compilation all passed. `git diff --check` passed. Protected business/data, ViewModel, manifest/build, schema, and widget files match the P6 starting snapshot. The normal Gradle build and device behavior remain unverified in this environment.

No tests mirroring reversible layout implementation were added. Existing domain tests and source/resource checks are used to detect regressions. File hashes are compared with the P6 starting snapshot to verify that business/data code and all widget files remain unchanged. Nothing is staged, committed, pushed, deployed, uninstalled, cleared or restored during this phase.

## Samsung Galaxy A06 validation checklist

Use a successful normal Gradle build and the same application ID/signing key for an in-place update. Do not uninstall or clear app data. Existing records and photo storage must remain present.

- [ ] Open every destination in light and dark modes. Confirm maroon accents, neutral surfaces, readable small text, clear active navigation and consistent spacing. There should be no repeated outlined statistic/event cards.
- [ ] Try default and larger system font/display sizes, gesture and three-button navigation, and portrait/landscape. Confirm content clears status/navigation bars, lists scroll, text wraps and keyboard-focused fields remain reachable. Verify all five tabs remain identifiable.
- [ ] Dashboard: compare completed/required/remaining/percentage with the previous version. Check unset and exceeded goals without changing genuine attendance records; inspect recorded events and recent activities. Test all Dashboard shortcuts.
- [ ] Attendance: check the four-event timeline, next-action availability, busy state, earlier-day recovery and legacy open-session blocking. Record genuine attendance only through capture → preview → explicit confirmation. Check retake, cancel, permission denial/retry and timestamp overlay.
- [ ] Open original and timestamped evidence, then share manually from Attendance and History. Verify both image modes remain available and the system Sharesheet still requires the user to choose the destination.
- [ ] On appropriate genuine attendance days, verify skip/incomplete confirmation and audit labels. Confirm no pending row can bypass event order and no recovery action invents an event/photo or adds hours.
- [ ] Activities: test empty/list/detail states, long titles/descriptions, thumbnail and no-photo entries, past dates and optional times. Confirm ordering and previews match the data.
- [ ] Create/edit an activity; leave for CameraX/Photo Picker and return. Try five attachments, reordering, removing, full-screen previous/next, and all sharing options. Confirm text and pending attachments survive normal recreation. Check Save validation, confirmed discard and confirmed deletion.
- [ ] History: inspect recent dates, incomplete/skip audit entries, legacy P1 records, cross-midnight legacy hours and actual timestamps. Compare totals with the previous app. Verify photo actions and scroll performance on a larger history.
- [ ] Profile: verify name, hours, optional start date and legacy break values persist, invalid values prevent Save, and saved goal changes update Dashboard/widget.
- [ ] Export a backup to a secure local destination. Inspect a restore preview and cancel to verify non-destructive cancellation. Test actual REPLACE only on a separate safe installation or with explicit readiness and a verified backup; UI testing alone does not authorize replacing valuable records.
- [ ] Navigate between tabs while editing/saving and after process recreation. Verify draft state, duplicate-tap guards and restoration of tab state. Check both widget shortcuts on cold and warm starts; P5.1 widget appearance and updates must remain unchanged.
- [ ] Enable TalkBack: verify page/section headings, selected tabs, event status/time, labeled photo controls and form error states. Check all actions can be discovered and activated without relying on color.

P6 is intended for local build and device validation, not claimed as device-verified or released.
