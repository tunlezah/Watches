# Watches

A private, offline-first watch collection app for one collector. Kotlin Multiplatform with Compose Multiplatform: Android first, iPhone and Mac from the same code.

- Everything lives on the phone. No accounts, servers, analytics, ads or crash reporting.
- The network is used by Lookup only, and only when you press Search, open a result or save a picture. Every request is listed in Settings › Request log.
- Service history, reminders, box & papers, warranties, and documents such as manuals, quick start guides, receipts and invoices.
- Backup to one .zip you save wherever you like, restore by merging or replacing, CSV export for spreadsheets, and an optional app lock.
- Designed for 500+ watches with photos.

Plan and decisions: [`docs/PLAN.md`](docs/PLAN.md), with the iPhone in [`docs/IOS_PLAN.md`](docs/IOS_PLAN.md) and the Mac in [`docs/MACOS_PLAN.md`](docs/MACOS_PLAN.md). Data sources: [`docs/SOURCES.md`](docs/SOURCES.md). Changes: [`CHANGELOG.md`](CHANGELOG.md).

## Project layout

| Module | What it holds |
| --- | --- |
| `shared/core` | Domain model, the spec-field registry, units and formatting, collection queries. Pure Kotlin. |
| `shared/data` | Room database (schema exported to `shared/data/schemas`), repositories, file storage, photo pipeline, sample catalogue. |
| `shared/search` | Spec-source adapters and HTTP. The only module allowed to use the network. |
| `shared/ui` | Design system (three designs: Atelier, Instrument, Gallery; see [`docs/DESIGN.md`](docs/DESIGN.md)), screens, view models, navigation. Produces the iOS framework `WatchApp`. |
| `androidApp` | Android shell: activity, manifest, signing, screenshot tests. |
| `iosApp` | SwiftUI shell, generated with XcodeGen, with its app-hosted and UI tests. |
| `desktopApp` | Mac shell on Compose's desktop target: one window, packaging, the self-test, desktop tests and screenshots. |

Shared tests live in `commonTest`, so the same tests run on the JVM (as Android host tests in `shared/ui`) and on the iOS simulator. The shared modules' JVM target runs those fast tests, and it is also what the Mac app runs on. The app's version, for both phones, is in [`version.xcconfig`](version.xcconfig).

## Build

Requirements: JDK 21 and the Android SDK (compile SDK 37). Gradle is provided by the wrapper.

```sh
./gradlew :androidApp:assembleDebug          # debug APK
./gradlew :shared:core:jvmTest :shared:data:jvmTest :shared:search:jvmTest :shared:ui:testAndroidHostTest   # shared tests
WATCHES_LIVE_TESTS=1 ./gradlew :shared:search:jvmTest --tests '*LiveSourcesTest*'   # one real lookup per source
./gradlew :androidApp:recordRoborazziDebug   # screenshots of every design -> androidApp/build/outputs/roborazzi
./gradlew checkNetworkIsolation              # fails if core, data, ui or the Mac shell can reach the network, by library or Apple's or Java's APIs
./gradlew checkIosProject                    # checks the iOS project spec, version, icon and privacy manifest (works on Linux)
./gradlew :shared:ui:compileTestKotlinIosSimulatorArm64   # checks shared code and its tests compile for iOS (works on Linux)
./gradlew :shared:ui:iosSimulatorArm64Test   # the shared tests on an iOS simulator (macOS only; CI runs every module)
./gradlew :desktopApp:run :desktopApp:test   # the Mac app on this computer, and its tests (see Mac, below)
```

### Release APK and signing

Every release APK is signed, so Android installs it. Without a key of your own, it's signed with the debug key of the machine that builds it. Each GitHub Actions run is a new machine with a new debug key, and Android won't install an update signed with a different key over the app. So to move to a newer build: back up your collection (Settings › Backup & restore), uninstall the app, install the new APK, then restore.

```sh
./gradlew :androidApp:assembleRelease   # -> androidApp/build/outputs/apk/release/androidApp-release.apk
```

Its version name and code come from `version.xcconfig`, which the iPhone build reads too.

The Android workflow builds the release APK on every push and keeps it for 14 days as the run's `watches-release-apk` artifact, named `Watches-<version>-<build>.apk`. The run's summary says which key signed it, and its certificate's SHA-256.

#### Optional: your own key, so updates install over the top

Signed with your own key, every build matches, so new builds install over the app and keep its data. The key is never stored in this repository. Provide it via environment variables (or the matching `watches.*` Gradle properties in `~/.gradle/gradle.properties`):

