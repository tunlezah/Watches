# Watches on the Mac: plan

Status: **milestones mac-1 and mac-2 built** (23 Nov 2026, versions 0.7.0 and 0.8.0); mac-3 and mac-4 to come. Written against `aa79289` (0.6.0, with iPhone milestone iOS-1 built).

**Decided so far** (section 7): the Mac is a current Apple silicon model on macOS 27. Builds come from the GitHub release, beside the APK. Work starts now, with mac-1. The photo component is fixed for Android, iPhone and Mac together, as part of this plan (section 5).

**mac-1 as built.** All of 8.1, with these differences:
- **A second copy (8.1.4)** is told "Watches is already open" and closes. It doesn't bring the first to the front itself. macOS already does that when the app is opened twice the usual way, so the lock only matters for a copy started some other way.
- **The limits (8.1.7)** are one small declaration for each platform (`PlatformLimits`), and the screens show its sentence where the buttons would be. A photo offered as a document is turned away by its content, so one renamed to `.pdf` is caught too.
- **One gap in "no Mac-made backup is ever wrong" (4.2).** A hand-edited backup that leaves out a photo's renditions has them rebuilt on restore, and until mac-2 the Mac does that with the test-grade image code. A rotated photo restored that way gets a sideways rendition. Backups the app makes always carry their renditions, so this doesn't arise between your own devices.
- **Sharing (8.1.3).** Share, and "Open with another app" on a PDF, open the file in its usual app, which is Preview for a PDF. The copy named after the document and the Finder actions are still 8.3.12.
- **The version (8.1.6).** The packaging tool does refuse a version that starts with 0: the first macOS run stopped on it. Until 1.0.0 the bundle is `1.0.<build>`, as planned. Only Finder's Get Info shows that number.
- **The Java runtime (8.1.9)** carries four modules beyond the plugin's defaults, found by its `suggestRuntimeModules` task. The self-test asks the runtime about TLS directly.
- **Tests (9.2).** `ShellTest` holds the data-folder, single-copy, newer-collection and version checks. The 18 screenshots are at 420, 760 and 1280 dp wide.
- **CI (8.1.10).** As planned. The disk image is named `…-not-notarised.dmg` until 8.4.3.
- **Sleep (8.1.1).** `KeepScreenOn` holds idle sleep off with macOS's own `caffeinate`, tied to the app's process, so it needs no bridge.
- **Wording.** The Mac build still says "this phone" throughout (8.3.8).

**What has and hasn't been checked.** On Linux: every test, the real window opening to the welcome screen, and the packaged app passing its self-test. On an Apple silicon runner (macOS 15): the disk image builds, the app inside it passes the self-test, and its bundle has the right identity, version and a valid ad hoc signature. The app is 157 MB, and the download 93 MB. The iPhone workflow passed with the shared-code changes. Not yet checked anywhere: the app on a real Mac, including macOS 27, the file panels, and a real backup from a phone. That is yours to try, with appendix C's "Install and update" and "Moving a collection".

**mac-2 as built** (version 0.8.0). All of 8.2, except what is listed under "Still open" below:
- **The rules (8.2.1)** are in `shared/data/photos`: `PhotoFormat`, `Orientation`, `RenderPlan` and the `ImageCodec` interface, with `CodecImageProcessor` on top.
- **Location removal (8.2.2)** is `PhotoMetadata`, and every platform stores photos and photo documents through it. It works on a file's bytes before they are stored. Run on real photos from a Nikon camera and a Nokia phone, HEIF included, it removed the location and changed nothing else, as read back by a different library. No real iPhone HEIC has been through it yet.
- **The conformance suite (8.2.3)** is `PhotoConformanceTest`, on 31 small fixtures made by `tools/photo_fixtures.py`. CI also reads the cleaned fixtures back with Pillow, which shares no code with the app.
- **Android (8.2.4)** is `AndroidImageCodec`, and the Photos workflow runs the whole shared data suite on an emulator. Its first run found two things, both fixed:
  - **Android has no TIFF decoder.** Each codec now says which formats it reads (`ImageCodec.formats`), the suite holds it to that, and a format a device can't read is refused cleanly. A TIFF or raw file can be added on an iPhone or a Mac, not on Android.
  - **Android's decoder ignores a HEIC's "clean aperture".** A HEIC can't be coded smaller than 64 pixels a side or at an odd size, so such a picture is coded larger with a box (`clap`) saying which part is the picture. Android showed the padding too. The shared code now reads a HEIC's own boxes (`PhotoMetadata.heicLayout`): the cut, and the turning (`irot`, `imir`), which by the standard counts where EXIF's doesn't. Android's codec cuts and turns by that. A phone's photos have no such box; files from converters do.
