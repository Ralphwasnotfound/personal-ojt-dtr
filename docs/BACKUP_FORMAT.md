# My OJT Companion backup format, version 2 (P3 version 1 readable)

The P4 backup is a standard single-disk ZIP archive, without ZIP64, executable prefix, directory entries, trailing payloads, or encryption. It contains exactly:

```text
manifest.json
assets/<lowercase UUID>.jpg
assets/<lowercase UUID>.png
assets/<lowercase UUID>.webp
```

Only referenced assets are included. Attendance originals and timestamped images are JPEGs. Legacy/activity photos may also use PNG or WebP. Each distinct local source path receives a generated archive asset ID. Raw filesystem paths and source document URIs are never serialized. Assets are copied byte for byte; originals, overlays and genuine event timestamps are not regenerated during backup or restore.

The manifest is strict UTF-8 JSON with these exact top-level keys:

```json
{
  "format": "my-ojt-companion",
  "version": 2,
  "schemaVersion": 4,
  "applicationId": "com.lazarus.personalojtdtr",
  "records": {
    "createdAt": 1793088000000,
    "profile": null,
    "events": [],
    "legacy": [],
    "activities": [],
    "resolutions": [],
    "assets": []
  }
}
```

The timestamp above is an illustrative integer. A real export uses its snapshot time from the device clock. All timestamp integers are UTC epoch milliseconds; calendar dates are epoch-day integers representing the Asia/Manila calendar date. No timestamp is independently verified.

## Record fields

All listed fields are required JSON keys. Optional values use explicit `null`, not omitted keys. Names are case-sensitive. Integers use ordinary decimal notation; fractional/exponent values and integers outside signed 64-bit range are rejected.

| Record | Fields |
|---|---|
| `profile` (object or null) | `id` (1), `displayName`, `requiredHours`, `startDate` (epoch day or null), `defaultBreakSeconds` |
| `events[]` | `id`, `date`, `type`, `at`, `capturedAt`, `original` (asset ID), `stamped` (asset ID), `createdAt` |
| `legacy[]` | `id`, `timeIn`, `timeOut` (timestamp or null), `breakSeconds`, `original` (asset ID or null), `outPhoto` (asset ID or null), `createdAt`, `updatedAt` |
| `activities[]` | `id`, `date`, `description`, `photos` (ordered asset ID array, zero to five), `createdAt`, `updatedAt`, `title`, `timeMinutes` (0–1439 or null) |
| `resolutions[]` | `id`, `date`, `session`, `kind`, `actedAt` |
| `assets[]` | `id` (lowercase UUID), `mediaType`, `bytes`, `sha256` (64 lowercase hex digits) |

Event `type` codes are 0 morning Time In, 1 morning Time Out, 2 afternoon Time In, 3 afternoon Time Out. Session codes are 0 morning and 1 afternoon. Resolution `kind` codes are 0 incomplete/unverified and 1 skipped/not worked. A resolution never substitutes an event or photograph.

Asset media types are `image/jpeg`, `image/png`, or `image/webp`; archive extension is derived from the media type. `original` and `stamped` must refer to distinct JPEG assets and cannot reuse another event's assets. An asset's declared byte count, SHA-256, magic signature and actual image decoding are verified. SHA-256 detects accidental damage; it is not authentication or proof that attendance occurred.

## P3 compatibility

Exports now use format 2 / schema 4 and preserve required activity titles and optional local times. Format 1 / schema 3 is accepted with its original activity fields: titles are derived from descriptions, time remains unset, and photo ordering/timestamps are preserved. Only these explicit format/schema pairs are accepted; fields remain strict for each version. Activity records with more than five or duplicate photo references are rejected rather than silently trimmed. Normal P3 archives from its description-only editor remain compatible.

Activity originals are backed up; derived thumbnails and transient camera/editor drafts are excluded. Operational activity revisions reset to zero during REPLACE; all genuine creation/update timestamps remain intact. User-visible editor/navigation state resets after restoration.

## Limits and validation

- Compressed archive and expanded contents: at most 1 GiB each.
- Manifest and central directory: at most 8 MiB each.
- Individual photo: at most 20 MiB.
- At most 10,001 entries (one manifest and up to 10,000 photos).
- At most 20,000 records per record array; IDs must be unique within their table.
- JSON depth at most 16 and at most 250,000 nodes; individual text values at most 20,000 characters. Activity descriptions retain the app's 10,000-character limit.
- Image dimensions at most 16,000 on either edge and 80 million pixels; decoding is sampled.
- Backup dates/timestamps must lie in years 1800–2400. Legacy closed sessions may span at most 366 days. These import bounds prevent extreme calendar/duration processing; ordinary OJT records fit within them.
- Required hours, default break and profile validation retain existing app bounds.

