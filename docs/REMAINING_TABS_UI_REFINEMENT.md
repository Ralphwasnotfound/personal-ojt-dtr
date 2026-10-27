# Activities, History and Profile UI refinement

## Design

These tabs use the same warm neutral surfaces, restrained maroon actions, subtle gold highlights and rounded cards as Dashboard V2 and Attendance. The shared bottom bar, theme implementation and navigation graph are unchanged. Each screen scrolls inside the app Scaffold content area so the bottom bar stays fixed.

## Activities

The journal list shows an entry count, an Export Journal action, and the existing Add activity button. Entries are date grouped cards with the saved title and description preview, optional time, up to three actual thumbnails, and the total photo count. Tapping a card opens saved details. Details retain the full saved description, all attachments, photo viewing, editing, deletion confirmation, sharing, and timestamps. The editor and its five-photo limit, camera and picker controls, ordering/removal, validation, confirmation and save workflow are preserved.

## History

History starts with the recorded-day count, Export DTR and total completed/required/remaining hours from the existing calculations. One card per date shows its total and morning/afternoon hours, followed by all four event timestamps and available photo previews with view/share actions. Recovery audit actions and legacy P1 session data, break and net hours remain visible with distinct status treatment. Empty history still links to Attendance. Monthly DTR preview, generation, save and share flow is unchanged.

## Profile

The header uses initials derived only from the saved display name and shows the saved required-hour target. One grouped form retains name, required hours, start date, legacy break, validation and Save. Appearance opens the same System Default, Light and Dark choices and uses the existing persisted theme callback. Backup export/restore entry points remain, with concise but explicit unencrypted-archive and replacement warnings. App information remains available.

## Shared status colors

`AppStatusColors` centralizes light/dark success ink. Maroon comes from the primary theme roles; gold comes from tertiary containers; warnings and recovery use the existing theme semantic roles. No database, calculation, attendance, activity or export logic changed.

## Validation

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — successful; 218 JVM tests passed.
- `./gradlew :app:assembleDebugAndroidTest` — successful.
- Samsung Galaxy A06: **26 instrumentation tests passed**. Fixture checks cover Activities add/detail/edit/delete confirmation and the five-photo picker limit, History summaries/photo viewing/sharing and DTR entry, and Profile details/theme selection at 1.35× text. The existing Dashboard, Attendance, controller and real bottom-navigation touch suites also passed. The three remaining-tabs tests passed again after the final test update.
- After the final concise backup/privacy copy update, the debug build and all 3 remaining-tabs fixture tests were run again successfully.
- Tests use in-memory UI fixtures and do not save or delete user records. Backup, restore and export generation are not activated during UI checks.
- App and instrumentation APKs installed in place with `adb install -r`; both reported Success. App data was retained.

## Manual review

Review saved activity thumbnails and full details, real attendance photos and recovery/legacy cases, and backup SAF flows on the A06. The fixture tests verify the controls and rendering paths without changing local records; they do not run a real camera capture, restore, or PDF save destination.
