# Backup archive format

Version 1 · written by Watches 0.4.0 and later · read by every platform build of the app.

A backup is one ordinary ZIP file holding everything in the collection: every table of the database as JSON, and every photo, document and saved source page as the original file. Any unzip tool can open it, and the JSON reads in a text editor. Restoring it brings the collection back exactly, on Android or iOS.

## Layout

```
Watches backup 2026-10-18 1530.zip
├── manifest.json                         always first
├── collection.json                       always second
└── files/
    ├── photos/<watch id>/<photo id>-original.jpg
    ├── photos/<watch id>/<photo id>-display-<tag>.jpg
    ├── photos/<watch id>/<photo id>-thumb-<tag>.jpg
    ├── documents/<watch id>/<document id>.pdf
    └── snapshots/<watch id>/<source>-<snapshot id>.json
```

- **Entries are stored uncompressed.** Photos and PDFs are already compressed, and stored entries are the simplest thing to read. Every entry's size and CRC-32 are in its local header, with no data descriptors, so the archive can be read front to back as a stream. The app never needs to seek, which matters for files handed over by cloud providers.
- **ZIP64** records are added when an entry or offset passes 4 GB, or when there are more than 65,535 entries. Large photo libraries are fine.
- **Names are UTF-8**, with the language-encoding flag set.
- **The manifest comes first**, so the app can show what a backup holds, and refuse a file that isn't one, after reading only its start.

## manifest.json

```json
{
  "format": "io.github.tunlezah.watches.backup",
  "format_version": 1,
  "schema_version": 1,
  "created_at": "2026-10-18T05:30:12.401Z",
  "app": "Watches 0.4.0-m4 (Android 16)",
  "counts": { "watches": 212, "photos": 640, "service_entries": 95, "documents": 41, "snapshots": 180 },
  "files": [
    { "path": "collection.json", "size": 812345, "sha256": "9f2c…" },
    { "path": "files/photos/5b0e…/a41c…-original.jpg", "size": 3812004, "sha256": "41aa…" }
  ]
}
```

| Key | Meaning |
| --- | --- |
| `format` | Always `io.github.tunlezah.watches.backup`. Any other value means it isn't a Watches backup. |
| `format_version` | The version of this document's layout. An app refuses archives with a higher number than it knows. |
| `schema_version` | The database schema the rows follow (the `AppDatabase` version). An app refuses a schema newer than its own ("made by a newer version of Watches"). Once there is a schema 2, older archives will be upgraded as they're read. |
| `created_at` | When the backup was made: ISO-8601, UTC. |
| `app` | The app version and platform that made it, for support only. |
| `counts` | Row counts, shown before restoring. |
| `files` | `collection.json` and every file under `files/`, each with its byte size and lower-case hex SHA-256. |

## collection.json

One object. Each key is a database table, and each value is an array of that table's rows. The keys inside a row are the table's column names, exactly as `shared/data/schemas/…/1.json` lists them (a test checks this). IDs are kept, which is what merging matches on.

```json
{
  "watch": [ { "id": "5b0e…", "status": "owned", "brand": "Omega", "model_name": "Seamaster 300", "diameter_mm": 41.0, "price_minor": 1090000, "price_currency": "AUD", "created_at": 1790000000000, "updated_at": 1790000000000, "service_reminder": true } ],
  "watch_complication": [ { "watch_id": "5b0e…", "key": "date" } ],
  "field_provenance": [ … ],
  "other_spec": [ … ],
  "photo": [ { "id": "a41c…", "watch_id": "5b0e…", "position": 0, "original_file": "photos/5b0e…/a41c…-original.jpg", "display_file": "…", "thumb_file": "…", "width": 4032, "height": 3024, "kind": "user", "added_at": 1790000000000 } ],
  "service_entry": [ … ],
  "box_papers_item": [ … ],
  "document": [ … ],
  "source_snapshot": [ … ],
  "setting": [ { "key": "ui.accent", "value": "bordeaux" } ]
}
```

Value conventions:
- **Timestamps** are epoch milliseconds (UTC). **Dates** are `YYYY-MM-DD` strings.
- **Money** is two columns: an integer amount in minor units (cents) and an ISO 4217 currency code.
- **Enumerations** are stable lower-case keys (`owned`, `full_service`, `quick_start`).
- **Sets** inside a column are comma-separated keys (`service_entry.work`).
- **Nulls are left out.** A missing key means null, which lets a later version add nullable columns without breaking older archives.
- **File paths** are relative to the app's files folder. Each one names an entry under `files/` in the archive.

Left out on purpose: the full-text search index, which is rebuilt after a restore, and recent lookup searches, which are short-lived and personal.

## Restoring

The app checks before it changes anything:
1. `manifest.json` must have the right `format`, and its versions must not be newer than the app's.
2. `collection.json` must match its SHA-256 in the manifest.
3. While the files stream in, each one is checked against its SHA-256. Rows whose file is missing from the archive are skipped and listed as warnings. A photo without its renditions keeps its original, and the renditions are rebuilt from it.
4. Only file paths of the form `photos|documents|snapshots/<id>/<name>` are accepted. Anything else is ignored, so an archive can't write outside the app's media folders.

Then:
- **Replace** removes the collection on the phone and puts the backup's in its place, settings included.
- **Merge** keeps everything on the phone and adds the backup's. It matches on IDs and never deletes anything:
  - A watch only in the backup is added.
  - A watch on both sides takes its details, specs, complications and box & papers from whichever side was edited more recently (`updated_at`). The phone's version wins a tie.
  - Photos, service entries, documents and source pages are combined. Ones only in the backup are added. A service entry on both sides keeps its more recently edited version. Photos and documents on both sides follow their watch.
  - Settings on the phone are kept.
  - Every watch that differed is listed in the restore report, with which side won.

All database changes happen in one transaction. Incoming files are written next to the existing ones (a `-r<tag>` suffix avoids any name clash), and files that are no longer used are removed only after the transaction commits. If the app is stopped partway, the next launch removes the half-restored files using a journal (`restore-journal.txt`), and the collection stays as it was.

A backup restored onto the phone it came from writes almost nothing: files already there with the same SHA-256 are reused.

## Making a compatible archive by hand

Unzipping a backup, editing `collection.json` and zipping it again works, with these rules:
- Update the file's `sha256` in the manifest.
- Any ZIP tool is fine. The app reads deflated entries, data descriptors, a top-level folder around everything, and `__MACOSX` entries (which it ignores).
- An encrypted ZIP is refused.

## CSV export

Separate from backups and one-way, for spreadsheets. It has one row per watch:
- every field of the spec registry, with units in the column names (`Case diameter (mm)`);
- each watch's status, complications, last service, next service due, warranty end, and box & papers summary.

The file is UTF-8 with a byte-order mark, so Excel opens it correctly, and is quoted per RFC 4180. Cells that start with `=`, `+`, `-` or `@` get a leading apostrophe, so a spreadsheet never runs them as formulas. CSV can't be imported back; use a backup for that.
