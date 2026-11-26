# Making a Watches backup to import a collection: a spec for agents

**Who this is for:** an AI agent (large or small) or a developer who has a list of watches (a spreadsheet, a notes file, a web page, a folder of photos) and wants to turn it into a file the Watches app can restore. The app has no CSV or spreadsheet import. The only way to bring a collection in is to **restore a backup**, so an "import" is a backup file made outside the app.

**What it covers:** exactly what a backup file is, how the app reads and restores one, every field it holds, the allowed values, what breaks a restore, and a step-by-step recipe with a worked example.

**It applies to:** backup format version 1, database schema version 1, Watches app 0.8.0 and 0.9.0 (the version in `version.xcconfig`; 0.9.0 added the home server and changed nothing this file covers). The rules come from the app's own code, which is listed in §21. Where this file and `docs/ARCHIVE_FORMAT.md` overlap they agree; this file goes further, for people making a backup by hand.

**Verified, not guessed:** every case in §13 was checked by building a hand-made backup and restoring it with the app's own restore code (the shared Kotlin code that every platform runs). The example in §16, packaged with the code in §15, restores without a warning. "(✅ §13)" elsewhere points to one of those checks.

---

## Contents

1. [The golden rules](#1-the-golden-rules)
2. [What a backup is](#2-what-a-backup-is)
3. [How the app restores a backup](#3-how-the-app-restores-a-backup)
4. [Replace or Merge](#4-replace-or-merge)
5. [The ZIP file](#5-the-zip-file)
6. [manifest.json](#6-manifestjson)
7. [collection.json and its tables](#7-collectionjson-and-its-tables)
8. [Allowed values (enumerations)](#8-allowed-values-enumerations)
9. [Units, numbers, money, dates and times](#9-units-numbers-money-dates-and-times)
10. [Files: photos, documents, snapshots](#10-files-photos-documents-snapshots)
11. [IDs](#11-ids)
12. [Merge in detail, and re-importing corrections](#12-merge-in-detail-and-re-importing-corrections)
13. [What the app does with imperfect data (tested)](#13-what-the-app-does-with-imperfect-data-tested)
14. [Recipe: build an import backup step by step](#14-recipe-build-an-import-backup-step-by-step)
15. [Reference packaging code](#15-reference-packaging-code)
16. [A complete worked example](#16-a-complete-worked-example)
17. [Turning a user's list into fields](#17-turning-a-users-list-into-fields)
18. [Final checklist](#18-final-checklist)
19. [Error messages and their causes](#19-error-messages-and-their-causes)
20. [Another route: the in-repo backup maker and checker](#20-another-route-the-in-repo-backup-maker-and-checker)
21. [Where this comes from in the code](#21-where-this-comes-from-in-the-code)

---

## 1. The golden rules

If you read nothing else, follow these. Each one is a real way an import fails.

1. **The file is a `.zip`.** The restore picker only offers files ending in `.zip`.
2. **Two JSON files, then the media.** `manifest.json` first, `collection.json` second, then every photo and document under `files/`. All at the top of the ZIP (or all inside one folder; see §5).
3. **Store, don't compress.** Use ZIP method "stored" (no compression) or ordinary "deflate". Nothing else. No password.
4. **Write the ZIP to a real file**, not to a pipe or stdout (see §5.3). A stored ZIP written to a pipe is refused.
5. **Every key in `manifest.json` is required**, including `app`, `created_at` and all five `counts`. A manifest missing one is rejected as "isn't a Watches backup".
6. **`format` is exactly `io.github.tunlezah.watches.backup`, `format_version` is `1`, `schema_version` is `1`.**
7. **SHA-256 hashes are lower-case hex**, 64 characters. Upper case fails.
8. **`manifest.json` lists `collection.json` and every file under `files/`**, each with its byte size and SHA-256. The hash of `collection.json` must be of the exact bytes in the ZIP. If you change `collection.json` after hashing, hash it again.
9. **JSON keys are `snake_case` database column names**, exactly as in §7. A misspelt key is silently ignored, so that value is lost.
10. **Use real JSON types.** Whole numbers have no decimal point (`300`, not `300.0`: one `300.0` makes the whole file unreadable), decimals are numbers (`41.5`), true/false are `true`/`false` (not `1`). Text is **always** in quotes, even a reference, calibre or serial number that looks like a number (`"reference": "16610"`, not `16610`: also fatal). No `NaN`, no comments, no trailing commas, no byte-order mark. See §7.2.
11. **Every `watch` row needs `id`, `status`, `created_at`, `updated_at` and `service_reminder`.** One missing value makes the whole backup unreadable.
12. **Leave an optional value out rather than writing `""` or `0`** when it is unknown. Missing means "not set". Required keys are never left out; the one required key that can be empty is `service_entry.work`, which is `""` when the work is unknown.
13. **IDs are new random UUIDs** (lower case), unique across the whole collection. Never start one with `sample-`. Keep them if you might import the same watches again (§11, §12).
14. **Timestamps are milliseconds since 1970 (UTC)**, 13 digits today (for example `1795600000000`). Dates are `YYYY-MM-DD` text.
15. **Money is an integer number of cents** plus a 3-letter currency code: A$1,234.50 is `"price_minor": 123450, "price_currency": "AUD"`.
16. **Enumerations are lower-case keys from §8** (`owned`, `automatic`, `full_service`). Anything else is ignored or replaced by a default.
17. **Every file path matches** `photos|documents|snapshots/<folder>/<name>`, using only letters, digits, `.`, `_` and `-`. No spaces. Use the conventions in §10.2.
18. **A photo needs only its original file.** Set `display_file` and `thumb_file` to the same path as `original_file`, and the app makes the smaller images itself after the restore (§10.3).
19. **Every child row (photo, service entry, document and so on) must belong to a watch in the same backup.** Rows for other watches are skipped, and for complications, provenance, other specs and box & papers that happens **without any warning**.
20. **Leave `setting` empty**, and tell the user that **Replace** also resets their app settings (theme, accent, units) to the defaults (§4).

---

## 2. What a backup is

A backup is one ordinary ZIP file holding the whole collection:

```
My watches import.zip
├── manifest.json        what the backup is, and a checksum for every other file
├── collection.json      every database table as JSON: watches, photos (as rows), service history...
└── files/
    ├── photos/<watch id>/<photo id>-original.jpg
    ├── photos/<watch id>/<photo id>-display-<tag>.jpg     (optional for an import)
    ├── photos/<watch id>/<photo id>-thumb-<tag>.jpg       (optional for an import)
    ├── documents/<watch id>/<document id>.pdf
    └── snapshots/<watch id>/<source>-<snapshot id>.json   (leave out for an import)
```

- `collection.json` is the data. Its rows point at files by **relative path** (`photos/…`, without `files/`).
- Inside the ZIP each file sits at `files/` + that path.
- `manifest.json` lists every file with its size and SHA-256, so the app can check nothing is damaged before it changes anything.

The smallest useful import is a ZIP with just `manifest.json` and a `collection.json` holding `watch` rows. No `files/` folder is needed when there are no photos or documents.

---

## 3. How the app restores a backup

Knowing the steps tells you which mistakes stop a restore and which are only skipped.

### 3.1 The user chooses the file

Settings › Backup & restore › **Restore from a backup**, or **Restore from a backup** on the first-run welcome screen. The system file picker shows `.zip` files only. Works the same on Android, iPhone and Mac.

### 3.2 Inspect: the app reads and checks the start of the file

Nothing on the device changes in this step.

1. It reads ZIP entries in order until it has found both `manifest.json` and `collection.json`.
   - No `manifest.json` → **"This file isn't a Watches backup."**
2. It parses `manifest.json`. If any key is missing or has the wrong type, or `format` isn't `io.github.tunlezah.watches.backup` → **"This file isn't a Watches backup."**
3. If `format_version` > 1 or `schema_version` > 1 → **"This backup was made by a newer version of Watches."**
4. `collection.json` must be in the ZIP, listed in the manifest's `files`, and its SHA-256 must match. Otherwise → **"This backup is damaged and can't be restored (…)"**.
5. It parses `collection.json`. If any row breaks the typing rules (§7.2) → **damaged ("collection.json can't be read")**.
6. It **cleans** the data (nothing fatal here; each kind of problem becomes one warning line):
   - Child rows whose `watch_id` isn't a watch in this backup are dropped. For photos, service entries, documents and snapshots: *warning "N records for watches not in the backup will be skipped."* For complications, provenance, other specs and box & papers: **dropped with no warning at all**, so check those `watch_id`s yourself.
   - A photo whose `original_file` isn't listed in the manifest, or whose path breaks the path rule (§10.1), is dropped. *Warning: "N photos missing from the backup will be skipped."*
   - A photo whose display or thumbnail file is missing or unsafe keeps its original and has those rebuilt later (no warning).
   - A document whose `file` isn't listed or is unsafe is dropped. *Warning: "N documents missing…"*
   - A document whose `service_entry_id` isn't a service entry in this backup has that link removed (no warning).
   - A source snapshot whose file isn't listed is dropped (no warning).
   - Settings whose key starts with `device.` are dropped (no warning).
7. It works out what **Merge** would do against the collection on this device (§12).

### 3.3 The preview screen

The user sees:
- "Made *date* at *time*" from the manifest's `created_at` (or just "Watches backup" if that can't be read),
- the manifest's `app` text, in small type: write something honest, such as `"Watches import (made from my spreadsheet)"`,
- counts of watches, photos and records, **counted from `collection.json`**, not from the manifest's `counts`,
- a choice between **Merge with this phone** and **Replace this phone's collection** (Replace is pre-selected when the device has no watches),
- for Merge, the list of watches that differ and which side wins,
- any warnings from step 6, under "Some items can't be restored".

### 3.4 Restore

1. **Free space:** the files it has to write, plus 64 MB, must fit. Otherwise → "Not enough free space…".
2. It reads the ZIP again from the start. `manifest.json` must hash the same as when it was inspected → otherwise "The backup file changed after it was checked."
3. Each file under `files/` that a kept row needs is written into the app's storage and **checked against its SHA-256 in the manifest**. A mismatch → **damaged ("… doesn't match its checksum")**, and nothing changes.
4. If any needed file is listed in the manifest but **not in the ZIP** → **damaged ("N files are missing")**, and nothing changes.
5. A file already on the device with the same contents is reused. A file whose path is taken by **different** contents is written beside it under a new name (`…-r<6 letters>.jpg`). See §11.4 for older app versions.
6. All database changes happen in **one transaction**: either everything is saved or nothing is.
   - **Replace:** deletes every watch (and with them every photo, service entry, document and so on), deletes every setting except `device.*` ones, then inserts the backup's rows and settings.
   - **Merge:** see §12.
7. A watch whose `cover_photo_id` isn't one of its own photos has its cover cleared. The app then shows its first own photo (`kind: "user"`, lowest `position`).

### 3.5 After the restore

- The search index is rebuilt, so everything is searchable.
- Every photo whose `display_file` or `thumb_file` is the same as its `original_file` gets proper display and thumbnail images rendered.
- A photo check runs in the background over every photo and every photographed document. It removes any GPS location still in a file, turns any format the app doesn't store into a JPEG, and corrects a recorded `width`/`height` that doesn't match the upright picture (dropping that photo's crop). (§13 has what this does to imperfect files.)
- If the app is closed partway through, the next launch finishes or undoes the restore: either the old collection or the new one, never a mix.

---

## 4. Replace or Merge

The user picks; your job is to tell them which one fits.

| Situation | Choose | Why |
| --- | --- | --- |
| A new install, or a device with no watches yet | **Replace** (pre-selected) | Nothing to lose. |
| The device has watches and the import should be **added** to them | **Merge** | Adds every watch with a new ID, deletes nothing, keeps the device's settings. |
| Importing a **corrected** version of a list imported earlier | **Merge**, with the **same IDs** as before and a **newer `updated_at`** | The backup's version of each watch then wins (§12). |
| The user wants the device to hold **only** the import | **Replace** | Deletes everything on the device first. Cannot be undone. Suggest they make a backup first. |

**Replace also replaces settings.** It removes every app setting except the device-only ones (app lock, last backup date) and puts in the backup's `setting` rows. An import usually has none, so the theme, design, accent, sort order, date format, water-resistance unit and default currency go back to their defaults. Say so to the user. Merge never touches settings.

---

## 5. The ZIP file

### 5.1 Requirements

| Rule | Detail |
| --- | --- |
| File name | Anything ending in `.zip`. A suggested pattern: `Watches import 2026-11-25.zip`. |
| Compression | Each entry is **stored** (method 0) or **deflated** (method 8). Stored is recommended: photos and PDFs are already compressed. bzip2, LZMA, Zstandard and others are refused. |
| Encryption | Not allowed. An encrypted entry is refused. |
| Entry sizes | A **stored** entry must have its sizes in its own header (no "data descriptor" with zero sizes). Deflated entries may use data descriptors. See §5.3. |
| Entry names | Forward slashes, no leading `/`. UTF-8. In practice every name is plain ASCII (§10.1). |
| Order | `manifest.json`, then `collection.json`, then the `files/…` entries. The app also copes with another order (✅ §13), but this one is fastest and is what the app writes. |
| Folders | Directory entries (names ending `/`) are ignored. |
| Top folder | Allowed: if the ZIP holds `My import/manifest.json`, then everything else must be under `My import/` too. Entries outside that folder are ignored. Easiest: no top folder. |
| Junk | `__MACOSX/…` entries and `._…` files (from macOS) are ignored. |
| Nothing in front | The file must start with a ZIP entry. No self-extracting stub. |
| Size | ZIP64 is supported for files over 4 GB or more than 65,535 entries. `manifest.json` may be at most 32 MB and `collection.json` at most 512 MB. |
| Duplicates | One entry per path. |

### 5.2 Tools that work

| Tool | Works? |
| --- | --- |
| Python `zipfile`, writing to a file path or an open file, `ZIP_STORED` or `ZIP_DEFLATED` | ✅ (§15 uses it) |
| Python `zipfile` writing to a pipe or stdout | ⛔ with `ZIP_STORED`, ✅ with `ZIP_DEFLATED` (§5.3) |
| Info-ZIP `zip -r` (Linux, macOS), default or `-0` (store), with or without a top folder | ✅ Run it inside the folder that holds `manifest.json`: `cd staging && zip -r -0 "../My import.zip" manifest.json collection.json files` |
| macOS Finder "Compress" | ✅ (the app's own tests cover a Finder-style re-zip: deflated, data descriptors, top folder, `__MACOSX`) |
| Windows "Send to › Compressed folder" | Not tested. It writes deflated entries, which the app reads; zip the files themselves (not a folder holding them) or the top-folder rule applies. |

### 5.3 The one trap: writing a ZIP to a stream

When a ZIP library writes to something it can't seek in (a pipe, stdout, a network stream, an HTTP response), it usually puts each entry's sizes *after* the data, in a "data descriptor", and writes zeros in the header. For a **stored** entry the app can't know where the data ends, so it refuses the file. Because `manifest.json` is the first entry, the message is the misleading **"This file isn't a Watches backup."** (✅ §13). Always write the ZIP to a file on disk. If you must stream, use deflate.

---

## 6. manifest.json

UTF-8 JSON, one object. **Every key below is required.**

| Key | Type | Value |
| --- | --- | --- |
| `format` | text | Exactly `"io.github.tunlezah.watches.backup"` |
| `format_version` | whole number | `1` |
| `schema_version` | whole number | `1` |
| `created_at` | text | When you made the file, ISO-8601 in UTC, for example `"2026-11-25T10:00:00Z"`. Shown on the preview screen. If it can't be read the restore still works. |
| `app` | text | Who made it, for the user and for support, for example `"Watches import (from collection.xlsx)"`. |
| `counts` | object | Five whole numbers: `watches`, `photos`, `service_entries`, `documents`, `snapshots`. Use the number of rows in the matching `collection.json` table. All five keys are required; the app doesn't otherwise use them. |
| `files` | array | One object per file: `collection.json` first, then every file under `files/`, sorted by path. |

Each object in `files`:

| Key | Type | Value |
| --- | --- | --- |
| `path` | text | `"collection.json"`, or `"files/"` + the file's relative path, for example `"files/photos/<watch id>/<photo id>-original.jpg"`. Never includes a top folder, even when the ZIP has one. |
| `size` | whole number | The file's length in bytes. |
| `sha256` | text | SHA-256 of the file's bytes, **lower-case hex**, 64 characters. |

Rules:
- `manifest.json` does **not** list itself.
- Every file a row points at must be listed **and** be in the ZIP. Listed but absent stops the restore (§3.4). In the ZIP but not listed: the file is ignored, and a row pointing at it is skipped with a warning (§3.2).
- The hash of `collection.json` is of the exact bytes stored in the ZIP. Pretty-printed or compact JSON both work; just hash what you write.
- No byte-order mark (BOM) at the start of `manifest.json` (see §13).

Example:

```json
{
  "format": "io.github.tunlezah.watches.backup",
  "format_version": 1,
  "schema_version": 1,
  "created_at": "2026-11-25T10:00:00Z",
  "app": "Watches import (from collection.xlsx)",
  "counts": { "watches": 2, "photos": 1, "service_entries": 1, "documents": 1, "snapshots": 0 },
  "files": [
    { "path": "collection.json", "size": 3184, "sha256": "<64 lower-case hex characters: the SHA-256 of collection.json>" },
    { "path": "files/documents/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/9b1f5c2d-6e4a-4d8b-9c3f-7a2e1b0d4c58.pdf", "size": 81234, "sha256": "<SHA-256 of the PDF>" },
    { "path": "files/photos/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/4e2b8a1c-0d3f-4b6e-8a7c-1f2e3d4c5b6a-original.jpg", "size": 2381177, "sha256": "<SHA-256 of the JPEG>" }
  ]
}
```

The `<…>` values are placeholders: compute every hash from the real bytes. This is the manifest of the §16 example.

---

## 7. collection.json and its tables

### 7.1 Shape

One JSON object. Each key is a table name, each value is an array of rows. Each row is an object whose keys are that table's column names.

```json
{
  "watch": [ … ],
  "watch_complication": [ … ],
  "field_provenance": [ … ],
  "other_spec": [ … ],
  "photo": [ … ],
  "service_entry": [ … ],
  "box_papers_item": [ … ],
  "document": [ … ],
  "source_snapshot": [ … ],
  "setting": [ … ]
}
```

- A table you have nothing for is `[]` or left out, **never `null`** (`"photo": null` makes the whole file unreadable).
- Unknown table names and unknown column names are ignored (so a typo loses that data without any error).
- The app inserts parents before children, so the order of rows and tables doesn't matter.
- Two rows with the same primary key: the later one wins.

### 7.2 JSON value rules

The app reads this file with a strict JSON parser (kotlinx.serialization, non-lenient). The table below says what each kind of column accepts; §13 has the tested behaviour for each mistake.

| Column type in the tables below | Write it as | Don't |
| --- | --- | --- |
| **text** | a JSON string: `"Omega"`, and **always** in quotes, even when it looks like a number: `"reference": "16610"`, `"caliber": "8800"`, `"serial_number": "87654321"` | `"reference": 16610` or `"brand": true` (⛔ the whole file is refused); empty strings `""` (leave the key out instead) |
| **whole** (Int/Long) | a JSON integer: `300`, `1795600000000` | `300.0` (⛔ the whole file is refused); `""` (⛔); `3e2` or `"300"` (happen to work, don't rely on them) |
| **decimal** | a JSON number: `41`, `41.5` | `NaN`, `Infinity`, `""` (⛔); `"41.5"` (happens to work, don't rely on it) |
| **true/false** | `true` or `false` | `1`, `"yes"` (⛔); `"true"` (happens to work, don't rely on it) |
| **date** | text `"YYYY-MM-DD"`: `"2021-05-01"` | `"01/05/2021"`, `"May 2021"`, `"2021-5-1"` |
| **timestamp** | whole number of milliseconds since 1970-01-01 UTC | seconds (`1795600000`), decimals, ISO text |
| **enum** | one of the lower-case keys in §8 | labels like `"Automatic"` |

General rules:
- **Required** in the tables below means the key must be present and not `null`; otherwise the whole file is unreadable and nothing is restored.
- **Optional** keys can be left out or set to `null`; both mean "not set". Leaving them out is tidier.
- The file is UTF-8 without a byte-order mark. Any Unicode is fine inside strings (accents, `…`, emoji).
- Text has no length limit, but keep single-line fields to one line. `private_notes`, `story` and `notes` may hold `\n` line breaks.

### 7.3 `watch`: one row per watch or wishlist entry

The **Field key** column is the stable name the app uses for each field elsewhere (provenance in §7.5, CSV export). The **Shown as** column is the label in the app.

| Column | Type | Req. | Field key | Shown as | Notes |
| --- | --- | --- | --- | --- | --- |
| `id` | text | **yes** | | | A new UUID (§11). Used as the folder name for this watch's files. |
| `status` | enum | **yes** | `ownership.status` | Status | `owned`, `wishlist`, `incoming`, `in_service`, `sold` (§8.1). |
| `archived_at` | timestamp | no | | | Set only to archive the watch (hidden from the main tabs, shown under Archived). |
| `created_at` | timestamp | **yes** | | | When the entry was made. "Recently added" sorts on it, newest first. |
| `updated_at` | timestamp | **yes** | | | Last edit. Merge compares it (§12). Use the same value as `created_at` for a first import. |
| `cover_photo_id` | text | no | | | The `id` of one of **this watch's** photos. Leave out to use the first own photo. |
| `brand` | text | no | `identity.brand` | Brand | "Omega" |
| `collection` | text | no | `identity.collection` | Collection | "Seamaster" |
| `model_name` | text | no | `identity.model_name` | Model | "Seamaster Diver 300M" |
| `reference` | text | no | `identity.reference` | Reference | "210.30.42.20.01.001" |
| `nickname` | text | no | `identity.nickname` | Nickname | The owner's name for it. |
| `year_from` | whole | no | `identity.year_from` | Produced from | 4-digit year. |
| `year_to` | whole | no | `identity.year_to` | Produced until | 4-digit year. |
| `limited_number` | whole | no | `identity.limited_number` | Limited edition no. | This watch's number, e.g. 123 of 500. |
| `limited_total` | whole | no | `identity.limited_total` | Limited edition of | The edition size, e.g. 500. |
| `case_material` | text | no | `case.material` | Material | "Stainless steel" |
| `diameter_mm` | decimal | no | `case.diameter_mm` | Diameter | Millimetres. |
| `thickness_mm` | decimal | no | `case.thickness_mm` | Thickness | Millimetres. |
| `lug_to_lug_mm` | decimal | no | `case.lug_to_lug_mm` | Lug to lug | Millimetres. |
| `lug_width_mm` | decimal | no | `case.lug_width_mm` | Lug width | Millimetres. |
| `bezel` | text | no | `case.bezel` | Bezel | |
| `crystal` | text | no | `case.crystal` | Crystal | "Sapphire" |
| `case_back` | text | no | `case.case_back` | Case back | |
| `crown` | text | no | `case.crown` | Crown | |
| `water_resistance_m` | whole | no | `case.water_resistance_m` | Water resistance | **Metres** (convert from ATM/bar/ft, §9). |
| `dial_colour` | text | no | `dial.colour` | Colour | British spelling in the key. |
| `dial_finish` | text | no | `dial.finish` | Finish | "Sunburst" |
| `indices` | text | no | `dial.indices` | Indices | |
| `hands` | text | no | `dial.hands` | Hands | |
| `lume` | text | no | `dial.lume` | Lume | "Super-LumiNova" |
| `caliber` | text | no | `movement.caliber` | Calibre | "8800" |
| `base_caliber` | text | no | `movement.base_caliber` | Base calibre | "ETA 2824-2" |
| `movement_type` | enum | no | `movement.type` | Type | §8.2. |
| `jewels` | whole | no | `movement.jewels` | Jewels | |
| `frequency_bph` | whole | no | `movement.frequency_bph` | Frequency | **Beats per hour** (convert from Hz, §9). |
| `power_reserve_h` | decimal | no | `movement.power_reserve_h` | Power reserve | **Hours**. |
| `hacking` | true/false | no | `movement.hacking` | Hacking seconds | |
| `hand_winding` | true/false | no | `movement.hand_winding` | Hand-winding | |
| `strap_type` | text | no | `strap.type` | Type | "Bracelet", "Strap" |
| `strap_material` | text | no | `strap.material` | Material | |
| `clasp` | text | no | `strap.clasp` | Clasp | |
| `fitted_strap` | text | no | `strap.fitted` | Currently fitted | |
| `acquired_on` | date | no | `ownership.acquired_on` | Date acquired | Full date only (§9). |
| `acquired_from` | text | no | `ownership.acquired_from` | Acquired from | |
| `price_minor` | whole | no | `ownership.price_paid` | Price paid | Minor units (cents). Shown only when `price_currency` is also set. |
| `price_currency` | text | no | `ownership.price_paid` | | ISO 4217, upper case: `AUD`, `USD`, `EUR`… |
| `condition` | enum | no | `ownership.condition` | Condition | §8.3. |
| `serial_number` | text | no | `ownership.serial_number` | Serial number | Hidden behind a tap in the app. |
| `private_notes` | text | no | `ownership.private_notes` | Private notes | Multi-line allowed. |
| `story` | text | no | `ownership.story` | The watch's story | Multi-line allowed. |
| `service_interval_months` | whole | no | `service.interval_months` | Service interval | e.g. `60`. |
| `service_reminder` | true/false | **yes** | | | Whether the app reminds about servicing. `false` unless the user wants reminders. A due date also needs `service_interval_months` and a start (`acquired_on` or a service entry), and only `owned` or `in_service` watches that aren't archived get one. |
| `warranty_start` | date | no | `service.warranty_start` | Warranty start | |
| `warranty_months` | whole | no | `service.warranty_months` | Warranty length | |

**The title** the app shows is `brand` + (`model_name`, else `collection`, else `reference`), else `nickname`, else "Untitled watch". Give every watch at least a brand and a model.

### 7.4 `watch_complication`: the watch's complications

One row per complication. Primary key: (`watch_id`, `key`).

| Column | Type | Req. | Notes |
| --- | --- | --- | --- |
| `watch_id` | text | **yes** | The watch's `id`. |
| `key` | text | **yes** | A key from §8.4, or `custom:<Label>` for anything else, e.g. `"custom:Helium valve"`. A custom label must not contain `\|`. |

### 7.5 `field_provenance`: where each value came from (optional)

One row per field, saying whether the user typed it or it came from a source. Primary key: (`watch_id`, `field_key`). **This table is optional.** A filled field with no row is treated exactly like one the user typed: shown without a source label, and protected from being replaced by a later online lookup.

| Column | Type | Req. | Notes |
| --- | --- | --- | --- |
| `watch_id` | text | **yes** | |
| `field_key` | text | **yes** | A field key from the §7.3 table (e.g. `case.diameter_mm`), or `movement.complications`. |
| `source` | text | **yes** | `"manual"` for the user's own data. For data from a website: `watchbase`, `grail`, `wikidata`, `wikipedia`, `ranfft`, or any short name (shown as written). |
| `source_url` | text | no | The page it came from. The app links to it. |
| `fetched_at` | timestamp | no | When it was fetched. |
| `user_edited` | true/false | **yes** | `true` for the user's own values. `false` for an untouched value from a source. |

What the app itself writes when someone types a watch in: one row per filled field (not for `status`) with `"source": "manual", "user_edited": true`. Copy that, or leave the table empty. Use a non-manual source only when the value really came from that source; the app then shows the source's name and link beside the value.

### 7.6 `other_spec`: extra specs with no column of their own (optional)

For facts that fit no field in §7.3 ("Certification: COSC", "Bracelet taper: 20–16 mm"). Shown in their own list on the watch.

| Column | Type | Req. | Notes |
| --- | --- | --- | --- |
| `id` | text | **yes** | Unique. A UUID, or `<watch id>-spec-<n>`. |
| `watch_id` | text | **yes** | |
| `label` | text | **yes** | "Certification" |
| `value` | text | **yes** | "COSC" |
| `source` | text | no | As in §7.5. |
| `source_url` | text | no | |
| `fetched_at` | timestamp | no | |
| `position` | whole | **yes** | Display order within the watch: 0, 1, 2… |

### 7.7 `photo`: one row per photo

| Column | Type | Req. | Notes |
| --- | --- | --- | --- |
| `id` | text | **yes** | A new UUID. |
| `watch_id` | text | **yes** | |
| `position` | whole | **yes** | Order in the watch's gallery: 0, 1, 2… |
| `original_file` | text | **yes** | Relative path, e.g. `photos/<watch id>/<photo id>-original.jpg` (§10). |
| `display_file` | text | **yes** | For an import, **the same path as `original_file`** (§10.3). |
| `thumb_file` | text | **yes** | For an import, **the same path as `original_file`**. |
| `width` | whole | **yes** | Width in pixels of the **upright** picture, after the EXIF orientation is applied (§10.3). |
| `height` | whole | **yes** | Height in pixels of the upright picture. |
| `crop_left`, `crop_top`, `crop_right`, `crop_bottom` | decimal | no | Fractions 0–1 of the upright picture, with right > left and bottom > top. All four or none. Leave out for an import. |
| `kind` | enum | **yes** | `user` (the owner's photo) or `reference` (a press or catalogue image). §8.5. |
| `attribution` | text | no | Credit for a `reference` image, e.g. "© Omega". |
| `attribution_url` | text | no | Where the image came from. |
| `added_at` | timestamp | **yes** | |

### 7.8 `service_entry`: service history

| Column | Type | Req. | Notes |
| --- | --- | --- | --- |
| `id` | text | **yes** | A new UUID. |
| `watch_id` | text | **yes** | |
| `serviced_on` | date | **yes** | `"2024-02-01"`. If the day is unknown use the 1st and say so in `notes`. |
| `serviced_by` | text | no | "Omega Service Centre Sydney" |
| `work` | text | **yes** | Keys from §8.6 joined by commas, **no spaces**: `"full_service,gaskets"` (a space stops a full service counting towards the next-service date). Use `""` if unknown: the key itself is required. |
| `cost_minor` | whole | no | Cents. |
| `cost_currency` | text | no | ISO 4217. If left out while `cost_minor` is set, `AUD` is assumed. |
| `warranty_months` | whole | no | Warranty on this work. |
| `notes` | text | no | |
| `created_at` | timestamp | **yes** | |
| `updated_at` | timestamp | **yes** | Merge compares it. |

### 7.9 `box_papers_item`: the full-set checklist

One row per item. Primary key: (`watch_id`, `item`). An item with no row shows as "not recorded".

| Column | Type | Req. | Notes |
| --- | --- | --- | --- |
| `watch_id` | text | **yes** | |
| `item` | enum | **yes** | §8.7: `inner_box`, `outer_box`, `warranty_card`, `manual`, `hang_tags`, `extra_links`, `receipt`, `other`. |
| `state` | enum | **yes** | §8.8: `present`, `missing`, `not_applicable`. |
| `label` | text | no | Not used by the app. Leave out. |
| `note` | text | no | "Card dated 2019" |

A row whose `item` or `state` isn't a known key is ignored by the app.

### 7.10 `document`: PDFs and photographed papers

| Column | Type | Req. | Notes |
| --- | --- | --- | --- |
| `id` | text | **yes** | A new UUID. |
| `watch_id` | text | **yes** | |
| `service_entry_id` | text | no | The `id` of a `service_entry` in this backup, for an invoice from that service. |
| `kind` | enum | **yes** | §8.9: `manual`, `quick_start`, `receipt`, `invoice`, `warranty`, `certificate`, `other`. Manuals and quick-start guides are offered to other watches of the same model. |
| `title` | text | **yes** | "Service invoice March 2024". Not empty. |
| `file` | text | **yes** | `documents/<watch id>/<document id>.pdf` (or `.jpg`, `.png`, `.webp`). |
| `mime_type` | text | **yes** | `application/pdf`, `image/jpeg`, `image/png` or `image/webp`, matching the file's real format. |
| `size_bytes` | whole | **yes** | The file's exact length in bytes. |
| `added_at` | timestamp | **yes** | |

### 7.11 `source_snapshot`: saved lookup pages (leave empty)

The app keeps the raw page an online lookup fetched, so a fixed parser can re-read it later. An import has no use for it: **write `[]`**. For completeness:

| Column | Type | Req. | Notes |
| --- | --- | --- | --- |
| `id` | text | **yes** | |
| `watch_id` | text | **yes** | |
| `source` | text | **yes** | `watchbase`, `grail`, `wikidata`, `ranfft` |
| `url` | text | **yes** | |
| `fetched_at` | timestamp | **yes** | |
| `content_type` | text | **yes** | e.g. `application/json` |
| `body_file` | text | **yes** | `snapshots/<watch id>/<source>-<snapshot id>.json` or `.html` |

### 7.12 `setting`: app settings (leave empty)

| Column | Type | Req. |
| --- | --- | --- |
| `key` | text | **yes** |
| `value` | text | **yes** |

**Recommended: `[]`.** Only used by Replace (§4). If the user asks for particular settings, these keys and values work (values are text):

| Key | Values |
| --- | --- |
| `ui.theme` | `system`, `light`, `dark` |
| `ui.direction` (the design) | `atelier`, `instrument`, `gallery` |
| `ui.accent` | `champagne`, `lume`, `ice_blue`, `racing_green`, `bordeaux`, `sunburst_blue`, `tropical_amber` |
| `collection.layout` | `grid`, `list` |
| `collection.sort` | `brand`, `date_acquired`, `case_size`, `recently_added` |
| `collection.sort_reversed` | `true`, `false` |
| `display.date_format` | `dmy`, `ymd`, `mdy` |
| `display.water_resistance_unit` | `atm`, `bar` |
| `display.currency` | an ISO 4217 code, e.g. `AUD` |
| `lookup.disabled_sources` | comma-separated source names, e.g. `ranfft,wikidata` |

Keys starting with `device.` are never restored.

---

## 8. Allowed values (enumerations)

Write the **key** (left column) exactly, lower case. The label is what the app shows.

### 8.1 Watch status (`watch.status`)
| Key | Label | Use for |
| --- | --- | --- |
| `owned` | Owned | In the collection now. |
| `wishlist` | Wishlist | Wanted, not owned. |
| `incoming` | Incoming | Bought, not yet arrived. |
| `in_service` | In service | Away being serviced. |
| `sold` | Sold / gone | Sold, traded, given away, lost. |

An unknown status is shown as `owned`.

### 8.2 Movement type (`watch.movement_type`)
| Key | Label |
| --- | --- |
| `automatic` | Automatic |
| `manual` | Manual winding |
| `quartz` | Quartz |
| `solar` | Solar |
| `spring_drive` | Spring Drive |
| `kinetic` | Kinetic |
| `other` | Other |

An unknown key shows as no movement type.

### 8.3 Condition (`watch.condition`)
`unworn`, `mint`, `excellent`, `very_good`, `good`, `fair`, `poor` (Unworn, Mint, Excellent, Very good, Good, Fair, Poor). An unknown key shows as no condition.

### 8.4 Complications (`watch_complication.key`)
| Key | Label | Key | Label |
| --- | --- | --- | --- |
| `date` | Date | `power_reserve` | Power-reserve indicator |
| `day_date` | Day-date | `alarm` | Alarm |
| `big_date` | Big date | `annual_calendar` | Annual calendar |
| `gmt` | GMT / dual time | `perpetual_calendar` | Perpetual calendar |
| `world_time` | World time | `small_seconds` | Small seconds |
| `chronograph` | Chronograph | `jump_hour` | Jump hour |
| `flyback` | Flyback | `tourbillon` | Tourbillon |
| `rattrapante` | Rattrapante | `minute_repeater` | Minute repeater |
| `moon_phase` | Moon phase | | |

Anything else: `custom:<Label>`, e.g. `custom:Helium valve`, `custom:Tide indicator`.

### 8.5 Photo kind (`photo.kind`)
`user` (Your photo), `reference` (Reference image). A reference image is never picked as the cover automatically. An unknown kind (even `"User"`) shows as `user` on the watch's own page, but is never picked as the cover in the grid or the Service tab.

### 8.6 Service work (`service_entry.work`, comma-separated)
`full_service`, `regulation`, `crystal`, `gaskets`, `battery`, `polish`, `other`. Unknown keys are dropped.

### 8.7 Box & papers item (`box_papers_item.item`)
`inner_box` (Inner box), `outer_box` (Outer box), `warranty_card` (Warranty card / papers), `manual` (Manual), `hang_tags` (Hang tags), `extra_links` (Extra links), `receipt` (Receipt), `other` (Other).

### 8.8 Box & papers state (`box_papers_item.state`)
`present`, `missing`, `not_applicable`.

### 8.9 Document kind (`document.kind`)
`manual`, `quick_start`, `receipt`, `invoice`, `warranty`, `certificate`, `other`. An unknown kind is treated as `other`.

---

## 9. Units, numbers, money, dates and times

The app stores one unit per measurement and converts for display. Always convert to the stored unit.

| Field | Stored unit | Conversions |
| --- | --- | --- |
| `diameter_mm`, `thickness_mm`, `lug_to_lug_mm`, `lug_width_mm` | millimetres | inches × 25.4 |
| `water_resistance_m` | metres, whole | 1 ATM = 1 bar = 10 m; feet × 0.3048, then round. "100M", "10ATM", "10 bar" → `100` |
| `frequency_bph` | beats per hour, whole | Hz × 7200: 4 Hz → `28800`; 3 Hz → `21600`; 2.5 Hz → `18000`; 5 Hz → `36000`. "vph" and "A/h" are the same as bph. |
| `power_reserve_h` | hours | days × 24 |
| `price_minor`, `cost_minor` | minor units (whole) | Amount × 100, rounded. **JPY and KRW: × 1** (no minor unit). The app treats every other currency as having 2 decimals. |
| `acquired_on`, `warranty_start`, `serviced_on` | `YYYY-MM-DD` | Zero-padded. A date that doesn't parse is shown as empty (`serviced_on` as 1 January 1970). |
| `created_at`, `updated_at`, `added_at`, `archived_at`, `fetched_at` | ms since 1970-01-01T00:00:00Z | Seconds × 1000. Python: `int(time.time() * 1000)`. |

**Partial dates.** The app only takes full dates. If the user's list says "2019" or "March 2019" for when they bought a watch, either leave `acquired_on` out and put "Bought March 2019" in `private_notes`, or, if the user agrees, use the first of the month (`2019-03-01`).

**Money without a currency.** Ask the user, or use the currency of the rest of their list. The app's own default is AUD.

**Prices with a range or "approx."**: use the number and put the wording in `private_notes`.

---

## 10. Files: photos, documents, snapshots

### 10.1 The path rule

Every path in a row (`original_file`, `display_file`, `thumb_file`, `file`, `body_file`) must match:

```
^(photos|documents|snapshots)/[A-Za-z0-9][A-Za-z0-9._-]{0,127}/[A-Za-z0-9][A-Za-z0-9._-]{0,191}$
```

In words: one of the three top folders, a slash, a folder name, a slash, a file name. Folder and file names start with a letter or digit and contain only letters, digits, `.`, `_` and `-`. No spaces, no accents, no other slashes, no `..`. A row whose path breaks this is skipped with a warning.

Inside the ZIP the same file is at `files/` + that path, and the manifest lists it as `files/` + that path.

**Use lower case.** A Mac's disk usually doesn't tell `A.jpg` from `a.jpg`. The app copes, but avoid names that differ only by case.

### 10.2 Naming conventions (what the app itself does)

| File | Path |
| --- | --- |
| Photo original | `photos/<watch id>/<photo id>-original.<ext>`, `<ext>` = `jpg`, `png`, `webp` or `heic` |
| Photo display image | `photos/<watch id>/<photo id>-display-<tag>.jpg` |
| Photo thumbnail | `photos/<watch id>/<photo id>-thumb-<tag>.jpg` |
| Document | `documents/<watch id>/<document id>.<ext>`, `<ext>` = `pdf`, `jpg`, `png` or `webp` |
| Source snapshot | `snapshots/<watch id>/<source>-<snapshot id>.<json\|html>` |

`<tag>` is a few random lower-case letters and digits. The app doesn't require these patterns (only the §10.1 rule), but follow them.

### 10.3 Photos

**Formats.** The original must be **JPEG, PNG, WebP or HEIC**, judged by the file's bytes, not its name. Give it the matching extension. Convert anything else (TIFF, GIF, BMP, AVIF, RAW) to JPEG before packaging. The app would convert them after the restore, but only on devices that can decode them.

**Originals only: the recommended way.** Don't make display images or thumbnails. Put the same path in all three columns:

```json
"original_file": "photos/W/P-original.jpg",
"display_file":  "photos/W/P-original.jpg",
"thumb_file":    "photos/W/P-original.jpg"
```

After the restore the app renders a display image (long edge at most 2560 px) and a thumbnail (at most 640 px) from the original, with the crop applied. (✅ §13.)

**If you do make renditions** (only worth it for a very large import, so the first launch is quicker): JPEG, sRGB, upright, no metadata, long edge ≤ 2560 px (display) and ≤ 640 px (thumbnail), never enlarged, transparent areas black, crop already applied. Name them as in §10.2 and list them in the manifest.

**`width` and `height`** are the pixel size of the picture **as a person sees it**: if the JPEG's EXIF orientation is 5, 6, 7 or 8 (turned 90°), swap the stored width and height. With Pillow: `ImageOps.exif_transpose(img).size`. If they're wrong, the app's photo check corrects them after the restore and drops that photo's crop (✅ §13), but get them right.

**Location.** Remove GPS data from photos before packaging, for the user's privacy: re-save without EXIF GPS, or strip the GPS block. The app's photo check removes any location left in an original after the restore (✅ §13), but the backup file itself would still carry it.

**Cover.** Set the watch's `cover_photo_id` to the photo you want first in the grid, usually the first `user` photo. Without one the app uses the `user` photo with the lowest `position`. A watch with only `reference` photos and no cover shows the app's drawing in the grid.

**Size.** No hard limit. Phone photos (2–8 MB) are fine. Very large files slow the restore, and the free-space check needs the total plus 64 MB.

### 10.4 Documents

- **PDF**, or a photographed page as **JPEG, PNG or WebP**. A HEIC is turned into a JPEG by the photo check after the restore. Other types (Word, text) aren't documents the app can show: convert to PDF.
- `mime_type` must match the bytes (`application/pdf` for a file starting `%PDF-`).
- `size_bytes` = the exact file length.
- Keep each under **50 MB**. That is the app's limit when adding a document by hand; a restore doesn't enforce it, but don't exceed it.
- Photographed documents have their location removed by the photo check, like photos.

### 10.5 Snapshots

Leave out (§7.11).

---

## 11. IDs

### 11.1 Format

Use a **random UUID, version 4, lower case**: `"7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47"`. Python: `str(uuid.uuid4())`. This is what the app makes. Any text that follows the §10.1 folder rule works, but UUIDs never clash.

### 11.2 Rules

- **Unique across the whole collection**, not just your file: Merge matches watches, photos, service entries and documents **by ID**. Reusing an ID that's already on the device makes Merge treat your row as a new version of that existing one.
- **Never start an ID with `sample-`.** The app's Settings › Diagnostics screen deletes every watch whose ID starts with `sample-` when the user removes the sample watches.
- `watch.id` is also the folder name in file paths, so it must follow the §10.1 rule (a UUID does).
- `other_spec.id` can be `<watch id>-spec-<n>`.

### 11.3 Keep a map if you may import again

If the user might give you a corrected list later, keep a table of *their* key for each watch (a row number, a reference plus serial, a name) → the UUID you gave it, and the photo and document IDs. A corrected import must reuse these IDs; otherwise Merge adds duplicates beside the earlier watches (§12).

### 11.4 Never reuse a file name for different contents

If a corrected import replaces a photo or document, give the new file a **new path** (for example `<photo id>-original-<tag>.jpg`), even when the photo's ID stays the same. App versions **before 0.8.0** stop a restore with an error when a file arrives at a path that already holds different contents. 0.8.0 and later cope, but the new path is safe everywhere. (In-repo: `PersonalBackupMaker` does exactly this, and `PersonalBackupCheck` tests for it.)

---

## 12. Merge in detail, and re-importing corrections

Merge matches on IDs and **never deletes anything**.

**Watches.** For each watch in the backup:

| On the device? | Rule | Result |
| --- | --- | --- |
| No watch with that `id` | | **Added**, with all its rows. |
| Yes, and the watch row and its own lists are identical | | **Unchanged.** |
| Yes, and the backup's `updated_at` is **greater** | the backup is newer | **Updated:** the backup's watch row replaces the device's, and its complications, provenance, other specs and box & papers **replace** the device's lists for that watch. |
| Yes, and the backup's `updated_at` is **equal or smaller** | the device is newer or the same age | **Kept:** the device's watch row and lists stay. |

**Other rows** (only for watches that are in the backup):

| Table | Not on the device | Same ID on the device |
| --- | --- | --- |
| `photo` | added | replaced only if its watch was Updated |
| `document` | added | replaced only if its watch was Updated |
| `service_entry` | added | replaced if the backup's `updated_at` is greater |
| `source_snapshot` | added | kept |
| `setting` | ignored | ignored |

Rows on the device that the backup doesn't have are left alone, even for an Updated watch: a photo removed from your list stays on the device. Removing things is the user's job, in the app.

**What this means for an agent:**

- **First import onto a device with watches:** new UUIDs for everything. All are Added.
- **Corrected import:** reuse every ID from the first import, and set every changed watch's `updated_at` to **now** (later than the first import's). Unchanged watches can keep their old `updated_at`.
  - If the user edited a watch in the app after the first import, the device's `updated_at` is newer: the device's version is Kept unless your `updated_at` is later still. Tell the user.
  - A photo with a **new ID** is added beside the old one, not instead of it. To replace a photo, keep its ID, give its file a new path (§11.4), and make sure its watch is Updated.
  - What needs which `updated_at` raised:
    - a changed field on the watch, or changed complications, box & papers, provenance or other specs → the **watch's** `updated_at`;
    - a changed photo or document (same ID) → the **watch's** `updated_at` (they follow their watch);
    - a changed service entry (same ID) → **that entry's own** `updated_at`. Raising only the watch's isn't enough: the change is silently lost.
- **Adding photos, service history or documents to watches already on the device** (watches the user typed in, not ones you imported): you need those watches' IDs, and the backup must include **their `watch` rows**. Child rows for a watch that isn't in the backup are skipped (✅ §13). Ask the user for a backup made in the app (Settings › Backup & restore › Back up now), copy the `watch` rows (and their complication, provenance, other-spec and box & papers rows) **unchanged**, and add your new photo, service-entry and document rows with new IDs. The watch then counts as Unchanged and your new rows are added. (✅ §13)

---

## 13. What the app does with imperfect data (tested)

Each case below was made with a hand-made backup and restored with the app's own code (Replace onto a new install, unless it says Merge). **Fatal** means nothing is restored and the device is unchanged.

Legend: ⛔ fatal (nothing restored) · ⚠️ restored, with a warning on screen · 🔧 restored, and the app quietly fixed or ignored something · ✅ works as you'd hope.

### 13.1 The ZIP

| Case | Result |
| --- | --- |
| Stored entries, written with Python `zipfile` to a file | ✅ |
| Deflated entries, a top folder (`My import/…`), a `__MACOSX/` entry and directory entries | ✅ |
| `zip -r` (Info-ZIP) with default compression, with `-0` (store), with a top folder | ✅ |
| Entries in another order (`files/…` first, `manifest.json` last) | ✅ |
| Python `zipfile` writing **stored** entries to a pipe (sizes in data descriptors) | ⛔ "This file isn't a Watches backup." |
| Python `zipfile` writing **deflated** entries to a pipe | ✅ |

### 13.2 manifest.json

| Case | Result |
| --- | --- |
| `app`, `created_at` or `counts` missing | ⛔ "This file isn't a Watches backup." |
| `format` misspelt | ⛔ "This file isn't a Watches backup." |
| A UTF-8 byte-order mark before the `{` | ⛔ "This file isn't a Watches backup." |
| `format_version` 2, or `schema_version` 2 | ⛔ "…made by a newer version of Watches…" |
| Hashes in upper case | ⛔ damaged ("collection.json doesn't match its checksum") |
| `collection.json` not in `files` | ⛔ damaged ("collection.json isn't listed") |
| `created_at` that isn't a date ("yesterday") | ✅ restores; the preview says "Watches backup" instead of a date |
| Wrong `counts` | ✅ ignored; the preview counts the rows itself |
| Wrong `size` values | ✅ restores (only used for the space check and progress bar). Still write the right sizes: the space check relies on them. |

### 13.3 JSON values in collection.json

| Case | Result |
| --- | --- |
| A UTF-8 byte-order mark before the `{` | ⛔ damaged ("collection.json can't be read") |
| A trailing comma (`[ … ],` before `}`) | ⛔ can't be read |
| Pretty-printed or all on one line | ✅ |
| A whole-number field with a decimal point: `"water_resistance_m": 300.0` | ⛔ can't be read |
| A text field holding an unquoted number or boolean: `"reference": 16610`, `"brand": true` | ⛔ can't be read |
| An empty string in a number field: `"year_from": ""` | ⛔ can't be read |
| A whole table set to `null`: `"source_snapshot": null` | ⛔ can't be read |
| A whole number written with an exponent: `"water_resistance_m": 3e2` | 🔧 read as 300. Don't rely on it. |
| A timestamp as a decimal: `"created_at": 1790000000.5` | ⛔ can't be read |
| `true` written as `1` | ⛔ can't be read |
| A required key missing (`service_reminder`, `updated_at`, a service entry's `work`, a child row's `watch_id`) | ⛔ can't be read |
| A required key set to `null` (`"status": null`) | ⛔ can't be read |
| An optional key set to `null` | ✅ same as leaving it out |
| Unknown keys in a row, an unknown table | 🔧 ignored |
| A number in quotes (`"41"`, `"300"`) | 🔧 accepted. Don't rely on it: write real numbers. |
| `true` in quotes (`"true"`) | 🔧 accepted. Don't rely on it: write `true`. |
| A whole number in a decimal field (`"diameter_mm": 42`) | ✅ read as 42.0 |
| Two `watch` rows with the same `id` | 🔧 the later row wins; the preview's count includes both |

### 13.4 Rows

| Case | Result |
| --- | --- |
| Only a `watch` table, with only the required keys and a brand and model | ✅ the smallest working import |
| `status` `"collection"`, `movement_type` `"mechanical"`, `condition` `"used"` (not keys) | 🔧 stored as written, shown as **Owned**, no movement type, no condition |
| `acquired_on` `"01/05/2021"` | 🔧 stored, shown as **no date** |
| `work` `"Full Service, battery"` | 🔧 only `battery` is shown: keys are case-sensitive. Spaces are trimmed for display only: the Service tab looks for `,full_service,` exactly, so `"battery, full_service"` shows "Full service" but **doesn't count it** for the next-service date. No spaces. |
| Complication `"Helium valve"` without `custom:` | 🔧 shown as "Helium valve", but it isn't the app's form: use `custom:Helium valve` |
| A watch's `cover_photo_id` naming another watch's photo | 🔧 cleared; the first own photo is used |
| A document's `service_entry_id` naming no service entry | 🔧 the link is removed |
| A photo whose `watch_id` is no watch in the backup | ⚠️ "1 record for watches not in the backup will be skipped." |
| A complication, provenance, other-spec or box & papers row whose `watch_id` is no watch in the backup | 🔧 dropped **with no warning** (read from the code: the warning is counted before these four tables are filtered) |
| A crop outside 0–1 | 🔧 ignored: the photo shows uncropped |
| `setting` rows `ui.accent` and `device.app_lock` | 🔧 `ui.accent` restored, `device.app_lock` dropped |
| Replace onto a device that had settings, with `setting: []` | 🔧 the device's settings are all removed (back to defaults) |

### 13.5 Files

| Case | Result |
| --- | --- |
| A file listed in the manifest but not in the ZIP | ⛔ preview works, then the restore stops: damaged ("1 files are missing") |
| A file in the ZIP but not in the manifest | ⚠️ "1 photo missing from the backup will be skipped." |
| A photo path with a space (`photos/<id>/my photo.jpg`) | ⚠️ the photo is skipped (same warning) |
| A photo with only its original (`display_file` = `thumb_file` = `original_file`) | ✅ display image and thumbnail made after the restore |
| A photo with its own display and thumbnail files | ✅ used as they are |
| A PNG original | ✅ kept as PNG; renditions are JPEG |
| PNG bytes in a file named `.jpg` | 🔧 accepted, kept. Use the right extension anyway. |
| An original with GPS in its EXIF | 🔧 the photo check rewrites it without the location (under a new file name) |
| A JPEG stored on its side (EXIF orientation 6) with `width`/`height` of the stored pixels | 🔧 the photo check corrects them to the upright size |
| `width` and `height` of `0` | 🔧 corrected by the photo check, on a device that can decode the photo |
| A photographed document (JPEG) with GPS | 🔧 rewritten without the location; `size_bytes` updated |
| A document with the wrong `size_bytes` and `mime_type` `application/octet-stream` | 🔧 restored **as written, not corrected**. The app decides how to show a document from `mime_type`, so it must be right. |

### 13.6 Merge

| Case | Result |
| --- | --- |
| Same watch IDs, one watch changed with a **newer** `updated_at` | ✅ that watch Updated, the other Unchanged |
| Same watch IDs, one watch changed, **same** `updated_at` | 🔧 Kept: the device's version stays, the change is lost |
| Same photo ID, new file path, watch Updated | ✅ photo replaced; the old file is deleted |
| Same photo ID, **same path with different contents**, watch Updated | ✅ on 0.8.0 (the file is stored as `…-r<tag>.jpg`); **fails on older versions** (§11.4) |
| New watch IDs for the same watches | 🔧 duplicates: every watch now appears twice |
| Photo and service-entry rows for a watch that is only on the device (no `watch` row in the backup) | ⚠️ "2 records for watches not in the backup will be skipped." Nothing added. |
| The same, with that watch's `watch` row (and its own lists) copied unchanged into the backup | ✅ watch Unchanged; the photo and service entry are added |
| Merge onto a device with other watches and settings | ✅ all added beside them; the device's settings stay |
| Restoring the §16 example, packaged with the §15 code | ✅ no warnings; photo rendered; invoice linked to its service entry |

---

## 14. Recipe: build an import backup step by step

Follow in order.

**Step 1: Read the user's list.** Identify one row per watch. Note which columns map to which fields (§17). Ask the user about anything you can't map: currency, date format (is 03/04/2021 the 3rd of April or the 4th of March?), units.

**Step 2: Decide Replace or Merge** with the user (§4). For a corrected re-import, load the ID map from last time (§11.3).

**Step 3: Make the IDs.** One UUID per watch, photo, service entry, document. Reuse IDs from the map for a re-import.

**Step 4: Build each `watch` row.**
- Start with the five required keys: `id`, `status`, `created_at`, `updated_at`, `service_reminder` (`false`).
- `created_at` = `updated_at` = the current time in ms. "Recently added" lists the **newest first**, so to show the user's list in their order there, count **down**: the first row gets the latest time, and each later row 1 s (1000 ms) less. (The app's default sort is by brand, so this only matters if the user sorts by Recently added.)
- Add each known field with the right type and unit (§7.3, §9). Leave out unknown ones. Don't write `""`, `null`, `0` or `"N/A"` for unknown values.
- Text fields are JSON strings even when the value is a number: `"reference": "16610"`. A spreadsheet or CSV reader often hands you `16610` (an int) or `16610.0` (a float) for such a column: convert it to text first, without the `.0`.
- Map statuses, movement types and conditions to keys (§8). Put anything that doesn't fit in `private_notes`.

**Step 5: Complications** → `watch_complication` rows (§7.4).

**Step 6 (optional): Provenance** → for each filled field, `{"watch_id": …, "field_key": …, "source": "manual", "user_edited": true}`. Skipping it is fine.

**Step 7: Extra facts** with no column → `other_spec` rows, or `private_notes`.

**Step 8: Photos.** For each image:
1. Check the format by its first bytes (JPEG `FF D8`, PNG `89 50 4E 47`, WebP `RIFF....WEBP`, HEIC `....ftypheic`/`mif1`). Convert others to JPEG.
2. Remove GPS metadata.
3. Read the upright width and height.
4. Path: `photos/<watch id>/<photo id>-original.<ext>`.
5. Row with `display_file` = `thumb_file` = `original_file`, `kind` = `user` (or `reference` for a press image, with `attribution`), `position` 0, 1, 2…, `added_at`.
6. Set the watch's `cover_photo_id`.

**Step 9: Service history** → `service_entry` rows (§7.8), dates as `YYYY-MM-DD`, work as keys.

**Step 10: Box & papers** → `box_papers_item` rows (§7.9).

**Step 11: Documents** → convert to PDF (or JPEG/PNG/WebP), path `documents/<watch id>/<document id>.pdf`, row with exact `size_bytes` and `mime_type` (§7.10).

**Step 12: Assemble `collection.json`** with all ten tables (`source_snapshot` and `setting` as `[]`). Encode as UTF-8 JSON, no BOM.

**Step 13: Write `manifest.json`** (§6): hash `collection.json` and every file, lower-case hex.

**Step 14: Write the ZIP to a file on disk:** `manifest.json`, `collection.json`, then each `files/…` entry, stored (§5, §15).

**Step 15: Check it** with §18. If you have a JDK, you can also run the in-repo checker (§20.2), but only on a backup where **every watch has photos and an explicit `cover_photo_id`**. It fails any other backup on purpose, even a valid one (such as §16's, whose wishlist Tudor has no photo). Don't "fix" a valid backup to please it.

**Step 16: Hand it over** and tell the user: copy the `.zip` to the device (Files, a cloud folder, AirDrop, USB), open Watches › Settings › Backup & restore › Restore from a backup, choose the file, check the preview, pick Merge or Replace as agreed. Save the ID map if they may want a corrected import later.

---

## 15. Reference packaging code

Python 3, standard library only. It turns the data you built (steps 4–12) into a valid backup. It does no data checking; §18 is for that.

```python
import hashlib, json, zipfile
from datetime import datetime, timezone

def write_watches_backup(out_path, collection, files, made_by="Watches import"):
    """
    out_path:   where to write, ending in .zip (a file on disk, not a pipe).
    collection: dict of table name -> list of row dicts, already following section 7.
    files:      dict of relative path (e.g. "photos/<watch id>/<photo id>-original.jpg") -> bytes.
                Must hold every path any row in `collection` points at.
    """
    collection_bytes = json.dumps(collection, ensure_ascii=False, indent=2, allow_nan=False).encode("utf-8")

    def listed(path, data):
        return {"path": path, "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}  # lower-case hex

    paths = sorted(files)
    manifest = {
        "format": "io.github.tunlezah.watches.backup",
        "format_version": 1,
        "schema_version": 1,
        "created_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "app": made_by,
        "counts": {
            "watches": len(collection.get("watch", [])),
            "photos": len(collection.get("photo", [])),
            "service_entries": len(collection.get("service_entry", [])),
            "documents": len(collection.get("document", [])),
            "snapshots": len(collection.get("source_snapshot", [])),
        },
        "files": [listed("collection.json", collection_bytes)] + [listed("files/" + p, files[p]) for p in paths],
    }
    manifest_bytes = json.dumps(manifest, indent=2).encode("utf-8")

    with zipfile.ZipFile(out_path, "w", compression=zipfile.ZIP_STORED) as z:
        z.writestr("manifest.json", manifest_bytes)
        z.writestr("collection.json", collection_bytes)
        for p in paths:
            z.writestr("files/" + p, files[p])
```

Notes:
- `allow_nan=False` makes Python refuse a `NaN` rather than write invalid JSON. Values from pandas or a CSV reader are often `NaN` for empty cells, floats for whole numbers, and ints for text columns that look numeric. **Before** building rows: drop empty values; convert whole-number columns with `int()`; convert text columns (reference, calibre, serial number, model names like "5513") with `str()`, removing any `.0` a float added.
- `hexdigest()` is already lower case.
- Python writes JSON `true`/`false`/numbers correctly from `True`/`False`/`int`/`float`.

---

## 16. A complete worked example

The user's list:

| # | Watch | Ref | Size | WR | Bought | Paid | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Omega Seamaster Diver 300M, blue dial, auto, date | 210.30.42.20.03.001 | 42 mm | 300 m | 12 March 2021 from Omega Sydney | A$7,350 | Full set. Serviced Feb 2024 by Omega, A$850, invoice attached. Photo: seamaster.jpg |
| 2 | Tudor Black Bay 58 (want one) | 79030N | 39 mm | 200 m | | | |

Assume the current time is `1795600000000` (2026-11-25), the IDs below are freshly made UUIDs, `seamaster.jpg` is a 4032 × 3024 JPEG (upright, GPS removed, 2,381,177 bytes), and the invoice is an 81,234-byte PDF.

`collection.json`:

```json
{
  "watch": [
    {
      "id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47",
      "status": "owned",
      "created_at": 1795600001000,
      "updated_at": 1795600001000,
      "cover_photo_id": "4e2b8a1c-0d3f-4b6e-8a7c-1f2e3d4c5b6a",
      "brand": "Omega",
      "collection": "Seamaster",
      "model_name": "Seamaster Diver 300M",
      "reference": "210.30.42.20.03.001",
      "diameter_mm": 42,
      "water_resistance_m": 300,
      "dial_colour": "Blue",
      "movement_type": "automatic",
      "acquired_on": "2021-03-12",
      "acquired_from": "Omega Sydney",
      "price_minor": 735000,
      "price_currency": "AUD",
      "service_reminder": false
    },
    {
      "id": "c81f6d20-3b9a-4e57-a1d4-6e0b2f9c7a13",
      "status": "wishlist",
      "created_at": 1795600000000,
      "updated_at": 1795600000000,
      "brand": "Tudor",
      "collection": "Black Bay",
      "model_name": "Black Bay 58",
      "reference": "79030N",
      "diameter_mm": 39,
      "water_resistance_m": 200,
      "service_reminder": false
    }
  ],
  "watch_complication": [
    { "watch_id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47", "key": "date" }
  ],
  "field_provenance": [],
  "other_spec": [],
  "photo": [
    {
      "id": "4e2b8a1c-0d3f-4b6e-8a7c-1f2e3d4c5b6a",
      "watch_id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47",
      "position": 0,
      "original_file": "photos/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/4e2b8a1c-0d3f-4b6e-8a7c-1f2e3d4c5b6a-original.jpg",
      "display_file": "photos/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/4e2b8a1c-0d3f-4b6e-8a7c-1f2e3d4c5b6a-original.jpg",
      "thumb_file": "photos/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/4e2b8a1c-0d3f-4b6e-8a7c-1f2e3d4c5b6a-original.jpg",
      "width": 4032,
      "height": 3024,
      "kind": "user",
      "added_at": 1795600000000
    }
  ],
  "service_entry": [
    {
      "id": "9a3e7c51-2d8b-4f60-b4e9-0c7d5a1f3e82",
      "watch_id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47",
      "serviced_on": "2024-02-01",
      "serviced_by": "Omega",
      "work": "full_service",
      "cost_minor": 85000,
      "cost_currency": "AUD",
      "notes": "Day not recorded; February 2024.",
      "created_at": 1795600000000,
      "updated_at": 1795600000000
    }
  ],
  "box_papers_item": [
    { "watch_id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47", "item": "inner_box", "state": "present" },
    { "watch_id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47", "item": "outer_box", "state": "present" },
    { "watch_id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47", "item": "warranty_card", "state": "present" }
  ],
  "document": [
    {
      "id": "9b1f5c2d-6e4a-4d8b-9c3f-7a2e1b0d4c58",
      "watch_id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47",
      "service_entry_id": "9a3e7c51-2d8b-4f60-b4e9-0c7d5a1f3e82",
      "kind": "invoice",
      "title": "Omega service invoice, February 2024",
      "file": "documents/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/9b1f5c2d-6e4a-4d8b-9c3f-7a2e1b0d4c58.pdf",
      "mime_type": "application/pdf",
      "size_bytes": 81234,
      "added_at": 1795600000000
    }
  ],
  "source_snapshot": [],
  "setting": []
}
```

Things to notice:
- "Full set" became three `box_papers_item` rows; ask the user what their "full set" includes if it matters.
- "Feb 2024" had no day, so `serviced_on` is the 1st and `notes` says so.
- "(want one)" became `"status": "wishlist"`. A wishlist watch has no purchase details.
- `diameter_mm` is `42`: a whole number is fine for a decimal field. `water_resistance_m` must be a whole number.
- The Omega (row 1) has the later `created_at`, by 1 s, so "Recently added" (newest first) shows the two in the user's order.
- `reference` values are strings. One that is all digits, such as Rolex's 16610, is still written `"16610"`.

ZIP contents, in this order:

```
manifest.json
collection.json
files/documents/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/9b1f5c2d-6e4a-4d8b-9c3f-7a2e1b0d4c58.pdf
files/photos/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/4e2b8a1c-0d3f-4b6e-8a7c-1f2e3d4c5b6a-original.jpg
```

`manifest.json` (hashes shortened here; write all 64 characters):

```json
{
  "format": "io.github.tunlezah.watches.backup",
  "format_version": 1,
  "schema_version": 1,
  "created_at": "2026-11-25T09:46:40Z",
  "app": "Watches import (from the user's spreadsheet)",
  "counts": { "watches": 2, "photos": 1, "service_entries": 1, "documents": 1, "snapshots": 0 },
  "files": [
    { "path": "collection.json", "size": 3184, "sha256": "<sha256 of collection.json bytes>" },
    { "path": "files/documents/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/9b1f5c2d-6e4a-4d8b-9c3f-7a2e1b0d4c58.pdf", "size": 81234, "sha256": "<sha256 of the PDF>" },
    { "path": "files/photos/7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47/4e2b8a1c-0d3f-4b6e-8a7c-1f2e3d4c5b6a-original.jpg", "size": 2381177, "sha256": "<sha256 of the JPEG>" }
  ]
}
```

With the §15 code: `write_watches_backup("Watches import 2026-11-25.zip", collection, {photo_path: photo_bytes, invoice_path: pdf_bytes})`.

---

## 17. Turning a user's list into fields

### 17.1 Common column names

| The user's column says | Field |
| --- | --- |
| Make, Manufacturer, Maker | `brand` |
| Model, Name | `model_name` (and `collection` if a family name is separate, e.g. Seamaster / Diver 300M) |
| Line, Family, Series | `collection` |
| Ref, Ref. no., Reference number, Model number | `reference` |
| Serial, S/N, Case number | `serial_number` |
| Nickname, Name I call it | `nickname` |
| Year, Production year, Made | `year_from` (and `year_to` for a range "1969–1972") |
| Edition "123/500" | `limited_number` 123, `limited_total` 500 |
| Size, Case size, Diameter, Width | `diameter_mm` |
| Thickness, Height (of the case) | `thickness_mm` |
| L2L, Lug to lug | `lug_to_lug_mm` |
| Lug width, Strap width | `lug_width_mm` |
| Case, Material, Metal | `case_material` |
| WR, Water resistance, Depth rating | `water_resistance_m` |
| Glass, Crystal | `crystal` |
| Dial, Dial colour/color | `dial_colour` (finish words like "sunburst" → `dial_finish`) |
| Movement, Calibre, Caliber | `caliber`; "based on ETA 2824" → `base_caliber` |
| Type, Movement type, Auto/Quartz | `movement_type` |
| Beat rate, vph, bph, Hz | `frequency_bph` |
| PR, Power reserve | `power_reserve_h` |
| Strap, Bracelet | `strap_type` ("Bracelet"/"Strap"), `strap_material` ("Leather", "Steel", "Rubber") |
| Bought, Purchased, Date acquired | `acquired_on` |
| From, Dealer, Seller, AD | `acquired_from` |
| Price, Paid, Cost | `price_minor` + `price_currency` |
| Condition | `condition` |
| Notes, Comments | `private_notes` |
| Story, History, Provenance (as in the watch's past) | `story` |
| Status, Owned?, Sold? | `status` |
| Box, Papers, Full set | `box_papers_item` rows |
| Complications, Functions | `watch_complication` rows |
| Service, Last service | `service_entry` row |
| Warranty until / Warranty from | `warranty_start` + `warranty_months` |

### 17.2 Words to keys

| The list says | Key |
| --- | --- |
| Automatic, auto, self-winding, mechanical automatic | `movement_type: automatic` |
| Manual, hand-wound, manual wind | `manual` |
| Quartz, battery | `quartz` |
| Solar, Eco-Drive, light-powered | `solar` |
| Spring Drive | `spring_drive` |
| Kinetic, automatic quartz, auto-quartz | `kinetic` |
| Meca-quartz, smartwatch, anything else | `other` (and the word in `private_notes`) |
| "Mechanical" with no more detail | ask, or leave out |
| Own, have it, in collection | `status: owned` |
| Want, grail, wishlist, considering | `wishlist` |
| Ordered, on the way, in transit | `incoming` |
| At the watchmaker, being serviced | `in_service` |
| Sold, flipped, traded, gifted, lost | `sold` |
| New, NOS, unworn, stickers on | `condition: unworn` |
| Like new | `mint` |
| Light wear, very good | `very_good` |
| Worn, used | `good` (ask if it matters) |
| Date window | complication `date` |
| Day-date | `day_date` |
| Chrono, chronograph | `chronograph` |
| GMT, dual time, second time zone | `gmt` |
| Moonphase | `moon_phase` |
| Power reserve indicator | `power_reserve` |
| Sub-seconds, small seconds | `small_seconds` |
| Helium escape valve, tachymeter, rotating bezel… | `custom:<as written>` (or a text field: a rotating bezel is better in `bezel`) |
| "Full set", "box and papers" | `inner_box`, `outer_box`, `warranty_card` = `present` |
| "Watch only", "no box or papers" | `inner_box`, `outer_box`, `warranty_card` = `missing` |

### 17.3 When in doubt

- Don't guess a value the user didn't give. Leave it out.
- Don't put measurements in text fields with units ("42mm") when there is a number field.
- Anything you couldn't place goes in `private_notes`, so nothing is lost.
- Show the user a short summary before packaging: number of watches by status, how many have photos, and anything you couldn't map.

---

## 18. Final checklist

Check every item. The first group are the ones that make the whole restore fail.

**Fatal if wrong**
- [ ] The file ends in `.zip`, was written to disk (not a pipe), and has no password.
- [ ] Each entry is stored or deflated.
- [ ] `manifest.json` and `collection.json` are at the top of the ZIP (or both in the same single folder, with everything else).
- [ ] `manifest.json` has all seven keys; `counts` has all five; `format` / `format_version` / `schema_version` are exactly right.
- [ ] Every `sha256` is 64 lower-case hex characters and matches the bytes in the ZIP.
- [ ] `collection.json` is listed in `files` with `"path": "collection.json"`.
- [ ] Every file under `files/` that a row uses is both listed and present.
- [ ] Neither JSON file starts with a BOM; both parse with a strict JSON parser.
- [ ] Every `watch` row has `id`, `status`, `created_at`, `updated_at`, `service_reminder`, with the right types.
- [ ] Every row of every other table has its required keys (§7), with the right types. Every `service_entry` has `work` (`""` if unknown).
- [ ] Whole-number fields hold integers without `.0`; booleans are `true`/`false`; no `NaN`; no `""` in a number field.
- [ ] Every text field is a quoted string, including references, calibres and serial numbers made of digits.
- [ ] No table is `null` (use `[]` or leave it out).

**Data quietly lost if wrong**
- [ ] Every child row's `watch_id` is a watch in this backup. (Complication, provenance, other-spec and box & papers rows that fail this vanish without a warning.)
- [ ] `service_entry.work` has no spaces.
- [ ] Every `photo.kind` is exactly `user` or `reference`.
- [ ] Every file path follows the §10.1 rule.
- [ ] Column names are spelt exactly as in §7 (`dial_colour`, not `dial_color`).
- [ ] Enum values are keys from §8.
- [ ] Dates are `YYYY-MM-DD`; timestamps are milliseconds.
- [ ] Prices are in minor units with a currency.
- [ ] `cover_photo_id` names a photo of the same watch.

**Good practice**
- [ ] IDs are fresh lower-case UUIDs, none starting with `sample-`, and saved in an ID map.
- [ ] Photos are JPEG/PNG/WebP/HEIC, upright `width`/`height` recorded, GPS removed.
- [ ] `display_file` = `thumb_file` = `original_file` for each photo (unless you made renditions to the §10.3 rules).
- [ ] Documents are PDF/JPEG/PNG/WebP with exact `size_bytes` and matching `mime_type`.
- [ ] `setting` and `source_snapshot` are `[]`.
- [ ] No empty strings in optional fields; unknown values are left out. (`service_entry.work` is the one required key that may be `""`.)
- [ ] You told the user whether to pick Merge or Replace, and that Replace resets settings.

---

## 19. Error messages and their causes

| The app says | Likely cause |
| --- | --- |
| "This file isn't a Watches backup." | No `manifest.json` in the ZIP; a key missing from it (`app`, `created_at`, `counts` or one of its five numbers…); a value of the wrong type; a BOM before it; `format` misspelt; a stored ZIP written to a pipe (§5.3); a compression method other than stored/deflate, or a password, on `manifest.json`; or the file isn't a ZIP at all. |
| "This backup was made by a newer version of Watches. Update the app, then try again." | `format_version` or `schema_version` above 1. |
| "This backup is damaged and can't be restored (collection.json is missing). Nothing on this phone was changed." | `collection.json` not in the ZIP, or not in the same folder as `manifest.json`. |
| "… (collection.json isn't listed) …" | No `{"path": "collection.json", …}` in the manifest's `files`. |
| "… (collection.json doesn't match its checksum) …" | Wrong or upper-case hash, or the file changed after hashing. |
| "… (collection.json can't be read) …" | Invalid JSON, a BOM, a missing required key (often `service_entry.work`), `null` in a required key, a table set to `null`, or a wrong type: most often an unquoted number in a text field (`"reference": 16610`), `300.0` in a whole-number field, or `""` in a number field (§7.2, §13). |
| "… (photos/…/x.jpg doesn't match its checksum) …" | A media file's hash is wrong, or the file was changed after hashing. |
| "… (N files are missing) …" | Files listed in the manifest and used by rows, but not in the ZIP. |
| "… (x is stored without its size) …" | A media file stored with a data descriptor: the ZIP was written to a stream (§5.3). (On `manifest.json` this shows as "isn't a Watches backup".) |
| "… (x uses an unsupported compression method) …" | A media file compressed with something other than deflate. |
| "… (x is encrypted) …" | Password-protected ZIP. |
| "The backup file changed after it was checked. Choose it again." | The file was replaced between the preview and the restore. |
| "Not enough free space on this phone to restore this backup." | The device needs the size of the new files plus 64 MB. |
| Warning "N photos missing from the backup will be skipped." | Photo rows whose `original_file` isn't listed in the manifest, or breaks the path rule. |
| Warning "N documents missing from the backup will be skipped." | The same, for documents. |
| Warning "N records for watches not in the backup will be skipped." | Child rows whose `watch_id` isn't a `watch` row in the backup. |

---

## 20. Another route: the in-repo backup maker and checker

The repository has two JVM tests that only run when environment variables name their files. They use the app's own code, so their output is exactly what the app would write. They need a JDK 21 and the project's Gradle build (it downloads its dependencies the first time).

### 20.1 `PersonalBackupMaker`: a backup from a catalogue file

`shared/data/src/jvmTest/kotlin/io/github/tunlezah/watches/data/PersonalBackupMaker.kt` reads a JSON **catalogue**, adds each watch, photo and document through the app's own repositories (so photos get the app's real handling: format, location removal, orientation, renditions) and exports a backup with the app's exporter.

```bash
WATCHES_CATALOGUE=/absolute/path/catalogue.json \
WATCHES_BACKUP_OUT=/absolute/path/out.zip \
./gradlew :shared:data:jvmTest --rerun --tests '*PersonalBackupMaker*'
```

Use absolute paths (the test runs in `shared/data`). `--rerun` goes after the task name; without it Gradle may skip the test as up to date.

The catalogue format:

```json
{
  "platform": "collection import",
  "watches": [
    {
      "key": "row-1",
      "id": "7d0c2a4e-5f1b-4c3a-9e8d-2b6f1a0c9e47",
      "fields": {
        "brand": "Omega", "collection": "Seamaster", "model_name": "Seamaster Diver 300M",
        "reference": "210.30.42.20.03.001", "diameter_mm": 42, "water_resistance_m": 300,
        "caliber": "8800", "movement_type": "automatic", "hacking": true,
        "complications": ["date", "custom:Helium valve"]
      },
      "sourced": { "movement.caliber": { "source": "grail", "url": "https://…" } },
      "other_specs": [ { "label": "Certification", "value": "METAS", "source": "wikidata", "url": "https://…" } ],
      "photos": [
        "/absolute/path/front.jpg",
        { "file": "/absolute/path/press.jpg", "kind": "reference", "credit": "© Omega", "credit_url": "https://…", "cover": false, "id": "optional-existing-photo-id" }
      ],
      "documents": [ { "kind": "manual", "file": "/absolute/path/manual.pdf", "title": "Manual", "id": "optional-existing-document-id" } ]
    }
  ]
}
```

- `fields` takes these keys only: `brand`, `collection`, `model_name`, `reference`, `nickname`, `year_from`, `year_to`, `limited_total`, `case_material`, `diameter_mm`, `thickness_mm`, `lug_to_lug_mm`, `lug_width_mm`, `bezel`, `crystal`, `case_back`, `crown`, `water_resistance_m`, `dial_colour`, `dial_finish`, `indices`, `hands`, `lume`, `caliber`, `base_caliber`, `movement_type`, `jewels`, `frequency_bph`, `power_reserve_h`, `hacking`, `hand_winding`, `complications` (array), `strap_type`, `strap_material`, `clasp`, `fitted_strap`, `private_notes`, `story`, `service_interval_months`.
- **Limits:** every watch is `owned`; there is no way to give status, `acquired_on`, `acquired_from`, price, condition, serial number, warranty, `limited_number`, service history or box & papers. The first `user` photo becomes the cover unless one is marked `"cover": true`. For those, use the hand-made route in this document, or restore the maker's output and finish in the app.
- **Two kinds of key:** `fields` uses column names (`caliber`, `diameter_mm`), while `sourced` uses the dotted field keys from §7.3 (`movement.caliber`, `case.diameter_mm`). A `sourced` entry only counts when that field has a value in `fields`; otherwise it's ignored.
- `id` (watch) is required. Photo and document `id`s are optional; give the earlier backup's IDs to make a corrected backup that Merge applies over the earlier one (§12).

### 20.2 `PersonalBackupCheck`: test any backup before handing it over

`PersonalBackupCheck.kt` restores a backup the way the app does: Replace onto a new install, Merge onto one that has watches, then a second backup and restore round trip, a CSV export and the photo check. It fails on any warning.

```bash
WATCHES_BACKUP_CHECK=/absolute/path/out.zip \
./gradlew :shared:data:jvmTest --rerun --tests '*PersonalBackupCheck*'
```

Add `WATCHES_BACKUP_PREVIOUS=/absolute/path/earlier.zip` to also check that restoring this backup with Merge over an earlier one leaves exactly this one's collection, and that no file name of the earlier backup is reused for other contents (§11.4).

It was written for one personal collection, so it expects **every watch to have a `cover_photo_id` naming one of its photos** (the app's "first own photo" fallback doesn't count) and photos without a location. A valid backup with a watch that has no photo, or no explicit cover, fails it. That doesn't mean the backup is broken. The JVM's image code reads JPEG and PNG, not HEIC.

---

## 21. Where this comes from in the code

| Topic | File |
| --- | --- |
| Format constants, manifest and collection classes, JSON settings, path rule | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/backup/ArchiveFormat.kt` |
| Export, inspect, clean, restore, aftercare | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/backup/BackupRepository.kt` |
| Merge rules | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/backup/Merge.kt` |
| ZIP reader and writer | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/backup/Zip.kt` |
| Tables and columns | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/db/Entities.kt`, `shared/data/schemas/io.github.tunlezah.watches.data.db.AppDatabase/1.json` |
| How stored values become what the app shows | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/db/Mappers.kt` |
| Enumerations | `shared/core/src/commonMain/kotlin/io/github/tunlezah/watches/core/model/Enums.kt` |
| Field keys and labels | `shared/core/src/commonMain/kotlin/io/github/tunlezah/watches/core/spec/SpecFields.kt` |
| Money | `shared/core/src/commonMain/kotlin/io/github/tunlezah/watches/core/model/Money.kt` |
| Photo formats, renditions, the photo check | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/photos/PhotoFormat.kt`, `.../data/PhotoRepository.kt`, `.../data/photos/StoredPhotoCheck.kt` |
| Documents | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/CareRepositories.kt` (`DocumentRepository`) |
| Settings keys | `shared/data/src/commonMain/kotlin/io/github/tunlezah/watches/data/SettingsRepository.kt` |
| A real backup to compare against | `shared/data/src/commonTest/fixtures/archives/mac.zip` |
| The format overview | `docs/ARCHIVE_FORMAT.md` |
