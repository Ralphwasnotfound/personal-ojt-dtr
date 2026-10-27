# P8 — Reliability and data safety audit

Audit date: 2026-10-26. Target device: Samsung Galaxy A06 (SM-A065F).

## Baseline and scope

The working tree was clean on `main`, at the P7 checkpoint
`cb0baf18f9371ff03294dab3edff474bc5fe7b5b`. This audit changes source and tests only;
no commit, push, history rewrite, application install/uninstall, data clearing, or
restore against personal data was performed. P7 remains HEAD.

Methods: source tracing across storage, UI callers and lifecycle handling; focused
fault-injection JVM tests; existing host SQLite migration/constraint tests; Android
compilation. Compilation alone is not treated as evidence of runtime correctness.
`adb devices -l` identified a connected A06. No device application operations were
performed. There is no `app/src/androidTest` suite to run.

## Existing architecture

| Area | Storage and ownership | Integrity boundary |
| --- | --- | --- |
| Room | `my-ojt-companion.db`, schema 4; profile, legacy sessions, attendance events, resolutions, activities, restore marker | Explicit migrations 1→2→3→4; no destructive fallback |
| Attendance | Four immutable events; unique `(date,type)` and photo paths; instants stored as epoch milliseconds, local dates as epoch days | Domain validation, SQL triggers, transaction, repository mutex and capture mutex |
| Recovery | Immutable, unique daily/session resolution rows | Explicit skip or previous-day incomplete action; no fabricated Time Out or credited unfinished interval |
| Attendance evidence | Private `attendance-drafts`; published original/stamped JPEG pair under `photos/attendance/date/type/UUID` | Prepare files before insert; startup reconciles directories against all committed photo references |
| Activities | Room row with ordered JSON photo paths and optimistic revision; max five photos | Ownership/revision validation and transactional row writes; immutable new file generation before edit commit |
| Activity images | Private `activity-drafts`; published `activity-photos/ownerHash/generation`; separate thumbnails | Bounded import/decode, file/directory sync, cleanup after successful row commit |
| Backup | Private `backup-work` staging; ZIP manifest and assets; Android document picker export | Bounded archive validation, checksums, image decode, readback verification |
| Restore | Private immutable `restore-generations/UUID` plus Room marker | Prepare/fsync first; one transaction switches all records/references and marker; verify marker on ambiguous failure |
| Preferences | One Preferences DataStore named `appearance` | Atomic edits; default System; failed read/write shown without resetting Room |
| Widget | Reads a transactional Room snapshot; Glance preferences hold refresh revision only | WorkManager serialized refresh, bounded retry, boot/time/package broadcasts and inexact midnight alarm |

Photos are external to relational tables: SQLite cannot enforce file existence.
No foreign-key cascade connects profile updates to attendance or activities. File
ownership, record revision and reference reconciliation supply those protections.
Android automatic backup is disabled; the explicit ZIP workflow is the supported
portable backup. No network service is involved.

## Findings and implemented fixes

| ID | Severity | Confirmed failure boundary | Fix and verification |
| --- | --- | --- | --- |
| P8-01 | CRITICAL | `PhotoConfirmation` deleted prepared evidence on every insert exception, including a committed transaction whose result delivery fails. The row could remain with missing evidence. | A fresh database read now determines whether the exact event committed. Verified commit returns success and retains images; unreadable outcome retains images and reports uncertainty; only verified noncommit permits rollback cleanup. Tests simulate committed delivery failure and unavailable outcome read. |
| P8-02 | CRITICAL | Attendance synced JPEG bytes but not rename/mkdir directory entries before committing references. Abrupt power loss could leave a durable row pointing to nondurable names. | Flush event directory and every ancestor through private files root before insert, using Android `Os.fsync` in production. Sync failure aborts before insert and retains draft. Tests cover failure and required ordering. This closes the application-level ordering gap; hardware power-loss durability was not tested. |
| P8-03 | HIGH | Restore reset activity revisions to zero. A stale revision-zero editor could pass optimistic validation after restore, especially if process recreation occurs before the UI reset is delivered. Text-only edits and stale delete requests could overwrite/delete restored content. | Within the replacement transaction, existing activity IDs receive their previous revision plus one. Stale revisions are rejected; new IDs start at zero. Overflow aborts before replacement rather than wrapping. Tests cover stale revisions and overflow. Backup format/schema and user timestamps are unchanged. |

Six focused tests were added. Existing attendance rollback, duplicate confirmation,
retake, missing capture, draft cleanup and replay tests remain in place and pass.
The revision change is local concurrency metadata only, not an attendance rule or
backup format change. No database migration is required.

### Files changed