| Variable | Meaning |
| --- | --- |
| `WATCHES_KEYSTORE` | Path to the `.jks` keystore |
| `WATCHES_KEYSTORE_PASSWORD` | Keystore password |
| `WATCHES_KEY_ALIAS` | Key alias (default `watches`) |
| `WATCHES_KEY_PASSWORD` | Key password (defaults to the keystore password) |

For CI, make the key once, in either of two ways. Both use the alias `watches`, the default above.

**On GitHub, with nothing to install.** GitHub can make the key, but a workflow can't save secrets, so you paste it in yourself:

1. Add a repository secret (Settings › Secrets and variables › Actions) named `WATCHES_KEYSTORE_PASSWORD`: a password of at least 16 characters that you make up and save in a password manager. It locks the key.
2. Run Actions › Make Android signing key › Run workflow.
3. Download `watches-signing-key` from the run's page and follow its `README.txt`: paste the contents of `WATCHES_KEYSTORE_BASE64.txt` into a new secret of that name, keep `watches-release.jks` with the password, then delete the download.

The download is deleted after a day anyway. While this repository is public, anyone signed in to GitHub can fetch a run's downloads, so until then only the password protects the key. The workflow won't make another key once `WATCHES_KEYSTORE_BASE64` exists.

**On your own computer**, with a JDK or Android Studio (both include `keytool`). It asks for a password and a name:

```sh
keytool -genkeypair -v -keystore watches-release.jks -alias watches -keyalg RSA -keysize 4096 -validity 10000
```

Then add these repository secrets. Encode the keystore with `base64 -w0 watches-release.jks` on Linux, or `base64 -i watches-release.jks` on a Mac.

| Secret | Value |
| --- | --- |
| `WATCHES_KEYSTORE_BASE64` | `watches-release.jks`, base64-encoded |
| `WATCHES_KEYSTORE_PASSWORD` | Its password |
| `WATCHES_KEY_ALIAS` | Optional: the alias, if not `watches` |
| `WATCHES_KEY_PASSWORD` | Optional: the key's password, if not the keystore's (`keytool` uses one password for both) |

Keep the `.jks` and its password somewhere private, such as a password manager, and keep a second copy. Without them no later build installs over the app: the only way on is to uninstall it, which deletes its data unless you've saved a backup.

#### Releasing

To publish an APK that stays downloadable, tag the commit with its version from `version.xcconfig` and push the tag:

```sh
git tag v0.6.0 && git push origin v0.6.0
```

The Android workflow then publishes a GitHub release with the APK attached and that version's changelog entry as its notes, which start with the uninstall-first steps when it's signed with a debug key. It refuses an unsigned APK, and a tag that doesn't match `version.xcconfig`. A tag with a suffix, such as `v0.7.0-rc1`, is published as a pre-release. Running the workflow again on the same tag replaces the APK. The same tag also starts the iOS release workflow.

## Sideload on Android

1. On the phone, open the repository's Releases page and download the latest `Watches-….apk`. Or download a build's `watches-release-apk` artifact and copy the APK inside it to the phone.
2. Open it. Android asks once to allow installs from that app (Files, Chrome, etc.). Allow it.
3. Install. If Android says the app isn't installed because it conflicts with the existing one, the new build is signed with a different key: back up in Settings › Backup & restore, uninstall Watches, install the new APK, then restore. With your own key, later builds install over the top and keep your data.

The app needs Android 11 or newer.

Permissions:
- internet and network state, for Lookup only;
- notifications, for service reminders, asked for only when you switch one on;
- the biometric prompt, for the optional app lock (Android shows no permission prompt for this one);
- wake lock and "run at start-up", which WorkManager adds so reminders survive a restart.

None of these is a location, contacts, storage or camera permission. Photos are taken with the phone's camera app, and backups are saved through the system file picker.

## iPhone

The same app runs on iPhone: the shared Kotlin code, every screen included, inside a small SwiftUI shell. The plan for finishing it is [`docs/IOS_PLAN.md`](docs/IOS_PLAN.md); milestone iOS-1 is built.

### Building and testing

Building for iOS needs a Mac with Xcode 26. The Xcode project is generated from `iosApp/project.yml`:

