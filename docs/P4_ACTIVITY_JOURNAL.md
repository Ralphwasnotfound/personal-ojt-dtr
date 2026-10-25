# P4 activity journal

P4 extends the existing activity table and screens. Attendance event ordering, recovery, two-session hour calculations, original attendance photos and timestamp overlays are unchanged.

## Implemented

- Required title (1–120 trimmed characters), detailed description (1–10,000), Manila calendar date and optional minute-precision local time. Past dates are supported; activity dates are independent of capture dates.
- Add, detail, edit and confirmed delete. Dates sort descending, then optional time descending, creation timestamp descending and ID for stable ties.
- Up to five optional ordered photos: CameraX capture with preview/retake/use/cancel, or Android Photo Picker. Single-image picking is used when only one slot remains. Results are checked again in the ViewModel and repository, including picker fallbacks that return too many items.
- Editor text, date/time and attachment references live in a ViewModel with lightweight `SavedStateHandle` data. Camera/picker excursions and configuration changes keep the editor. System process recreation can recover saved editor state; force-stop, task removal or reboot are not a permanent draft guarantee ([Android saved state](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate)). Explicit Cancel confirms discarding changes.
- Gallery items are streamed into app-private storage immediately; saved records do not depend on the original URI grant. JPEG, PNG and WebP are supported, at most 20 MiB each, at most 16,000 pixels on either edge and 80 million pixels. HEIC/GIF and unsupported/corrupt images show an error instead of being silently accepted. No broad gallery/storage permission is added ([Photo Picker contracts](https://developer.android.com/training/data-storage/shared/photo-picker)).
- Originals preserve their bytes/EXIF orientation. Activity photos do not receive attendance overlays. Orientation-aware 320-pixel JPEG thumbnails serve new attachment/list previews. Restored/legacy images fall back to sampled background decoding. The list and detail views are lazy; full-screen viewing decodes one selected image at a bounded resolution.
- Photo previews, reordering, attachment removal, full-screen previous/next viewing, selected-photo sharing, text sharing, and multi-photo sharing with text. Shares use FileProvider `content://` URIs, ClipData and temporary read grants. Receiving apps may omit accompanying text. The app never chooses recipients or sends automatically.
- Dashboard displays at most three recent activities, links each detail, and provides View All. Existing hours/progress remain intact.
- Existing Material 3 light/dark colors, cards and typography are retained. Navigation has small code-native vector icons without another icon dependency. Editor/detail/photo dialogs support scrolling, keyboard insets and accessible action/image descriptions.

## Persistence, ownership and failures

The existing ordered `photoPaths` JSON list already represents zero to five attachments, so no separate photo table/database is required. Room version **4** adds `title`, nullable `timeMinutes`, and an optimistic edit `revision` to `daily_activities`. Explicit `3 → 4` migration derives a nonempty legacy title from the original description while preserving descriptions, dates, photo order and creation/update timestamps. Migrations `1 → 2` and `2 → 3` remain unchanged. All four exported schemas are produced by the real Room compiler.

Normal writes validate the five-photo limit, metadata and attachment ownership. New activity IDs are stable for the editor session; repeat creation cannot overwrite an existing row. Edits/deletes must match the revision originally opened. Database triggers reject stale revisions, changed creation timestamps, backwards update timestamps, blank titles/descriptions and invalid time values.

Managed storage:

```text
files/activity-drafts/<editor UUID>/<photo UUID>.<extension>
files/activity-photos/<SHA-256 of activity ID>/<generation UUID>/<photo UUID>.<extension>
files/activity-photos/<owner>/<generation>/<photo UUID>.thumb.jpg
```

Hashing the owner key keeps filenames safe while supporting valid older non-UUID record IDs. Retained attachments must already belong to the edited record; pending attachments must belong to that editor's private draft. File paths from picker metadata are never trusted.

A save validates/copies new originals and generates thumbnails before its Room transaction. Files and parent directories are synced first. Database failure removes only that attempt's prepared generation when rollback is known. If commit delivery fails, the stored row/revision is checked; committed files are retained, and an unknown outcome retains files and asks the user to reopen the record. Creation time is preserved on edit; update time comes from the device clock, which must not precede the saved update.

After commit/deletion, cleanup considers all attendance, legacy and activity photo references under the shared write gate. It only removes unreferenced files in activity-managed storage; restored shared assets and photos outside that root are never swept by activity deletion. Cancel only removes its own pending draft. Thumbnail cleanup failure cannot turn a committed save into a failed save.

Recent drafts are retained across recreation. Explicit Cancel/save removes the editor's draft; abandoned private drafts older than seven days are cleaned. This is temporary recovery, not a permanent draft archive. Cleanup can retry after storage errors; database records are never silently deleted by file cleanup.

## Backup compatibility

P4 exports **format 2 / schema 4**. Ordered activity asset references, title, optional local time, original photo bytes and creation/update timestamps are included. Derived thumbnails and transient drafts are excluded. Operational edit revisions reset to zero after an explicit REPLACE restore; editor/navigation state is reset as well.

P3 **format 1 / schema 3** archives remain readable. Their activity titles are derived from descriptions and optional time stays unset; genuine timestamps/photos are not invented. Legacy archives with more than five or duplicate activity photo references are rejected for review instead of silently truncating attachments. Existing database rows are preserved during migration even if historical data requires correction before a new save/export.

All P3 path/size/entry/CRC/SHA-256/record-validation safeguards, private staging, explicit confirmation, immutable photo generations, transactional replacement and rollback handling remain in place. New activity guards are dropped/rebuilt only inside the same confirmed restore transaction. See [BACKUP_FORMAT.md](BACKUP_FORMAT.md).

## P4 file map

Created:

- `domain/usecase/ActivityRules.kt`
- `data/local/ActivitySchema.kt` and real Room schema `4.json`
- `data/photos/ActivityPhotoStore.kt`
- `data/repository/ActivityJournal.kt`
- `ui/activity/ActivityViewModel.kt`, `ActivityCameraScreen.kt`, `ActivityPhotoViewer.kt`
- `ui/AppIcons.kt`, `util/ActivitySharing.kt`
- `ActivityRulesTest.kt`, `ActivityPhotoStoreTest.kt`, `ActivityBackupTest.kt`, `tools/test_activity_migration.py`

Modified: activity domain/entity/DAO/repository mapping, activity screen, Dashboard, navigation, main ViewModel/application wiring, shared page/photo components, CameraPreview visibility for reuse, FileProvider paths, backup records/codec/Room adapter, prior backup version fixtures, module version metadata, cached compiler helper, README and format documentation. No dependency was added. App version is `0.4.0` (code 4).

## Validation and remaining device work

Host Kotlin/JUnit tests, host SQLite migrations/constraints, real Room/KSP generation, generated source compilation, SDK resource linking and icon path parsing are local checks. They do not replace Android runtime/device validation.

P1/P2/P3 builds and the device behaviors listed in the task are user-confirmed. Actual P3 restore was not device-confirmed, and P4 does not claim it is. The phone was not accessed, modified or used for tests. No emulator/install/launch, commit/push or deployment was performed.

Next: run normal Gradle tasks, then manually verify P4 camera/picker returns, editor recreation, five-photo edits/deletes, orientation, caption/multi-photo sharing and a backed-up full restore on a separate/safe installation. P3 actual restore and P4 restore integration still need that validation. Optional follow-up work includes persistent draft management, HEIC conversion, undo/trash, and encrypted backups.