- `OjtApplication.kt`: wire Android directory synchronization for attendance.
- `data/photos/LocalAttendancePhotos.kt`: durable directory publication before insert.
- `data/photos/AttendanceRecorder.kt`: authoritative post-error commit lookup.
- `domain/usecase/PhotoConfirmation.kt`: conservative handling of uncertain commits.
- `data/backup/RoomBackupStore.kt`: invalidate existing activity revisions on restore.
- `domain/usecase/ActivityRules.kt`: checked restore revision advancement.
- `data/photos/PhotoConfirmationTest.kt`: four regression tests and explicit outcome callbacks for existing cases.
- `domain/usecase/ActivityRulesTest.kt`: two restore revision regression tests.
- `docs/P8_RELIABILITY_AUDIT.md`: this report.

Source paths above are relative to `app/src/main/java/com/lazarus/personalojtdtr`
and test paths to `app/src/test/java/com/lazarus/personalojtdtr`, respectively.

## Attendance and evidence audit

All four event types go through the same confirmation pipeline. Capture itself
creates no attendance row. The synchronous UI busy latch, recorder mutex, domain
replay checks and database uniqueness prevent repeated confirmation from inserting
the same event. SQL uses ABORT rather than replacement of existing evidence rows.
Validation is repeated in the Room transaction. Preparation failures leave no row;
post-commit cleanup failure does not report a failed save.

Chronology remains strictly increasing, with Manila (`Asia/Manila`) dates and +08:00
SQL guards. A capture from the previous local day cannot be confirmed for the next
day; backwards clock movement is rejected. Device timezone changes do not change
the established Manila business day. There is deliberately no noon cutoff; explicit
skip resolutions support afternoon-only attendance and incomplete recovery preserves
original events without inventing hours. Tests cover these rules and overlap with
legacy sessions. No calculation was changed.

Original JPEG bytes remain separate from a physically stamped JPEG. Existing EXIF
orientation handling and capture/confirmation timestamps are unchanged. Times still
come from the device clock, not a trusted external clock. Sharing uses FileProvider
`content://` URIs, ClipData and temporary read grants; missing images report failure
without deleting attendance. No `file://` exposure was found in these share paths.

CameraX binds to lifecycle, disposes its own use cases, and disables shutter/switch
while busy. Permission denial does not loop requests. Initialization has a timeout;
capture errors have explicit messages. Configuration recreation retains ViewModel
state; process death abandons unconfirmed attendance drafts. Neither case should
create an attendance row without confirmation. Vendor camera callback behavior and
actual low-storage/background interruption remain device-validation items.

## Activity Journal audit

Save checks title/date/time, five distinct photos, retained-photo ownership and
expected revision. Newly imported photos are copied privately; old published files
remain until the row transaction succeeds. Failure rolls back only the new owned
generation after checking the actual save outcome; unreadable outcome retains files.
Delete requires the expected revision. Cleanup consults references across attendance,
legacy and activity tables, preserving shared referenced files and missing-original
thumbnails. Existing SQLite tests verify failed edit rollback and revision-guarded
delete; JVM tests cover attachment limits, draft ownership and partial-file failures.

`SavedStateHandle` carries editor metadata and pending paths across system recreation;
it is not a durable draft database and is not guaranteed after user force-stop/task
removal. Confirmed rows survive restarts through Room. A retained attachment already
missing from disk is not silently removed from its row: viewing/sharing/export fails
rather than inventing evidence. Text-only edits do not repair missing originals.

## Backup, restore and migration audit

Export takes a consistent snapshot under the repository lock, validates every
referenced photo, creates a portable archive, re-inspects it, and verifies bytes
read back from the document destination. A failed/cancelled export is not reported
as success; best-effort partial-document deletion can fail at the provider.

Inspection rejects unsafe names (including traversal/absolute paths), duplicate or
unexpected entries, unsupported format, malformed JSON/UTF-8, missing assets, bad
CRC/SHA-256, truncated ZIP, excessive entry counts and size limits. Image decoding
is bounded. Preview counts come from the validated manifest. Restore requires the
explicit **Replace all local data** confirmation, retains appearance preferences,
and performs no automatic merge.

Restore validates staging again, writes a new generation without overwriting old
photos, then commits all records, guards and marker together. Commit ambiguity is
resolved by marker lookup; unreadable outcome preserves files. Startup cleans only
unreferenced inactive generations. Old data replacement is intentional only after
user confirmation, not an audit operation.

Existing host tests exercise migration SQL, compare schema columns to exported Room
schemas, test trigger constraints and transaction rollback. They do not run Android
Room MigrationTestHelper, Android SQLite, DocumentProvider, or real image codecs.
No schema change, destructive fallback or database reset was introduced.

## Widget and preference audit

