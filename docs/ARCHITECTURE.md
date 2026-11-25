# How Watches works inside

The code's layout, and the rules each feature follows. For what the app does, see the [README](../README.md). To build it, see [BUILD.md](BUILD.md).

- [Project layout](#project-layout)
- [Privacy and the network](#privacy-and-the-network)
- [Designs](#designs)
- [Lookup](#lookup)
- [Photos](#photos)
- [Service, box & papers and documents](#service-box--papers-and-documents)
- [Backup, restore and export](#backup-restore-and-export)
- [App lock](#app-lock)
- [The Mac shell](#the-mac-shell)
- [Performance](#performance)
- [Tests](#tests)
- [Other documents](#other-documents)

## Project layout

Watches is Kotlin Multiplatform with Compose Multiplatform. Nearly all the code is shared, every screen included. Each platform adds a small shell.

| Module | What it holds |
| --- | --- |
| `shared/core` | Domain model, the spec-field registry, units and formatting, collection queries, the service rules. Pure Kotlin. |
| `shared/data` | Room database (schema exported to `shared/data/schemas`), repositories, file storage, the photo pipeline, the backup engine, the sample catalogue. |
| `shared/search` | Spec-source adapters and HTTP. The only module allowed to use the network. |
| `shared/ui` | Design system (three designs: Atelier, Instrument, Gallery; see [DESIGN.md](DESIGN.md)), screens, view models, navigation. Produces the iOS framework `WatchApp`. |
| `androidApp` | Android shell: activity, manifest, signing, screenshot tests. |
| `iosApp` | SwiftUI shell, generated with XcodeGen, with its app-hosted and UI tests. |
| `desktopApp` | Mac shell on Compose's desktop target: one window, packaging, the self-test, desktop tests and screenshots. |
| `macBridge` | The Mac bridge: the iPhone's image code (`shared/data`'s `appleMain`) built as a small native library, which the Mac app loads because Java can't call the Mac's own frameworks. |

The shared modules also have a JVM target. It runs the fast shared tests, and it is what the Mac app runs on.

The stack and every pinned version, with the reasons for each choice, are in [PLAN.md](PLAN.md), section 2. In short: Room with bundled SQLite (the same SQLite build, FTS included, on every platform), Koin, Navigation 3, Coil without its network module, Okio, Ktor in `shared/search` only.

## Privacy and the network

- **No accounts, servers, analytics, ads or crash reporting.** None of those libraries is in the build.
- **Only `shared/search` can reach the network.** Ktor is an `implementation` dependency of that module alone. `./gradlew checkNetworkIsolation` fails the build if an HTTP client or Coil's network fetcher is on the compile classpath of `core`, `data`, `ui`, the Mac shell or the bridge, or if any of their sources imports one or calls Apple's or Java's networking APIs.
- **Every request goes through one client**, `PoliteHttpClient`, which logs it. The log is on screen in Settings › Lookup sources › Request log.
- **Nothing leaves through system backups.** On Android, `allowBackup` is off and `data_extraction_rules.xml` excludes everything from cloud backup and device transfer. On iPhone, the app's data lives in Application Support, excluded from iCloud and computer backups. The backup file you save is the only copy that leaves.

### Android permissions

- Internet and network state, for Lookup only. Network state is read so an offline search sends nothing.
- Notifications, for service reminders, asked for only when you switch one on.
- Biometrics, for the optional app lock. Android shows no permission prompt for this one.
- Wake lock and "run at start-up", which WorkManager adds so reminders survive a restart. Its foreground-service permission is removed from the manifest, since reminders are short.

None of these is a location, contacts, storage or camera permission. Photos are taken with the phone's camera app, and backups are saved through the system file picker.

### iPhone permissions

iOS asks for the camera when you first take a photo, for notifications when you first switch on a reminder, and for Face ID when you turn on the app lock. The photo picker needs no permission: it hands over only the photos you pick. The privacy manifest (`iosApp/iosApp/PrivacyInfo.xcprivacy`) declares that nothing is tracked or collected; CI checks it against the built app.

## Designs

There are three complete designs, Atelier, Instrument and Gallery, each with light and dark modes and seven accents. The brief is in [DESIGN.md](DESIGN.md).

In code, each design is one `DirectionStyle` (layout and ornament), a `WatchTypography` (type scale) and a `WatchColors` palette, in `shared/ui/.../theme`. Components only read those tokens and never ask which design is active. `DesignContrastTest` checks every design, mode and accent against the contrast rules: text at least 4.5:1, control edges about 3:1. All fonts are bundled, under the SIL Open Font License (`shared/ui/fonts-licences`).

## Lookup

- **Sources:** Wikidata and Wikipedia (identity and history), Ranfft DB (calibres, with the owner's permission) and Grail Watch Reference (movements). WatchBase has a connection test only, until its pages can be checked. Details and terms are in [SOURCES.md](SOURCES.md).
- **Politeness:** every request goes through `PoliteHttpClient` in `shared/search`.
  - Honest User-Agent (`WatchCollection/1.0 (+https://github.com/tunlezah)`), and no cookies. The contact link is the owner's GitHub profile, which stays public even though the repository may not.
  - At least 1 second between requests to the same host, and a 10 s timeout.
  - `robots.txt` is checked first and cached for a day. Wikimedia's API endpoints follow its API policy instead, and are marked "(API)" in the log.
  - `Retry-After` is honoured. A bot-protection page is reported, never bypassed.
- **Your values win:** in the review screen, any value you typed or edited starts unticked. A lookup replaces it only if you tick it. Applied values record their source, page and fetch date. Changing one later marks it "Edited".
- **Pictures:** pictures from Ranfft and Grail are shown, never saved. Freely licensed Wikimedia Commons pictures can be saved to a watch with their credit. They never become the cover unless you choose it.
- **Movement data never fills case fields.** Ranfft's "26 mm" is the movement's size and is kept as an extra spec, not the case diameter. A movement's maker is never written as the watch's brand.

### Adding a spec source

1. Check the site's terms and `robots.txt`. Record the result in `docs/SOURCES.md`, and skip any site that forbids automated access.
2. Implement `SpecSource` in `shared/search/.../sources`. Use one search request and one detail request per lookup, always through `PoliteHttpClient`. Keep parsing in pure `parseSearch`/`parseDetail` functions.
3. Map every value to a key from `SpecFields` (core), converting units with `UnitParser` and words with `Vocabulary`. Anything without a field goes in `extras`.
4. Save trimmed fixtures under `shared/search/src/commonTest/fixtures/<source>/` (see the README there) and add a parser test. Parser tests run on the JVM and on the iOS simulator.
5. Add the source to `searchModule`. Its switch appears in Settings automatically.

## Photos

One photo component serves Android, iPhone and Mac. The rules are shared code; each platform keeps only a decoder. The design is in [MACOS_PLAN.md](MACOS_PLAN.md), section 5.

- **One way in.** A watch's photos and a document's photos come in through the same steps (`PhotoIntake`). A picked photo is streamed into the app's files, not read into memory first.
- **Location removed by shared code.** It finds the GPS data in a file's EXIF and XMP and overwrites it where it lies, in JPEG, PNG, WebP and HEIC. The file keeps its length, and the picture, the capture date and everything else are untouched. Then the platform's own reader is asked whether it still finds a location (Android's `ExifInterface`, Apple's ImageIO). If it does, or if the metadata can't be followed far enough to be sure, the app keeps a full-size JPEG made from the photo instead, which carries no metadata.
- **Formats.** JPEG, PNG, WebP and HEIC are stored as they are, told apart by their bytes (`PhotoFormat`). TIFF, GIF, BMP or camera raw are stored as a full-size JPEG made from them, so a restored copy shows on every device. A format a device can't read is refused cleanly.
- **Renditions.** Each photo has a display rendition (2560 px on its long edge) and a thumbnail (640 px). What a crop means in pixels is shared arithmetic (`RenderPlan`), so every platform cuts the same rectangle at the same size.
- **Decoders.** Android uses its own decoders, with region decoding. The iPhone and the Mac share `AppleImageCodec` (ImageIO), which reads a photo's size and rotation without decoding it and decodes straight to the size needed. The Mac reaches it through the bridge. Where there's no bridge (Linux, and `:desktopApp:run`), plain-Java code stands in for tests and screenshots, and the app takes no new photos.
- **Photos already stored are checked once** after an update and after every restore: any location an older version left is removed, and a size recorded for a picture on its side is corrected.

## Service, box & papers and documents

- **When a service is due:** one interval after the last *full* service, or after the date acquired if none is logged. Other work (battery, regulation, a crystal) is recorded as the last service but doesn't move the due date. "Due soon" means within 90 days. The rules are in `CarePlan` (`shared/core/.../care`) and its tests.
- **Reminders:** per watch, off by default. `ReminderSync` watches the collection and keeps one local notification per watch on hand (owned or at the watchmaker, not archived) at 9 am on the due date.
  - Android uses WorkManager, initialised on demand rather than at launch. A job that is due but held back by Doze is left to run.
  - iOS uses `UNUserNotificationCenter`. The app delegate installs the notification delegate at launch, so a tap that launches the app still opens the right screen.
  - Only future times are scheduled, so an overdue watch isn't re-announced on every launch. The Service tab lists it instead.
- **Full set:** every checklist item is recorded, none is missing and at least one is present.
- **Documents:** PDFs and images (JPEG, PNG, WebP, HEIC) up to 50 MB, identified by their content rather than their name. HEIC is converted to JPEG when stored. They are stored in app-private storage under `documents/<watchId>/`. Location data is stripped from images. A document can belong to a service entry, and when that entry is deleted you choose whether its documents go too.
- **Manuals and quick start guides** are document kinds of their own. They are shown first on the watch, can be reused from another watch, and are copied when a watch is duplicated.
- **Viewer and sharing:** PDFs render in the app, with `PdfRenderer` on Android and PDFKit on iOS; the Mac opens them in Preview for now. Share hands one file to the system share sheet. On Android it goes through a `FileProvider` limited to the documents folder.

## Backup, restore and export

Settings › Backup & restore. The file format is in [ARCHIVE_FORMAT.md](ARCHIVE_FORMAT.md).

- **Back up now** writes one ZIP through the system save dialog. The manifest comes first, then `collection.json` (every table, keys = column names), then every photo, document and saved source page. The code is in `shared/data/.../backup`: a streaming ZIP64 writer and reader written for this app, with no extra library.
- **Restoring** checks every file's SHA-256 before the database changes, and commits in one transaction.
  - Incoming files are written beside existing ones. After the commit, only the files the restore itself stopped using are removed (worked out inside the transaction), so a photo being added at the same time is never touched.
  - A journal undoes an interrupted restore at the next launch, or finishes one that was saved but not tidied up (search index, photo renditions).
  - **Replace** swaps the collection and settings. **Merge** matches on IDs, never deletes, lets the more recently edited side win each watch, and reports every watch that differed.
- **Across platforms.** A backup made on any of Android, iPhone or Mac restores on the others. The macOS workflow makes a reference backup with the Mac's image code, and the shared tests restore it on every platform (`ReferenceArchiveTest`).
- **CSV export** gives one row per watch with every registry field. It's UTF-8 with a BOM, quoted per RFC 4180, with formula-like text neutralised.
- **Device-only settings** (`device.*`: the app lock and the last-backup date) never travel in a backup.
- **Long jobs** run in an app-wide coordinator (`ui/backup/BackupCoordinator.kt`), so leaving the screen doesn't stop them.
- **Making a backup outside the app**, to import a collection from elsewhere, is specified in [BACKUP_IMPORT_SPEC.md](BACKUP_IMPORT_SPEC.md).

## App lock

Settings › Privacy & security › App lock. It's optional and uses the device's own unlock: Android's `BiometricPrompt` (strong biometrics or the device PIN), or LocalAuthentication on iOS. The Mac doesn't have it yet.

- The app locks at launch, and again after a chosen delay in the background. Time spent in the unlock prompt doesn't count.
- Turning it on requires one successful unlock.
- The lock screen is a full-screen window of its own, above any open dialog or sheet, and takes the back gesture.
- If the phone's own screen lock is removed, the app lock pauses instead of locking its owner out, and resumes once one is set up again.
- On Android 13+ the app switcher shows a blank card while the lock is on.

The rules are in `ui/security/AppLock.kt`, and `AppLockTest` covers them.

## The Mac shell

- The collection is in `~/Library/Application Support/Watches/`. `WATCHES_DATA_DIR` points a run at another folder.
- Only one copy runs at a time (`SingleInstance`). A second says Watches is already open.
- A build refuses a collection made by a newer build, with a message, and changes nothing. A phone can't meet this, because the system won't install an older build over a newer one.
- Quitting during a backup or restore asks first. Stopping removes a half-written backup.
- What the Mac leaves out for now (camera, reminders, app lock, PDF pages in the app, menus and Mac wording) is declared per platform in `PlatformLimits`, and each screen shows a sentence in place of the missing control.

## Performance

The app is designed for 500+ watches with photos. The targets, for a Motorola Edge 60 Fusion (120 Hz), are a cold start to the collection under 1 second, a smooth grid, a search update under 100 ms and opening a watch under 150 ms. The approach (no work on the main thread before the first frame, pre-sized thumbnails, FTS search) is in [PLAN.md](PLAN.md), section 9.

Settings › About Watches › Diagnostics shows cold-start, search and detail-open timings measured on the device against those targets. It can add 500 realistic sample watches, each with a generated photo, and remove them again afterwards.

## Tests

- Shared tests live in `commonTest`, so the same tests run on the JVM (as Android host tests in `shared/ui`), on the iPhone simulator, on an Android emulator (`shared/data`, the Photos workflow) and natively on a Mac.
- Checks that need `java.util.zip` (archives open in standard tools, ZIP64, JSON keys match the schema) stay JVM-only, in `ArchiveInteropTest`.
- Photo fixtures in `shared/data/src/commonTest/fixtures/photos` (made by `tools/photo_fixtures.py`) and parser fixtures in `shared/search/src/commonTest/fixtures` are compiled into the tests, so no platform reads files at run time.
- Screenshots: Roborazzi on Android (`ScreenshotTest`), and an off-screen renderer on the desktop (`DesktopScreenshotTest`).

How to run each is in [BUILD.md](BUILD.md).

## Other documents

| Document | What it covers |
| --- | --- |
| [BUILD.md](BUILD.md) | Building, testing, signing, releasing, CI |
| [DESIGN.md](DESIGN.md) | The three designs |
| [SOURCES.md](SOURCES.md) | Lookup sources: terms, `robots.txt`, what each covers |
| [ARCHIVE_FORMAT.md](ARCHIVE_FORMAT.md) | The backup file format |
| [BACKUP_IMPORT_SPEC.md](BACKUP_IMPORT_SPEC.md) | Making a backup outside the app, to import a collection |
| [PLAN.md](PLAN.md) | The original plan, decisions, stack and schema |
| [IOS_PLAN.md](IOS_PLAN.md) | Finishing the iPhone app |
| [MACOS_PLAN.md](MACOS_PLAN.md) | The Mac app, and the shared photo component |
| [WINDOWS_PLAN.md](WINDOWS_PLAN.md) | A Windows app (plan only) |
| [CHANGELOG.md](../CHANGELOG.md) | What changed in each version |