The ZIP end-of-central-directory record is checked before opening `ZipFile`, bounding central-directory allocation and entry count. Multi-disk, ZIP64, unexpected trailing content, unsupported compression, unsafe names, duplicate entries, unknown entries and unlisted assets are rejected. Entry declared lengths, actual expanded lengths and CRC32 are checked independently. Entry names are never used as extraction destinations: private staged filenames are generated from validated UUIDs and known extensions.

JSON rejects duplicate, missing and unknown fields. All referenced assets must exist and every included asset must be referenced. Attendance and resolution records are replayed chronologically through the same application rules. Missing Time In, invalid Time Out, overlapping/open sessions, skipped/completed-session conflicts, duplicate actions, invalid capture timestamps and invalid audit dates are rejected. Backup dates cannot precede the contained event/audit/create/update timestamps.

## Export

Android's Create Document picker chooses the destination, with `EXTRA_LOCAL_ONLY` requested. The Open Document picker requests the same local-only hint. A consistent database snapshot and its referenced files are read while application writes are serialized. A private ZIP is completed and flushed, then fully re-opened and validated. Only then is it streamed to the chosen document. The output is read back and compared with the completed archive by length and SHA-256 before reporting success.

Failures/cancellation remove private work. Deletion of a newly created partial destination is attempted through its document provider; a provider may refuse deletion, so the cancellation message warns that an incomplete file may need manual removal. No existing backup is selected for overwrite by the Create Document flow. The app does not initiate cloud uploads; use a secure local document provider for offline storage. SAF providers and destination availability remain under Android/user control.

## REPLACE restore and crash recovery

The source document is opened read-only, copied to bounded app-private storage, validated and extracted into private staging. Preview shows the backup date, profile/settings, record counts, action count and photo count. The user must explicitly confirm **Replace all local data**. Cancel before apply only removes staging. There is no automatic merge and the source backup is never deleted by restore.

Apply revalidates staged data/assets and copies photos to a fresh immutable namespace:

```text
files/restore-generations/<new UUID>/assets/<asset UUID>.<extension>
```

Photo files and parent directories are synced before any database replacement. The local photo references are generated here; backup metadata cannot choose app filesystem paths. The original live photo files are never renamed or overwritten.

One Room transaction drops only the application's fixed guard triggers, replaces the profile/settings, attendance, activities and audit records, restores all guards, and writes a `restore_marker` naming the new generation. Trigger DDL, records and marker roll back together if any part fails. Insert values are bound through DAOs; no archive-supplied SQL or executable content is used. Clearing immutable records is available only through this explicit validated replace path.

The database commit is the switch point: before commit, all old records point to untouched old files; after commit, all new references point to already prepared files. SQLite transaction recovery handles process death during commit. If commit delivery throws, the marker is read: a committed generation is retained and reported as committed; a definite rollback removes only the new generation. If the marker cannot be read, both generations are retained and the user is told to restart/check the outcome. The app never deletes prepared photos while the outcome is unknown.

At a later startup, the committed marker and all live photo references determine which private generations must be retained. Unreferenced generations and abandoned staging are cleaned. P2 managed photo cleanup also preserves legacy/activity references. Old files outside managed photo/generation directories are not swept. This may retain extra private legacy files, which is preferable to deleting unrelated data.

The final apply phase is non-cancellable to finish commit or rollback. Its UI explains that it is applying and disables Cancel. Process death is still handled by the database/files generation scheme. A successful restore resets navigation/editor state so the dashboard and profile reflect restored data.

## Privacy and portability

Archives contain personal information and images in plaintext. They are **not automatically encrypted** and must be stored securely. Temporary files are app-private and are not exposed through FileProvider. Only attendance photo files can be shared via the existing explicit Sharesheet action. No internet/storage permissions, hosted APIs, automatic uploads, messaging or backup-content logging are added.

The version-2 format restores on another P4 installation of this application through generated local paths. Raw SQLite files, Room operational restore markers, device paths, URI grants and camera drafts are intentionally excluded. P1/P2 did not have an export format; the documented P3/P4 format/schema pairs are accepted. Future format versions require explicit compatibility code.