The widget uses the app's attendance/progress calculations and a single transactional
snapshot; refresh does not mutate business records. Missing data gets a reload prompt.
WorkManager retry is bounded; force-stop and idle can delay refresh. Date labels help
expose a stale midnight snapshot. The widget continues to follow the system theme.
An observation failure can end the live flow until another platform/app refresh;
this is a presentation availability limitation, not data loss. No widget code changed.

Theme writes use the existing DataStore and survive restart; failures leave Room and
photos untouched. Preference corruption is surfaced rather than silently clearing
user data. Existing preference tests passed.

## Remaining risks and validation limits

- **MEDIUM — unsaved draft retention:** existing activity cleanup can remove a draft
  older than seven days if no saved row references it, even when an editor is restored
  from saved state. Save then rejects the missing pending photo, preserving the old
  row. Long-lived unsaved attachments are not guaranteed. A subsequent phase should
  define durable draft retention/ownership before changing cleanup policy; do not
  expand deletion heuristics or treat unsaved drafts as permanent evidence.
- **MEDIUM — device/runtime coverage:** no physical interruption, disk-full, reboot,
  low-memory kill, Room runtime migration or real-provider restore was exercised.
  In particular, full restore remains unvalidated on Android with disposable data.
- **MEDIUM — existing missing evidence:** there is no read-only integrity-report UI
  or automatic repair. Missing/corrupt files cause photo/export failures, while rows
  remain. Restore from a verified backup must remain an explicit user decision.
- **LOW — cleanup availability:** best-effort deletion can leave orphans/temporary
  bytes. Unknown commit outcomes intentionally retain files until a readable DB
  snapshot permits reconciliation. Prefer storage leakage over deleting evidence.
- Backups are unencrypted and replace rather than merge. Provider semantics and
  actual storage hardware durability cannot be proven by host fault injection.
- Revisions invalidate editors for IDs present at replacement. They are not a global
  durable editor-generation token across arbitrary delete/restore sequences. If
  multi-window editing or durable drafts are expanded, bind drafts to a restore
  generation and test process death at every transaction/UI boundary.

## Automated validation

Final required command:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

**BUILD SUCCESSFUL — 177 JVM tests passed, 0 failures/errors/skips.** XML result
counts were summed after the successful run (P7 baseline: 171). An initial run caught
a new JUnit method returning a value instead of Unit; corrected and rerun successfully.

Additional successful host checks:

| Command | Passing tests |
| --- | ---: |
| `python3 tools/test_database_guards.py` | 10 |
| `python3 tools/test_event_migration.py` | 16 |
| `python3 tools/test_recovery_migration.py` | 13 |
| `python3 tools/test_activity_migration.py` | 9 |
| Total host SQLite checks | 48 |

**225 passing checks combined (177 JVM + 48 SQLite).** `git diff --check` is clean.
No instrumented tests exist in the repository; connected-device enumeration is not
physical application validation.

## Samsung Galaxy A06 manual checklist

Use normal intended attendance on the personal installation. Use a separate test
device/emulator and disposable data for fault injection, clock changes, force-stop
around writes, full-disk experiments and replacement restore. Obtain permission
before destructive testing on the physical phone. Do not uninstall or clear data.

- [ ] Confirm each of the four events in a legitimate workflow; rapid taps must
  create one row and one original/stamped pair. Retake/cancel must create no row.
- [ ] View/share each confirmed image through the Android Sharesheet; verify actual
  capture and confirmation timestamps, orientation and Manila day.
- [ ] Background and rotate during capture/review; reopen normally and verify saved
  history and progress. Check permission denial and return from Settings.
- [ ] On disposable data, interrupt before/after confirmation and reopen; verify no
  partial row or missing evidence. Confirm Android directory fsync succeeds on A06.
- [ ] Verify explicit half-day skip and next-day incomplete recovery preserve prior
  evidence and credit only closed intervals. Test midnight/clock rollback in fixtures.
- [ ] Create/edit five-photo activities, reorder/remove/retake, cancel edits, and
  reopen. Failed saves must preserve prior metadata and published photos.
- [ ] Export a backup and cancel a restore preview on the personal device only with
  user consent. On disposable data, round-trip a verified archive; test corrupt,
  truncated, missing-asset and interrupted replacements without touching real records.
- [ ] On disposable data, restore while an old revision-zero editor can be recreated;
  stale save/delete must be refused. Validate migrations from each shipped schema.
- [ ] Check theme persistence and widget totals after normal restart; on an approved
  test device check reboot, midnight and idle refresh. Allow Android scheduling delay.

## P9 recommendation

**GO for controlled P9 development; not a production reliability sign-off.** The
three identified high-risk write-boundary defects have minimal fixes and passing
regression coverage. Make Android restore/migration and interruption testing with
disposable data a release gate. Prioritize durable draft ownership and a read-only
missing-evidence report in subsequent planning. No broader retention, automatic
repair or destructive recovery behavior is authorized or implemented by this audit.
