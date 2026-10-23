# Watches on iPhone: conversion plan

Status: **plan only** (23 Oct 2026). Nothing in the app has been changed. Written against `f93940f` (0.5.0) and the green iOS CI run of 18 Oct. Library behaviour described below was checked in the published sources of Compose Multiplatform 1.12.1, FileKit 0.16.0 and Coil 3.6.3. Anything not yet confirmed is marked **verify**.

Contents

1. Summary
2. Where the iPhone build stands
3. How the iPhone app works
4. Decisions for you
5. What needs implementing
6. Tests
7. Signing, distribution and CI
8. Milestones
9. Risks
10. Not in this plan

Appendices: A, changes by file · B, Info.plist and privacy manifest · C, release workflow · D, device checklist · E, alternatives considered

---

## 1. Summary

**This is finishing, not rewriting.** Watches is Kotlin Multiplatform with Compose Multiplatform. About 93% of its code (17,400 lines) is shared, including every screen, the database, Lookup and the backup engine. The iPhone-specific part is about 550 lines of Kotlin plus a 33-line SwiftUI shell. That part already covers the database location, image processing, ZIP decompression, PDFs, reminders, notifications, Face ID, sharing, connectivity and the HTTP engine.

CI already builds the app for the simulator, launches it and takes a screenshot. The 18 Oct screenshot shows the Atelier welcome screen drawn correctly on a Dynamic Island iPhone, with the bundled fonts, safe areas and bottom bar all right.

**Recommendation: keep one Compose UI for both phones** (alternatives in appendix E). The three designs are drawn by the app, not taken from the platform, so Compose gives the same Atelier, Instrument and Gallery on both phones.

**What's missing falls in five groups:**

1. **Getting onto your iPhone.** The iOS build is unsigned and simulator-only. It needs:
   - an Apple Developer Program membership and signing;
   - an app icon and launch screen;
   - a privacy manifest (App Store Connect refuses uploads without one);
   - one shared version number;
   - a release workflow that uploads to TestFlight.
2. **Behaving like an iPhone app.** Some behaviour assumes Android:
   - reminders (iOS keeps at most 64 scheduled notifications);
   - notification permission after "Don't Allow" (iOS never asks twice);
   - the app-switcher snapshot, and the status bar when you force Light or Dark;
   - number keyboards with no return key;
   - the swipe-back gesture next to the crop handles, and landscape next to the Dynamic Island;
   - wording such as "fingerprint or phone PIN" and "Android's cloud backup".
3. **Photos, documents and backups.**
   - iPhone photos are HEIC and large. The Crop screen shows the original through Coil's iOS decoder, which reads whole files and, as far as I can tell, can't decode HEIC.
   - The iOS image processor decodes at full size (about 190 MB for a 48 MP photo).
   - Backups stream into cloud folders without file coordination.
   - iOS suspends an app a few seconds after you leave it, so a long backup stops.
4. **Tests on iOS.** 53 of the 182 tests run on the simulator today. Moving the portable ones into shared test code brings that to about 135. The plan adds iOS-only tests, app-hosted tests, UI tests and a device checklist.
5. **CI cost.** macOS minutes count ten times against a private repo's allowance. The work needs a budget decision (7.4).

**What I need from you** is in section 4. Two decisions block everything else: joining the Apple Developer Program (about A$149 a year), and how builds reach your phone (TestFlight is the recommendation).

**Size.** Four milestones, iOS-1 to iOS-4. The first ends with Watches on your iPhone from TestFlight, before most of the fixes, so device testing starts early. In engineering effort the whole plan is about the size of milestones 3 and 4 combined. The elapsed time depends mostly on Apple's account setup, macOS CI turnaround and your device testing rounds.

---

## 2. Where the iPhone build stands

✅ works as on Android · ⚠️ works, with gaps listed in section 5 · ❌ missing

| Area | Android | iPhone today | |
| --- | --- | --- | --- |
| Shell | `MainActivity`, `WatchesApplication` | SwiftUI `App` hosting `ComposeUIViewController` | ✅ |
| Wiring | Koin with `androidPlatformModule` | Koin with `iosDataModule`, `iosSearchModule`, `iosPlatformModule` | ✅ |
| Database and search | Room, bundled SQLite, FTS4 | The same SQLite build, in Application Support, excluded from iCloud backup | ✅ |
| Photo renditions | `AndroidImageProcessor`: region decode, EXIF | `IosImageProcessor`: `UIImage`, full-size decode | ⚠️ 5.3.1 |
| Showing originals (Crop) | Coil on Android: EXIF, HEIC, subsampling | Coil on iOS: Skia, whole-file decode | ⚠️ 5.3.2 |
| Photo library | Android photo picker | PHPicker, with no permission prompt | ✅ HEIC by default |
| Camera | System camera app | `UIImagePickerController`, JPEG at quality 1.0 | ✅ |
| ZIP decompression | `java.util.zip` | zlib | ✅ tested on the simulator |
| Saving and opening files | Storage Access Framework | Document picker; the app writes into the chosen URL | ⚠️ 5.3.5 |
| Free-space check | `StatFs` | `NSFileSystemFreeSize` | ⚠️ 5.3.7 |
| PDF viewer | `PdfRenderer` | PDFKit, via a PNG round trip | ⚠️ 5.3.4 |
| Share | FileProvider and chooser | `UIActivityViewController` | ⚠️ 5.2.5 |
| Reminders | WorkManager, future times only | A calendar trigger for every due date | ⚠️ 5.2.1 |
| Notification permission | `POST_NOTIFICATIONS` | `UNUserNotificationCenter` | ⚠️ 5.2.2 |
| Tapping a reminder | Intent extra → `DeepLinks` | Delegate set at launch → `DeepLinks` | ✅ |
| App lock | `BiometricPrompt` | `LAContext`: Face ID or Touch ID, passcode fallback | ✅ wording 5.2.6 |
| App-switcher privacy | Blank card while the lock is on | Nothing | ❌ 5.2.3 |
| System UI in a forced theme | Follows the system, not the app | The same | ❌ 5.2.4 |
| Long jobs after leaving the app | Keep running | Suspended within seconds | ❌ 5.3.6 |
| Cold-start timing | Process start time | `null` | ❌ 5.2.11 |
| HTTP and connectivity | OkHttp, `ConnectivityManager` | Ktor's Darwin engine (no cookies, no cache), `NWPathMonitor` | ✅ |
| Keep screen on, reduced motion | `keepScreenOn`, animator scale | `idleTimerDisabled`, `UIAccessibilityIsReduceMotionEnabled` | ✅ |
| Icon and launch screen | Adaptive icon, window background | None; a plain launch screen | ❌ 5.1.1 |
| Privacy manifest | Not needed | None | ❌ 5.1.3 |
| Signing and delivery | Keystore → signed APK | Unsigned simulator build (`CODE_SIGNING_ALLOWED=NO`) | ❌ 5.1.5 |
| Version number | Gradle | Repeated in `iosApp/project.yml` | ⚠️ 5.1.4 |
| Tests | 182 on the JVM | 53 on the simulator, plus a launch screenshot | ⚠️ 6 |

---

## 3. How the iPhone app works

### 3.1 Layers

