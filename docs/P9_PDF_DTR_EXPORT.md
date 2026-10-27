# P9 — Offline PDF DTR export

## Scope and safety

History now offers **Export DTR** with month/year selection, a saved-data preview,
PDF generation, Android Sharesheet sharing, and Save PDF through the Storage Access
Framework. No attendance, activity, recovery, profile or evidence writes occur in
this flow. P8 changes remain uncommitted and intact. No Room schema, application ID,
permissions, widget, camera, backup format or attendance rules changed. No PDF
library or network dependency was added.

## Architecture and files

Production paths are under `app/src/main/java/com/lazarus/personalojtdtr/`:

| File | Responsibility |
| --- | --- |
| `domain/usecase/DtrReport.kt` (new) | Pure report snapshot, rows, notes, summaries and page partitioning |
| `data/repository/OjtRepository.kt` (modified) | Read profile, events, legacy sessions and resolutions in one Room transaction |
| `data/report/DtrPdf.kt` (new) | Built-in Android PdfDocument/Canvas renderer |
| `data/report/ReportFiles.kt` (new) | Own temporary output, publication, destination validation, verified stream copy |
| `data/report/DtrExportService.kt` (new) | IO dispatcher, cancellation, Android content resolver and FileProvider integration |
| `ui/report/DtrExportViewModel.kt` (new) | Snapshot/export state, synchronous busy gate, errors and save-picker lifecycle |
| `ui/report/DtrExportDialog.kt` (new) | Material 3 month/year controls, preview, generation/share/save controls |
| `ui/history/HistoryScreen.kt` (modified) | Export entry point using the existing EXPORT icon |

Tests under `app/src/test/java/com/lazarus/personalojtdtr/`:
`domain/usecase/DtrReportTest.kt` and `data/report/ReportFilesTest.kt` (both new).
This document is `docs/P9_PDF_DTR_EXPORT.md`.

The existing FileProvider `cache-path` for `exports/` already covers the new report
cache; its permission scope was not expanded.

## Report content and calculation source

- Title: DAILY TIME RECORD; current saved student name; selected month/year.
- Missing names explicitly display **Name not configured**, never a fabricated name.
- Summary: completed hours and recorded attendance-day count for the selected month.
- Every calendar day gets a row with Date, Morning In/Out, Afternoon In/Out and Daily
  Total. Event cells contain saved `occurredAt` timestamps in `HH:mm:ss`, Manila time.
  Missing cells are blank. Evidence capture timestamps are not substituted for
  attendance confirmation timestamps.
- `DailyAttendance.sessions(events, resolutions)` produces eligible four-event
  sessions. These are combined with preserved legacy sessions and passed to
  `AttendanceCalculations.dailyHours`. Calculation uses the entire snapshot before
  selecting dates, retaining overlap exclusions and cross-midnight break allocation.
- Daily durations are summed without introducing another duration formula. Display
  uses the app's `hoursLabel()` (hours/minutes). Sub-minute precision is retained for
  summation, so a sum of individually truncated printed daily labels can differ by
  a minute from the monthly label; no timestamps/durations are rounded in storage.
- A recorded day has a saved four-event timestamp or an intersecting legacy session.
  An open legacy session counts only on its actual start date. A resolution-only day
  gets a note but does not invent an attendance day.
- Preview flags days with missing four-event timestamps or open legacy sessions.
  Skipped/incomplete resolutions appear in record notes, distinguishing deliberate
  half-day handling from actual saved timestamps. Unfinished sessions add no hours.
- Legacy P1 sessions have actual start/end/break values in a separate notes section.
  They contribute existing daily allocated hours but are not guessed into four-event
  columns. Cross-month legacy sessions show full source times; only selected-period
  allocated hours contribute to the main summary.

The preview and generation share one immutable snapshot. The interface explicitly
says to **Preview period** again for newer records. PDF generation never reads or
modifies evidence photos, and never embeds activity or attendance images.

## PDF layout

A4 portrait, 595×842 points, 32-point horizontal margins, white background, dark
text, maroon title/section accents, light header fill and thin grey dividers. Body
text is 9–12 points, title 23 points. Name uses wrapped Android text layout; embedded
newlines/spacing are normalized for the header without altering Profile data.

The attendance table uses 24 intact rows per page (21-point rows). Month reports
therefore use two table pages, followed by notes pages when necessary. Table headers,
student/period, summary, explanatory footer and page numbers repeat. Notes use ten
intact 50-point rows per page with wrapped detail text. No table row crosses a page
boundary. The final row ends above the explanatory footer. Photo bytes and bitmap
allocations are not involved in rendering.

The PDF stays light and printer-friendly regardless of the selected application
theme; the export interface follows Material 3 light/dark semantic colors.

## Workflow, storage and errors

1. Open History → Export DTR. The current Manila month is initially previewed.
2. Choose a month and enter a year (1800–2400); select Preview period. A changed period
   invalidates the prior preview/file association. Empty periods show a clear message
   and disable generation.
3. Generate PDF uses the frozen preview on Dispatchers.IO, with a loading state and
   synchronous guard against concurrent generation. Cancellation is checked between
   pages/rows and around document writing; PdfDocument is closed in finally.
