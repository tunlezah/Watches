# Watches

A private, offline-first watch collection app for one collector. Kotlin Multiplatform with Compose Multiplatform: Android first, iOS from the same code.

- Everything lives on the phone. No accounts, servers, analytics, ads or crash reporting.
- The network is used by Lookup only, and only when you press Search, open a result or save a picture. Every request is listed in Settings › Request log.
- Service history, reminders, box & papers, warranties, and documents such as manuals, quick start guides, receipts and invoices.
- Backup to one .zip you save wherever you like, restore by merging or replacing, CSV export for spreadsheets, and an optional app lock.
- Designed for 500+ watches with photos.

Plan and decisions: [`docs/PLAN.md`](docs/PLAN.md). Data sources: [`docs/SOURCES.md`](docs/SOURCES.md). Changes: [`CHANGELOG.md`](CHANGELOG.md).

## Project layout

| Module | What it holds |
| --- | --- |
| `shared/core` | Domain model, the spec-field registry, units and formatting, collection queries. Pure Kotlin. |
| `shared/data` | Room database (schema exported to `shared/data/schemas`), repositories, file storage, photo pipeline, sample catalogue. |
| `shared/search` | Spec-source adapters and HTTP. The only module allowed to use the network. |
| `shared/ui` | Design system (three directions), screens, view models, navigation. Produces the iOS framework `WatchApp`. |
| `androidApp` | Android shell: activity, manifest, signing, screenshot tests. |
| `iosApp` | SwiftUI shell, generated with XcodeGen. |

Shared modules also have a JVM target, used only for fast tests.

## Build

Requirements: JDK 21 and the Android SDK (compile SDK 37). Gradle is provided by the wrapper.

```sh
./gradlew :androidApp:assembleDebug          # debug APK
./gradlew :shared:core:jvmTest :shared:data:jvmTest :shared:search:jvmTest   # unit and parser tests
WATCHES_LIVE_TESTS=1 ./gradlew :shared:search:jvmTest --tests '*LiveSourcesTest*'   # one real lookup per source
./gradlew :androidApp:recordRoborazziDebug   # screenshots of every design direction -> androidApp/build/outputs/roborazzi
./gradlew checkNetworkIsolation              # fails if core, data or ui can compile against a network library
./gradlew :shared:ui:compileKotlinIosSimulatorArm64   # checks shared code compiles for iOS (works on Linux)
./gradlew :shared:data:iosSimulatorArm64Test        # shared tests on an iOS simulator (macOS only; CI runs it)
```

### Release APK and signing

Sideloaded updates must always be signed with the same key, or Android refuses to install them over the existing app. The key is never stored in this repository. Provide it via environment variables (or the matching `watches.*` Gradle properties in `~/.gradle/gradle.properties`):

| Variable | Meaning |
| --- | --- |
| `WATCHES_KEYSTORE` | Path to the `.jks` keystore |
| `WATCHES_KEYSTORE_PASSWORD` | Keystore password |
| `WATCHES_KEY_ALIAS` | Key alias (default `watches`) |
| `WATCHES_KEY_PASSWORD` | Key password (defaults to the keystore password) |

```sh
./gradlew :androidApp:assembleRelease   # -> androidApp/build/outputs/apk/release/androidApp-release.apk
```

Without these variables the release APK is built unsigned.

For CI, add these repository secrets: `WATCHES_KEYSTORE_BASE64` (the keystore, base64-encoded), `WATCHES_KEYSTORE_PASSWORD`, `WATCHES_KEY_ALIAS` and `WATCHES_KEY_PASSWORD`. The Android workflow then uploads a signed APK as an artifact.

## Sideload on Android

1. Copy `androidApp-release.apk` to the phone, or download it there.
2. Open it. Android asks once to allow installs from that app (Files, Chrome, etc.). Allow it.
3. Install. Later builds signed with the same key install over the top and keep your data.

The app needs Android 11 or newer.

Permissions:
- internet and network state, for Lookup only;
- notifications, for service reminders, asked for only when you switch one on;
- the biometric prompt, for the optional app lock (Android shows no permission prompt for this one);
- wake lock and "run at start-up", which WorkManager adds so reminders survive a restart.

None of these is a location, contacts, storage or camera permission. Photos are taken with the phone's camera app, and backups are saved through the system file picker.

## iOS

iOS needs a Mac with Xcode. The `iOS` GitHub Actions workflow builds the app for the simulator, launches it and saves a screenshot. It runs when iOS-specific code or the build changes, on `v*` tags, and on demand (Actions › iOS › Run workflow), because macOS minutes are costly on a private repo. The Android workflow still compiles the shared Kotlin for iOS on every push. Locally:

```sh
brew install xcodegen
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

The Xcode build phase compiles the shared Kotlin framework (`:shared:ui:embedAndSignAppleFrameworkForXcode`).

## Performance checks on the phone

Settings › Diagnostics shows cold-start, search and detail-open timings measured on the device against the brief's targets. It can also add 500 realistic sample watches, each with a generated photo, and remove them again afterwards.

## How Lookup behaves

- **Sources:** Wikidata and Wikipedia (identity and history), Ranfft DB (calibres, with the owner's permission) and Grail Watch Reference (movements). WatchBase has a connection test only until its pages can be checked. Details and terms are in [`docs/SOURCES.md`](docs/SOURCES.md).
- **Politeness:** every request goes through `PoliteHttpClient` in `shared/search`.
  - Honest User-Agent (`WatchCollection/1.0 (+https://github.com/tunlezah)`), and no cookies. The contact link is the owner's GitHub profile, which stays public even though the repository may not.
  - At least 1 second between requests to the same host, and a 10 s timeout.
  - `robots.txt` is checked first and cached for a day. Wikimedia's API endpoints follow its API policy instead, and are marked "(API)" in the log.
  - `Retry-After` is honoured. A bot-protection page is reported, never bypassed.
- **Your values win:** in the review screen, any value you typed or edited starts unticked. A lookup replaces it only if you tick it. Applied values record their source, page and fetch date. Changing one later marks it "Edited".
- **Pictures:** pictures from Ranfft and Grail are shown, never saved. Freely licensed Wikimedia Commons pictures can be saved to a watch with their credit. They never become the cover unless you choose it.

## Service, box & papers and documents

- **When a service is due:** one interval after the last *full* service, or after the date acquired if none is logged. Other work (battery, regulation, a crystal) is recorded as the last service but doesn't move the due date. "Due soon" means within 90 days. The rules are in `CarePlan` (`shared/core/.../care`) and its tests.
- **Reminders:** per watch, off by default. `ReminderSync` watches the collection and keeps one local notification per watch on hand (owned or at the watchmaker, not archived) at 9 am on the due date. Android uses WorkManager, initialised on demand rather than at launch. iOS uses `UNUserNotificationCenter`. Only future times are scheduled, so an overdue watch isn't re-announced on every launch. The Service tab lists it instead. On Android a job that is due but held back by Doze is left to run. On iOS the app delegate installs the notification delegate at launch, so a tap that launches the app still opens the right screen.
- **Full set:** every checklist item is recorded, none is missing and at least one is present.
- **Documents:** PDFs and images (JPEG, PNG, WebP, HEIC) up to 50 MB, identified by their content rather than their name. HEIC is converted to JPEG when stored. They are stored in app-private storage under `documents/<watchId>/`. Location data is stripped from images. A document can belong to a service entry, and when that entry is deleted you choose whether its documents go too.
- **Manuals and quick start guides** are document kinds of their own. They are shown first on the watch, can be reused from another watch, and are copied when a watch is duplicated.
- **Viewer and sharing:** PDFs render on the phone, with `PdfRenderer` on Android and PDFKit on iOS. Share hands one file to the system share sheet. On Android it goes through a `FileProvider` limited to the documents folder.

## Adding a spec source

1. Check the site's terms and `robots.txt`. Record the result in `docs/SOURCES.md`, and skip any site that forbids automated access.
2. Implement `SpecSource` in `shared/search/.../sources`. Use one search request and one detail request per lookup, always through `PoliteHttpClient`. Keep parsing in pure `parseSearch`/`parseDetail` functions.
3. Map every value to a key from `SpecFields` (core), converting units with `UnitParser` and words with `Vocabulary`. Anything without a field goes in `extras`.
4. Save trimmed fixtures under `shared/search/src/jvmTest/resources/fixtures/<source>/` (see the README there) and add a parser test.
5. Add the source to `searchModule`. Its switch appears in Settings automatically.

## Backup, restore and export

Settings › Backup & restore.
- **Back up now** writes one ZIP through the system save dialog. The manifest comes first, then `collection.json` (every table, keys = column names), then every photo, document and saved source page. The full format is in [`docs/ARCHIVE_FORMAT.md`](docs/ARCHIVE_FORMAT.md). The code is in `shared/data/.../backup`: a streaming ZIP64 writer and reader written for this app, with no extra library.
- **Restoring** checks every file's SHA-256 before the database changes, and commits in one transaction.
  - Incoming files are written beside existing ones. After the commit, only the files the restore itself stopped using are removed (worked out inside the transaction), so a photo being added at the same time is never touched.
  - A journal undoes an interrupted restore at the next launch, or finishes one that was saved but not tidied up (search index, photo renditions).
  - **Replace** swaps the collection and settings. **Merge** matches on IDs, never deletes, lets the more recently edited side win each watch, and reports every watch that differed.
- **CSV export** gives one row per watch with every registry field. It's UTF-8 with a BOM, quoted per RFC 4180, with formula-like text neutralised.
- **Device-only settings** (`device.*`: the app lock and the last-backup date) never travel in a backup.
- **Long jobs** run in an app-wide coordinator (`ui/backup/BackupCoordinator.kt`), so leaving the screen doesn't stop them.

## App lock

Settings › Privacy & security. It's optional and uses the phone's own unlock: Android's `BiometricPrompt` (strong biometrics or the device PIN), or LocalAuthentication on iOS.
- The app locks at launch, and again after a chosen delay in the background. Time spent in the unlock prompt doesn't count.
- Turning it on requires one successful unlock.
- The lock screen is a full-screen window of its own, above any open dialog or sheet, and takes the back gesture.
- If the phone's own screen lock is removed, the app lock pauses instead of locking its owner out, and resumes once one is set up again.
- On Android 13+ the app switcher shows a blank card while the lock is on.

The rules are in `ui/security/AppLock.kt`, and `AppLockTest` covers them.