```
iPhone
└── Watches.app
    ├── Swift shell: iosApp/iosApp/iOSApp.swift
    │     @main SwiftUI App → WindowGroup → ComposeView (UIViewControllerRepresentable)
    │     AppDelegate.didFinishLaunching → onAppLaunched()
    │         notification delegate today; the new privacy cover and foreground hooks too
    │
    └── WatchApp.framework (static, Kotlin/Native, built by Gradle in an Xcode build phase)
          MainViewController() → initKoin() → ComposeUIViewController { WatchesApp() }
          ├── shared/ui      every screen, view model, design and navigation (the same code as Android)
          ├── shared/data    Room on bundled SQLite, file store, backup engine
          ├── shared/search  Lookup sources and PoliteHttpClient (Ktor Darwin engine → NSURLSession)
          ├── shared/core    model, spec registry, units, care rules
          └── iosMain        the platform services, about 550 lines:
                ImageIO and UIKit images · zlib · PDFKit · UserNotifications · LocalAuthentication
                UIActivityViewController · Network (NWPathMonitor)
                FileKit → PHPicker, document picker, camera
```

Compose draws every pixel itself, with Skia on Metal, which is why the three designs look the same on both phones. Compose Multiplatform bridges text input, accessibility and gestures to UIKit.

### 3.2 What happens at launch

1. iOS shows the launch screen. New: the design's ground colour rather than plain white or black.
2. `didFinishLaunching` calls `onAppLaunched()`, which installs the notification delegate. A tapped reminder that launches the app therefore still opens the right watch. The new privacy cover and foreground hooks are installed here too.
3. SwiftUI asks for the view controller. `MainViewController()` starts Koin and returns the Compose UI. Koin builds nothing until a screen asks for it.
4. `WatchesApp()` loads the settings, primes the app lock before the first frame, tidies up an interrupted restore and starts `ReminderSync`.

### 3.3 Where things live

- The database, photos, documents and source pages are in `Application Support/Watches/` inside the app's container. That folder is excluded from backup, so iCloud and computer backups skip it (decision D6). iOS encrypts it at rest, with the default protection: readable once the phone has been unlocked after starting up.
- The pickers copy picked photos and files into `tmp/`; the app then copies them into its own storage.
- Backups and CSV files go wherever you choose in the Files sheet: On My iPhone, iCloud Drive, a USB drive, or another app's folder such as Google Drive.

### 3.4 Privacy on iPhone

- iOS asks for a permission only on first use:
  - **Camera**, when you take a photo;
  - **Notifications**, when you switch on a reminder;
  - **Face ID**, when you turn on the app lock.
- The photo picker needs no permission: it hands over only the photos you pick. Nothing asks for location, contacts, tracking or the local network.
- Only `shared/search` may reach the network. The build check that enforces this scans imports, and will also look for Apple's networking APIs outside the search module (5.1.8).
- The iPhone's own **App Privacy Report** (Settings › Privacy & Security, once you turn it on) lists every domain an app has contacted. It is the on-phone check that Watches only goes online during Lookup, as PCAPdroid is on Android.

### 3.5 How iOS behaviour maps onto the existing code

These come from the Compose Multiplatform 1.12.1 sources.

- **Lifecycle.** Compose reports:
  - `RESUMED` while the window is visible, in the foreground and active;
  - `STARTED` while it's in the foreground but inactive: Control Center, the Face ID sheet, the app switcher;
  - `CREATED` in the background.

  `AppLock` listens for start and stop (`App.kt:133`), so its own Face ID prompt never re-locks it. That matches the existing rule that time spent in the unlock prompt doesn't count.
- **Back.** A swipe from the left edge is always sent as a back event. `NavDisplay(onBack)` and every `BackHandler` (the discard prompts, the lock screen) receive it, with the same interactive animation as predictive back on Android. Every pushed screen already has a visible back or close button, which iPhone needs because it has no system back button.
- **Text size.** iOS text sizes map to a Compose font scale from 0.8 up to 1.8 at the largest accessibility size. The Android build is already tested at 2.0.
- **Accessibility.** Compose semantics become VoiceOver elements. Headings get the header trait, custom actions become VoiceOver actions, and `testTag` becomes the accessibility identifier, so XCUITest can find elements.
- **Files.** FileKit holds security-scoped access to a picked file for as long as a stream on it is open. To save, FileKit exports an empty placeholder through the document picker, deletes it at the destination, and gives the app that URL to write into.

### 3.6 Developing without a Mac

Linux CI catches most iOS breakage, because every push compiles the shared Kotlin for iOS. Linking the framework, running simulator tests and building the app need macOS, so those run in CI. Device behaviour (Face ID, camera, iCloud, background suspension, notification timing) needs your iPhone, through TestFlight. A Mac would shorten every loop, but it isn't required (decision D3).

---

## 4. Decisions for you

| # | Decision | Options | Recommendation |
| --- | --- | --- | --- |
| D1 | Apple Developer Program | Join as an individual, or don't | **Join.** Without it, an app installed from Xcode stops opening after 7 days, and installing it needs a Mac. About A$149 (US$99) a year. Approval usually takes a day or two. |
| D2 | How builds reach your iPhone | TestFlight · Ad Hoc · App Store (unlisted or public) · free 7-day installs | **TestFlight, internal testing.** No App Review; installs and updates through the TestFlight app; CI uploads directly. Each build stops opening after 90 days, so a new one is needed at least that often (your data stays). Ad Hoc builds last up to a year but need your iPhone's UDID and a manual install. App Store builds never expire, but need App Review, a privacy policy page and a store listing. |
| D3 | A Mac | None · occasional · your own | **Not required.** CI builds, tests and uploads. Occasional access helps with Xcode Cloud's one-time setup (D10) and with Instruments profiling. |
| D4 | Devices | iPhone only · iPhone and iPad | **iPhone only** for v1. The adaptive layouts exist, but iPad multitasking and its orientations are a second test matrix. |
| D5 | Minimum iOS | 17 (current setting) · 18 · 26 | **26, if your iPhone runs it** (iPhone 11 or later). Nobody else uses the app, it removes untested iOS versions, and it allows iOS 26's continued-processing tasks for long backups without a fallback (5.3.6). Otherwise keep 17. |
| D6 | iCloud and computer backups | Excluded (now) · included | **Keep excluded**, matching the Android decision that your backups are the files you save. The trade-off: a new iPhone set up from iCloud won't bring the collection with it. You restore a .zip instead. |
| D7 | Photos from the library | Keep HEIC originals · ask the picker for JPEG | **Keep HEIC.** It's the true original and about half the size. It needs 5.3.2. JPEG would be simpler, but every photo would be recompressed. |
| D8 | Names | Bundle ID `io.github.tunlezah.watches`; a name in App Store Connect | **Keep the bundle ID.** Like the Android signing key, it is the app's identity for good. App Store Connect needs a name no other app uses, such as "Watches – Collection". The home screen still says "Watches". |
| D9 | Where "Back up now" writes | Straight into the chosen place (now) · inside the app first, then handed over | **Straight in**, with file coordination (5.3.5). Fall back for a particular provider only if testing shows a problem; the fallback needs free space for a whole backup. |
| D10 | macOS CI minutes | GitHub minutes · Xcode Cloud · your own Mac as a runner · paid minutes | See 7.4. **Xcode Cloud**, if you can borrow a Mac for its one-time setup: 25 compute hours a month come with the membership. Otherwise GitHub, with the trimmed triggers in 7.4 and a minutes budget. |

---

## 5. What needs implementing

Sizes: **S** under half a day · **M** about a day · **L** two to three days. Each item lists its tests; section 6 collects them.

### 5.1 Get it onto your iPhone (milestone iOS-1)

