# Watches

A private, offline-first watch collection app for one collector. Kotlin Multiplatform with Compose Multiplatform: Android first, iOS from the same code.

- Everything lives on the phone. No accounts, servers, analytics, ads or crash reporting.
- The network is used by Lookup only, and only when you press Search, open a result or save a picture. Every request is listed in Settings › Request log.
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
./gradlew :androidApp:recordRoborazziDebug   # screenshots of every design direction -> androidApp/build/outputs/roborazzi
./gradlew checkNetworkIsolation              # fails if core, data or ui can compile against a network library
./gradlew :shared:ui:compileKotlinIosSimulatorArm64   # checks shared code compiles for iOS (works on Linux)
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

## iOS

iOS needs a Mac with Xcode. The `iOS` GitHub Actions workflow builds the app for the simulator on every push, launches it and saves a screenshot. Locally:

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
  - Honest User-Agent (`WatchCollection/1.0 (+https://github.com/tunlezah/Watches)`), and no cookies.
  - At least 1 second between requests to the same host, and a 10 s timeout.
  - `robots.txt` is checked first and cached for a day. Wikimedia's API endpoints follow its API policy instead, and are marked "(API)" in the log.
  - `Retry-After` is honoured. A bot-protection page is reported, never bypassed.
- **Your values win:** in the review screen, any value you typed or edited starts unticked. A lookup replaces it only if you tick it. Applied values record their source, page and fetch date. Changing one later marks it "Edited".
- **Pictures:** pictures from Ranfft and Grail are shown, never saved. Freely licensed Wikimedia Commons pictures can be saved to a watch with their credit. They never become the cover unless you choose it.

## Adding a spec source

1. Check the site's terms and `robots.txt`. Record the result in `docs/SOURCES.md`, and skip any site that forbids automated access.
2. Implement `SpecSource` in `shared/search/.../sources`. Use one search request and one detail request per lookup, always through `PoliteHttpClient`. Keep parsing in pure `parseSearch`/`parseDetail` functions.
3. Map every value to a key from `SpecFields` (core), converting units with `UnitParser` and words with `Vocabulary`. Anything without a field goes in `extras`.
4. Save trimmed fixtures under `shared/search/src/jvmTest/resources/fixtures/<source>/` (see the README there) and add a parser test.
5. Add the source to `searchModule`. Its switch appears in Settings automatically.

## Backup archive format

Specified in `docs/ARCHIVE_FORMAT.md` (milestone 4). Outline: a versioned `.zip` with `manifest.json`, `collection.json`, and original photos and documents. See the plan, §10.
