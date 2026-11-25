# Building, testing and releasing

How to build Watches from source, run its tests, and publish a release for Android, iPhone and Mac. To use the app, see the [README](../README.md). For how the code is organised and how each feature works inside, see [ARCHITECTURE.md](ARCHITECTURE.md).

- [Requirements](#requirements)
- [What builds where](#what-builds-where)
- [Commands at a glance](#commands-at-a-glance)
- [Android](#android)
- [iPhone](#iphone)
- [Mac](#mac)
- [Continuous integration](#continuous-integration)
- [Versions](#versions)

## Requirements

- **JDK 21.** Gradle itself comes with the wrapper (`./gradlew`).
- **The Android SDK**, with compile SDK 37. Point Gradle at it with `ANDROID_HOME`, or with `sdk.dir` in a `local.properties` file at the root (it is ignored by git).
- **For the iPhone app and the Mac disk image:** a Mac with Xcode 26 and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`). The Mac bridge needs an Apple silicon Mac.

## What builds where

Everything except the iPhone app, the Mac disk image and the Mac bridge builds and tests on Linux, which is how CI checks most of the app on every push.

| | Linux or Windows | Mac |
| --- | --- | --- |
| Android APK, its tests and screenshots | ✅ | ✅ |
| Shared tests on the JVM | ✅ | ✅ |
| Shared code and tests compiled for iOS | ✅ | ✅ |
| Shared tests on the iPhone simulator | | ✅ |
| iPhone app (Xcode) | | ✅ |
| Mac app: run, tests, screenshots, self-test | ✅ (with plain-Java image code) | ✅ |
| Mac bridge and disk image | | ✅ (Apple silicon) |

## Commands at a glance

```sh
./gradlew :androidApp:assembleDebug                      # debug APK
./gradlew :androidApp:assembleRelease                    # signed release APK (see Release APK and signing)
./gradlew :shared:core:jvmTest :shared:data:jvmTest :shared:search:jvmTest :shared:ui:jvmTest :shared:ui:testAndroidHostTest   # shared tests
WATCHES_LIVE_TESTS=1 ./gradlew :shared:search:jvmTest --tests '*LiveSourcesTest*'   # one real lookup per source
./gradlew :androidApp:recordRoborazziDebug               # Android screenshots of every design -> androidApp/build/outputs/roborazzi
./gradlew checkNetworkIsolation                          # fails if core, data, ui or the Mac shell can reach the network
./gradlew checkIosProject                                # checks the iOS project spec, version, icon and privacy manifest (works on Linux)
./gradlew :shared:ui:compileTestKotlinIosSimulatorArm64  # shared code and its tests compile for iOS (works on Linux)
./gradlew :shared:ui:iosSimulatorArm64Test               # the shared tests on an iOS simulator (Mac only)
./gradlew :desktopApp:run                                # the Mac app, on this computer
./gradlew :desktopApp:test                               # the Mac app's tests and screenshots
./gradlew :desktopApp:packageDmg                         # the Mac disk image (Mac only)
```

Everything the Android workflow checks, in one go:

```sh
./gradlew checkNetworkIsolation checkIosProject \
  :shared:core:jvmTest :shared:data:jvmTest :shared:search:jvmTest :shared:ui:testAndroidHostTest :shared:ui:jvmTest \
  :desktopApp:test :desktopApp:createDistributable \
  :shared:ui:compileKotlinIosSimulatorArm64 \
  :shared:core:compileTestKotlinIosSimulatorArm64 :shared:data:compileTestKotlinIosSimulatorArm64 \
  :shared:search:compileTestKotlinIosSimulatorArm64 :shared:ui:compileTestKotlinIosSimulatorArm64 \
  :shared:data:compileTestKotlinMacosArm64 :macBridge:compileKotlinMacosArm64 \
  :androidApp:recordRoborazziDebug :androidApp:assembleRelease
```

The shared tests live in `commonTest`, so the same tests run on the JVM, on the iPhone simulator, on an Android emulator (the Photos workflow) and natively on a Mac.

## Android

### Building and installing a debug build

```sh
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

The app needs Android 11 (API 30) or newer.

### Tests and screenshots

- `:shared:ui:testAndroidHostTest` runs the shared UI tests as Android host tests.
- `:androidApp:recordRoborazziDebug` draws real screens with fixed data (collection, detail, specs, settings and appearance) in every design, light and dark, on Robolectric. They land in `androidApp/build/outputs/roborazzi`. No device or emulator is needed.
- Robolectric downloads its Android runtime on first use. To run offline, set `robolectric.dependency.dir` in `~/.gradle/gradle.properties` to a folder holding it.

### Release APK and signing

Every release APK is signed, so Android installs it. Without a key of your own, it's signed with the debug key of the machine that builds it. Each GitHub Actions run is a new machine with a new debug key, and Android won't install an update signed with a different key over the app. So to move to a newer build: back up your collection (Settings › Backup & restore), uninstall the app, install the new APK, then restore.

```sh
./gradlew :androidApp:assembleRelease   # -> androidApp/build/outputs/apk/release/androidApp-release.apk
```

Its version name and code come from `version.xcconfig`, which the iPhone and Mac builds read too.

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

### Releasing a version

To publish an APK that stays downloadable, set the version in `version.xcconfig`, add its entry to `CHANGELOG.md`, then tag the commit with that version and push the tag:

```sh
git tag v0.8.0 && git push origin v0.8.0
```

- The **Android** workflow publishes a GitHub release with the APK attached and that version's changelog entry as its notes. The notes start with the uninstall-first steps when the APK is signed with a debug key.
- It refuses an unsigned APK, and a tag that doesn't match `version.xcconfig`.
- A tag with a suffix, such as `v0.7.0-rc1`, is published as a pre-release.
- Running the workflow again on the same tag replaces the APK.
- The same tag starts the **iOS release** workflow (TestFlight), and the **macOS** workflow adds the Mac disk image to the release.

## iPhone

The same app runs on iPhone: the shared Kotlin code, every screen included, inside a small SwiftUI shell (`iosApp`). The plan for finishing it is [IOS_PLAN.md](IOS_PLAN.md).

### Building and testing

Building for iOS needs a Mac with Xcode 26. The Xcode project is generated from `iosApp/project.yml`:

```sh
brew install xcodegen
cd iosApp && xcodegen generate && open iosApp.xcodeproj
```

The Xcode build phase compiles the shared Kotlin framework (`:shared:ui:embedAndSignAppleFrameworkForXcode`). To run it on your own phone from Xcode, pick your team under Signing & Capabilities. The minimum iOS version is 17.

The Xcode project has two test targets:
- `iosAppTests` runs inside the app and checks what the build put in it: the privacy manifest, purpose strings, one window, the icon and launch colour, the version and the bundled fonts.
- `iosAppUITests` launches the app and finds its welcome screen.

To run the shared tests on a simulator: `./gradlew :shared:ui:iosSimulatorArm64Test` (and the same for `core`, `data` and `search`). Pass `-PiosSimulator=<udid>` to choose the simulator.

On Linux, `./gradlew checkIosProject` checks the parts of the iPhone app that are plain files: the project spec, the shared version, the 1024 px opaque icon, the launch colour and the privacy manifest. The Android workflow runs it on every push, and compiles the shared code and its tests for iOS.

### App icon

The app icon is drawn from the Android one. After changing `androidApp/src/main/res/drawable/ic_launcher_foreground.xml`, run:

```sh
java tools/RenderIosAppIcon.java
```

It writes the iPhone's `AppIcon.png` and the Mac's `desktopApp/icons/Watches.icns`.

### Privacy manifest

`iosApp/iosApp/PrivacyInfo.xcprivacy` declares that nothing is tracked or collected, and gives a reason for each system API Apple asks about. App Store Connect refuses uploads without one. `tools/check-privacy-symbols.py` compares it with the built app, in both iOS workflows.

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

Then run Actions › iOS release › Run workflow, or push a `v*` tag. Until the secrets exist, that workflow builds and checks an unsigned archive instead, and uploads nothing. Once App Store Connect has processed a signed build (a few minutes), the README's [iPhone install steps](../README.md#iphone) take over.

Each upload's build number is `version.xcconfig`'s build number followed by the workflow's run number, so every upload is new to TestFlight.

## Mac

The same app runs on a Mac: the shared Kotlin code, every screen included, in a small desktop shell (`desktopApp`) on Compose's desktop target. The plan is [MACOS_PLAN.md](MACOS_PLAN.md).

### Building and testing

The desktop target also runs on Linux and Windows, which is how CI tests the Mac app without a Mac:

```sh
./gradlew :desktopApp:run                              # the app, on this computer
WATCHES_DATA_DIR=/tmp/try ./gradlew :desktopApp:run    # the same, with its collection in another folder
./gradlew :desktopApp:test                             # wiring, the shell's rules, the self-test, and screenshots
./gradlew :desktopApp:createDistributable              # the app with its own Java runtime, for this computer
./gradlew :desktopApp:packageDmg                       # the Mac disk image (on a Mac only)
./gradlew :macBridge:linkReleaseSharedMacosArm64       # the Mac bridge on its own (on a Mac only)
```

- **Screenshots.** `:desktopApp:test` draws the app in every design, light and dark, at three window widths, into `desktopApp/build/outputs/screenshots`. It needs no display.
- **Self-test.** `Watches --self-test [folder]` checks a build without opening a window: it starts the app's wiring in a temporary folder, makes sample watches, backs them up and restores the backup, exports a CSV, puts photos through the image code and draws a frame. CI runs it inside the packaged app, because the bundled Java runtime is trimmed to the modules the app needs, and a missing one shows only when the app runs. After `createDistributable` on Linux: `desktopApp/build/compose/binaries/main/app/Watches/bin/Watches --self-test /tmp/self-test`.
- **Icon.** `java tools/RenderIosAppIcon.java` also writes `desktopApp/icons/Watches.icns`.

### The Mac bridge

Photos on the Mac go through the iPhone's image code (Apple's own decoders, HEIC included), built as a small native library by the `macBridge` module, because Java can't call the Mac's own frameworks. Only a Mac can build it, and the disk image carries it.

`:desktopApp:run` and any build on Linux use plain-Java image code instead: they show photos but don't take new ones in, and say so.

To run the shared tests on a Mac through the bridge, as the macOS workflow does, give them the library:

```sh
./gradlew :macBridge:linkReleaseSharedMacosArm64
WATCHES_BRIDGE=$PWD/macBridge/build/bin/macosArm64/releaseShared/libwatchesbridge.dylib ./gradlew :shared:data:jvmTest
```

### Signing

The disk image is signed ad hoc and not notarised, so macOS asks once per build before it opens it (README › [Mac](../README.md#mac)). Signing with a Developer ID and notarising is milestone mac-4 in the plan.

## Continuous integration

Six GitHub Actions workflows. macOS minutes count ten times against a private repository's free allowance, so the workflows on macOS runners run only when their part of the code changes, on `v*` tags, and on demand. While the repository is public they cost nothing.

| Workflow | Runs on | When | What it does |
| --- | --- | --- | --- |
| **Android** (`android.yml`) | Linux | Every push and pull request | Network isolation and iOS project checks; every shared test on the JVM; the Mac app's tests, screenshots, packaging and self-test; the shared code compiled for iOS and the Mac; Android screenshots; the signed release APK. An independent check (Pillow) that photos the tests cleaned carry no location. On a `v*` tag, publishes the GitHub release. |
| **iOS** (`ios.yml`) | macOS | iOS code, the build or the version changes; tags; on demand | Every module's shared tests on the iPhone simulator; builds the app and runs its own tests and UI tests; checks the privacy manifest against the binary; launches the app and saves a screenshot. About 16 minutes, or 26 after a dependency change or a week without runs. |
| **iOS release** (`ios-release.yml`) | macOS | Tags; on demand | Archives a Release build and uploads it to TestFlight. Without the signing secrets, builds and checks an unsigned archive. |
| **macOS** (`macos.yml`) | macOS (Apple silicon) | The Mac shell, the bridge, shared photo or data code, or the build changes; tags; on demand | The shared data tests natively on the Mac and again through the bridge; builds the bridge and the disk image; runs the self-test inside it; checks the bundle's identity, version and signature; makes the reference backup. On a tag, adds the disk image to the release. |
| **Photos** (`photos.yml`) | Linux, Android emulator | Shared data or core code, or the build changes; tags; on demand | The shared data tests, above all the photo conformance suite, on an Android emulator against Android's own decoders. |
| **Make Android signing key** (`android-signing-key.yml`) | Linux | By hand, once | Makes your own Android signing key (see [above](#optional-your-own-key-so-updates-install-over-the-top)). |

Artifacts each run keeps for 14 days:

| Artifact | From | Holds |
| --- | --- | --- |
| `watches-release-apk` | Android | The signed APK |
| `screenshots` | Android | Android screenshots of every design |
| `desktop-screenshots` | Android | Mac screenshots, every design and three widths |
| `ios-launch-screenshot` | iOS | The app on the simulator, after launch |
| `watches-mac-dmg` | macOS | The disk image |
| `mac-self-test` | macOS | The frame the packaged app drew in its self-test |
| `reference-archive` | macOS | A backup made with the Mac's image code |
| `test-reports`, `ios-test-results`, `android-device-test-reports` | Each | Test reports, when something fails |

### Test outputs and the build cache

The Gradle build cache is on (`gradle.properties`), and CI keeps it between runs. When a push changes nothing a test depends on (a docs-only change, say), Gradle restores the test task from the cache instead of running it. A test that writes files for a later CI step must declare that folder as an output of its task, or the restored task leaves it empty and the later step fails. `:shared:data:jvmTest` declares `build/outputs/cleaned-photos`, and `:desktopApp:test` declares `build/outputs/screenshots`. The Roborazzi plugin declares its own.

## Versions

`version.xcconfig` holds the version for every platform: `MARKETING_VERSION` (numbers only, x.y.z) and `CURRENT_PROJECT_VERSION` (the build number, and Android's version code). Android, Xcode and the Mac build all read it. The Mac's packaging tool refuses a bundle version that starts with 0, so until 1.0.0 Finder's Get Info shows `1.0.<build>`; About, the disk image's name and every backup carry the real version.

Library versions are pinned in `gradle/libs.versions.toml`. The reasons for each choice are in [PLAN.md](PLAN.md), section 2.