**5.1.1 App icon and launch screen** (S)
- Add `iosApp/iosApp/Assets.xcassets` with:
  - `AppIcon`: one opaque 1024 × 1024 PNG of the Android icon (the champagne watch at 10:10 on graphite `#121110`), drawn from an SVG port of `ic_launcher_foreground.xml` kept beside it. Dark and tinted variants are optional.
  - `LaunchBackground`: `#F5F1E8` in light mode and `#121110` in dark mode, the Android window backgrounds.
- Set `UILaunchScreen` to `UIColorName: LaunchBackground`.
- Match the colour `WatchesApp()` paints while settings load (`App.kt:125`) to the launch colour, so launch → first frame doesn't flash.
- Tests: the Xcode build fails on an incomplete asset catalogue. Check the icon on the phone.

**5.1.2 Info.plist** (S, sketch in appendix B)
- Add `NSPhotoLibraryAddUsageDescription`. Without it, "Save Image" in the share sheet crashes the app when you share a photo document.
- Keep the camera and Face ID purpose strings. Drop `NSPhotoLibraryUsageDescription` unless something turns out to read the library; PHPicker doesn't.
- One window: `UIApplicationSceneManifest` with `UIApplicationSupportsMultipleScenes = false`. The app's singletons (Koin, `DeepLinks`, `BackupCoordinator`) assume one.
- iPhone only (`TARGETED_DEVICE_FAMILY = 1`, D4). Drop the iPad orientation list.
- Take the version keys from build settings (5.1.4).
- Tests: a Linux CI check that `project.yml` declares every required key and purpose string.