- **Apple (8.2.5)** is `AppleImageCodec`, on ImageIO, in a new `appleMain` source set that the iPhone and a new `macosArm64` target both compile. It replaces `IosImageProcessor` and its UIKit drawing. The iPhone simulator runs the suite against it, and the macOS runner runs it natively.
- **The Mac bridge (8.2.6)** is the `macBridge` module: three C functions over `AppleImageCodec`, built as `libwatchesbridge.dylib` and loaded with JNA (`MacBridge` in `shared/data`'s `jvmMain`). The packaged app carries it among its resources and finds it at start-up; without one (Linux, or a run from the source tree) the plain-Java codec stands in. The macOS workflow builds it, runs the shared data tests through it on Java, and has the packaged app's self-test store a HEIC through it. It is Kotlin, as planned: the Swift fallback wasn't needed. **Verify** on the first signed build that notarisation accepts the library (8.4.3).
- **The Crop screen (8.2.7)** draws on a picture made by the app's own codec (`PhotoRepository.cropPreview`), not on the original as the image loader decodes it. An uncropped photo's display image is already that picture; otherwise one is made in the cache folder and removed when the screen closes. An original this device can't read says so, where the screen used to stay black.
- **One way in (8.2.8)** is `PhotoIntake`: stream the file into the store, tell its format from its bytes, keep it with its location removed or re-make it as a JPEG. A watch's photos and a document's photos both come in through it, with a rule each for which formats are kept. A picked photo is streamed from the picker, where it used to be read into memory whole. A document's photo can now be in any format the device reads, and the document picker offers those.
- **Photos already stored (8.2.9)** are gone through by `StoredPhotoCheck`: once on each device after the update, and again after every restore. A photo or photographed document that still carries a location, or is in a format that isn't stored, goes back through the way in. A recorded size that isn't the upright picture's is corrected, with the photo's renditions made again. It runs in the background from the app's first screen, notes the last photo it finished, and picks up there if it is stopped. Removing a location needs no decoder, so it works even on a photo the device can't show.
- **Photos on the Mac (8.2.10)** are on wherever the bridge is loaded, which is every packaged build. Adding from files, cropping, Lookup pictures and photos as documents all work as on the phones. There is no camera button. A build without the bridge still shows photos and says why it can't take new ones in.
- **Round trips (8.2.11), so far.** The macOS runner's disk ignores capitals in file names, as most Macs' do, and a test there restores a backup with two names that differ only by capitals. Its first run found a fault that was never the Mac's alone: a restore that met an existing file with different contents failed on every platform, from an overflow in the code that names the file put beside it. Fixed, with a test that reaches it everywhere. `ReferenceArchiveMaker` makes a backup with the Mac's image code on each macOS run. One of those is committed (`fixtures/archives/mac.zip`), and `ReferenceArchiveTest` restores it wherever the shared tests run: every row and file, no location anywhere, the Mac's renditions read by each platform's plain decoder, and each photo rendered again there to the same size and picture. That is "Mac to phone" and "crop parity" from 9.5. The other direction, backups made on a real iPhone and a real Android phone, needs the phones: see the fixtures' README.
- **A second opinion on location, each time a photo is stored.** After the shared code has removed a location, the platform's own reader is asked whether it still finds one: `ExifInterface` on Android, ImageIO on the iPhone and the Mac. If it does, the app keeps a JPEG made from the photo instead. The suite checks that each platform's reader does find a location in an ordinary photo, and finds none afterwards.
- **Checked in CI**, on the 0.8.0 commit (`08fd398`):
  - **Linux:** every shared test on the plain-Java codec, the desktop tests and screenshots, and the packaged app's self-test.
  - **An Apple silicon runner (macOS 15):** every shared data test natively on ImageIO, and again on Java through the bridge; the disk image; and the packaged app's self-test, which stores a HEIC through the bridge.
  - **The iPhone simulator:** every shared test, and the app building and passing its own tests and its launch check with the new image code.
  - **An Android emulator (Android 14):** every shared data test on Android's own decoders. That job doesn't run for a change of version alone; its run was on the commit before, with the same code.
  - On all four, the Mac-made backup restores and each photo rendered again comes out the size and the picture the Mac made.
- **Still open:**
  - **Yours to do.** Try the app on your Mac, with photos this time: add a few from Finder, a HEIC from your iPhone among them, crop one, back up, and restore that backup on a phone. And one backup from each phone, made from sample watches, for the test fixtures (9.5; the fixtures' README says where they go). No real iPhone HEIC has been through the location removal yet.
  - **Wide-gamut and very large photos** aren't in the suite (9.4), nor are AVIF and raw fixtures. Android and Apple both convert to sRGB as they decode; the photos to prove it are still to make.
  - **Notarisation of the bridge** (8.4.3) waits for the first signed build.
- **Differences from the plan:**
  - Plain Java has no metadata reader of its own, so on Linux the second opinion is the shared reader again. That only affects tests: the Mac asks ImageIO, through the bridge.
  - The bridge is a module of its own (`macBridge`), not a source set of `shared/data`: only what its three functions reach is linked into the library, which keeps the database code out of it.
  - The pass over stored photos keeps its place in a note in the cache folder, not in the marker that finishes a restore's aftercare. The restore only asks for a pass; the app runs it afterwards, so a restore doesn't wait for it.
  - That pass drops a crop when it corrects a photo's size, because the crop was drawn on a picture of another shape. Such a photo shows whole until it is cropped again.
  - A photo's location is still removed in memory, so a photo in a stored format is read whole once, to be cleaned. What streaming saves is the copy the picker made, and all of a file that is converted: a camera's raw file is never in memory. A stored-format photo over 128 MB is re-made as a JPEG instead.
  - The Crop screen has no image-loader decoder of its own, as 8.2.7 proposed. It shows a JPEG the codec made, which needed no per-platform code and goes through the path the suite already tests. The image loader now only ever opens the app's own JPEGs.

What was checked for the plan:

- **Libraries.** The published builds of every UI and data library at its pinned version, on Maven Central: Compose Multiplatform 1.12.1, Material 3 1.9.0, Material 3 adaptive 1.3.0, Navigation 3 1.1.2, Lifecycle 2.11.0, Koin 4.2.2, Coil 3.6.3, FileKit 0.16.0, Room 2.8.5, bundled SQLite 2.7.1 and Ksoup 0.2.6. Each one has a desktop build.
- **A trial build**, on Linux, thrown away afterwards. `shared/ui` was given a desktop target, six small platform functions and a wrapper around the camera button. The first compile had 14 errors, all of them the camera button in two files. With the wrapper, every screen compiled.
- **A trial run.** A small harness started the app's own wiring on the desktop (the real `dataModule`, `searchModule` and `uiModule`, Room in a temporary folder), made 12 sample watches with the app's own generator and photo pipeline, and drew `WatchesApp()` off-screen. All three designs rendered correctly, light and dark, at 1280 × 800 and at 420 × 820, with the bundled fonts, the navigation rail and the two-pane layout.
- Anything not yet confirmed is marked **verify**.

Contents

1. Summary
2. Where the Mac build stands
3. How the Mac app works
4. Import and export with the phones
5. One photo component for Android, iPhone and Mac
6. The same three designs
7. Decisions
8. What needs implementing
9. Tests
10. Signing, distribution and CI
11. Milestones
12. Risks
13. Not in this plan

Appendices: A, changes by file · B, build and release sketch · C, Mac checklist · D, alternatives considered

---

## 1. Summary

**The Mac app is the same app, not a new one.** Watches is Kotlin Multiplatform with Compose Multiplatform, and Compose has a supported desktop target that runs on the Mac. Three of the four shared modules (`core`, `data`, `search`) already build for it, because that is where their fast tests run. The fourth, `shared/ui`, compiled for it in the trial with one small change. So the plan is a Mac shell around the shared code, as `androidApp` and `iosApp` are shells, rather than a port.

**Backups already work in principle.** The backup engine is shared code, and its tests run on the desktop's runtime on every push (`BackupRepositoryTest`, `ArchiveInteropTest`). A backup made on either phone is read by the same code on the Mac, and the reverse. What import and export still need is everything around the engine (section 4):

1. **One photo component for all three devices** (section 5). Each platform has its own image code today, and no test checks that they agree. The desktop's is test-grade: it ignores a photo's rotation, leaves its location in, and can't read HEIC, which is what iPhones take. Android and iPhone have gaps of their own. The plan fixes the component once, for Android, iPhone and Mac, with one set of rules and one test suite. This is the largest single piece of work.
2. **Only formats every device can show.** A Mac will happily open TIFF or RAW files that a phone can't.
3. **The plumbing.** Where the collection lives on the Mac, the app's version in the backup, file dialogs, one copy of the app at a time.
4. **Tests with real backups** from each device, in both directions.

**The three designs come across unchanged.** Atelier, Instrument and Gallery are drawn by the app from shared code and bundled fonts, so nothing is redrawn for the Mac. The trial render confirms it. What the Mac adds is around them: the window's title bar, hover and keyboard focus, and the wide layout (section 6).

**What a Mac needs that a phone doesn't:** a menu bar and shortcuts, back without a swipe, right-click, scroll bars, drag and drop from Finder, a resizable window, and wording that says "this Mac". And what needs a bridge to macOS: HEIC photos, PDF pages, Touch ID and reminders.

**Three things to know:**
- **It needs an Apple silicon Mac** on macOS 13 or later, because Compose's desktop support lists only that and the bundled SQLite has no Intel Mac build. Yours is a current model on macOS 27, so that's settled (M1). macOS 27 is newer than anything the toolkit's documentation names, so the first build is also the check that it runs there.
- **There is a shortcut.** An Apple silicon Mac can run the iPhone app as it is, installed through TestFlight. It costs almost nothing and gives the same designs and backups, but it's a phone app in a window that expires every 90 days (3.6). It's worth having while the real Mac app is built, not instead of it.
- **Most of it can be built and checked without a Mac.** The desktop target runs on Linux, so I can build it, run its tests and render every screen here, as the trial did. Only packaging, signing and the macOS bridge need a macOS runner, and only the final feel needs your Mac.

**Size.** Four milestones, mac-1 to mac-4. Together they are about the size of the iPhone plan, plus the photo work that moves here from it. The first ends with Watches on your Mac restoring a phone backup.

**Decisions** are in section 7. The three that mattered most are made.

---

## 2. Where the Mac build stands

✅ ready · ⚠️ exists, with gaps · ❌ missing

| Area | Phones | Mac today | |
| --- | --- | --- | --- |
| Shared code builds for the desktop | n/a | `core`, `data` and `search` have a JVM target. `ui` compiled in the trial. | ⚠️ 8.1.1 |
| Database and search | Room on bundled SQLite, FTS4 | The same, used by the JVM tests. Apple silicon only. | ✅ M1 |
| Backup engine, ZIP, CSV | Shared | Runs and is tested on the JVM on every push | ✅ |
| ZIP decompression, free space | `java.util.zip`, zlib | `RawInflater.jvm.kt`, `FreeSpace.jvm.kt` | ✅ |
| HTTP for Lookup | OkHttp, Darwin | OkHttp, no cookies, no cache (`Engine.jvm.kt`) | ✅ |
| Connectivity | `ConnectivityManager`, `NWPathMonitor` | None | ❌ 8.1.3 |
| Data wiring (database place, files, version) | `androidDataModule`, `iosDataModule` | A test helper only | ❌ 8.1.3 |
| Photo renditions | Region decode with EXIF on Android; a full-size decode on iPhone | `JvmImageProcessor`: no orientation, no GPS removal, no HEIC or WebP | ❌ section 5, 8.2 |
| Showing originals (Crop) | Coil | Coil through Skia: no HEIC | ❌ 8.2.7 |
| Choosing and saving files | FileKit | FileKit's desktop dialogs: the Mac's own open and save panels | ✅ **verify** on a Mac |
| Camera | System camera | None on desktop. The only compile errors in the trial. | ❌ 8.1.2 |
| PDF viewer | `PdfRenderer`, PDFKit | None | ❌ 8.3.11 |
| Share | Share sheet | None | ❌ 8.3.12 |
| Reminders and notification permission | WorkManager, `UNUserNotificationCenter` | None | ❌ 8.4.2 |
| App lock | `BiometricPrompt`, `LAContext` | None | ❌ 8.4.1 |
| Keep awake, reduced motion, cold-start time | Per platform | None | ❌ 8.1.1 |
| The three designs, fonts, layouts | Shared | Rendered correctly in the trial | ✅ section 6 |
| Shell: window, menus, icon | Activity, SwiftUI app | None | ❌ 8.1.4 |
| Packaging and signing | APK, TestFlight | None | ❌ 8.1.9, 8.4.3 |
| Tests | JVM, Android host, iOS simulator | The shared JVM tests | ⚠️ 9 |

The Mac-specific code that exists today is about 90 lines, all in `jvmMain`.

---

## 3. How the Mac app works

### 3.1 Layers

```
Mac (Apple silicon, macOS 13 or later)
└── Watches.app
    ├── A launcher and its own Java runtime (nothing else to install)
    ├── desktopApp        the Mac shell (new): main(), one window, menu bar, shortcuts, drag and drop
    │     startKoin(dataModule, jvmDataModule, searchModule, jvmSearchModule, macPlatformModule, uiModule)
    │     Window { WatchesApp() }
    ├── shared/ui         every screen, view model, design and navigation: the same code as the phones
    ├── shared/data       Room on bundled SQLite, file store, backup engine
    ├── shared/search     Lookup sources and PoliteHttpClient (OkHttp)
    ├── shared/core       model, spec registry, units, care rules
    └── the Mac bridge    one small native library (new), built from Kotlin shared with the iPhone:
          ImageIO (photos, HEIC) · PDFKit · LocalAuthentication (Touch ID) · UserNotifications
          appearance · sleep
```

Compose draws every pixel itself, with Skia on Metal, as it does on the iPhone. That is why the designs match.

The bridge exists because the desktop target runs on Java, which can't call the Mac's own frameworks directly. It is one library with plain C functions, loaded through JNA (which FileKit already brings). Its code is Kotlin from a source set the iPhone build also uses, so the two Apple platforms share one implementation (8.2.5, 8.2.6). On Linux the bridge is absent and plain-Java fallbacks take over, so tests and screenshots run there.

### 3.2 What happens at launch

1. `main()` takes a lock file, so a second copy of the app activates the first instead of opening the same database.
2. Koin starts. As on the phones, nothing is built until a screen asks for it.
3. The window opens on the design's ground colour, at the size and place it had last time.
4. `WatchesApp()` loads the settings, primes the app lock, tidies up an interrupted restore and starts `ReminderSync`: the shared code, unchanged.

### 3.3 Where things live

- The database, photos, documents and source pages are in `~/Library/Application Support/Watches/`. That is outside iCloud Drive, and it stays when the app is dragged to the Bin (unlike a phone, where uninstalling deletes the data). Settings › Privacy gets "Show the collection folder in Finder".
- macOS encrypts it at rest when FileVault is on.
- Time Machine backs the folder up unless you'd rather it didn't (decision M5).
- Backups and CSV files go wherever you choose in the save panel.

### 3.4 Privacy on the Mac

- The app asks for one permission, and only on first use: **notifications**, when you switch on a reminder. It reaches files only through the open and save panels and through things you drag in, which macOS treats as your consent.
- Only `shared/search` may reach the network. The build check that enforces this will also look for Java's own networking (8.1.8).
- **No update check.** The app can't look for a new version, because that would be a network request outside Lookup. Updates are a download you choose to make (10.2).
- The on-Mac check that Watches only goes online during Lookup is a tool such as Little Snitch or LuLu, as PCAPdroid is on Android and the App Privacy Report on iPhone.
- The app is not sandboxed, because it is distributed outside the Mac App Store. It is signed with the hardened runtime and notarised by Apple (10.1).

### 3.5 How Mac behaviour maps onto the existing code

- **Window size.** The existing breakpoints do the work (`AppShell.kt:212–215`). Under 600 dp wide the window shows the phone layout with its bottom bar. From 600 dp it shows the navigation rail. From 840 dp it shows the list and a watch side by side. The trial rendered both ends.
- **Back.** There is no back gesture. Esc and ⌘[ send the same back event that every `BackHandler` already receives (the discard prompts, the lock screen), and every pushed screen already has a visible back button. **Verify** how much of this Compose's desktop target does unprompted.
- **Leaving the app.** A Mac app is rarely "in the background" the way a phone app is. For the app lock, leaving means the window is minimised or hidden, the display sleeps or the Mac locks. Having another window in front doesn't count. **Verify** the lifecycle states Compose reports for each.
- **Long jobs.** macOS doesn't suspend apps, so the iPhone's background problem (iPhone plan 5.3.6) doesn't arise. The app holds off idle sleep during a backup or restore, and asks before quitting in the middle of one.
- **Files.** The app reads and writes the chosen path with ordinary file streams. On a Mac, iCloud Drive is an ordinary folder, so the file coordination the iPhone needs (iPhone plan 5.3.5) isn't needed here.
- **Text size.** macOS has no system-wide text size for apps. View › Zoom scales the app's type instead (8.3.6).

### 3.6 The shortcut: the iPhone app on the Mac

Apple silicon Macs run iPhone apps. Once the iPhone build is on TestFlight, App Store Connect has a setting on the tester group for testing iPhone and iPad apps on Macs with Apple silicon, and TestFlight for Mac then installs the same build. **Verify** on the first try: Apple's help describes the setting, and its forums have reports of builds that showed on the Mac but wouldn't install until it was switched on.

- **For:** no new code, and by definition the same designs and the same backups.
- **Against:**
  - It's a phone app in a window: no menus, no shortcuts, no drag and drop. While the app is iPhone-only (iPhone plan D4), the window is the size of a phone.
  - It stops opening after 90 days until a new build arrives, like the iPhone build.
  - It keeps its own collection, separate from the Mac app's. A backup moves the collection between them.

It's a setting rather than a project, so it's worth switching on if you'd like Watches on the Mac before mac-1 is done.

---

## 4. Import and export with the phones

### 4.1 What already works

- **One engine.** `BackupRepository`, the ZIP reader and writer, the merge rules and the CSV exporter are shared code. The Mac runs the same lines as both phones, with the same format version (1) and schema version (1). `docs/ARCHIVE_FORMAT.md` already describes the Mac's backups.
- **Tested where the Mac runs.** The backup and restore tests run on the JVM on every push. That includes archives opened by an independent ZIP tool, 70,000-entry archives, and an archive re-zipped the way the Mac's Archive Utility does it (`ArchiveInteropTest`).
- **The dialogs.** "Back up now", "Restore from a backup" and "Export CSV" use FileKit's save and open dialogs, which have desktop versions. The streaming adapters in `FileStreams.kt` compiled unchanged.
- **Safe restores.** The SHA-256 checks, the single transaction and the journal that tidies up an interrupted restore at the next launch are shared, and `WatchesApp()` already runs the tidy-up.

### 4.2 What a Mac-made backup needs to be right on a phone

A backup carries each photo's original file, its two renditions, its size and its crop. All three devices have to agree on what those mean. Section 5 covers the photos. The rest is plumbing:

| Rule | The desktop code today | Work |
| --- | --- | --- |
| Photos follow one contract: rotation applied, location removed, formats every device can show, renditions in sRGB (5.3) | `JvmImageProcessor` is test-grade. It ignores rotation (`JvmImageProcessor.kt:17`), removes no location (`JvmImageProcessor.kt:25`) and can't read HEIC. Android and iPhone have gaps too (5.1). | 8.2 |
| HEIC documents are converted to JPEG when stored | Java can't read HEIC | 8.2.5 |
| The manifest's `app` names the version and platform | No version is wired in | 8.1.6 |
| `device.*` settings never travel (`Daos.kt:270`) | Shared. New Mac-only settings (window size, zoom) must use `device.*` keys. | 8.3.7 |
| One process owns the database and the restore journal | Nothing stops a second copy of the app | 8.1.4 |

Until the photo component is done (mac-2), the Mac app doesn't offer adding or cropping photos, or adding photo documents (8.1.7). Everything it can do, it does to the shared rules, so no Mac-made backup is ever wrong on a phone.

### 4.3 What a phone-made backup needs on the Mac

| In the backup | On the Mac |
| --- | --- |
| Rows, settings, source pages | ✅ Shared code |
| Photo renditions (JPEG) | ✅ Shown by Coil. The collection, the detail screen and the viewer work from mac-1. |
| HEIC originals from an iPhone | ❌ Needed to crop a photo, and to rebuild a rendition that came without one. Java and Skia can't decode HEIC. macOS can, so the bridge does it (8.2.5, 8.2.7). |
| WebP originals | ⚠️ Skia reads them and Java's own image reader doesn't. The Mac image code goes through macOS, which does. |
| PDFs | ❌ No viewer yet. "Open in Preview" from mac-1, pages in the app from 8.3.11. |
| A backup that was unzipped, or re-zipped | ✅ Safari unzips downloads unless told not to. A folder can't be restored, but compressing it again in Finder gives an archive the app reads. This is the case `archivesRezippedOnAComputerStillRestore` tests. |
| A backup from a newer app version | ✅ Refused with "made by a newer version of Watches". The three apps share one schema version, so they are released together (10.2). |

### 4.4 Getting the file across

| Between | Routes |
| --- | --- |
| iPhone and Mac | AirDrop. iCloud Drive, which is a folder in Finder. A USB-C drive. |
| Android and Mac | Google Drive in a browser. A USB-C drive. A USB cable also works, but macOS has no built-in way to browse an Android phone, so it needs an extra tool. |
| Mac conveniences (8.3.13, 8.3.9) | Drop a `.zip` on the window to see what it holds. "Open With › Watches" in Finder. |

Then Settings › Backup & restore, the same screens as on the phones.

### 4.5 What travels, and the designs

- **Replace** brings the collection and its settings. The settings include the design, the accent and Light, Dark or System (`ui.direction`, `ui.accent`, `ui.theme`), so the Mac looks the way the phone did.
- **Merge** keeps the Mac's settings.
- The app lock, the last-backup date, and the Mac's window size and zoom stay on the Mac.
- Each watch's reminder switch travels with the watch. Notifications are allowed per device, so restoring a phone's backup doesn't make the Mac start notifying (decision M8).

### 4.6 Using three devices

This is how the app already behaves between two phones, but a Mac makes it matter more:

- **Merge never deletes.** A watch deleted on the Mac comes back when a phone's backup is merged in. To carry a deletion across, restore with Replace.
- **The more recently edited side wins** each watch, by the device's clock.
- The simplest habit is one direction at a time: back up where you last edited, and restore everywhere else.

Carrying deletions through a merge would need a schema change on all three apps. It isn't in this plan (section 13).

### 4.7 CSV

Shared code, one way, already tested on the JVM. It is UTF-8 with a byte-order mark, so Numbers and Excel on the Mac open it correctly.

---

## 5. One photo component for Android, iPhone and Mac

A backup carries each photo's original file, its two renditions, its size and its crop. For a collection that moves between three kinds of device, every device has to treat those the same way. Today they don't quite, and nothing checks. This section fixes the photo component for all three platforms, not only for the Mac. It takes over 5.3.1 to 5.3.3 of the iPhone plan.

### 5.1 Where it stands

Each platform has its own image code behind one interface (`ImageProcessor`):

| | Android (`AndroidImageProcessor`) | iPhone (`IosImageProcessor`) | Desktop (`JvmImageProcessor`) |
| --- | --- | --- | --- |
| Rotation | ✅ All eight EXIF cases, by its own maths | ✅ Left to `UIImage` | ❌ Ignored |
| Location removed | ⚠️ From JPEG, PNG and WebP. A HEIC keeps its location. | ⚠️ From whatever ImageIO can rewrite. A WebP probably keeps its location (**verify**). | ❌ Nothing removed |
| When removal fails | Silent | Silent | n/a |
| HEIC | ⚠️ Decoded by Android. A rotated HEIC has never been tested, and HEIC stores rotation differently from JPEG. | ⚠️ Renditions work. The Crop screen probably can't show one (iPhone plan 5.3.2). | ❌ |
| Large photos | ✅ Decodes only the region needed, subsampled | ❌ Decodes the whole picture: about 190 MB for a 48 MP photo | ❌ Whole picture |
| Turning a crop into pixels | Rounds each edge, subsamples by a power of two, then scales | Works in fractions and rounds the result | Rounds the corner and the size |
| Colour | Whatever the decoder returns (**verify** for wide-gamut iPhone photos) | The device's default (**verify**) | Unchecked |
| Formats let in | Anything Android decodes | Anything iOS decodes | JPEG and PNG |
| Tests | None for rotation, location, HEIC or crops | None | None |

The effects:
- **A crop can land slightly differently** on each platform, and rendition sizes can differ by a pixel. That is harmless today, but nothing stops it growing.
- **A location can leave a device in a backup**: from Android in a HEIC, and probably from an iPhone in a WebP. The app's promise is that it never does.
- **A photo one device accepts can be one another can't show**, because each accepts whatever its own system decodes.
- **The Crop screen** shows the original through a different decoder on each platform, separate from the one that cuts the crop.
- **Nothing would catch a mistake.** The shared tests use a plain coloured rectangle. No test on any platform has a rotated photo, a photo with a location, a HEIC or a real crop.

### 5.2 The fix

1. **The rules move into shared code.** What is the same everywhere is written once, in `shared/data`:
   - *Formats.* Recognised by content. JPEG, PNG, WebP and HEIC are stored as they are. AVIF, TIFF, GIF, BMP and RAW are converted to a full-size JPEG as they come in (M6). The list is the same on every platform.
   - *Geometry.* One function turns a photo's size, rotation, crop and target size into the exact pixel rectangle to cut and the exact size to write. Every platform uses its numbers, so a crop means the same pixels everywhere. Android's rotation maths (`toRaw`) moves here and gets tests.
   - *Location.* Removed by shared code, in place, without re-encoding the picture, for all four stored formats (8.2.2). The file is then read back with the platform's own reader to confirm it has gone. If it can't be removed, the photo is stored as a JPEG made without it. An original whose location couldn't be removed is never kept.
2. **The platform part shrinks to a codec.** Each platform only reads a photo's size and rotation, decodes a rectangle at a size, and writes a JPEG in sRGB. There are two real codecs, not three:
   - **Android's**, on the system's decoders.
   - **Apple's**, on ImageIO, written once in Kotlin and used by both the iPhone and the Mac (8.2.5). It decodes at reduced size, so it also fixes the iPhone's memory problem.
   - A plain-Java codec stays for tests on Linux. It isn't shipped.
3. **One test suite that every codec must pass** (9.4). The same photos and the same expected results run on Android, on the iPhone simulator, on a Mac and on Linux. This is what keeps the three in step from then on.
4. **The Crop screen uses the same codec** to show the original, so what you frame is what gets cut, on every platform.
5. **One way in.** A photo from the library, the camera, a file dropped on the Mac, a picture saved from Lookup or a photographed document all pass through the same steps: recognise, convert, remove the location, measure, store, render.
6. **Photos already stored are checked once.** After the update, each device goes through its originals and removes any location the old code left. The same pass runs over the originals a restore brings in.

### 5.3 The contract

This is what the tests assert, and what `docs/ARCHIVE_FORMAT.md` will say about photos:

- **Originals** are JPEG, PNG, WebP or HEIC, kept byte for byte apart from the location.
- **No stored file carries a location**, in EXIF or in XMP. The capture date stays.
- **Width, height and crop** are in display orientation: the picture as a person sees it.
- **A crop** is cut by the shared geometry. The same crop gives the same output size on every platform, to the pixel.
- **Renditions** are JPEG in sRGB, 8 bits a channel, upright, with no metadata. They are at most 2560 px and 640 px on the long edge, and never enlarged.
- **Transparent areas** come out black, as on all three today.
- **Pictures with several frames** (a GIF, a Live Photo) use the first.
- **A file that can't be decoded** fails with a clear message and leaves nothing behind.
- **Colours** match across platforms within a small tolerance, because each system has its own decoder and JPEG writer. Sizes and regions match exactly.

### 5.4 What changes on each platform

| Platform | Change | Size |
| --- | --- | --- |
| Shared | Formats, geometry, location removal, the intake steps, the conformance suite | L |
| Android | Uses the shared geometry and location removal. Fixes whatever the suite finds: a rotated HEIC and wide-gamut colour are the likely ones. A new CI job runs the suite on an Android emulator, against Android's real decoders. | M |
| iPhone | The Apple codec replaces `IosImageProcessor`. The Crop screen can show a HEIC. | L, shared with the Mac |
| Mac | The same Apple codec, reached through the bridge (8.2.6) | M |

---

## 6. The same three designs

### 6.1 Why they match

A design is a `DirectionStyle`, a `WatchTypography` and a `WatchColors` palette in `shared/ui/.../theme`. Components read those tokens and never ask which platform they're on. The fonts are bundled files, and Skia draws them. None of it is platform code, so there is nothing to port. Settings › Appearance, with its live specimens, is the same screen.

The trial drew the Collection screen in all three designs, light and dark. The masthead rule, the accent dash, the catalogue numbers, the minute track with its lume pip, the zero-padded numbering, and the soft cards and pills were all there, in the right type.

`DesignContrastTest` is shared, so it will also run for the desktop target.

### 6.2 What the Mac adds around them

| Item | Detail | § |
| --- | --- | --- |
| Title bar | A standard Mac title bar is a grey or dark strip above the design. Recommended: a transparent title bar, so each design's ground colour runs to the top edge, with headers moved clear of the window buttons. The headers already pad for the status bar, and that padding becomes the hook. | 8.3.4 |
| System Light and Dark | "System" follows macOS, and changes when macOS does. **Verify** that Compose reports the change live. | 8.3.4 |
| Forced Light or Dark | The window buttons, menus and file dialogs follow the app's choice rather than the system's. This is the Mac half of iPhone plan 5.2.4. | 8.3.4 |
| Hover and keyboard focus | Phones have neither. Each design gets a hover and a focus look in its own language: a hairline for Atelier, a 1 dp frame for Instrument, a soft tint for Gallery. They become new `DirectionStyle` tokens, so components still don't ask which design is active. | 8.3.3 |
| Scroll bars | Thin, drawn in each design's colours, shown while scrolling. | 8.3.3 |
| App icon | The same watch at 10:10, drawn into the Mac's rounded-square icon shape by the tool that draws the iPhone icon. | 8.1.9 |

### 6.3 The wide layout

The trial showed the one place the designs need work, and it's in shared code rather than Mac code:

- At 1280 dp the list pane is about 360 dp wide, and the rest is the watch, or "Choose a watch". On a Mac the collection is squeezed into a phone-wide column beside a large empty pane. Atelier's tall tiles fit only one to a row there.
- The rail's label reads "COLLECTIO" in Atelier: tracked capitals don't fit the 88 dp rail (`AppShell.kt:845`). An Android tablet would show the same.

Options, for you to choose from rendered screenshots in mac-3:
- a wider list pane that grows with the window, showing two to four columns;
- the grid across the whole window until you pick a watch, then list and watch side by side;
- the grid across the whole window, with the watch as a page of its own.

The designs also need a maximum width for forms and settings, so a field doesn't stretch across a 27-inch display.

---

## 7. Decisions

Made on 23 Nov 2026:

| # | Decision | Chosen |
| --- | --- | --- |
| M1 | Which Mac | **A current Apple silicon Mac on macOS 27.** Apple silicon is required: Compose's desktop support lists only "macOS 13 arm64", and the bundled SQLite ships no Intel Mac build. The app is built to run on macOS 13 and later, and tested on 27. |
| M3 | How builds reach your Mac | **From the GitHub release, beside the APK.** Notarised once the certificate exists (10.1). Until then a build needs one extra step to open. It never expires, and it needs no review. |
| M9 | Order with the iPhone milestones | **Start now, with mac-1.** Later milestones share work with the iPhone ones (section 11), and each shared piece is built once. |
| M10 | The photo component | **Fixed for Android, iPhone and Mac together, in this plan** (section 5). It takes over 5.3.1 to 5.3.3 of the iPhone plan. |

Going ahead on these recommendations unless you say otherwise:

| # | Decision | Options | Recommendation |
| --- | --- | --- | --- |
| M2 | What kind of Mac app | A desktop app from the shared code · the iPhone app run on the Mac · both | **The desktop app**, and the iPhone app as early access if you'd like it sooner (3.6). |
| M4 | Names | Bundle ID `io.github.tunlezah.watches.mac`; "Watches" in the Dock | **Keep them for good**, like the Android key and the iPhone bundle ID. The ID differs from the iPhone's so that both builds can be on one Mac. |
| M5 | Time Machine | Collection folder included · excluded | **Included.** Time Machine is your own disk, and it covers a failed Mac for free. The phones exclude theirs from cloud backups, which is a different case. Say if you'd rather exclude it. |
| M6 | Photos in formats a phone can't show (TIFF, GIF, BMP, AVIF, RAW) | Convert to JPEG at full size when added · refuse them | **Convert.** It matches the iPhone plan's rule for ProRAW (5.3.2). The file you added stays where it was. |
| M7 | App lock on the Mac | Touch ID, Apple Watch or the Mac's password · leave it out | **Include it**, in mac-4. It's device-only either way, so it doesn't affect backups. |
| M8 | Reminders on the Mac | Notifications at 9 am, as on the phones · the Service tab only | **Include them**, in mac-4. You allow notifications per device, so a watch reminds you only on the devices where you've allowed it. |

---

## 8. What needs implementing

Sizes: **S** under half a day · **M** about a day · **L** two to three days. Section 9 collects the tests.

### 8.1 It runs, and reads your backup (milestone mac-1)

**8.1.1 A desktop target for `shared/ui`** (S)
- Add `jvm()` beside the Android and iOS targets, with Compose's desktop runtime and the Swing coroutine dispatcher.
- Six functions in `jvmMain`:
  - `encodeJpeg`, through Skia as on iOS;
  - `processUptimeMillis`, from the process's start time;
  - `systemAnimationsEnabled`, from the bridge (true until 8.2.6);
  - `KeepScreenOn`, which holds off idle sleep;
  - `openPdf`, null until 8.3.11;
  - `coverWindowProperties`, for the lock screen.
- The shared tests in `shared/ui` then run for this target as well.

**8.1.2 The camera button** (S)
- FileKit's camera launcher exists only on phones (`PhotosScreen.kt:81`, `DocumentScreens.kt:238`), and those two lines were the trial's only compile errors.
- Change: a `rememberCameraLauncher` with a version per platform. Where there's no camera to open, "Camera" and "Photograph a paper copy" aren't shown.

**8.1.3 Wiring** (S)
- `jvmDataModule(root)`: the database and files folder under `root`, the image processor and `AppInfo("0.7.0", "macOS 15.1")`.
- `jvmSearchModule()`: a `Connectivity` that reports offline only when the Mac has no network interface up. Requests decide the rest, as on iOS before the first update.
- `macPlatformModule()`: reminders, notification access, the app lock and sharing. In mac-1 the first three report "not available", and Settings says so.

**8.1.4 The shell** (M)
- A new module, `desktopApp`: `main()`, one window with a minimum size, the standard application menu (About, Settings…, Quit), and the app's ground colour before the first frame.
- One copy at a time, by a lock file in the collection folder. A second launch brings the first to the front.
- One window: the app's singletons assume one, as on iPhone.
- Quitting during a backup, restore or export asks first. A restore that has started saving finishes regardless, as now.
- An unexpected error shows a message with the detail to copy. Nothing is sent anywhere.

**8.1.5 A collection from a newer build** (S)
- Nothing stops someone dragging an older Watches.app over a newer one, and Room refuses to open a database newer than itself.
- Change: check the database's version before opening it, and show "This collection was made by a newer version of Watches" rather than failing.

**8.1.6 One version number** (S)
- A Gradle task turns `version.xcconfig` into a `BuildInfo` constant for the shell and into the package's version, so all three apps report the same version.
- Older versions of the packaging tool refuse a version that starts with 0, and the app is at 0.6.0. **Verify** on the first package. If it's refused, the Mac bundle reads `1.0.<build>` in Finder until the app reaches 1.0.0, while About and the backup manifest show the real version.

**8.1.7 Nothing offered that isn't right yet** (S)
- Until mac-2, the Mac doesn't offer adding, replacing or cropping photos, or adding photo documents. The buttons say that this arrives in the next version.
- PDF documents can be added. Everything else works.

**8.1.8 Network isolation on the JVM** (S)
- `checkNetworkIsolation` (`build.gradle.kts:18`) looks for Ktor, OkHttp, Coil's network module and Apple's networking APIs. Java can reach the network without any of them.
- Change: the scan also fails on `java.net` connections, `HttpURLConnection`, `java.net.http` and sockets outside `shared/search`. `desktopApp` joins the modules that must be network-free.

**8.1.9 Packaging** (M)
- Compose's Gradle plugin builds `Watches.app` and a disk image with its own Java runtime, on a macOS runner (it can't be built from Linux).
- The Java runtime is trimmed to the modules the app uses. A missing module shows only when the packaged app runs. The usual one is the TLS module, which would break Lookup in the packaged app and nowhere else. The self-test in 9.3 covers it.
- An `.icns` icon, drawn by an extended `tools/RenderIosAppIcon.java`.
- `Info.plist`: the bundle ID, the minimum macOS, and `.zip` as a file type the app can open (not as its default opener).
- Signed ad hoc for now. Developer ID and notarisation are in 8.4.3.

**8.1.10 CI** (S)
- Linux, on every push: the desktop compile, the desktop tests and screenshots, and a Linux package launched under a virtual display as a smoke test.
- macOS, on Mac changes, tags and on demand: the bridge's tests, the disk image, and its self-test.

### 8.2 One photo component, on all three (milestone mac-2)

Section 5 says why. This milestone changes Android and iPhone code as well as the Mac's.

**8.2.1 The rules, in shared code** (M)
- `PhotoFormat`: detection by content. It replaces `ImageFormats.extension` (`ImageProcessor.kt:29`), which knows four formats and calls everything else `.img`. It adds AVIF, TIFF, GIF, BMP and DNG, and marks each format "stored" or "converted".
- `RenderPlan`: the geometry in 5.2. It takes the raw size, the rotation, the crop and the longest edge. It gives the rectangle to cut and the size to write, in whole pixels.
- `ImageCodec`: the smaller platform interface. `ImageProcessor` becomes a shared class on top of it, so `PhotoRepository` and `DocumentRepository` don't change.

**8.2.2 Location removal, in shared code** (L)
- Shared Kotlin finds the EXIF block in each container (a JPEG segment, a PNG or WebP chunk, a HEIC item) and overwrites the GPS entries where they are. It does the same for location fields in an XMP block. The file's length doesn't change, so nothing else in it moves.
- The platform's own reader then confirms the location has gone. If it hasn't, or the file can't be parsed, the photo is stored as a JPEG made without metadata.
- It replaces `stripLocation` on every platform. That closes the HEIC gap on Android and the WebP gap on iPhone, and stops a failure being silent.

**8.2.3 The conformance suite** (M)
- The photos and expected results in 9.4, as shared test code. Small binary fixtures are generated into a Kotlin source at build time, the way the Lookup fixtures are, so the same tests read them on every platform.
- It runs against the plain-Java codec on Linux first, which proves the suite itself.

**8.2.4 Android to the suite** (M)
- `AndroidImageProcessor` becomes `AndroidImageCodec`, on the shared geometry. Its region decoding stays, because it is the right way to cut a small crop from a large photo.
- A new CI job runs the suite on an Android emulator. Robolectric can't stand in here, because the point is Android's real decoders.
- Fix what it finds. The likely ones are a rotated HEIC and the colour of wide-gamut photos.

**8.2.5 One Apple codec, for iPhone and Mac** (L)
- A new `appleMain` source set in `shared/data`, compiled for the iPhone and for the Mac (`macosArm64`). ImageIO and CoreGraphics are the same on both.
- The pipeline:
  - size and rotation from the image's properties, without decoding;
  - a downsampled, already-rotated decode, sized so the cut still has enough pixels;
  - the cut from the shared geometry;
  - a JPEG written in sRGB.
- It replaces `IosImageProcessor` and its UIKit drawing, and with it iPhone plan 5.3.1.
- Linux compiles it for both platforms on every push, as it does the iPhone code today. Its tests run natively on the macOS runner, with no simulator, and on the iPhone simulator.

**8.2.6 The Mac bridge** (M)
- The desktop app runs on Java, so it reaches the Apple codec through a small native library: the Kotlin from 8.2.5, built as a Mac dynamic library with C-callable functions and loaded with JNA.
- A Kotlin `MacBridge` interface in the shell, with the JNA implementation and a fallback for Linux.
- The first task is a one-function trial on the macOS runner, because a Kotlin/Native library inside a Java process is uncommon. The fallback is the same functions in about 150 lines of Swift. The Java side is the same either way.
- **Verify** that the packaging step signs the library, and the native libraries inside JNA's and Skia's jars, in a way notarisation accepts. The plugin is known to handle the jars.

**8.2.7 The same picture on the Crop screen** (M)
- The Crop screen loads the original with Coil (`CropScreen.kt:100`), through each platform's default decoder. On the iPhone and the Mac that decoder can't read HEIC.
- Change: a Coil decoder that asks the codec for the picture at the size shown, upright. It is installed on the iPhone and the Mac. On Android, Coil's own decoder is checked by the suite, and replaced if it disagrees with the codec.
- This is iPhone plan 5.3.2, done once for both Apple platforms.

**8.2.8 One way in** (M)
- `PhotoIntake` in shared code: the steps in 5.2. The library picker, the camera, the Mac's drop and paste (8.3.13), Lookup pictures and document photos all use it.
- Pickers offer the stored formats and the ones that are converted (M6).
- It streams each file into the file store, where `PhotoImporter` reads it into memory whole today (`PhotoImporter.kt:38`). This is iPhone plan 5.3.3.

**8.2.9 Photos already stored** (S)
- A one-time pass on each device after the update, and after each restore. It checks every original for a location, removes it, and corrects the recorded size if the old code got it wrong.
- It runs in the background, a few photos at a time, and picks up where it stopped. It uses the marker that already finishes a restore's aftercare.
- A cleaned original has a new hash, so the next backup carries the cleaned file.

**8.2.10 Switch photos on, on the Mac** (S)
- Remove the limits from 8.1.7.

**8.2.11 Round trips** (M)
- The cross-platform tests in 9.5.

### 8.3 Documents, and like a Mac app (milestone mac-3)

**8.3.1 Menus and shortcuts** (M)
- **File:** New Watch ⌘N, Look Up… ⌘L, Back Up Now…, Restore from a Backup…, Export CSV…, Close ⌘W.
- **Edit:** the standard items, and Find ⌘F, which focuses the collection's search.
- **View:** the four tabs ⌘1–⌘4, Grid or List, the design, Light, Dark or System, Zoom, Enter Full Screen.
- **Go:** Back ⌘[.
- **Watches:** Settings… ⌘,.
- Items are disabled while a form is open, by the rule that already hides the tabs (`AppShell.kt:197`), and while the app is locked.

**8.3.2 Back without a gesture** (S)
- Esc and ⌘[ send the back event from the window's root. A changed form still shows its discard prompt, and the lock screen still swallows it.

**8.3.3 The pointer** (M)
- Hover and focus looks per design (6.2), and a visible focus ring for keyboard use.
- Right-click wherever a long press does something: copying a spec value (`SpecSheetView.kt:132`), and a tile's actions. ⌘C copies the focused value.
- Scroll bars.
- Photos reorder by dragging straight away, not after a long press.
- The viewer zooms with ⌘-scroll and a double-click. **Verify** whether trackpad pinch reaches Compose on the desktop.

**8.3.4 The window and the chosen theme** (M)
- The transparent title bar and the top inset (6.2).
- A common hook from `WatchTheme` tells the platform which appearance the app is in. The Mac sets the application's appearance through the bridge, so the window buttons, menus and file dialogs match. iPhone plan 5.2.4 uses the same hook.
- Live changes when macOS switches between Light and Dark.

**8.3.5 The wide layout** (M)
- The option chosen in 6.3, the rail's label, and maximum widths for forms and settings.
- It is shared code, so Android tablets and landscape phones get it too.

**8.3.6 Zoom** (S)
- ⌘+, ⌘− and ⌘0 scale the app's type from 0.85 to 1.5, kept as a device setting. The designs are already tested at 2.0 on Android.

**8.3.7 Remembering the window** (S)
- Its size and place, and the zoom, stored under `device.*` keys so they never travel in a backup.

**8.3.8 Wording and licences** (M)
- About 35 strings say "phone" ("Nothing on this phone was changed", "Unlock with your fingerprint, face or phone PIN").
- iPhone plan 5.2.10 introduces one `PlatformText` per platform. The Mac adds its own: "this Mac", "Touch ID or your Mac's password", and "your Mac, iCloud Drive or an external drive" as places for a backup.
- Two of those strings are in `shared/data` (`BackupRepository.kt:86, 88`). They move to the UI layer, where the wording lives.
- Licences (`InfoScreens.kt:38`): the Mac build adds the Java runtime (GPL 2 with the Classpath exception, which allows bundling), JNA, OkHttp, and Skia and Skiko. Android-only libraries drop off the Mac's list.

**8.3.9 Finder and the Dock** (S)
- "Open With › Watches" for a `.zip`, and a `.zip` dropped on the Dock icon, open the restore preview.
- "Show the collection folder in Finder" on the Privacy page.

**8.3.10 Accessibility and performance** (M)
- VoiceOver: Compose's desktop target exposes its semantics to macOS. **Verify** by ear with the headings, switches and spec rows.
- Every action reachable from the keyboard.
- Reduce Motion, read through the bridge.
- With 500 sample watches: cold start, search, opening a watch and scrolling, measured in Diagnostics as on the phones.

**8.3.11 PDF pages** (S)
- `openPdf` on the Mac, through the bridge: PDFKit draws a page straight into a bitmap exactly `widthPx` wide, and the pixels go to Skia. It is the same rendering as the iPhone's.
- "Open in Preview" stays beside it.

**8.3.12 Instead of the share sheet** (S)
- `FileSharer` on the Mac copies the document to a temporary file named after its title, as iPhone plan 5.2.5 does.
- The viewer offers Open in Preview, Show in Finder and Save a copy….

**8.3.13 Drag, drop and paste** (M)
- Drop photos on a watch's Photos screen, and files on its Documents screen.
- Drop a `.zip` anywhere to open the restore preview.
- Paste a picture from the clipboard as a photo.
- Dropped and pasted files go through the same intake as picked ones (8.2.8).

### 8.4 Lock, reminders and release (milestone mac-4)

**8.4.1 App lock** (M)
- `MacDeviceAuth`, through the bridge: `LAContext` with the device-owner policy, which is Touch ID, an Apple Watch or the Mac's password.
- The shared `AppLock` rules, unchanged. "Leaving" follows 3.5.
- The Mac's counterpart to Android's blank recents card: the window is excluded from screenshots and screen sharing while the lock is on. **Verify**.
- Wording from `PlatformText`.

**8.4.2 Reminders** (M)
- `MacReminders` and `MacNotificationAccess`, through the bridge to `UNUserNotificationCenter`. macOS delivers a scheduled notification even when the app isn't running.
- They use the 9 am planner, the "not asked, allowed, denied" states and Diagnostics' one-minute test reminder from iPhone plan 5.2.1, 5.2.2 and 5.2.11. Whichever platform comes first builds them in common code.
- The bridge installs the notification delegate in `main()`, so a click that launches the app still opens the watch's service history through `DeepLinks`.
- **Verify** that the notification centre works in an app packaged this way. It needs the process to be the bundle's own, and it is.

**8.4.3 Signing and notarisation** (M, mostly your setup in 10.1)
- A Developer ID Application certificate in a temporary keychain on the runner, then `notarytool` and `stapler`.
- Without the secrets, the workflow still builds and checks an ad hoc signed disk image, so the release path is always tested. The Android and iPhone workflows work the same way.

**8.4.4 Docs and release** (S)
- A Mac section in the README: installing, updating, moving a collection between devices.
- CHANGELOG entries, and the Mac added to `docs/ARCHIVE_FORMAT.md`'s first line.

---

## 9. Tests

### 9.1 Layers

| Layer | Runs on | Covers |
| --- | --- | --- |
| T1 Shared tests (`commonTest`) | Linux, on the JVM. This is the Mac's own runtime. | Logic, repositories, backup and restore, parsers, HTTP politeness, app-lock rules, colour contrast, and the photo rules: formats, geometry, location removal |
| T2 Desktop tests (`desktopApp`) | Linux | The desktop Koin graph, Compose UI tests with keys and pointer, the shell's rules |
| T3 Desktop screenshots | Linux | Every design, light and dark, at three window widths |
| T4 Photo conformance (9.4) | Linux, an Android emulator, the iPhone simulator, the macOS runner | Every codec against the same photos and the same expected results |
| T5 The bridge and the packaged app | The macOS runner | The bridge's functions; a self-test inside the real bundle; the signature and the notarisation |
| T6 Mac checklist | Your Mac, with your phones | What CI can't do (appendix C) |

T1 already exists. T2 and T3 are where this plan differs most from the iPhone one: the Mac's user interface can be exercised and drawn on Linux, on every push, without a Mac. T4 is new for all three platforms.

### 9.2 New tests

| Test | Layer | Proves | § |
| --- | --- | --- | --- |
| `DesktopDependencyGraphTest` | T2 | Every desktop definition resolves (Koin's `verify()` works on the JVM) | 8.1.3 |
| `SingleInstanceTest` | T2 | A second start doesn't open the database | 8.1.4 |
| `NewerDatabaseTest` | T2 | A newer database gives the message, and isn't touched | 8.1.5 |
| `VersionTest` | T2 | The shell's version matches `version.xcconfig` | 8.1.6 |
| `NetworkIsolation` (extended) | Linux | Java networking appears only in `shared/search` | 8.1.8 |
| `DesktopScreenshotTest` | T3 | The screens the Android screenshot test covers, drawn by the desktop target | 6 |
| `BackKeyUiTest` | T2 | Esc pops a screen; on a changed form it shows the discard prompt; on the lock screen it does nothing | 8.3.2 |
| `MenuStateTest` | T2 | Menu items are disabled with a form open and while locked | 8.3.1 |
| `DropTargetUiTest` | T2 | A dropped `.zip` opens the preview; a dropped photo goes through the import checks | 8.3.13 |
| `ZoomTest` | T2 | The zoom applies and is kept as a device setting | 8.3.6 |
| `DeviceSettingsStayTest` | T1 | Window and zoom settings aren't in a backup, and survive a Replace | 8.3.7 |
| `PlatformTextTest` (extended) | T1 | Every text exists for the Mac; no Mac screen says "phone" | 8.3.8 |
| `PhotoFormatTest` | T1 | Each format is recognised by its content, and marked stored or converted | 8.2.1 |
| `RenderPlanTest` | T1 | The same rectangle and output size for every rotation, crop and target size; never enlarged; never empty | 8.2.1 |
| `LocationRemovalTest` | T1 | A location in EXIF or XMP is gone from JPEG, PNG, WebP and HEIC; the file's length and picture data are unchanged; the capture date stays; a file it can't parse is reported, not kept | 8.2.2 |
| `PhotoConformanceTest` | T4 | Every codec passes the suite in 9.4 | 8.2.3, 8.2.4, 8.2.5 |
| `CropPreviewTest` | T4 | What the Crop screen shows is what the codec cuts, including for a rotated HEIC | 8.2.7 |
| `PhotoIntakeTest` | T1 | Picked, dropped and Lookup pictures and document photos take the same steps; a failed step leaves nothing behind | 8.2.8 |
| `StoredPhotosPassTest` | T1 | The one-time pass cleans an original that has a location, updates its record, runs once, and resumes after being stopped | 8.2.9 |
| `MacBridgeTest` | T5 | Each bridge function gives the Apple codec's own answer through JNA | 8.2.6 |
| `MacPdfTest` | T5 | Page 1 of a fixture PDF is exactly the asked width and not blank | 8.3.11 |
| `PackagedAppSelfTest` | T5 | The bundle starts, opens a database, draws a frame, finds every Java module it needs, including TLS, and exits | 8.1.9 |
| `MacRemindersSelfTest` | T5 | Scheduling inside the bundle leaves the right pending requests; rescheduling twice changes nothing | 8.4.2 |

### 9.3 The packaged app

The disk image is the thing you install, so CI tests it, not only the code that went into it. The workflow launches `Watches.app` with a `--self-test` argument against a temporary folder. It then checks the signature (`codesign --verify --strict`), and for release builds Gatekeeper's verdict (`spctl`) and the stapled ticket.

### 9.4 The photo conformance suite

One set of tests in shared test code, run against every codec. The photos are small files made for the purpose and committed with their expected results.

| Photo | What is checked |
| --- | --- |
| Eight JPEGs, one for each EXIF rotation, with a different colour in each corner | The size in display orientation; upright renditions; the same crop cuts the same corner |
| A rotated HEIC, as an iPhone writes it | The same, through HEIC's own way of storing rotation |
| A wide-gamut (Display P3) HEIC and JPEG | Renditions come out in sRGB, with the expected colours |
| JPEG, PNG, WebP and HEIC with a location, in EXIF and in XMP | No location afterwards, by the shared reader and by the platform's own; the capture date kept; the picture data untouched |
| A PNG and a WebP with transparency | Black where they were transparent |
| An 8000 × 6000 JPEG | It renders, and the peak memory is recorded |
| A crop of 3% of the frame | The exact output size from the shared geometry, and no enlargement |
| AVIF, TIFF, GIF, BMP and DNG | Converted to JPEG per M6, the same on every platform that can decode them |
| A truncated file, and a HEIC header with nothing behind it | A clear failure, and nothing left on disk |

Sizes are compared exactly. Pictures are compared as an 8 × 8 grid of average colours, with a tolerance.

Where it runs:
- **Linux**, against the plain-Java codec, on every push. The HEIC cases are skipped there.
- **An Android emulator**, in a new CI job, against Android's real decoders.
- **The iPhone simulator**, in the existing iPhone workflow.
- **The macOS runner**, natively against the Apple codec's Mac build, and again through the bridge.

### 9.5 Cross-platform checks

- **Device-made archives as fixtures.** One small backup from each phone, made from sample watches (Settings › Diagnostics makes them, so nothing personal is committed). Between them they hold a HEIC photo from the iPhone, a rotated JPEG from the Android phone, a cropped photo, a PDF and a photo document. Each restores on the JVM in CI, and is deep-compared: every row, every file's hash, renditions present.
- **Mac to phone.** An archive made by the Mac code restores on the iOS simulator and in the Android host tests. With iPhone plan 5.3.8, that covers every pair in both directions.
- **Crop parity**, from the suite: a photo cropped on the Mac, re-rendered on a phone, shows the same region.
- **A case-insensitive disk.** A Mac's disk usually ignores case in file names, and the phones' don't. A test restores a hand-made archive with two names that differ only by case, and checks that both files survive.

### 9.6 What only your Mac can test

Touch ID and the password fallback, a reminder arriving with the app closed, AirDrop and iCloud Drive, Gatekeeper on a downloaded build, the feel of the trackpad, VoiceOver by ear, and your real backups. They're in appendix C.

---

## 10. Signing, distribution and CI

### 10.1 Accounts (you, once)

1. The Apple Developer Program membership from iPhone plan D1.
2. A **Developer ID Application** certificate. It's made the way the README makes the iPhone's distribution certificate: a request from `openssl`, uploaded on Apple's developer website, then joined into a `.p12`. No Mac is needed. Only the account holder can create this kind.
3. Two new repository secrets, `MAC_DEVELOPER_ID_P12_BASE64` and `MAC_DEVELOPER_ID_P12_PASSWORD`. Notarisation uses the App Store Connect API key the iPhone release already has (`ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64`). **Verify** that key's role is enough for `notarytool`.

**Without the certificate,** builds still work but aren't notarised. The first time you open each one, macOS refuses. You then allow it in System Settings › Privacy & Security › Open Anyway. That is fine for trying mac-1, and tedious as a habit.

### 10.2 How a build reaches the Mac

Tag `v0.7.0` → the macOS workflow packages, signs, notarises and staples `Watches-0.7.0-7.dmg` → it's added to the same GitHub release as the APK → download, open, drag Watches to Applications.

- **Updating** is the same: drag the new app over the old one. The collection is in Application Support, so it's untouched.
- **Nothing expires.** A notarised app keeps opening after the membership lapses; only new builds need it. **Verify** against Apple's current terms before relying on it.
- **All three apps are released from one tag**, so their schema versions move together and a backup from one is never "too new" for another that is up to date.

### 10.3 Workflows

| Workflow | When | What it does | Runner |
| --- | --- | --- | --- |
| Android (extended) | Every push | As now, plus the desktop compile, the desktop tests and screenshots, and the Linux package smoke test | Linux |
| Photos (new) | Photo code changes, tags, on demand | The conformance suite on an Android emulator | Linux, about 10 minutes **verify** |
| macOS (new) | Mac changes, tags, on demand | Runs the Apple codec's tests natively, and builds the bridge (from mac-2); packages the disk image; runs the self-test; on tags, signs, notarises and adds it to the release | macOS, about 10 minutes **verify** |

Until mac-2 there is no Kotlin/Native in the Mac build, so it has nothing like the iPhone workflow's 15-minute first link. From mac-2 it builds the bridge, and uses the same cache as the iPhone workflow. The iPhone workflow already runs the shared data tests on the simulator, so it picks the suite up without changes. While the repository is public, macOS minutes are free. Private, they count ten times, which is why the triggers are narrow.

---

## 11. Milestones

| Milestone | Scope | Size | Shares with | You test | Done when |
| --- | --- | --- | --- | --- | --- |
| **mac-1** It runs, and reads your backup | 8.1 | L | Nothing | Install it. Restore a copy of a phone backup with Replace. Look through each design. Back up on the Mac and restore that on a phone. Keep the phone as the main copy. | The disk image opens on your Mac; a phone backup restores; a Mac backup restores on a phone; the desktop tests and screenshots run on every push |
| **mac-2** One photo component, on all three | 8.2 | 2 × L | Takes over iOS-3's photo items (5.3.1 to 5.3.3), and shares its archive tests (5.3.8) | On each device, add a rotated photo, a HEIC and a photo with a location, and crop one. Back up, and restore on the other two: the same picture, the same crop, no location. | The conformance suite passes on Android, the iPhone simulator and the Mac; the one-time pass has run on each device; appendix C's photo sections pass |
| **mac-3** Documents, and like a Mac app | 8.3 | L | iOS-2: wording (5.2.10), the appearance hook (5.2.4). iOS-3: PDF pages (5.3.4). | View a PDF. Drop photos and a backup on the window. Menus, shortcuts, right-click, the title bar in each design, forced Light and Dark, the wide layout you chose, zoom | The 8.3 tests pass; appendix C's document, look-and-feel and accessibility sections pass |
| **mac-4** Lock, reminders and release | 8.4 | M | iOS-2: the reminder planner (5.2.1), notification states (5.2.2), lock wording (5.2.6), the test reminder (5.2.11) | Touch ID, a test reminder with the app closed, a notarised download that opens without a warning | All of appendix C passes; the notarised disk image is on the release beside the APK |

Each milestone ends, as before, with a changelog entry and green CI. Where a milestone shares work with an iPhone one, whichever is built first adds the common half, and the other adds its platform's.

---

## 12. Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| macOS 27 | It is newer than anything the toolkit's documentation names | mac-1's first build on your Mac is the check. Updating the toolkit and the Java runtime is routine. |
| Three codecs drifting apart | The same photo looks different, crops differently or keeps its location on one device | One set of rules in shared code, and one conformance suite that every codec must pass in CI (section 5). Photos stay off on the Mac until it does. |
| The photo work changes Android and iPhone | A regression in photo handling on the phone you use every day | The suite runs on Android's real decoders before anything ships. Originals are never rewritten except to remove a location. Back up first. |
| Location removal in shared code | A mistake could damage an original | It only overwrites bytes inside the metadata it has located, and never changes a file's length. The suite checks that the picture data is untouched. A file it can't parse is re-encoded instead. |
| The bridge | A Kotlin/Native library inside a Java process is uncommon, and its bugs show only on the macOS runner or your Mac | A one-function trial first, with the same functions in Swift as the fallback. Linux compiles the bridge's Kotlin on every push. mac-1 needs no bridge at all. |
| Notarisation and the bundled native libraries | The build is rejected, or opens with a warning | Standard for Compose desktop apps, and the plugin signs what's inside the jars; the ad hoc path works meanwhile; CI checks Gatekeeper's verdict |
| A missing Java module | Something works everywhere except the packaged app | The self-test runs inside the real bundle |
| It feels like a phone app | Fine to look at, awkward to use | mac-3 is a whole milestone; screenshots at Mac sizes from mac-1 |
| Compose on the desktop | Text editing, scrolling or accessibility slightly non-native | Checked on your Mac in mac-3. It's a supported target, and updating it is routine. |
| App size and start time | About 100–150 MB installed, and a second or so to open (**verify**), because it carries a Java runtime | Accepted for a desktop app. Trimming the runtime helps, and shrinking the code is possible later. |
| Three copies of the collection | A deleted watch returns after a merge; edits made in two places | 4.6 in the README and on the Restore screen; Replace for one-way moves |
| A newer app's collection opened by an older build | The app wouldn't start | 8.1.5 |
| Libraries dropping a platform | The bundled SQLite already has no Intel Mac build | Versions are pinned; the desktop compile runs on every push |
| App identity | A new bundle ID loses the notification permission, though not the data | Keep it for good (M4) |

---

## 13. Not in this plan

- **Sync.** iCloud or any other. The app stays offline-first, and a backup file is the transfer.
- **Deletions carried through a merge.** It needs tombstones, so a schema change on all three apps.
- **The Mac App Store.** It needs the sandbox and a store listing. It could follow.
- **Intel Macs.**
- **Windows and Linux releases.** The desktop target runs there, and Linux is where CI draws it, but nothing is packaged or tested for use.
- **An update check or auto-update.** It would be a network request outside Lookup.
- **Several windows**, Continuity Camera ("take a photo with your iPhone"), the system Share menu, Spotlight, widgets and Shortcuts.

---

## Appendix A: changes by file

| File | Change | § |
| --- | --- | --- |
| `settings.gradle.kts` | Include `:desktopApp` | 8.1.4 |
| `desktopApp/build.gradle.kts` (new) | Kotlin JVM, Compose desktop application, packaging, the version | 8.1.4, 8.1.6, 8.1.9 |
| `desktopApp/src/main/kotlin/.../Main.kt` (new) | `main()`, the lock file, Koin, the window, menus, quit and drop handling | 8.1.4, 8.3.1, 8.3.13 |
| `desktopApp/src/main/kotlin/.../platform/MacServices.kt` (new) | Reminders, notification access, app lock, sharing, `macPlatformModule` | 8.1.3, 8.3.12, 8.4 |
| `desktopApp/src/main/kotlin/.../platform/MacBridge.kt` (new) | The bridge's interface, its JNA binding and the Linux fallback | 8.2.6 |
| `shared/data/src/macosMain/.../Bridge.kt` (new) | The bridge's C-callable functions | 8.2.6 |
| `desktopApp/src/test` (new) | T2 and T3 | 9 |
| `shared/ui/build.gradle.kts` | The `jvm()` target and its dependencies | 8.1.1 |
| `shared/ui/src/jvmMain` (new) | The six platform functions; the camera launcher's desktop version | 8.1.1, 8.1.2 |
| `shared/ui/src/commonMain/.../platform/Camera.kt` (new); `androidMain`, `iosMain` | `rememberCameraLauncher` and its phone versions | 8.1.2 |
| `.../photos/PhotosScreen.kt`, `.../care/DocumentScreens.kt` | The camera wrapper; drop targets; the mac-1 limits | 8.1.2, 8.1.7, 8.3.13 |
| `.../photos/PhotoImporter.kt` | Streamed import | 8.2.8 |
| `.../nav/AppShell.kt` | The wide layout, the rail label, the back keys | 8.3.2, 8.3.5 |
| `.../theme/WatchTheme.kt` | Hover and focus tokens; the appearance hook; the type scale | 6.2, 8.3.4, 8.3.6 |
| `.../components/Controls.kt`, `Rows.kt`, `SpecSheetView.kt` | Hover, focus, right-click | 8.3.3 |
| Headers in `.../components/Fields.kt`, `Ornaments.kt` | The top inset under a transparent title bar | 8.3.4 |
| `.../security/LockScreen.kt`, `.../settings/SettingsScreen.kt`, `.../backup/BackupScreens.kt`, `.../care/ServiceScreens.kt`, `.../settings/InfoScreens.kt` | Platform text, licences, the collection folder | 8.3.8, 8.3.9 |
| `shared/data/src/jvmMain/.../JvmDataModule.kt` (new) | The database, files, image processor and version for the desktop | 8.1.3 |
| `shared/data/src/commonMain/.../backup/BackupRepository.kt` | The two "phone" messages move to the UI layer | 8.3.8 |
| `shared/data/src/commonMain/.../files/ImageProcessor.kt` | A shared class on top of `ImageCodec` | 8.2.1 |
| `shared/data/src/commonMain/.../photos/` (new) | `PhotoFormat`, `RenderPlan`, `ImageCodec`, location removal, `PhotoIntake`, the stored-photos pass | 8.2.1, 8.2.2, 8.2.8, 8.2.9 |
| `shared/data/build.gradle.kts`, `shared/core/build.gradle.kts` | A `macosArm64` target, and the bridge as a dynamic library | 8.2.5, 8.2.6 |
| `shared/data/src/appleMain/.../AppleImageCodec.kt` (new); `iosMain/.../IosImageProcessor.kt` (removed) | One ImageIO codec for iPhone and Mac | 8.2.5 |
| `shared/data/src/androidMain/.../AndroidImageProcessor.kt` | Becomes `AndroidImageCodec`, on the shared geometry | 8.2.4 |
| `shared/data/src/jvmMain/.../JvmImageProcessor.kt` | Becomes the plain-Java codec for tests | 8.2.1 |
| `shared/data/src/commonTest/fixtures/photos/` (new); `.../PhotoConformanceTest.kt` (new) | The suite and its photos | 8.2.3 |
| `shared/data/src/androidDeviceTest` (new) | The suite on an emulator | 8.2.4 |
| `.../photos/CropScreen.kt`, `App.kt` | The Coil decoder on the codec | 8.2.7 |
| `shared/search/src/jvmMain/.../JvmSearch.kt` (new) | `Connectivity` for the desktop | 8.1.3 |
| `shared/core/.../settings/AppSettings.kt`, `shared/data/.../SettingsRepository.kt` | Window and zoom as device settings | 8.3.6, 8.3.7 |
| `shared/data/src/commonTest/fixtures/archives/` (new) | Device-made archives | 9.5 |
| `build.gradle.kts` | Java networking in the isolation check; `desktopApp` network-free | 8.1.8 |
| `gradle/libs.versions.toml` | The Swing coroutine dispatcher, JNA, Compose's desktop test library | 8.1.1, 8.2.6, 9 |
| `tools/RenderIosAppIcon.java` | Also draws the Mac icon | 8.1.9 |
| `tools/import-developer-id.sh`, `tools/check-mac-app.sh` (new) | The certificate into a temporary keychain; the self-test and signature checks | 8.4.3, 9.3 |
| `.github/workflows/android.yml`; `macos.yml`, `photos.yml` (new) | CI | 10.3 |
| `README.md`, `CHANGELOG.md`, `docs/ARCHIVE_FORMAT.md`, `docs/IOS_PLAN.md` | The Mac; the photo contract; the iPhone plan's photo items pointing here | 5.3, 8.4.4 |

---

## Appendix B: build and release sketch

`shared/ui/build.gradle.kts`, the parts that change. This is what the trial build used:

```kotlin
kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
```

`desktopApp/build.gradle.kts`. The property names are from the plugin's documentation; the module list is a starting point that the self-test confirms.

```kotlin
compose.desktop {
    application {
        mainClass = "io.github.tunlezah.watches.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Watches"
            packageVersion = marketingVersion              // from version.xcconfig (7.1.6)
            modules("java.desktop", "java.logging", "jdk.unsupported", "jdk.crypto.ec")
            appResourcesRootDir.set(layout.buildDirectory.dir("bridge"))   // libWatchesMac.dylib
            macOS {
                bundleID = "io.github.tunlezah.watches.mac"                // M4
                dockName = "Watches"
                minimumSystemVersion = "13.0"
                packageBuildVersion = buildNumber
                iconFile.set(project.file("icons/Watches.icns"))
                infoPlist { extraKeysRawXml = zipDocumentType }            // "Open With" (7.3.9)
            }
        }
    }
}
```

`.github/workflows/macos.yml`, in outline. The runner label is the one the iPhone workflows use; **verify** it against the current images.

```yaml
name: macOS
on:
  push:
    tags: ["v*"]
    paths: ["desktopApp/**", "shared/*/src/jvmMain/**", "shared/*/build.gradle.kts", "gradle/**", "version.xcconfig"]
  workflow_dispatch:

jobs:
  package:
    runs-on: macos-15
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: "21" }
      - uses: gradle/actions/setup-gradle@v4
      - name: Apple codec tests, and the bridge      # from mac-2
        run: ./gradlew :shared:data:macosArm64Test :shared:data:linkReleaseSharedMacosArm64 :desktopApp:macTest
      - name: Certificate            # skipped without the secrets: the build is then signed ad hoc
        run: tools/import-developer-id.sh
      - name: Package
        run: ./gradlew :desktopApp:packageDmg
      - name: Self-test, signature
        run: tools/check-mac-app.sh desktopApp/build/compose/binaries/main/dmg/*.dmg
      - name: Notarise and staple    # tags only, with the secrets
        run: |
          xcrun notarytool submit "$DMG" --key "$RUNNER_TEMP/asc.p8" --key-id "$ASC_KEY_ID" --issuer "$ASC_ISSUER_ID" --wait
          xcrun stapler staple "$DMG"
      - name: Add to the release     # tags only
        run: gh release upload "$GITHUB_REF_NAME" "$DMG" --clobber
```

---

## Appendix C: Mac checklist

Run on your Mac with a downloaded build, with 500 sample watches where it says so.

**Install and update**
- [ ] The downloaded disk image opens without a warning (notarised builds), and Watches opens to the welcome screen.
- [ ] A later build dragged over it keeps the collection.
- [ ] An older build dragged over a newer one says the collection is newer, and changes nothing.
- [ ] Opening the app twice gives one window.
- [ ] Little Snitch or LuLu shows no connections until a Lookup, then only the sources' domains.

**Moving a collection**
- [ ] An iPhone backup arrives by AirDrop and by iCloud Drive, and restores with Replace. The counts match the phone. Photos, crops, documents, service history and box & papers are all there.
- [ ] An Android backup arrives by Google Drive and by USB drive, and restores the same way.
- [ ] After a Replace, the Mac shows the design, accent and Light or Dark the phone had.
- [ ] Merging the same backup again changes nothing.
- [ ] A backup made on the Mac restores on the iPhone and on the Android phone, with nothing missing.
- [ ] A backup unzipped by Safari, compressed again in Finder, still restores.
- [ ] Dropping a backup on the window, and Open With › Watches, both open the preview.

**Photos, on all three devices**
- [ ] A rotated photo, a HEIC and a photo with a location, added on the Android phone, on the iPhone and on the Mac. After a backup and restore, each looks the same on the other two, upright, with no location in the stored original.
- [ ] A crop made on each device shows the same region on the other two.
- [ ] After the update, photos added before it still look the same on each phone, and a HEIC that had a location no longer has one.

**Photos, on the Mac**
- [ ] From Finder and by drag and drop: an iPhone HEIC, a portrait shot, a PNG screenshot, a TIFF, then 20 at once.
- [ ] Crop a photo that came from the iPhone. Restore the Mac's backup on the iPhone: the crop shows the same region, and re-cropping there starts from the original.
- [ ] A photo with a location, added on the Mac, has none in the stored original.

**Documents**
- [ ] Add a PDF and a photo of a receipt. The viewer shows sharp pages.
- [ ] Open in Preview, Show in Finder and Save a copy… all work, and the copy has the document's title.

**Backups**
- [ ] Back up to the Mac, to iCloud Drive and to an external drive. Each restores with Merge.
- [ ] A backup over 4 GB.
- [ ] Quit during a backup: it asks first. Force-quit during a restore and reopen: the collection is unchanged or fully restored, never half.
- [ ] The CSV opens correctly in Numbers and Excel.

**Like a Mac app**
- [ ] Every menu item does what it says, and is greyed out when it can't.
- [ ] ⌘N, ⌘F, ⌘, and ⌘1–⌘4. Esc and ⌘[ go back, and a changed form asks before discarding.
- [ ] Right-click copies a spec value. The serial number stays hidden until clicked.
- [ ] The window remembers its size and place. Full screen and Split View work.
- [ ] Each design in Light and Dark, and forced opposite to macOS: the title bar, menus and file dialogs match.
- [ ] Narrow the window to phone width and widen it again: the layout follows without losing your place.
- [ ] Zoom in and out. Nothing is cut off at the largest size.

**App lock and reminders**
- [ ] Turn the lock on with Touch ID. Minimise and return, after "Immediately" and after 1 minute.
- [ ] A failed Touch ID falls back to the Mac's password.
- [ ] Diagnostics' test reminder arrives a minute later, also with the app closed. Clicking it opens the watch's service history.

**Accessibility and performance (500 sample watches)**
- [ ] VoiceOver: the tabs, headings, switches, spec rows and the lock screen.
- [ ] Everything reachable with the keyboard alone, with a visible focus ring.
- [ ] Reduce Motion is respected.
- [ ] Diagnostics: cold start, search and opening a watch. Fast scrolling stays smooth.

---

## Appendix D: alternatives considered

| Option | What it means | Verdict |
| --- | --- | --- |
| **A. Compose's desktop target** (recommended) | The shared UI on the JVM, in a Mac shell with its own Java runtime | A supported target. Three modules already build and test on it, and the fourth compiled and rendered in the trial. Builds, tests and screenshots run on Linux. The costs are the app's size and a small native bridge. |
| B. The iPhone app on the Mac | A TestFlight setting (3.6) | Nearly free, and worth having early. Not a Mac app: a phone window, no menus, and it expires every 90 days. |
| C. A native Kotlin Mac app | A `macosArm64` target sharing an Apple source set with the iPhone | Every library publishes a build for it, and it would reuse the iPhone's platform code with no bridge and no Java runtime. But Compose's native Mac target isn't in JetBrains' list of supported platforms, its users describe it as experimental, and it builds and runs only on macOS, so every change would wait on macOS CI. Worth another look if JetBrains makes it a supported target. |
| D. SwiftUI screens over the shared Kotlin | Rewrite the screens, view models and the three designs in Swift | The most Mac-like result, with every screen built twice and kept in step for good. It would also need the Kotlin code as a Mac framework, which is option C's build problem as well. |
| E. An Intel Mac | The JVM route with an Intel build of SQLite's native library made here, on a Compose target JetBrains no longer lists for Intel | Possible in principle, and unsupported at every layer. Only if M1 forces it. |

**The bridge's language.** The Mac's native library could be Swift instead of Kotlin: about 150 lines, built by `swiftc` with no Xcode project. The plan prefers Kotlin because the iPhone then runs the very same image code, and because Linux can compile it on every push, which it can't do for Swift. Swift stays as the fallback if a Kotlin/Native library proves awkward inside the Java process (8.2.6).