```sh
brew install xcodegen
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

The Xcode build phase compiles the shared Kotlin framework (`:shared:ui:embedAndSignAppleFrameworkForXcode`). To run it on your own phone from Xcode, pick your team under Signing & Capabilities.

Two GitHub Actions workflows use a macOS runner. They run only when needed, because macOS minutes count ten times on a private repo (while this repository is public, they're free):
- **iOS** runs every module's shared tests on the simulator, builds the app, and runs the tests hosted in the app (`iosAppTests`, which check what the bundle contains) and the UI tests (`iosAppUITests`, which launch the app to its welcome screen). It also checks the privacy manifest against the binary and saves a screenshot. It runs when iOS code or the build changes, on `v*` tags, and on demand. A run takes about 16 minutes. The first after a dependency change, or after a week without runs, takes about 26, because Kotlin/Native builds every library for the simulator again; the workflow keeps that build in the Actions cache.
- **iOS release** archives a Release build and uploads it to TestFlight, on `v*` tags and on demand (Actions › iOS release › Run workflow). Until the signing secrets below exist, it builds and checks an unsigned archive instead.

The Android workflow compiles the shared code and its tests for iOS on every push, and runs `checkIosProject`.

The app icon is drawn from the Android one: after changing `ic_launcher_foreground.xml`, run `java tools/RenderIosAppIcon.java`.

### Signing and TestFlight

Builds reach the iPhone through TestFlight. That needs a paid Apple Developer Program membership, but no Mac. Once:

1. Enrol in the [Apple Developer Program](https://developer.apple.com/programs/) as an individual. Your Team ID is under Account › Membership details.
2. In Certificates, Identifiers & Profiles › Identifiers, register an App ID with the explicit bundle ID `io.github.tunlezah.watches`. It needs no capabilities.
3. Make a distribution certificate. On any computer with OpenSSL, using your own email address:
   ```sh
   openssl req -new -newkey rsa:2048 -nodes -keyout watches-distribution.key -out watches-distribution.csr -subj "/emailAddress=you@example.com/CN=Watches distribution"
   ```
   Upload the `.csr` under Certificates › + › Apple Distribution, and download the certificate. Then join the two into a `.p12`, choosing a password when asked. `-legacy` (OpenSSL 3) writes a `.p12` that macOS's keychain can read; leave it out on OpenSSL 1.1.
   ```sh
   openssl x509 -inform DER -in distribution.cer -out distribution.pem
   openssl pkcs12 -export -legacy -inkey watches-distribution.key -in distribution.pem -out watches-distribution.p12
   ```
4. Under Profiles › + › App Store Connect (Distribution), make a profile for that App ID and certificate, and download it.
5. In [App Store Connect](https://appstoreconnect.apple.com), create the app (Apps › +): iOS, the bundle ID above, and a name no other app uses, such as "Watches – Collection". The home screen still says "Watches".
6. Under Users and Access › Integrations › App Store Connect API, make a team key with the App Manager role (the first time, the Account Holder requests API access there). Note its Key ID and the Issuer ID, and download the `.p8`, which can be downloaded only once.
7. Add these repository secrets (Settings › Secrets and variables › Actions). Encode files with `base64 -i file` on a Mac, or `base64 -w0 file` on Linux.

| Secret | Value |
| --- | --- |
| `APPLE_TEAM_ID` | Your Team ID |
| `IOS_DIST_P12_BASE64` | `watches-distribution.p12`, base64-encoded |
| `IOS_DIST_P12_PASSWORD` | The `.p12`'s password |
| `IOS_PROFILE_BASE64` | The profile (`.mobileprovision`), base64-encoded |
| `ASC_KEY_ID` | The API key's Key ID |
| `ASC_ISSUER_ID` | The Issuer ID |
| `ASC_KEY_P8_BASE64` | The `.p8`, base64-encoded |

Keep the key, the `.p12` and the `.p8` somewhere private, such as a password manager. They're never committed.

Then run Actions › iOS release › Run workflow, or push a `v*` tag. Once App Store Connect has processed the build (a few minutes), add yourself to an internal group under TestFlight › Internal Testing, install the TestFlight app on the iPhone, and install Watches from it. Later builds install over it and keep your data, as long as the bundle ID and team stay the same.

Each TestFlight build stops opening 90 days after it was uploaded, so release at least that often. Your data stays on the phone in the meantime.

### Privacy on iPhone

- iOS asks for the camera when you first take a photo, for notifications when you first switch on a reminder, and for Face ID when you turn on the app lock. The photo picker needs no permission: it hands over only the photos you pick.
- `iosApp/iosApp/PrivacyInfo.xcprivacy` declares that nothing is tracked or collected, and explains the system APIs Apple asks about. `tools/check-privacy-symbols.py` compares it with the built app in CI.
- App data lives in the app's Application Support folder, which iCloud and computer backups skip: your backups are the files you save.

## Mac

The same app runs on a Mac: the shared Kotlin code, every screen included, in a small desktop shell (`desktopApp`) on Compose's desktop target. It needs an Apple silicon Mac on macOS 13 or later. The plan is [`docs/MACOS_PLAN.md`](docs/MACOS_PLAN.md); milestone mac-1 is built.

What the Mac build does today:
- The collection, search, Lookup, service history, box & papers, PDF documents, and the three designs.
- Backup, restore and CSV export. A backup made on a phone restores on the Mac, and one made on the Mac restores on a phone.

What it leaves out for now, and says so on the screen:
- **Adding and cropping photos**, saving a Lookup picture, and photos as documents. Photos from a backup show as usual. The desktop's image code doesn't yet follow the phones' rules for rotation, location removal and HEIC, so the Mac writes no photos until the shared photo component is done (plan, section 5).
- **Reminders and the app lock.**
- **PDF pages inside the app.** A PDF opens in Preview instead.
- **Menus, shortcuts and Mac wording.** Screens still say "this phone".

### Installing

1. Download `Watches-<version>-<build>-not-notarised.dmg`. A tagged version has it on its GitHub release, beside the APK. Any other build has it on its run of the **macOS** workflow (Actions › macOS › the run › Artifacts), for 14 days.
2. Open it and drag Watches to Applications.
3. The build isn't notarised by Apple yet, so the first time macOS refuses to open it. Go to System Settings › Privacy & Security, find the message about Watches, and choose Open Anyway. That is needed once for each build.

Where things are, and how updating works:
- The collection is in `~/Library/Application Support/Watches/`.
- To update, drag a newer Watches over the old one. The collection stays.
- Dragging the app to the Bin leaves the collection where it is. Delete that folder to remove it.
- Only one copy of the app runs at a time.
- A build won't open a collection made by a newer build. It says so and changes nothing.

### Building and testing

The desktop target also runs on Linux and Windows, which is how CI tests the Mac app without a Mac:

```sh
./gradlew :desktopApp:run                              # the app, on this computer
WATCHES_DATA_DIR=/tmp/try ./gradlew :desktopApp:run    # the same, with its collection in another folder
./gradlew :desktopApp:test                             # wiring, the shell's rules, the self-test, and screenshots
./gradlew :desktopApp:createDistributable              # the app with its own Java runtime, for this computer
./gradlew :desktopApp:packageDmg                       # the Mac disk image (on a Mac only)
```

- **Screenshots.** `:desktopApp:test` draws the app in every design, light and dark, at three window widths, into `desktopApp/build/outputs/screenshots`. It needs no display.
- **Self-test.** `Watches --self-test [folder]` checks a build without opening a window: it starts the app's wiring in a temporary folder, makes sample watches, backs them up and restores the backup, exports a CSV and draws a frame. CI runs it inside the packaged app, because the bundled Java runtime is trimmed to the modules the app needs, and a missing one shows only when the app runs.
- **Icon.** `java tools/RenderIosAppIcon.java` also writes `desktopApp/icons/Watches.icns`.

Two workflows cover it:
- **Android**, on Linux and on every push, compiles the Mac app, runs its tests, keeps the screenshots, packages it and runs the packaged app's self-test.
- **macOS** builds the disk image on an Apple silicon runner, runs the self-test inside it, and checks the bundle's identity and signature. It runs when the Mac shell or the build changes, on `v*` tags, and on demand. On a tag it adds the disk image to the GitHub release.

## Performance checks on the phone

Settings › Diagnostics shows cold-start, search and detail-open timings measured on the device against the brief's targets. It can also add 500 realistic sample watches, each with a generated photo, and remove them again afterwards.

## Designs

There are three complete designs, chosen in Settings › Appearance, where each is shown as a live specimen in its own style:
- **Atelier**, the catalogue: serif titles, warm graphite or ivory, a thick–thin masthead rule, catalogue numbers and accent dashes.
- **Instrument**, the data sheet: expanded capitals, mono figures, a minute-track rule with a lume pip, dotted leaders and focus brackets.
- **Gallery**, the white room: light type, soft lifted cards and pills, and a floating navigation bar.

Each design suggests four of the seven accents, with its house colour first. The full brief is in [`docs/DESIGN.md`](docs/DESIGN.md).

In code, each design is one `DirectionStyle` plus a type scale and palette (`shared/ui/.../theme`), and components only read those tokens. `DesignContrastTest` checks every design, mode and accent against the contrast rules.

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
4. Save trimmed fixtures under `shared/search/src/commonTest/fixtures/<source>/` (see the README there) and add a parser test. Parser tests run on the JVM and on the iOS simulator.
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