4. The renderer writes a unique `.part` in `cacheDir/exports/dtr`, syncs completed bytes
   and renames to `.pdf`. Failure removes only that attempt's files. A cancellation
   while delivering a completed file also removes that unpublished result. Previously
   generated or shared report files and all evidence remain untouched.
5. Share PDF uses FileProvider `content://`, `application/pdf`, ClipData and temporary
   read permission. Only the user's Sharesheet choice initiates sharing.
6. Save PDF opens CreateDocument with `application/pdf`, local-only hint and suggested
   filename `DTR-YYYY-MM.pdf`. The selected report is pinned while the picker is open;
   export controls are locked. The content URI must have a valid content scheme and
   authority before any document is opened. No broad storage permission is used.
7. Save streams the report, closes output, reads the destination back with a size
   bound, and compares SHA-256. Success means matching bytes, not just a successful
   write call. Provider failure, invalid destination, missing cache file, disk-full
   exception, cancellation and readback mismatch produce feedback. Partial user
   documents are not automatically deleted; the UI explains they may need removal.
8. A denied/unavailable document picker is reported; picker cancellation changes no
   records. Failed save leaves the generated cache PDF available for retry/share.

The ViewModel retains snapshot/progress through configuration changes. Process death
ends an in-flight export; reopening loads a fresh snapshot and can regenerate. It
never resumes an ambiguous destination write automatically. Saved documents are not
tracked or deleted by the app. Report cache is intentionally temporary and Android
may evict it; a missing file requires regeneration. Process death can leave a `.part`
in the cache, and repeated reports can accumulate until cache eviction. No broad
cleanup of files/evidence was introduced.

## Automated validation

New tests cover empty periods; single, partial and full days; matching app totals;
daily/monthly totals; Manila versus UTC boundaries; December/January; leap years;
cross-midnight legacy breaks; open legacy sessions; explicit half-day skip; overlap
exclusions; missing/long names; ordered multi-page data; and invalid years.

File tests cover complete publication, partial-generation failure, cancellation,
empty output, invalid destinations before output opening, verified save, unavailable
provider, interrupted/disk-full write, truncated/corrupt readback, cancelled save and
evicted cache files. They use disposable host temporary files, not user data.

Final validation on 2026-10-26:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

**BUILD SUCCESSFUL — 205 JVM tests passed, zero failures/errors/skips**, including
28 new tests (16 report-data tests and 12 file/export tests). Counts were summed from
Gradle XML results. All 177 P8-era tests still pass. `git diff --check` is clean.
During implementation, compilation caught PdfDocument's explicit close requirement;
this was corrected with finally cleanup. A month-boundary fixture initially left an
overlapping prior session open; it was corrected without changing production hours
rules. The final required command passed after all source changes.

Host tests exercise report calculations/pagination and stream failure boundaries, not Android Canvas,
font rendering, real document providers or Sharesheet UI. No physical device testing
is claimed by these tests.

## Samsung Galaxy A06 validation checklist

- [ ] History → Export DTR is readable in light and dark mode, portrait/landscape and
  enlarged font sizes. Controls remain reachable by scrolling and TalkBack.
- [ ] Preview current month, an empty month, February 2024/2025 and a year boundary.
  Compare recorded days, daily totals and monthly total to saved attendance.
- [ ] Verify full, morning-only, afternoon-only, incomplete/recovered and legacy days.
  No missing time is invented; legacy source times and exceptions appear in notes.
- [ ] Test a missing and long Profile name. Verify generated PDF name wrapping and
  Unicode glyphs in a PDF reader without modifying actual attendance.
- [ ] Generate a full month; inspect every page at 100% and print preview. Confirm A4,
  all six columns, repeated headers, notes, page numbering and intact rows.
- [ ] Tap Generate repeatedly; observe one operation and a responsive loading/cancel
  state. Rotate/background during preview and generation; reopen after normal closure.
- [ ] Share PDF to an installed viewer/receiver and confirm it can read the content URI.
  Sharing must never grant access to unrelated evidence or data files.
- [ ] Save using a local Android document provider, open the saved PDF and verify it
  matches the preview. Cancel the picker and retry; exercise unavailable destination
  and low-storage behavior on disposable test storage rather than personal data.
- [ ] On a separate disposable test environment, interrupt saving/process execution;
  inspect partial destination behavior and regenerate. Do not clear/uninstall the
  personal app or perform destructive testing on real records.
- [ ] Confirm attendance evidence, activity attachments, backup and P8 recovery
  behavior remain unchanged. No P9 operation should mutate records.

## Known limitations

This is an academic-style DTR, not a claim of an institution-certified form. It has
no signatures, institutional approval, evidence appendix or authenticity guarantee.
Recorded device-clock times are faithfully displayed. Export is a point-in-time
snapshot, not a live report. Platform PdfDocument holds page content until writing;
unusually large legacy-note collections warrant memory testing on A06. Cancellation
cannot interrupt an individual blocking document-provider call until it returns.
Some providers cannot read their own newly written document; those saves are reported
as unverified failures rather than success. Local-only is a picker hint controlled by
Android/providers; the application itself performs no network operations.

Actual PDF rendering, Android font fallback, lifecycle behavior, print output and
SAF/receiver compatibility require the manual device checks above. No emulator or
physical-device app install, launch, data clearing or destructive testing occurred
in this phase. P8 fixes and P9 changes remain uncommitted; nothing was pushed.