**5.1.3 Privacy manifest** (M)
- Add `iosApp/iosApp/PrivacyInfo.xcprivacy`: no tracking, no tracking domains, no collected data, and the "required reason" APIs used by the app and the statically linked Kotlin framework. Expected:
  - disk space, `E174.1`: checking space before writing a restore;
  - file timestamps, `C617.1`: Okio and FileKit reading the metadata of files in the app's container;
  - system boot time, `35F9.1`: measuring elapsed time (the coroutines' monotonic clock, the Diagnostics timings);
  - user defaults, `CA92.1`, only if a library turns out to use them.
- Make it checkable: after archiving, list the binary's imported symbols (`nm -u`). Fail if one from Apple's required-reason list appears that the manifest doesn't declare. On a Mac, Xcode's privacy report for the archive gives the same answer.
- Tests: that symbol check, in the release workflow.

**5.1.4 One version number** (S)
- Today `0.5.0` (build 5) is written in `androidApp/build.gradle.kts:26` and again in `iosApp/project.yml:21`.
- Move it to `gradle.properties`. Android reads it there. A Gradle task writes `iosApp/Version.xcconfig` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`), which `project.yml` includes.
- CI sets the iOS build number to the run number: TestFlight needs a new one for every upload.
- Tests: a Linux check that both platforms report the same version.

**5.1.5 Signing** (M, mostly your setup in 7.1)
- In `project.yml`: automatic signing, with the team ID from `Version.xcconfig` or an environment variable.
- In CI, preferred: an App Store Connect API key with `-allowProvisioningUpdates`, so Xcode creates and renews the certificate and profile itself. This needs a key with the Admin role.
- In CI, fallback: a distribution certificate (.p12) and an App Store profile in a temporary keychain. Both can be made without a Mac (a certificate request from `openssl`, then Apple's developer website).
- Secrets: `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64` and `APPLE_TEAM_ID`. The fallback adds `IOS_DIST_P12_BASE64`, `IOS_DIST_P12_PASSWORD` and `IOS_PROFILE_BASE64`. Nothing signing-related is committed, as on Android.

**5.1.6 Release workflow** (M, sketch in appendix C)
- `ios-release.yml`, on `v*` tags and on demand:
  1. generate the Xcode project;
  2. archive a Release build for devices;
  3. run the privacy symbol check (5.1.3);
  4. export with `method: app-store-connect` and `destination: upload`, which goes straight to TestFlight;
  5. keep the dSYMs for 14 days.
- Pin the Xcode version, for two reasons:
  - App Store Connect only accepts builds made with a recent SDK. At the time of writing that is Xcode 26 and the iOS 26 SDK; **verify** before the first upload.
  - A newer Xcode can break Kotlin/Native until Kotlin catches up, so Kotlin and Xcode are updated together.
- TestFlight builds expire after 90 days. The README says so, and a cheap Linux job could warn at 75 days (optional).

**5.1.7 Shell tidy-up** (S)
- Keep the Swift minimal: new hooks live in Kotlin `iosMain` and are installed from `onAppLaunched()`.
- `MainViewController()` gets a `configure { }` block if the keyboard work (5.2.7) needs one.

**5.1.8 Network isolation on iOS** (S)
- `checkNetworkIsolation` (`build.gradle.kts:17`) looks for Ktor, OkHttp and Coil's network module. On iOS, code could also reach the network through Apple's own APIs without importing any of those.
- Change: the source scan also fails on `NSURLSession`, `NSURLConnection`, `nw_connection` and `CFNetwork` outside `shared/search`. File URLs (`NSURL.fileURLWithPath`) stay allowed.
- Tests: the extended check on Linux; a simulator test that the Darwin engine's session keeps no cookies and no cache, as `Engine.ios.kt` sets it up.

### 5.2 Behave like an iPhone app (milestone iOS-2)

**5.2.1 Reminders within iOS's limit** (M)
- iOS keeps only the 64 soonest scheduled notifications per app. Three problems:
  - `IosReminders.reschedule` (`Services.ios.kt:91`) schedules one per watch, with no cap.
  - `ReminderSync.reminders` (`ReminderSync.kt:45`) orders them by watch ID, and includes today's even after 9 am, which Android skips.
  - `reschedule` returns before iOS has applied the change, so two quick edits can interleave.
- Change:
  - A pure planner in common code: fire at 9 am local time on the due date, drop times already past, order by fire time then watch, keep the first 60, leaving headroom.
  - iOS waits for each completion handler, runs one reschedule at a time, and only replaces requests whose due date changed (stored in the request's `userInfo`).
  - Reschedule on every return to the foreground, so the 60 move on as reminders fire. Nothing runs in the background, as now.
  - Settings › Service reminders says when more reminders are on than the iPhone holds at once.
  - Android keeps its current logic. It must still see today's reminders, so that a job Doze held back isn't cancelled.
- Tests:
  - `ReminderPlanTest` (common): the cap, the order, today before and after 9 am, a daylight-saving week, a time-zone change.
  - `IosRemindersTests` (app-hosted): 80 reminders leave 60 pending, with the right identifiers and dates; rescheduling twice changes nothing; archiving a watch removes its reminder.

**5.2.2 Notifications after "Don't Allow"** (S)
- iOS asks once. After a refusal, "Allow notifications" (`ServiceScreens.kt:218`) calls `request()`, which returns false without showing anything.
- Change: `NotificationAccess` reports not asked, allowed or denied. When denied, the button becomes **Open Settings**: the app's notification settings on iOS, and `ACTION_APP_NOTIFICATION_SETTINGS` on Android (which also stops asking after two refusals). The hint "Allow them in the phone's settings" (`ServiceScreens.kt:162`) gets each platform's wording.
- Tests: a common test of state → button; a check on the phone.

**5.2.3 Hide the collection in the app switcher** (M)
- Android blanks the recents card while the lock is on (`MainActivity.kt:33`). iOS has nothing. Even with "Lock again: Immediately", Compose draws the lock screen a moment later, probably after iOS has taken its snapshot.
- Change: while the app lock is on, a native cover (the ground colour and the lock glyph) goes over the window as soon as the app becomes inactive, and comes off when it's active again.
  - It's in Kotlin `iosMain`, installed at launch.
  - It reads a flag the app keeps in step with the setting, so the decision is instant.
  - The Privacy page says what it does.
- Tests: a common test of when the cover applies; a check of the app switcher on the phone.

**5.2.4 System UI follows the chosen theme** (S)
- Nothing tells the system when Settings › Appearance forces Light or Dark. On iPhone, the status bar, keyboard, share sheet, pickers and the Face ID sheet stay in the system's mode, so the status bar can be dark on dark. Android's status bar icons have the same gap.
- Change: `WatchTheme` calls a small platform function. iOS sets the window's `overrideUserInterfaceStyle`; Android sets light or dark system-bar icons.
- Tests: XCUITest screenshots with the system Light and the app Dark, and the reverse.

**5.2.5 Share sheet** (S)
- `IosFileSharer` (`Services.ios.kt:182`) finds its window with the deprecated `keyWindow`. Files go out under their stored names, such as `3f2a9c….pdf`.
- Change:
  - Copy the file to a temporary one named after the document's title.
  - Present it from the active window. FileKit's `shareFile` already does this, iPad anchoring included.
  - Delete the copy when the sheet closes.
- Tests: an app-hosted test of the copy and its name. On the phone: Save Image, Save to Files, AirDrop and Mail.

**5.2.6 App lock wording and edges** (S)
- Already right: the passcode fallback, and the app's own prompt doesn't count as leaving (3.5).
- Change:
  - Name the phone's method from `LAContext.biometryType` ("Face ID", "Touch ID", "passcode") on the lock screen (`LockScreen.kt:74`) and the Privacy page (`SettingsScreen.kt:411–415`).
  - Cancel the `LAContext` if the unlock is abandoned.
  - Give the prompt's cancel button a title.
- Tests:
  - `AppLockTest` moves to common code and runs on both platforms.
  - A debug-only fake unlock (a launch argument, ignored by release builds) drives the XCUITest lock flows.
  - Real Face ID and the passcode are checked on the phone.

**5.2.7 Keyboards** (M)
- Decimal and number pads (`EditScreen.kt:267`, `Fields.kt:172`) have no return key on iPhone. `ImeAction.Next` (`Controls.kt:326`) therefore does nothing there, and nothing on the keyboard dismisses it.
- By default, Compose on iOS also pans the view to keep a focused field above the keyboard, while the forms already use `imePadding()`. **Verify** whether fields move twice.
- Change:
  - Tapping outside a field, or starting to scroll, clears focus on every form.
  - A small Done bar above number pads, if that isn't enough.
  - Keep only one of the two keyboard adjustments.
- Tests: XCUITest types a diameter, dismisses the keyboard and checks the value. A screenshot with the keyboard up shows the focused field.

**5.2.8 Swipe back next to things you drag** (S)
- On iPhone a swipe from the left edge always means back. The Crop screen's corner handles can sit 20 dp from the edge (`CropScreen.kt:96`), so dragging a left handle can start a back gesture instead. The viewer's first photo has the same edge.
- Change: a wider horizontal inset on the Crop screen on iOS, or no back gesture while a handle is held.
- Tests: XCUITest drags a left handle starting 25 pt from the edge: the crop changes and the screen stays. An edge swipe on a changed form shows the discard prompt.

**5.2.9 Landscape** (S)
- Headers use `WindowInsets.statusBars` only. In landscape, the Dynamic Island and the rounded corners can cover the sides.
- Change: horizontal safe-area padding on headers, lists and the bottom bar. Android phones with side cut-outs benefit too.
- Tests: XCUITest landscape screenshots on a Dynamic Island simulator; a Roborazzi landscape case.

**5.2.10 Wording and licences** (S)
- One `PlatformText` per platform, so screens don't branch on the platform. It covers:
  - the lock screen and the Privacy page (5.2.6);
  - "Not in cloud backups" (`SettingsScreen.kt:438`) and the backup notes (`BackupScreens.kt:155, 226`): iCloud Backup rather than Android's backup, and On My iPhone, iCloud Drive and USB drives as places;
  - the notification hint (5.2.2);
  - the Diagnostics hint about Android's developer options (`DiagnosticsScreen.kt:62`).
- Licences (`InfoScreens.kt:38`): the iPhone app bundles Skia and Skiko, which Android takes from the system, so they're credited there. Android-only libraries drop off the iOS list.
- Tests: a common test that every text exists on both platforms; screenshot review.

**5.2.11 Diagnostics on iPhone** (S)
- `processUptimeMillis()` returns `null` on iOS (`Platform.ios.kt:15`), so Diagnostics shows no cold start. Read the process start time with `sysctl`; the fallback is a timestamp taken first thing in Swift.
- Add "Send a test reminder in a minute" on both platforms, to check delivery and the tap-through on a phone without waiting for a due date.
- Tests: an app-hosted test that the uptime is positive and plausible.

**5.2.12 iPhone conventions (optional)** (S)
- Tapping the current tab scrolls its list to the top.
- A memory warning empties Coil's memory cache.

### 5.3 Photos, documents and backups (milestone iOS-3)

**5.3.1 Renditions without full-size decodes** (L)
- `IosImageProcessor.render` (`IosImageProcessor.kt:82`) decodes the whole picture with `UIImage` and draws it. Decoded, a default 24 MP iPhone photo is about 95 MB, and a 48 MP one (HEIF Max or ProRAW) about 190 MB. A 20-photo import risks iOS ending the app for memory. `size()` decodes too.
- Change: ImageIO throughout.
  - Size and orientation from the image's properties, without decoding.
  - A downsampled, already-rotated decode, sized so that the cropped region still has `maxEdge` pixels.
  - Crops in display orientation, as the `ImageProcessor` contract says, so a crop made on Android stays right on the iPhone and back.
  - JPEG written in sRGB, as on Android.
- Tests (simulator):
  - every EXIF orientation comes out upright (eight fixtures with coloured corners);
  - the same crop on the JVM and on iOS gives the same 8 × 8 colour grid, within a tolerance;
  - GPS is removed from JPEG and HEIC, while the capture date stays;
  - HEIC renders;
  - an 8000 × 6000 image renders, with peak memory recorded.

**5.3.2 HEIC originals and the Crop screen** (M)
- Photos keep their untouched original (`PhotoRepository.kt:53`). FileKit asks the picker for its "automatic" representation, which leaves the choice to iOS, so expect HEIC for photos the iPhone took.
- The Crop screen loads that original with Coil (`CropScreen.kt:100`). On iOS, Coil decodes through Skia: it reads the whole file into memory, and as far as I can tell Skia has no HEIC decoder there. **Verify** this first with a HEIC fixture on the simulator. If it's confirmed, the Crop screen is blank for HEIC photos.
- Change, recommended: a Coil decoder for iOS built on ImageIO, added only to the iOS image loader. It decodes at the size shown, applies orientation, and reads HEIC, HEIF and DNG. It also covers document photos and large JPEGs.
- Fallback: the Crop screen shows a 2048 px full-frame preview made by `ImageProcessor`.
- Set the picker's representation explicitly (D7) rather than leaving it "automatic".
- ProRAW: the picker can hand over a 25–75 MB DNG, which is stored today with the unknown extension `.img`. Detect DNG and keep a full-size HEIC or JPEG made from it, so that a restored copy renders on Android too.
- Tests:
  - a Compose UI test on the simulator loads HEIC and rotated fixtures through the app's image loader (not blank, upright, the right size);
  - DNG detection;
  - library photos on the phone: HEIC, a Live Photo, ProRAW, a panorama, a screenshot.

**5.3.3 Large picks** (S)
- `PhotoImporter` reads each picked file into memory whole (`PhotoImporter.kt:38`). Stream it into the file store instead, which matters for DNGs and 50 MB documents.
- Tests: the ported repository tests, plus a 60 MB streamed import on the simulator.

**5.3.4 PDF pages** (S)
- `IosPdf` draws a page into a `UIImage`, encodes it as PNG and decodes that again with Skia (`Services.ios.kt:66–76`). It also assumes a 2× screen, so on 3× iPhones it renders half as wide again as asked.
- Change: draw the page straight into a bitmap exactly `widthPx` wide, and hand the pixels to Skia.
- Tests: page 1 of a fixture PDF at 1,200 px has the exact width, isn't blank, and renders quickly enough to page through.

**5.3.5 Backups into cloud folders** (M)
- As 3.5 describes, the app streams the ZIP into the URL the document picker returns, and reads a backup where it is. Neither uses `NSFileCoordinator`, which iCloud Drive and other providers (Google Drive, OneDrive) expect. Without it, a write may not reach the provider properly, and a file that isn't on the phone yet may not download.
- Change: on iOS, wrap each whole stream in a coordinated write or read, on a background thread. If a provider still misbehaves in testing, fall back for it to writing inside the app first (D9).
- Tests: app-hosted tests of coordinated reads and writes; the device matrix in appendix D, including a backup over 4 GB. The places are On My iPhone, iCloud Drive (file on the phone, and not yet downloaded), a USB-C drive and Google Drive.

**5.3.6 Leaving the app during a long job** (M)
- iOS suspends an app a few seconds after you leave it. A backup then pauses, resumes if you come back soon, and leaves a half-written ZIP if iOS ends the app meanwhile. Restores are already safe: the journal undoes or finishes them at the next launch.
- Change:
  - Every backup, restore and CSV export holds a background task assertion, worth about 30 more seconds.
  - With D5 at iOS 26, a backup you start continues in the background as a continued-processing task, with iOS's own progress display. **Verify** the details when building it.
  - Otherwise, when the extra time runs out, the job is cancelled the existing way, which deletes the partial file. The screen then says to keep Watches open until a backup finishes.
- Tests:
  - a common test that an expiring assertion cancels a backup and removes the partial file;
  - an app-hosted test that assertions start and end in pairs;
  - on the phone, leave a 2 GB backup for 10 seconds, and for 2 minutes.

**5.3.7 Free space** (S)
- `NSFileSystemFreeSize` (`FreeSpace.ios.kt:10`) leaves out space that iOS frees on demand, so a restore could be refused on a phone with room. Use the "available for important usage" figure instead.
- Tests: an app-hosted test that it's at least the old figure.

**5.3.8 Moving from Android to iPhone** (M)
- The archive format is cross-platform by design. The route:
  1. On the Motorola, Back up now, and save it to Google Drive, a USB-C drive or a computer.
  2. On the iPhone, Files shows Google Drive (once its app is installed) and USB drives.
  3. Settings › Backup & restore › Restore from a backup › Replace.
  4. Switch the app lock and reminders on again. Device-only settings never travel, by design.
- Tests:
  - a golden archive made by the JVM tests is restored on the simulator and deep-compared: every row, every file hash, renditions present;
  - an archive made on the simulator restores on the JVM;
  - your real backup restores on your iPhone.

---

## 6. Tests

### 6.1 Layers

| Layer | Runs on | Covers |
| --- | --- | --- |
| L1 Shared tests (`commonTest`) | Linux (JVM and Android host) and the iOS simulator | Logic, repositories, backup and restore, parsers, HTTP politeness, app-lock rules |
| L2 iOS platform tests (`iosTest`) | The iOS simulator, without the app | Image processing and decoding, zlib, PDFs, HTTP engine settings |
| L3 App-hosted tests (new `iosAppTests`) | Inside the app, on the simulator | What needs a real app: notifications, sharing, background tasks, file coordination, the iOS Koin graph |
| L4 Compose UI tests on iOS | The iOS simulator | Screens drawn by iOS: the lock cover, the discard prompt, image loading, the largest text size |
| L5 XCUITest (new `iosAppUITests`) | The real app, on the simulator | End-to-end flows, screenshots, an accessibility audit, launch and scroll timings |
| L6 Device checklist | Your iPhone, with a TestFlight build | What a simulator can't do (appendix D) |

L3 exists because APIs like the notification centre need an app bundle and fail in a bare test executable. Its tests are written in Swift against the framework's public API (Kotlin suspend functions arrive in Swift as `async`).

### 6.2 Moving today's tests

| Tests | Today | Becomes | What it takes |
| --- | --- | --- | --- |
| core (6 files) and `ZipTest` | common, run on iOS | unchanged | Nothing |
| `RepositoryTest`, `CareRepositoryTest` (22 tests) | JVM | data `commonTest` | Per-platform test helpers: a temporary folder (Okio), image bytes and an `ImageProcessor`. `runTest` instead of `runBlocking`. |
| `BackupRepositoryTest` (15) | JVM | mostly common | Read archives with the app's own ZIP reader. The "re-zipped on a computer" check needs `java.util.zip`, so it stays JVM-only, with one or two others. |
| `ParserFixturesTest` (11) | JVM | search `commonTest` | Fixtures readable on iOS: generated into a Kotlin source at build time, or read from the source tree with Okio |
| `PoliteHttpClientTest`, `LookupServiceTest` (20) | JVM | search `commonTest` | Nothing: Ktor's mock engine is multiplatform |
| `AppLockTest`, `CareLogicTest`, `EditParsingTest`, `DesignContrastTest` (17) | Android | ui `commonTest` | Android host tests switched on for `shared/ui`, so Linux CI still runs them; `kotlin.test` assertions |
| `DependencyGraphTest` | Android | stays | An iOS twin in L3: start Koin with the iOS modules, data folder pointed at a temporary one, and resolve every definition. Koin's `verify()` is JVM-only. |
| `ScreenshotTest` (40) | Android | stays | iOS screenshots come from XCUITest (L5) |
| `LiveSourcesTest` (3, opt-in) | JVM | stays | An opt-in iOS run through the Darwin engine, before releases |

Result: about 135 of today's 182 tests run on the simulator as well, up from 53. Porting moves tests; it doesn't change what they assert. Running the parsers on iOS matters more than it looks: Kotlin/Native has its own regular-expression engine and number formatting, and Lookup parsing relies on both.

### 6.3 New tests

| Test | Layer | Proves | § |
| --- | --- | --- | --- |
| `ReminderPlanTest` | L1 | 9 am planner: cap, order, past times, daylight saving, time zones | 5.2.1 |
| `IosRemindersTests` | L3 | At most 60 pending; identifiers, dates, idempotence, removal | 5.2.1 |
| `NotificationStatusTest` | L1 | Not asked / allowed / denied → the right button | 5.2.2 |
| `PrivacyCoverTest` | L1 | The cover applies exactly when the lock is on and can unlock | 5.2.3 |
| `PlatformTextTest` | L1 | Every text exists on both platforms | 5.2.10 |
| `IosShareTests` | L3 | Temporary copy named after the title, removed afterwards | 5.2.5 |
| `UptimeTests` | L3 | Cold-start reading is positive and plausible | 5.2.11 |
| `IosKoinGraphTests` | L3 | Every iOS definition resolves | 6.2 |
| `IosImageProcessorTest` | L2 | Orientations, crop parity with the JVM, GPS stripping, HEIC, a large image | 5.3.1 |
| `ImageLoaderIosTest` | L4 | HEIC and rotated originals load upright through the app's image loader | 5.3.2 |
| `ImageFormatsTest` (extended) | L1 | DNG detection | 5.3.2 |
| `StreamedImportTest` | L2 | A 60 MB pick streams into storage | 5.3.3 |
| `IosPdfTest` | L2 | Exact width, not blank, quick | 5.3.4 |
| `CoordinatedFileTests` | L3 | Coordinated reads and writes stream correctly | 5.3.5 |
| `BackgroundAssertionTest` | L1 + L3 | Expiry cancels and cleans up; assertions pair up | 5.3.6 |
| `FreeSpaceTests` | L3 | Important-usage capacity ≥ the old figure | 5.3.7 |
| `CrossPlatformArchiveTest` | L1 on both | An Android-made archive restores exactly on iOS, and the reverse | 5.3.8 |
| `DarwinEngineConfigTest` | L2 | The Darwin engine's session keeps no cookies and no cache | 5.1.8 |
| `NetworkIsolation` (extended) | Linux | Apple networking APIs appear only in `shared/search` | 5.1.8 |
| `LockCoverUiTest` | L4 | The lock window swallows touches and hides content from VoiceOver | 5.2.6 |
| `LargestTextUiTest` | L4 | Detail and Edit at font scale 1.8 keep every value visible | 3.5 |

### 6.4 XCUITest flows

Setup:
- Debug builds accept launch arguments: a fresh data folder, seeded sample watches, the design and theme, animations off, a fake unlock, and a fake "offline". Release builds ignore them.
- `simctl addmedia` seeds library photos (JPEG, HEIC, a rotated portrait).
- Test files are copied into the simulator's On My iPhone folder.
- `simctl status_bar` gives clean screenshots.

Flows:
1. **First run.** Add a watch with a brand, a reference and a decimal diameter. It appears in the grid, and search finds it.
2. **Photos.** Add three library photos, set the cover, crop, reorder and delete.
3. **Detail and edit.** Long-press a spec to copy it; the serial number stays hidden until tapped. Edit, change a field, swipe back: the discard prompt appears.
4. **Service.** Log a full service, switch the reminder on and allow notifications. The Service tab lists the watch.
5. **Documents.** Add a PDF from Files, view it, rename it, open the share sheet.
6. **Backup.** Back up to On My iPhone. Restore with Merge (the report says nothing changed), then with Replace.
7. **App lock.** Turn it on, leave and return: locked. Unlock: the collection shows. A failed unlock stays locked.
8. **Lookup offline.** The offline notice shows, and the request log stays empty.
9. **Appearance.** Each design in Light and Dark, including forced opposite to the system.
10. **Landscape** on a Dynamic Island phone.
11. **Largest text size** on the key screens.
12. **Accessibility audit** (`performAccessibilityAudit`) on Collection, Detail, Edit, Settings and Backup.
13. **Timings.** A cold launch, and a fling through 500 sample watches.

Screenshots from flows 9–11 are kept as CI artifacts for review, like the Roborazzi ones, and can become baselines later.

### 6.5 Cross-platform checks

- Archives both ways (5.3.8).
- Crop parity between the JVM and iOS (5.3.1).
- CSV and `collection.json` byte-identical for the same data on both platforms. Number formatting differs between the JVM and Kotlin/Native in places, so this is worth a test.
- Parser results identical on both (6.2).

### 6.6 What only your iPhone can test

Face ID and the passcode fallback, the camera, notifications arriving at 9 am and opening the right screen, the app switcher, background suspension, iCloud Drive and other providers, USB drives, real HEIC, ProRAW and Live Photos, VoiceOver by ear, 120 Hz smoothness, memory with a full collection, and the App Privacy Report. They're all in appendix D.

---

## 7. Signing, distribution and CI

### 7.1 Accounts (you, once)

1. Enrol in the Apple Developer Program as an individual.
2. In App Store Connect, create the app: bundle ID `io.github.tunlezah.watches`, a unique name (D8), and any SKU.
3. Create an API key (Users and Access › Integrations), with the Admin role if CI is to manage signing (5.1.5). Add the four secrets to the repository.
4. Install TestFlight on your iPhone, signed in with the same Apple ID.

I can't do these steps: they need your Apple ID, payment and Apple's identity checks.

### 7.2 How a build reaches the phone

Tag `v0.6.0` → `ios-release` archives, signs and uploads → App Store Connect processes the build (minutes) → TestFlight tells your iPhone → install. Later builds install over it and keep your data, as long as the bundle ID and team stay the same.

### 7.3 Workflows

| Workflow | When | What it does | Approx. time |
| --- | --- | --- | --- |
| Android | Every push | As now, plus the tests moved to common code | Linux |
| iOS | iOS changes, tags, on demand | Shared tests for all four modules on the simulator, app build, app-hosted tests, launch screenshot | 15–20 min (about 10 today) |
| iOS UI (new) | Tags, on demand | XCUITest flows, screenshots, accessibility audit | 20–30 min |
| iOS release (new) | `v*` tags, on demand | Archive, privacy symbol check, upload to TestFlight | 20–25 min |

### 7.4 macOS minutes

`docs/PLAN.md` (Q2) notes that macOS minutes count ten times against a private repository's 2,000 free minutes. At the times above, that allows about 8–10 macOS runs a month, and the conversion will probably need 30–50. Options:
- **Xcode Cloud.** At the time of writing, 25 compute hours a month come with the developer membership. It builds, tests, uploads to TestFlight and manages signing itself. It needs Xcode on a Mac once, for setup, and a `ci_scripts/ci_post_clone.sh` that installs a JDK and runs XcodeGen (or the generated project committed).
- **A Mac you own, as a self-hosted runner.** Check GitHub's current pricing for self-hosted runners.
- **Paid GitHub minutes** for the months of the conversion.

Whichever you choose, trim the triggers: simulator tests on iOS changes only, UI tests and releases on tags and on demand, and superseded runs cancelled (already the case).

---

## 8. Milestones

| Milestone | Scope | Size | You test | Done when |
| --- | --- | --- | --- | --- |
| **iOS-1** On your iPhone | All of 5.1; the test moves in 6.2; app-hosted and UI test targets with one smoke test each; CI changes | L | Install from TestFlight. Try restoring a copy of your Android backup, keeping the Android phone as the main copy until iOS-3. | A TestFlight build runs on your iPhone; about 135 shared tests pass on the simulator; Android CI is green |
| **iOS-2** Like an iPhone app | 5.2 | L | Reminders (the one-minute test), notifications after Don't Allow, the app switcher, forced themes, keyboards, swipe back, landscape, wording | The 5.2 tests pass; appendix D's system sections pass |
| **iOS-3** Photos, documents and backups | 5.3 | L | HEIC and ProRAW photos, crops, PDFs, big backups to iCloud Drive, USB and Google Drive, leaving mid-backup, your Android backup restored | The 5.3 tests pass; appendix D's photo, document and backup sections pass |
| **iOS-4** Checked and released | The XCUITest flows (6.4), accessibility and performance passes, docs (an iPhone section in the README, CHANGELOG, PLAN Q1 resolved), v1.0 for both phones | M–L | All of appendix D | The checklist passes; `v1.0.0` is on TestFlight and as an APK |

Each milestone ends, as before, with a changelog entry and green CI, which now includes the iOS workflows.

---

## 9. Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| No Mac | iOS-only problems show up only in CI or on the phone, so loops are slow | Keep iOS code thin; Linux compiles for iOS on every push; TestFlight from iOS-1; borrow a Mac for the polish if needed |
| macOS minutes | CI blocked mid-month | 7.4 |
| HEIC and memory | Blank Crop screen; the app ended during imports | 5.3.1–5.3.2, confirmed first with fixtures |
| Cloud providers, big backups | Half-written or missing backups | 5.3.5–5.3.6 and the device matrix. A restore also checks every file's SHA-256, so a damaged backup never changes the phone. |
| TestFlight's 90 days; a lapsed membership | The app stops opening until a new build arrives (data stays) | Release at least every two months; keep the membership; App Store distribution if this becomes a nuisance (D2) |
| The 64-notification limit | Later reminders missing | 5.2.1 |
| Apple's yearly rules | Uploads refused (SDK minimum, privacy manifest) | Pin Xcode; check before each release; the symbol check |
| Kotlin and Xcode versions | A new Xcode breaks the Kotlin build | Pin both; update them together |
| Compose's feel on iOS | Text editing, scrolling or menus feel slightly non-native | Checked on the phone in iOS-2. Compose Multiplatform keeps improving this, and updating it is routine. |
| Simulator ≠ phone | Bugs that appear only on the device | Appendix D |
| App identity | A new bundle ID or team is a new app with empty storage | Keep both for good (D8), like the Android key |
| Uninstalling deletes the data | A lost collection | As on Android: back up. Settings already warns when the last backup is old or missing. |

---

## 10. Not in this plan

iPad layouts, widgets, Siri Shortcuts, Spotlight (it would index private notes), a Share extension ("Save to Watches" from Mail), the VisionKit document scanner, an Apple Watch app, and iCloud sync. Each could follow later. None is needed for the iPhone app to match the Android one.

---

## Appendix A: changes by file

| File | Change | § |
| --- | --- | --- |
| `iosApp/project.yml` | Device family, scene manifest, version xcconfig, signing, assets, privacy manifest, test targets | 5.1 |
| `iosApp/iosApp/Assets.xcassets` (new) | `AppIcon`, `LaunchBackground` | 5.1.1 |
| `iosApp/iosApp/PrivacyInfo.xcprivacy` (new) | Privacy manifest | 5.1.3 |
| `iosApp/Version.xcconfig` (generated), `iosApp/ExportOptions.plist` (new) | Version and build number; export settings | 5.1.4, 5.1.6 |
| `iosApp/iosAppTests/`, `iosApp/iosAppUITests/` (new) | App-hosted tests, XCUITest flows | 6 |
| `gradle.properties`, `androidApp/build.gradle.kts`, `build.gradle.kts` | Shared version and its task; Apple networking APIs in the isolation check | 5.1.4, 5.1.8 |
| `tools/check-ios-config` and `tools/check-privacy-symbols.sh` (new) | Required Info.plist keys (Linux); required-reason APIs against the manifest (release) | 5.1.2, 5.1.3 |
| `shared/ui/build.gradle.kts` | Android host tests; iOS UI test dependency | 6.2 |
| `shared/ui/src/iosMain/.../platform/Services.ios.kt` | Reminders, notification status, sharing, unlock | 5.2.1–5.2.6 |
| `shared/ui/src/iosMain/.../platform/Platform.ios.kt` | Process start time | 5.2.11 |
| `shared/ui/src/iosMain/.../MainViewController.kt` | Foreground hooks, privacy cover, appearance, image decoder | 5.2.3–5.2.4, 5.3.2 |
| `shared/ui/src/commonMain/.../care/ReminderSync.kt` and a new planner | The 9 am planner | 5.2.1 |
| `shared/ui/src/commonMain/.../platform/Services.kt` | Notification status, opening settings, platform text, background assertion | 5.2.2, 5.2.10, 5.3.6 |
| `shared/ui/src/commonMain/.../theme/WatchTheme.kt`, `App.kt` | System UI appearance; image loader parts; placeholder colour | 5.2.4, 5.3.2, 5.1.1 |
| `.../photos/CropScreen.kt` | iOS edge inset | 5.2.8 |
| `.../edit/EditScreen.kt`, `.../components/Controls.kt`, `.../components/Fields.kt` | Keyboard dismissal | 5.2.7 |
| Headers in `.../components/Fields.kt`, `.../components/Ornaments.kt`, `.../nav/AppShell.kt` | Landscape insets | 5.2.9 |
| `.../security/LockScreen.kt`, `.../settings/SettingsScreen.kt`, `.../backup/BackupScreens.kt`, `.../care/ServiceScreens.kt`, `.../settings/DiagnosticsScreen.kt`, `.../settings/InfoScreens.kt` | Platform text, licences | 5.2.10 |
| `.../backup/BackupCoordinator.kt`, `.../backup/FileStreams.kt` | Background assertion, coordinated streams | 5.3.5–5.3.6 |
| `.../photos/PhotoImporter.kt`, `.../photos/PhotosScreen.kt` | Streamed import, explicit picker representation | 5.3.2–5.3.3 |
| `shared/data/src/iosMain/.../files/IosImageProcessor.kt` | ImageIO pipeline | 5.3.1 |
| `shared/data/src/iosMain/.../files/FreeSpace.ios.kt` | Important-usage capacity | 5.3.7 |
| `shared/data/src/commonMain/.../files/ImageProcessor.kt` | DNG detection | 5.3.2 |
| `shared/*/src/commonTest`, `shared/*/src/iosTest` | Moved and new tests | 6 |
| `androidApp/.../platform/AndroidServices.kt`, `androidApp/.../MainActivity.kt` | Notification status and settings; system-bar icons | 5.2.2, 5.2.4 |
| `.github/workflows/ios.yml`; `ios-ui.yml` and `ios-release.yml` (new) | CI | 7.3 |
| `README.md`, `CHANGELOG.md`, `docs/PLAN.md` | Installing on iPhone, moving from Android; Q1 resolved | iOS-4 |

---

## Appendix B: Info.plist and privacy manifest

`iosApp/project.yml`, the parts that change:

```yaml
targets:
  iosApp:
    configFiles:
      Debug: Version.xcconfig
      Release: Version.xcconfig
    settings:
      base:
        TARGETED_DEVICE_FAMILY: "1"
        IPHONEOS_DEPLOYMENT_TARGET: "26.0"   # D5
        CODE_SIGN_STYLE: Automatic
        DEVELOPMENT_TEAM: $(WATCHES_TEAM_ID)
    info:
      properties:
        CFBundleDisplayName: Watches
        CFBundleShortVersionString: $(MARKETING_VERSION)
        CFBundleVersion: $(CURRENT_PROJECT_VERSION)
        UILaunchScreen:
          UIColorName: LaunchBackground
        UIApplicationSceneManifest:
          UIApplicationSupportsMultipleScenes: false
        CADisableMinimumFrameDurationOnPhone: true
        NSCameraUsageDescription: Take photos of your watches and their papers.
        NSFaceIDUsageDescription: Unlock Watches when the app lock is on.
        NSPhotoLibraryAddUsageDescription: Save a photo you share from Watches to your library.
        ITSAppUsesNonExemptEncryption: false   # HTTPS through iOS only
        UISupportedInterfaceOrientations:
          - UIInterfaceOrientationPortrait
          - UIInterfaceOrientationLandscapeLeft
          - UIInterfaceOrientationLandscapeRight
```

`iosApp/iosApp/PrivacyInfo.xcprivacy`. The final list of APIs comes from the symbol check (5.1.3), not from this sketch.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>NSPrivacyTracking</key><false/>
  <key>NSPrivacyTrackingDomains</key><array/>
  <key>NSPrivacyCollectedDataTypes</key><array/>
  <key>NSPrivacyAccessedAPITypes</key>
  <array>
    <dict>
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategoryDiskSpace</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>E174.1</string></array>
    </dict>
    <dict>
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategoryFileTimestamp</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>C617.1</string></array>
    </dict>
    <dict>
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategorySystemBootTime</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>35F9.1</string></array>
    </dict>
  </array>
</dict>
</plist>
```

---

## Appendix C: release workflow

A sketch of `.github/workflows/ios-release.yml`. The runner label and Xcode path are placeholders: **verify** them against the current runner images.

```yaml
name: iOS release

on:
  push:
    tags: ["v*"]
  workflow_dispatch:

jobs:
  testflight:
    runs-on: macos-26                     # an image that has the pinned Xcode
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: "21" }
      - uses: gradle/actions/setup-gradle@v4
      - run: sudo xcode-select -s /Applications/Xcode_26.app   # pinned
      - name: Generate the project
        run: |
          brew install xcodegen
          ./gradlew writeIosVersion
          cd iosApp && xcodegen generate
      - name: API key
        env: { P8: "${{ secrets.ASC_KEY_P8_BASE64 }}" }
        run: echo "$P8" | base64 -d > "$RUNNER_TEMP/asc.p8"
      - name: Archive
        run: >
          xcodebuild archive
          -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release
          -destination generic/platform=iOS -archivePath "$RUNNER_TEMP/Watches.xcarchive"
          CURRENT_PROJECT_VERSION=${{ github.run_number }}
          WATCHES_TEAM_ID=${{ secrets.APPLE_TEAM_ID }}
          -allowProvisioningUpdates -authenticationKeyPath "$RUNNER_TEMP/asc.p8"
          -authenticationKeyID ${{ secrets.ASC_KEY_ID }}
          -authenticationKeyIssuerID ${{ secrets.ASC_ISSUER_ID }}
      - name: Required-reason APIs match the privacy manifest
        run: tools/check-privacy-symbols.sh "$RUNNER_TEMP/Watches.xcarchive"
      - name: Upload to TestFlight
        run: >
          xcodebuild -exportArchive -archivePath "$RUNNER_TEMP/Watches.xcarchive"
          -exportOptionsPlist iosApp/ExportOptions.plist
          -exportPath "$RUNNER_TEMP/export"
          -allowProvisioningUpdates -authenticationKeyPath "$RUNNER_TEMP/asc.p8"
          -authenticationKeyID ${{ secrets.ASC_KEY_ID }}
          -authenticationKeyIssuerID ${{ secrets.ASC_ISSUER_ID }}
      - uses: actions/upload-artifact@v4
        with:
          name: dsyms
          path: ${{ runner.temp }}/Watches.xcarchive/dSYMs
          retention-days: 14
```

`ExportOptions.plist` sets `method` to `app-store-connect`, `destination` to `upload`, `signingStyle` to `automatic`, and the team ID.

---

## Appendix D: device checklist

Run on your iPhone with a TestFlight build, with 500 sample watches where it says so (Settings › Diagnostics adds and removes them).

**Install and update**
- [ ] Installs from TestFlight and opens to the welcome screen.
- [ ] A later build installs over it and keeps the collection.
- [ ] App Privacy Report: no network activity until a Lookup, then only the sources' domains.

**Moving from Android**
- [ ] Your Android backup restores with Replace. The counts match the Android phone, and photos, crops, documents, service history and box & papers are all there.
- [ ] Merging the same backup again changes nothing.

**Photos**
- [ ] From the library: a HEIC photo, a portrait shot, a Live Photo, ProRAW (if your iPhone takes it), a panorama and a screenshot, then 20 at once.
- [ ] From the camera: portrait and landscape shots come out upright.
- [ ] Crop each kind. The result matches the frame you chose, and re-cropping starts from the original.
- [ ] A shared original carries no location (check its info in Photos after saving it).

**Documents**
- [ ] Add a PDF from Files, one from iCloud Drive that isn't downloaded yet, and a photo of a paper receipt.
- [ ] The viewer: sharp pages, pinch to zoom. Share → Save Image, Save to Files, AirDrop and Mail all work, and the shared file has the document's title.

**Lookup**
- [ ] Search on Wi-Fi and on mobile data.
- [ ] In airplane mode the offline notice shows and the request log stays empty.

**Service and reminders**
- [ ] Diagnostics' test reminder arrives a minute later, also with the app closed.
- [ ] Tapping it opens the watch's service history; with a form open, it waits.
- [ ] After "Don't Allow": Open Settings, allow, return. Settings shows notifications allowed.

**App lock**
- [ ] Turn it on with Face ID. Try "Lock again" Immediately and after 1 minute.
- [ ] The app switcher shows the cover, not the collection.
- [ ] A failed Face ID falls back to the passcode, including when Face ID is locked out.
- [ ] Remove the iPhone's passcode: the lock pauses. Set it again: the lock resumes.

**Backups**
- [ ] Back up to On My iPhone, iCloud Drive, a USB-C drive and Google Drive. Each one restores with Merge.
- [ ] A backup over 4 GB (500 sample watches with photos) to iCloud Drive.
- [ ] Leave the app 10 seconds into a backup, and then 2 minutes into one, and come back.
- [ ] Force-quit during a restore and reopen: the collection is unchanged or fully restored, never half.
- [ ] On a nearly full phone: a clear message, and nothing damaged.
- [ ] The CSV opens correctly in Numbers and Excel.

**Look and feel**
- [ ] Each design in Light and Dark, and forced opposite to the system: the status bar, keyboard and share sheet match.
- [ ] Landscape: nothing sits under the Dynamic Island.
- [ ] Swipe back works everywhere, changed forms ask before discarding, and crop handles near the edge still drag.
- [ ] Every field's keyboard can be dismissed, and the focused field stays visible.
- [ ] Haptics feel right on tabs, switches and finished backups.

**Accessibility**
- [ ] VoiceOver: tabs, headings, switches, spec rows ("Diameter, 39.7 millimetres"), the copy action, the lock screen.
- [ ] The largest accessibility text size, Bold Text, Reduce Motion and Increase Contrast.

**Performance (500 sample watches)**
- [ ] Diagnostics: cold start under 1 s, search under 100 ms, detail under 150 ms.
- [ ] Fast scrolling stays smooth at 120 Hz, and opening a watch doesn't stutter.
- [ ] A 20-photo import completes without the app being ended.

---

## Appendix E: alternatives considered

| Option | What it means | Verdict |
| --- | --- | --- |
| **A. Compose on iOS** (recommended) | The current architecture: one UI for both phones | Most of it is done. The same three designs, one codebase to change. |
| B. SwiftUI screens over the shared Kotlin | Rewrite about 11,000 lines of screens, view models and the three designs in Swift; keep data, search and core | Native controls, but every screen built twice and kept in step for good. Several times the work. |
| C. A full Swift rewrite | Also rebuild the database, the backup engine, the parsers and the politeness rules | Throws away tested code, and the guarantee that a backup restores on either phone. |
