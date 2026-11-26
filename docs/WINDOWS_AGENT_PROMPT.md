# Part 1: the brief. Build the Windows app for Watches

You are working in the `tunlezah/Watches` repository. Your job is to make Watches run on Windows 11, in two milestones, and open one pull request for each. The plan you are building is Part 2 of this brief. Read all of it before you change anything: it holds the decisions, the reasons and the item numbers this brief refers to (such as 7.1.4).

Work through to the end without stopping for approval. The owner has already made the decisions in the plan's section 6. Don't reopen them and don't ask about them.

## 1. What you are building

Watches is a private, offline-first watch collection app in Kotlin Multiplatform with Compose Multiplatform. It ships on Android, iPhone and Mac from one codebase. The Mac app is a small shell (`desktopApp`) on Compose's desktop target, which is Java. Windows is the same shell, packaged for Windows.

- **Milestone win-1** (plan 7.1): it installs from an MSI, restores and makes backups, and takes photos in, HEIC included. Version 0.9.0.
- **Milestone win-2** (plan 7.2): it behaves like a Windows app. Most of this is shared code that the Mac gets too. Version 0.10.0.

If `version.xcconfig` no longer says 0.8.0 when you start, use the next two minor versions after whatever it holds, and the next two build numbers.

Not yours to build: the app lock, reminders, code signing, Windows on ARM, AVIF, a camera, a menu bar on Windows. The plan's section 10 lists them.

## 2. Before you write code

1. Read `README.md`, `docs/BUILD.md`, `docs/ARCHITECTURE.md`, `CHANGELOG.md` (the top two entries), `docs/MACOS_PLAN.md` (the status notes at the top, then sections 5, 6 and 8.3) and `docs/DESIGN.md`. The Mac plan is the specification for every shared item in win-2.
2. Read the desktop shell (`desktopApp/src/main`, all eight files), the `jvmMain` source sets of `shared/data`, `shared/search` and `shared/ui`, `shared/data/.../photos/ImageCodec.kt`, `RenderPlan.kt` and `PhotoConformanceTest.kt`, and the three workflows in `.github/workflows` that touch the desktop (`android.yml`, `macos.yml`, `photos.yml`).
3. If `docs/WINDOWS_PLAN.md` is not in your checkout, create it from Part 2 of this brief, word for word, as your first commit ("Add the Windows app plan").
4. Run `./gradlew :desktopApp:test` and see it pass before you change anything. That is your baseline.

## 3. Rules that hold throughout

These come from the project. Breaking one is a failed task even if CI is green.

- **Only `shared/search` may reach the network.** No sockets, no update check, no telemetry, anywhere else. `./gradlew checkNetworkIsolation` must pass. Find a running copy of the app with a file and a window handle, never a socket.
- **No schema change.** `AppDatabase.VERSION` and the archive format version stay as they are. Settings that belong to one device use `device.*` keys, which never travel in a backup.
- **The phones don't change.** Every string an Android or iPhone user sees stays the same, to the letter. Android, iOS, Photos and macOS workflows must stay green.
- **Stored paths are text with `/`.** The database keeps `photos/<id>/<name>`. Never build a stored path or an archive entry name from the file system's own path text, which uses `\` on Windows.
- **Never weaken a test to get green.** No skipping, no `@Ignore`, no loosened assertion, no "only on Linux" guard around something that should work on Windows. Fix the code. If a test is wrong, say why in the commit that changes it.
- **Nothing GPL in the build.** libheif is built with default features off and only `libde265` on. CI must fail if x265 or x264 is among the built files.
- **Every new library is pinned** in `gradle/libs.versions.toml` at an exact version you confirmed is published. The versions in this brief were current on 25 Nov 2026: use them, or a newer patch release of the same line, and say which.
- **Say what was and wasn't checked.** You have no Windows machine. Never write that something works on Windows unless the Windows runner showed it. The plan's status notes keep a "checked / not checked" list, as the Mac plan's do.
- **No secrets, no force-push, no tags, no merging.** Don't touch signing material or repository settings.

## 4. How you work without a Windows machine

**On Linux, in your session.** Shared code, the desktop codec, the JNA binding to libheif (once its HEVC plugin is installed), the desktop tests, the screenshots and a Linux package with its self-test all run here. Prove everything you can here first.

- JDK 21 and the Android SDK are installed. If Gradle can't find the SDK, write an untracked `local.properties` with `sdk.dir=` set to the SDK's path (`$ANDROID_HOME`, usually `/opt/android-sdk`).
- **Maven Central limits downloads from cloud sessions (HTTP 429).** A build that fails with "Could not GET" or "could not resolve plugin" hasn't failed: Gradle keeps what it fetched, so run it again after a pause. Expect several tries the first time and none afterwards. Don't change repositories or add mirrors to fix it.
- For HEIC on Linux: `apt-get update && apt-get install -y libheif1 libheif-plugin-libde265`, then name the library with `WATCHES_HEIF=/usr/lib/x86_64-linux-gnu/libheif.so.1`. If your session can't install packages, the Linux job in CI is where the HEIC cases run; say so.
- Useful commands: `./gradlew :shared:core:jvmTest :shared:data:jvmTest :shared:search:jvmTest :shared:ui:jvmTest :desktopApp:test`, `./gradlew checkNetworkIsolation checkIosProject`, `./gradlew :desktopApp:createDistributable` then `desktopApp/build/compose/binaries/main/app/Watches/bin/Watches --self-test <folder>`.
- Screenshots land in `desktopApp/build/outputs/screenshots`. Look at them: they are the only way you see the app.

**On Windows, through CI.** Workflows run on every push to your branch. `windows-latest` is your Windows machine.

- Push, then wait for the run. Use your session's GitHub tools to list the runs for your branch and read the logs of failed jobs. If your session delivers pull-request events, use those instead of polling. Don't poll more than once every few minutes.
- A Windows round trip is about ten minutes. Push only what already passes on Linux, and put a step's diagnostics in the workflow (directory listings, tool logs on failure) so one run tells you enough.
- An item is done when its Windows run is green, not before.
- The runner is Windows Server 2025, not Windows 11. It can't show you the title bar, the file dialogs or SmartScreen. Those go on the owner's checklist (plan, appendix A).

**If you are stuck.** After three Windows runs on one fault without progress, stop guessing. Add logging that would settle it, or reduce it to a small test. If an approach in this brief turns out not to work, use the fallback this brief names. If there is none, choose the simplest approach that keeps the rules in section 3, and record it in the plan's "as built" notes as a difference.

## 5. Branches, commits and pull requests

- **win-1** goes on the branch your session designates. When it is done, open pull request 1 against the repository's default branch. Find that with `git ls-remote --symref origin HEAD`; on 25 Nov 2026 it was `claude/new-session-3cm41x`.
- **win-2** goes on one further branch, named as your branch with `-win-2` added, created from the tip of win-1. The owner authorises you to create and push that one branch. Open pull request 2 with **your win-1 branch as its base**, and say in its description that it is stacked on pull request 1.
- Then carry straight on from win-1 to win-2. Don't wait for a review between them.
- **Commits** are small and each leaves the build green. Follow the repository's style: a plain sentence as the subject ("Photos through libheif, on Windows and Linux"), then a body that says why and what was found. Look at `git log` first. Add whatever attribution lines your session's instructions ask for.
- **Pull request descriptions** say what was built, what the Windows runner proved, what nobody has checked on a real PC, and any difference from the plan. Use the repository's pull request template if there is one.
- **Docs** are part of each milestone, in the house style: plain English, short sentences, British spelling ("licence", "colour"), no marketing. Each milestone ends with a `CHANGELOG.md` entry, the plan's status line and "as built" notes, the README (for people using the app) and `docs/BUILD.md` (for building it), and green CI.

## 6. Milestone win-1, in order

Do these in this order. The plan has the reasons; this section has what the plan doesn't.

**Step 1: line endings (7.1.1).** Add `.gitattributes`: `* text=auto eol=lf`, `*.bat text eol=crlf`, and `binary` for `*.png *.jpg *.jpeg *.heic *.webp *.gif *.bmp *.tiff *.zip *.jar *.icns *.ico *.ttf *.pdf`. Check with `git ls-files --eol` that no tracked file would be rewritten. If one would, fix the attribute, not the file.

**Step 2: the existing tests on Windows (7.1.2).** Add `.github/workflows/windows.yml`. Model it on `macos.yml`: the same header comment style, `concurrency`, `permissions: contents: read`, checkout, Temurin 21, `gradle/actions/setup-gradle`. Use `shell: bash` for Gradle steps and `pwsh` where Windows tools are involved. Trigger it on pushes and pull requests that touch `shared/**`, `desktopApp/**`, `tools/heif/**`, the Gradle files, `version.xcconfig` or the workflow itself, on `v*` tags, and on demand. First version: run the four shared `jvmTest` tasks and `:desktopApp:test`, and upload test reports on failure. Push it alone and read what breaks. Fix each failure at its cause (plan 7.1.2 lists the likely ones). Don't go on until this run is green.

**Step 3: the shell knows its system (7.1.3).** Add `DesktopOs`. Move the collection to `%LOCALAPPDATA%\Watches` in `AppPaths`, falling back to `<home>\AppData\Local` when the variable is missing. Update `ShellTest`. Make the shell's messages follow the system.

**Step 4: the desktop codec (7.1.4).** Turn `JvmImageCodec` into the codec the plan's 4.1 and 4.2 describe. Add `com.twelvemonkeys.imageio:imageio-webp` (3.15.3) to `shared/data`'s `jvmMain`. Keep the selection order: the Mac bridge where it is loaded, otherwise this codec. The conformance suite on Linux must pass with WebP now among the formats. Add a JVM-only test for a Display P3 JPEG (make the fixture with `tools/photo_fixtures.py`) and for a WebP with transparency. Don't add wide-gamut cases to the shared suite.

**Step 5: HEIC through libheif (7.1.5).** A JNA binding in `shared/data`'s `jvmMain`, beside `MacBridge.kt` and shaped like it: a system property (`watches.heif`), a lazy load, `isLoaded`, `problem`, and a rule that a named library which can't be used throws in tests. Bind only functions present in libheif 1.17, because Linux tests run on Ubuntu's 1.17.6 and Windows on a newer one:

- `heif_get_version`, `heif_have_decoder_for_format`
- `heif_context_alloc`, `heif_context_free`, `heif_context_read_from_memory_without_copy`
- `heif_context_get_primary_image_handle`, `heif_image_handle_release`
- `heif_image_handle_get_width`, `heif_image_handle_get_height`, `heif_image_handle_has_alpha_channel`
- `heif_decode_image`, `heif_image_get_plane_readonly`, `heif_image_release`
- `heif_image_handle_get_color_profile_type`, `heif_image_handle_get_raw_color_profile_size`, `heif_image_handle_get_raw_color_profile`

Points that matter:

- Most of these return `struct heif_error { int code; int subcode; const char* message; }` **by value**. Map it as a JNA `Structure.ByValue` with that field order. Zero is success.
- **Take every enum value and every signature from `heif.h` at the version you pin.** From memory, to be confirmed: HEVC compression is 1, the RGB colourspace is 1, interleaved RGB chroma is 10, interleaved RGBA is 11, the interleaved channel is 10.
- **Give libheif bytes, never a path.** Read the file in Java, copy it into JNA `Memory`, and keep that memory alive until the context is freed.
- One context per call, everything released in `finally`, nothing shared between threads.
- A picture with alpha is decoded as RGBA and laid over black. Everything else as RGB.
- libheif applies a HEIC's rotation, mirror and crop itself, so `probe` reports the handle's width and height and `Orientation.NORMAL`. The suite's `turned-6`, `turned-6-iphone` and `large-turned-*` HEIC fixtures tell you whether that holds on both library versions. If a version doesn't, use `PhotoMetadata.heicLayout`, as Android's codec does.
- Copy only the plan's region out of the native buffer, averaging by `plan.sampleSize` as you copy, so the Java heap never holds the full picture.
- `HEIC` is in `formats` only when the library loaded **and** `heif_have_decoder_for_format` says it can decode HEVC.
- Add `HeifLibraryTest` on the pattern of `MacBridgeTest`, printing one line CI can grep for. `MacBridgeTest`'s no-bridge branch asserts that the tests read no HEIC; that now holds only where no libheif was named, so correct it.

**Step 6: the DLL (7.1.6).** Under `tools/heif/`: a `vcpkg.json` manifest with a `builtin-baseline` (a vcpkg commit you choose and record) and the dependency `{ "name": "libheif", "default-features": false, "features": ["libde265"] }`. Build it in `windows.yml` with the runner's own vcpkg (`$env:VCPKG_INSTALLATION_ROOT`), release only. If the runner's copy can't resolve your baseline, clone vcpkg at that commit and bootstrap it instead.

- **Plan A:** an overlay triplet with `VCPKG_LIBRARY_LINKAGE dynamic` and `VCPKG_CRT_LINKAGE static`, so the DLLs carry their own C runtime.
- **Plan B**, if a port won't build that way: the stock `x64-windows-release` triplet, with `vcruntime140.dll`, `vcruntime140_1.dll` and `msvcp140.dll` copied beside the DLLs.
- Either way, add a step that reads each DLL's imports (`dumpbin /dependents`, found through `vswhere`, or an equivalent) and fails unless every import is a DLL that ships with Windows 11 or sits in the same folder. Print the list.
- Fail the job if any built file's name contains `x265` or `x264`.
- Cache vcpkg's binary cache with `actions/cache`, keyed on the files in `tools/heif`.
- Copy the DLLs, and the licence texts vcpkg installs for libheif and libde265, into a folder the packaging step picks up.
- Run the Windows tests with `WATCHES_HEIF` naming the built `heif.dll`, and grep the results for `HeifLibraryTest`'s line, as `macos.yml` does for the bridge.

In `android.yml` (Linux), install Ubuntu's libheif and its plugin and set `WATCHES_HEIF` for the test step, so the HEIC cases run on every push.

**Step 7: the second opinion (7.1.7).** `com.drewnoakes:metadata-extractor` (2.22.0 or later: earlier versions don't read XMP from HEIC). `hasLocation` is true if it finds GPS in EXIF or location properties in XMP. The conformance suite already checks that a platform's reader finds a location before cleaning and none after.

**Step 8: photos on (7.1.8).** `platformLimits.photos` depends on what the codec in use reads, not on the Mac bridge. Generalise `Bridge` in the shell so the packaged Windows app names its `heif.dll` at start-up the way the Mac app names its bridge.

**Step 9: the shell on Windows (7.1.9).** Add `net.java.dev.jna:jna-platform` at JNA's version for the Win32 types. Every native call is wrapped so that a failure is logged and the app carries on.

- Second start: the first copy writes its window handle to a small file in the collection folder. That file must not be the lock file, which Windows won't let another process read. A second copy that can't take the lock reads the handle, checks it is still a window, restores it, sets it to the foreground, and exits. If that fails, it shows today's message.
- `KeepScreenOn`: `SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED)`, set and cleared on one dedicated thread.
- Reduce motion: `SystemParametersInfoW` with `SPI_GETCLIENTAREAANIMATION` (0x1042).
- The window icon: pass `icon` to `Window`, from a PNG drawn by `tools/RenderIosAppIcon.java`.

**Step 10: packaging (7.1.10).** In `desktopApp/build.gradle.kts`: add `TargetFormat.Msi`; a `windows { }` block with `perUserInstall = true`, a fixed `upgradeUuid` (generate one, never change it), `menuGroup = "Watches"`, `dirChooser = false`, `shortcut = false`, `console = false` and the `.ico`. Add `jdk.accessibility` to `modules(...)`, then run `:desktopApp:suggestRuntimeModules` and add what the new libraries need.

- Feed the DLLs and licences in through `appResourcesRootDir`, in the folder the plugin uses for Windows x64 (expected `windows-x64`; confirm by checking where the Mac's `macos-arm64` folder name comes from).
- **The app must not install into the collection's folder.** A per-user install defaults to `%LOCALAPPDATA%\Watches`, which is where the collection lives. Set the install folder to `Programs\Watches` under `%LOCALAPPDATA%`, and prove on the runner where `Watches.exe` landed. **Fallback:** if the installer can't be moved, keep the collection in `%APPDATA%\Watches` instead, and record that as a difference from the plan.
- Extend `tools/RenderIosAppIcon.java` to write `desktopApp/icons/Watches.ico` (16 to 256 px) and the window icon, and commit both.
- The Mac's packaging must not change. Keep its block and its `1.0.<build>` bundle version as they are.

**Step 11: the self-test (7.1.11).** `--self-test <folder>` also writes `self-test.txt` into the folder: every line it prints, ending `self-test: passed` or `self-test: FAILED`. On Windows the photos step stores a WebP and a HEIC and fails if the packaged app has no libheif. Add a small WebP to `desktopApp/src/main/resources/self-test`.

**Step 12: the installed app in CI (7.1.12).** In `windows.yml`, after the tests:

1. `./gradlew :desktopApp:packageMsi`. On failure, print jpackage's logs from `desktopApp/build/compose/logs`.
2. Install silently with `Start-Process msiexec -ArgumentList '/i', <msi>, '/qn', '/norestart', '/l*v', <log> -Wait -PassThru`, and require exit code 0.
3. Find `Watches.exe`. Fail if its folder is the collection's folder.
4. Run `Watches.exe --self-test <temp folder>` with `Start-Process -Wait -PassThru`. Require exit code 0 **and** a `self-test.txt` that ends `self-test: passed` and shows the HEIC went through libheif.
5. Put a marker file in the collection folder, uninstall with `msiexec /x`, and require that the marker is still there.
6. Name the MSI `Watches-<version>-<build>-unsigned.msi`, write a line to the job summary, and upload it for 14 days.
7. A `release` job on `v*` tags that waits for the Android workflow's release and uploads the MSI to it. Copy the macOS workflow's job.

**Step 13: names a backup may not use (7.1.13).** In `ArchivePaths`, refuse a segment whose name before the first dot is `CON`, `PRN`, `AUX`, `NUL`, `COM1` to `COM9` or `LPT1` to `LPT9`, in any case, and a segment that ends in a dot. Test it in `commonTest`, with a restore of a hand-made archive that carries one.

**Step 14: finish win-1.** Set `version.xcconfig` to `0.9.0` and build `9`. Write the CHANGELOG entry, the README's Windows section and a Windows section in `docs/BUILD.md` (plan 7.1.14), and the plan's "win-1 as built" notes. Update the project layout table in `docs/ARCHITECTURE.md`, the README's platform table, and `docs/ARCHIVE_FORMAT.md`'s list of platforms. Confirm every workflow is green on the final commit. Open pull request 1.

## 7. Milestone win-2, in order

Create the `-win-2` branch and go through plan 7.2.1 to 7.2.10 in that order, one finished item at a time. For each: read the Mac plan section named beside it, build the shared part in `shared/ui` so that no component asks which platform it is on, then add the Windows part in the shell. Add `compose.desktop.uiTestJUnit4` to the desktop tests for the key and pointer tests.

What the plan doesn't say:

- **7.2.1 Wording.** `expect val platformText` with one object for each platform. As you move each string, add it to a test that holds the phones' text to the exact sentence in the code today, so the refactor can't change a phone. The desktop's actual chooses by `DesktopOs`. Two messages built in `shared/data` (`BackupException.Damaged` and `NotEnoughSpace`) must carry their facts as data and get their wording in the UI layer. Finish with a test that no desktop string contains "phone", and none on Windows contains "Mac".
- **7.2.2 Commands.** Keep the list of commands in shared code and the key bindings in the shell. Esc already reaches `BackHandler` on some Compose versions: write the test first and add only what is missing.
- **7.2.4 The title bar.** `DwmSetWindowAttribute` in `dwmapi`: attribute 20 (dark mode, a BOOL), 35 (caption colour) and 36 (text colour), each a `COLORREF`, which is `0x00BBGGRR`. Get the handle with `Native.getWindowPointer(window)`. Unit-test the colour conversion; the effect itself can't be seen in CI.
- **7.2.5 The window and zoom.** New keys start `device.`. Add them to the test that device settings stay out of a backup and survive a Replace.
- **7.2.7 Save a copy.** Take out `< > : " / \ | ? *` and control characters from the file name, trim trailing dots and spaces, and avoid the reserved names from step 13.
- **7.2.8 PDF.** `org.apache.pdfbox:pdfbox` (3.0.x). Render to a `BufferedImage` at the asked width and convert to an `ImageBitmap`. On a Mac, `openPdf` stays as it is today (decision W13). Have the self-test draw one page inside the packaged app, because a missing Java module shows only there.
- **7.2.10 The wide layout, last.** Add a device setting with three values and a control for it on the Diagnostics screen, so the owner can try each on the PC. With Navigation 3's list-detail strategy: "grid, then side by side" is the collection without a detail placeholder; "wider list" keeps the placeholder and lets the list pane grow; "grid, watch as a page" turns the strategy off at wide widths and gives the detail a maximum width. Give the collection grid adaptive columns in all three. Render each option in every design, light and dark, at 1280 and 1920 dp wide, and attach or link the screenshots in pull request 2 with a one-line question: which layout? The default is "grid, then side by side". Don't delete the other two; the owner's answer decides that.

Finish win-2 as you finished win-1: version `0.10.0`, build `10`, CHANGELOG, README, the plan's "win-2 as built" notes, and a note at the top of `docs/MACOS_PLAN.md` saying which of its 8.3 items are now built in shared code and which Mac-only parts remain. Open pull request 2.

**If you run short of room in win-2,** stop at the end of an item, never in the middle of one. Leave the build green, and list in the pull request exactly which items are done and which aren't.

## 8. When you are finished

End with a short report to the owner, in plain sentences:

1. The two pull requests, and whether every workflow is green on each.
2. What the Windows runner proved, as a list.
3. What nobody has checked on a real PC, pointing at the plan's appendix A.
4. Every difference from the plan, each with its reason.
5. The one open question: which wide layout.
6. Anything you found that the plan didn't expect, including faults in the phones' or the Mac's code that running on Windows exposed.

Don't claim more than the runs showed. If something failed and you couldn't fix it, say so, with the log lines that show it.

---

# Part 2: the plan (`docs/WINDOWS_PLAN.md`)

# Watches on Windows: plan

Status: **plan only** (25 Nov 2026). Nothing is built. Written against `7effdc1` (0.8.0, with Mac milestones mac-1 and mac-2 built).

**Decided so far** (section 6): the PC is Windows 11 on an Intel or AMD processor. The first hand-off covers two milestones, win-1 and win-2. iPhone photos (HEIC) are read by a bundled libheif, built from source in CI. The Windows-like work is shared code and is built for the Mac in the same change. A coding agent in a cloud session does the work, with GitHub's Windows runner as its only Windows machine.

**What was checked for the plan**

- **Libraries, by opening the published files.**
  - `androidx.sqlite:sqlite-bundled-jvm:2.7.1` carries native code for Linux x64 and arm64, Apple silicon and **Windows x64**. It has none for Windows on ARM.
  - FileKit 0.16.0's desktop build has Windows' own open and save dialogs, reached through JNA, and brings `jna-platform` 5.19.1.
  - Compose Multiplatform 1.12.1 publishes desktop builds for Windows x64 and arm64.
- **The packaging plugin, by opening its jar** (Compose Gradle plugin 1.12.1).
  - Its Windows settings are `console`, `dirChooser`, `perUserInstall`, `shortcut`, `menu`, `menuGroup`, `upgradeUuid`, `msiPackageVersion` and `exePackageVersion`.
  - A Windows version is `MAJOR.MINOR.BUILD` with MAJOR from 0 to 255, so 0.9.0 is accepted. The Mac's workaround for a version starting with 0 isn't needed.
  - It downloads WiX 3.11.2 itself, from the WiX project's GitHub releases.
- **A build, on Linux, in a cloud session.** `./gradlew :desktopApp:test` passes: the wiring, the shell's rules, the self-test and the 18 screenshots. Maven Central limited downloads (HTTP 429); the build got through on its fifth try, and took under three minutes once the libraries were fetched.
- **CI.** Workflows run on pushed `claude/*` branches, and a session can read their logs.
- **libheif.**
  - The vcpkg port (1.23.7) builds it for Windows. Its default features include `hevc`, which is the **x265 encoder under the GPL**. The decoder alone is the `libde265` feature (LGPL 3).
  - Ubuntu 24.04 has libheif 1.17.6. Its HEVC decoder is a separate package, `libheif-plugin-libde265`, which isn't installed by default.
- **Published sources** for packaging, runners, SmartScreen, the Win32 calls and PDFBox: appendix B.
- **Not checked: anything on Windows itself.** Nothing in this plan has run on a Windows machine. A trial of a pure-Java HEIC decoder was attempted and blocked by the session's safety check, so no decoder was run here either.
- Anything not yet confirmed is marked **verify**.

Contents

1. Summary
2. Where the Windows build stands
3. How the Windows app works
4. Photos on Windows
5. What Windows shares with the Mac plan
6. Decisions
7. What needs implementing
8. Tests and CI
9. Risks
10. Not in this plan

Appendices: A, Windows checklist · B, sources

---

## 1. Summary

**The Windows app is the Mac app's shell, packaged for Windows.** `desktopApp` runs on Compose's desktop target, which is Java, and Java runs on Windows. Every screen, the three designs, the database and the backup engine are the shared code. `AppPaths` already has a Windows branch. So this is a third use of one desktop shell, not a port.

**What stands between today's code and a Windows app:**

1. **Nothing here has ever run on Windows.** Windows treats files differently from Linux and macOS: a file that is open can't be deleted, names ignore capitals, some names are reserved, and Git changes line endings on checkout. The existing tests have to pass on a Windows machine before anything else is trusted. This is the largest unknown.
2. **Photos.** The desktop build takes photos in only on a Mac, through the Mac bridge to Apple's decoders. Windows needs a decoder of its own. Java reads JPEG and PNG. WebP needs a library, and HEIC, which is what iPhones take, needs libheif (section 4).
3. **A Windows shell and installer.** Where the collection lives, the icon, one copy at a time, an MSI, and a workflow that installs it on a Windows runner and runs the self-test inside it.
4. **Behaving like a Windows app.** Keyboard shortcuts, Back without a gesture, right-click, scroll bars, a title bar in the design's colour, "this PC" in place of "this phone", PDF pages, drag and drop, and a layout for a wide window. Most of this is the Mac plan's unbuilt milestone mac-3, and it is shared code.

**Three things to know:**

- **The build is x64 only.** The bundled SQLite has no Windows ARM build. An ARM PC would run the x64 build under Windows' emulation, which is unproven for this app.
- **The build is unsigned.** Windows shows "Windows protected your PC" the first time; More info › Run anyway opens it. **Smart App Control**, which is on for many Windows 11 PCs, blocks unsigned apps with no way round for a single app. Check it before installing (appendix A). Signing is not in this plan (section 10).
- **Nothing can be tried on Windows before the first CI run.** The agent has no Windows machine. GitHub's Windows runner is its test bench, at about ten minutes a round trip, and it is Windows Server, not Windows 11. What only your PC can show is in appendix A.

**Size.** Two milestones. win-1 is about the size of mac-1 plus the photo decoder. win-2 is about the size of mac-3.

---

## 2. Where the Windows build stands

✅ ready · ⚠️ exists, with gaps · ❌ missing

| Area | Mac today | Windows today | |
| --- | --- | --- | --- |
| Shared code on the desktop target | Runs | Compiles for any desktop; never run on Windows | ⚠️ 7.1.2 |
| Database and search | Bundled SQLite, Apple silicon | Bundled SQLite has a Windows x64 build | ✅ |
| Backup engine, ZIP, CSV | Shared, tested on Java | The same code; file handling on Windows untested | ⚠️ 7.1.2 |
| Choosing and saving files | FileKit, the Mac's panels | FileKit, Windows' own dialogs | ✅ **verify** on a PC |
| HTTP for Lookup | OkHttp | The same | ✅ |
| Where the collection lives | Application Support | `AppPaths` says `%APPDATA%`, which is the wrong folder (3.2) | ⚠️ 7.1.3 |
| Photos: JPEG, PNG | Apple's decoders, through the bridge | Plain Java reads them, test-grade | ⚠️ 7.1.4 |
| Photos: WebP | Apple's decoders | Nothing reads it | ❌ 7.1.4 |
| Photos: HEIC | Apple's decoders | Nothing reads it | ❌ 7.1.5 |
| A second opinion on location | ImageIO | None | ❌ 7.1.7 |
| One copy at a time | Lock file; macOS brings the first to the front | Lock file; a second start only shows a message | ⚠️ 7.1.9 |
| Keep awake during a backup | `caffeinate` | Nothing | ❌ 7.1.9 |
| Window icon | From the bundle | Java's default icon | ❌ 7.1.9 |
| Packaging | Disk image | Nothing | ❌ 7.1.10 |
| Self-test of the packaged app | Reads the launcher's output | A Windows launcher shows no output | ⚠️ 7.1.11 |
| CI | Linux and macOS | Nothing on Windows | ❌ 7.1.12 |
| Wording | "this phone" | "this phone", and "this Mac" in the shell | ❌ 7.2.1 |
| Shortcuts, Back, right-click, scroll bars | None (mac-3) | None | ❌ 7.2 |
| Title bar | The system's | White, even over a dark design | ❌ 7.2.4 |
| PDF pages | Opens in Preview | Opens in the default app | ⚠️ 7.2.8 |
| Reminders, app lock | Not yet (mac-4) | Not in this plan | ❌ 10 |

---

## 3. How the Windows app works

### 3.1 Layers

```
Windows 11 (x64)
└── Watches (installed per user, with its own Java runtime: nothing else to install)
    ├── desktopApp        the desktop shell: main(), one window, the Windows parts beside the Mac's
    ├── shared/ui         every screen, view model, design and navigation
    ├── shared/data       Room on bundled SQLite, file store, backup engine, the desktop image codec
    ├── shared/search     Lookup sources and PoliteHttpClient (OkHttp)
    ├── shared/core       model, spec registry, units, care rules
    ├── heif.dll, libde265.dll    HEIC decoding, built in CI, loaded with JNA
    └── a few Windows calls through JNA: title bar colour, keep awake, reduce motion, window to front
```

There is no bridge module for Windows. Everything Windows-specific is either a plain C function called through JNA, or a Java library. The Mac's bridge exists because Apple's frameworks can't be called that way.

### 3.2 Where things live

- **The collection** is in `%LOCALAPPDATA%\Watches\`: the database, photos, documents and source pages. That folder stays on the PC. `%APPDATA%`, which the code uses today, is the roaming profile, which some networks copy between computers, and it is the wrong place for a large photo store.
- **The app** is installed in `%LOCALAPPDATA%\Programs\Watches\`. It must not be the collection's folder: a per-user installer's default place is `%LOCALAPPDATA%\Watches`, which is the collection's. CI checks the two differ and that uninstalling leaves the collection alone.
- OneDrive doesn't sync either folder. Backups go wherever you choose in the save dialog.
- `watches.dataDir` or `WATCHES_DATA_DIR` still points the collection somewhere else, for tests.

### 3.3 Privacy on Windows

- Only `shared/search` may reach the network. `checkNetworkIsolation` already covers the desktop shell and Java's own networking.
- No update check, and no Windows notification or location permission is asked for in this plan.
- Second copies of the app are found by a lock file and a window handle, not by a socket.
- The check that Watches only goes online during Lookup is a tool such as Windows Firewall's log or GlassWire.

### 3.4 What Windows needs that a Mac doesn't

| Item | Detail | § |
| --- | --- | --- |
| A second start | Windows starts a second process every time the icon is clicked. It must bring the first window forward and exit. | 7.1.9 |
| Open files | A file being read can't be deleted or replaced. Restores and tests must close what they open. | 7.1.2 |
| Reserved names | `CON`, `NUL`, `COM1` and the like are devices, with or without an extension. A hand-made backup could name one. | 7.1.13 |
| The installer's output | A Windows app's launcher has no console, so the self-test writes its report to a file. | 7.1.11 |
| The C runtime | A DLL built with Microsoft's compiler needs its runtime. The runner has it; a new PC may not. | 7.1.6 |
| The title bar | White unless the app asks otherwise. Windows 11 lets an app set its colour. | 7.2.4 |
| The screen reader bridge | A PC with Java Access Bridge switched on stops the app at launch unless the bundled Java runtime includes `jdk.accessibility`. | 7.1.10 |

---

## 4. Photos on Windows

The rules are already shared code (the Mac plan, section 5): formats, geometry, location removal and the conformance suite. A platform only supplies a codec. Windows supplies one, and Linux gets the same one.

### 4.1 One desktop codec

`JvmImageCodec` is test-grade today, and the Mac plan says it isn't shipped. On Windows it ships, so it becomes a real codec:

| Format | Read by | Notes |
| --- | --- | --- |
| JPEG, PNG, GIF, BMP, TIFF | Java's own readers | As now |
| WebP | TwelveMonkeys `imageio-webp` (BSD 3), plain Java | Registers itself with Java's image readers |
| HEIC | libheif with libde265 (LGPL 3), through JNA | Only where the library is present and has an HEVC decoder |
| AVIF | Not read | libheif is built without an AV1 decoder. It can be added later. |

- The codec still says which formats it reads (`ImageCodec.formats`), found at run time. The conformance suite holds it to that.
- On a Mac with the bridge, Apple's codec is used, as now. The desktop codec is what runs on Windows, on Linux, and on a Mac started without the bridge.
- **Photos are on** when the codec reads all four stored formats: JPEG, PNG, WebP and HEIC. Otherwise the screens say what is missing, as they do today for a build without the bridge.

### 4.2 What "a real codec" means

- **Only the part needed is kept in memory.** It decodes the plan's region, not the whole picture where the reader allows it.
- **Reduced by averaging.** A 12-megapixel photo becomes a 640-pixel thumbnail by averaging blocks of pixels, then one smooth step to the exact size. Dropping pixels makes a watch's dial and bracelet shimmer.
- **sRGB.** An embedded colour profile is converted. iPhones take Display P3. **Verify** with a P3 photo on the Java target only; the shared suite's wide-gamut case stays open, as in the Mac plan.
- **The size is the shared geometry's**, to the pixel, as on every platform.

### 4.3 libheif

- **Through JNA, as plain C functions.** No bridge module and no compiler in the app's build.
- **Bytes, not paths.** The app reads the file and hands libheif its bytes. libheif's file functions take a narrow string, which goes wrong on Windows for a user name with an accent.
- **libheif turns the picture itself.** It applies a HEIC's own rotation, mirror and crop boxes, so the codec reports the upright size and "already turned". The conformance suite's rotated HEIC fixtures decide whether that is right.
- **Found at run time.** The packaged app carries the DLLs among its resources and names them at start-up, as the Mac app names its bridge. `watches.heif` or `WATCHES_HEIF` names a library for tests. A library that was named and can't be used fails the tests; it never falls back quietly.
- **Two versions must agree.** Linux tests run on Ubuntu's 1.17.6, Windows on vcpkg's newer one. The binding uses only functions both have.

### 4.4 Building the DLL

- vcpkg on the Windows runner, in manifest mode, with a pinned baseline commit and `libheif` with default features off and only `libde265` on. **No x265 and no x264**: both are GPL, and the app needs no encoder.
- The DLLs must depend only on files that come with Windows 11 or sit beside them in the app. CI reads their import tables and fails otherwise. The runner has Microsoft's C runtime installed, so a missing runtime would not show in the self-test there.
- The build is cached between runs. The first run after a change to the pin takes several minutes longer.
- **Licences.** libheif and libde265 are LGPL 3. The app ships their licence texts beside the DLLs, names them on the Licences screen, and keeps them as separate files that can be replaced. HEVC is patented; shipping a decoder puts that question on whoever distributes the build. Recorded here so it is a known choice.

### 4.5 A second opinion on location

On the phones and the Mac, the system's own reader confirms a location has gone after the shared code removes it. Plain Java has no such reader. `metadata-extractor` (Apache 2.0) becomes the desktop's: it reads GPS from EXIF and XMP in JPEG, PNG, WebP and HEIC, and shares no code with the app.

---

## 5. What Windows shares with the Mac plan

Milestone win-2 is mostly the Mac plan's 8.3. The Mac plan's rule applies: whichever platform builds a shared piece first builds the common half.

| Mac plan | Shared, built once in win-2 | Windows' own | Stays in the Mac plan |
| --- | --- | --- | --- |
| 8.3.1 Menus and shortcuts | One list of commands, with when each is enabled; the key handling | Ctrl shortcuts; no menu bar | The menu bar itself is small once the commands exist, and is built here if it can be tested |
| 8.3.2 Back | Esc, and the mouse's back button | Alt+Left | ⌘[ comes with the shared key handling |
| 8.3.3 The pointer | Hover and focus tokens, right-click, scroll bars, drag to reorder | Ctrl+scroll in the viewer | |
| 8.3.4 Window and theme | The hook that tells the platform the app's appearance | Title bar colour through DWM | Transparent title bar, forced appearance through the bridge |
| 8.3.5 The wide layout | All of it | | |
| 8.3.6 Zoom | All of it | Ctrl +, −, 0 | |
| 8.3.7 Remembering the window | All of it | | |
| 8.3.8 Wording and licences | `PlatformText`, with "this PC" and "this Mac" | | |
| 8.3.9 Finder and the Dock | "Show the collection folder" | Opens Explorer | Open With, the Dock |
| 8.3.10 Accessibility | Keyboard reach | Reduce motion from Windows; `jdk.accessibility` | VoiceOver, Reduce Motion through the bridge |
| 8.3.11 PDF pages | | PDFBox, on Windows and Linux | PDFKit through the bridge, as planned |
| 8.3.12 Instead of the share sheet | Save a copy named after the document | Open, Show in Explorer | Preview, Finder |
| 8.3.13 Drag, drop and paste | All of it | | |

The Mac gets the shared column without being run on a Mac: the macOS workflow builds and self-tests it, and the next Mac build is yours to try.

---

## 6. Decisions

Made on 25 Nov 2026:

| # | Decision | Chosen |
| --- | --- | --- |
| W1 | Which PC | **Windows 11 on Intel or AMD.** The build is x64. Windows 10 isn't a target, so the title bar can take each design's colour. |
| W2 | How far the first hand-off goes | **win-1 and win-2.** The app lock, reminders and signing are left out (section 10). |
| W3 | HEIC | **Bundle libheif.** Not Windows' own codecs, which a given PC may lack and CI can't test, and not a pure-Java decoder. |
| W4 | Where the DLL comes from | **Built from source in CI** with vcpkg, pinned, decoder only. Not a third party's prebuilt binary. |
| W5 | The wide layout | **The agent builds all three options behind a switch and renders each.** You choose from the screenshots. Until then the default is "grid, then side by side". |
| W6 | Delivery | **Straight through, one pull request for each milestone**, the second stacked on the first. |
| W7 | The Mac | **Shared work is built once for both.** The Mac's own native pieces stay in the Mac plan. |
| W8 | Who builds it | **A coding agent in a cloud session**, with GitHub's Windows runner as its Windows machine. |

Going ahead on these unless you say otherwise:

| # | Decision | Recommendation |
| --- | --- | --- |
| W9 | Installer | **A per-user MSI**, with a fixed upgrade code and a Start menu entry. No desktop shortcut, no folder chooser, no administrator prompt. Never switch between per-user and per-machine: Windows won't upgrade across that. |
| W10 | Where the collection lives | **`%LOCALAPPDATA%\Watches`**, and the app in `%LOCALAPPDATA%\Programs\Watches` (3.2). |
| W11 | Names | Upgrade code and the Start menu name "Watches", **kept for good**, like the Mac's bundle ID. |
| W12 | Menu bar | **None.** A Windows menu bar is drawn by Java in the system's style, above the design. Shortcuts do the work. |
| W13 | PDF on the Mac | **Unchanged.** The Mac plan chose PDFKit. PDFBox is used on Windows and Linux, behind one condition, so the Mac can take it later if you'd rather. |
| W14 | Versions | **0.9.0 for win-1, 0.10.0 for win-2**, for every platform, as `version.xcconfig` requires. |
| W15 | Signing | **Unsigned for now**, named `…-unsigned.msi`, as the Mac's is `…-not-notarised.dmg`. |

---

## 7. What needs implementing

Sizes: **S** under half a day · **M** about a day · **L** two to three days.

### 7.1 It runs, reads your backup and takes photos (milestone win-1)

**7.1.1 Line endings** (S)
- The repository has no `.gitattributes`, and GitHub's Windows runners turn LF into CRLF on checkout. Saved pages and photos used by the tests would change under them.
- Add one: text as LF everywhere, `*.bat` as CRLF, and every fixture and binary type marked binary.

**7.1.2 The existing tests, on Windows** (M, unknown until run)
- A Windows workflow that runs the shared JVM tests and the desktop tests on `windows-latest`. This comes first, before any Windows code.
- Fix what it finds in the code, not by skipping. The likely ones:
  - a test folder that can't be deleted because a database or a picture is still open;
  - a delete or replace that fails while the image loader or a virus scanner holds the file. Retry briefly, and leave the restore journal to tidy up what can't go now;
  - a path written with `/` and compared as text.
- Nothing in the app derives a stored path from the file system: the database keeps `photos/<id>/<name>` as text. Keep it so.

**7.1.3 The shell knows which system it is on** (S)
- One small `DesktopOs` (Mac, Windows, other) in place of the `startsWith("Mac")` tests.
- `AppPaths`: the collection in `%LOCALAPPDATA%\Watches`. No Windows build has shipped, so nothing has to move.
- `platformName()` gives "Windows 11" for the backup's manifest.
- The shell's own messages ("Your collection is safe on this Mac") follow the system.

**7.1.4 The desktop codec** (L)
- Section 4.1 and 4.2: region decode, reduction by averaging, sRGB, WebP through `imageio-webp`.
- The conformance suite on Linux is its test, on every push.

**7.1.5 HEIC through libheif** (L)
- Section 4.3: the JNA binding, bytes not paths, formats found at run time.
- Constants and structure layouts are read from `heif.h` at the pinned version, not written from memory.
- `HeifLibraryTest`, like `MacBridgeTest`: where a library was named, HEIC must be among the formats and the suite's HEIC cases must have run.

**7.1.6 Building the DLL** (M)
- Section 4.4. A manifest under `tools/heif/`, the import-table check, the cache, and the licence texts copied beside the DLLs.

**7.1.7 A second opinion on location** (S)
- Section 4.5. `ImageInfo.hasLocation` on the desktop codec comes from `metadata-extractor`, for every format it reads.

**7.1.8 Photos switched on** (S)
- `platformLimits.photos` is null when the codec in use reads the four stored formats, whichever codec that is.
- The reminders and app lock sentences say "this PC" and promise no version.

**7.1.9 The shell on Windows** (M)
- A window icon, from the same drawing as the other icons.
- A second start brings the first window forward and exits: find the window, restore it, set it to the foreground. The lock file still decides who owns the collection.
- `KeepScreenOn` holds idle sleep off with `SetThreadExecutionState`, set and cleared on one thread of its own.
- Reduce motion from `SPI_GETCLIENTAREAANIMATION`.
- About is already in Settings. The Mac's application-menu handlers are skipped where the system has none.

**7.1.10 Packaging** (M)
- `TargetFormat.Msi`, per user, a fixed `upgradeUuid`, `menuGroup`, no `dirChooser`, no desktop shortcut.
- The Java runtime's modules: the Mac's list plus `jdk.accessibility`, and whatever `suggestRuntimeModules` finds for the new libraries.
- A `.ico`, drawn by an extended `tools/RenderIosAppIcon.java`.
- The DLLs and their licences among the app's resources, in the plugin's folder for Windows x64 (`windows-x64`, **verify**).

**7.1.11 The self-test on Windows** (S)
- It writes its report to `self-test.txt` in the folder it is given, ending "self-test: passed" or "self-test: FAILED". CI reads the file and the exit code; it doesn't depend on a console.
- On Windows it stores a HEIC and a WebP, and fails if the packaged app has no libheif.

**7.1.12 CI** (M)
- **Windows** (new): the tests of 7.1.2 with libheif named; the MSI; a silent install; the self-test inside the installed app; an uninstall that leaves a marker file in the collection folder untouched; the MSI kept for 14 days. On a `v*` tag it adds the MSI to the release, as the macOS workflow adds the disk image.
- **Android** (Linux, every push): installs Ubuntu's libheif with its HEVC plugin and names it, so the HEIC cases of the conformance suite run on Linux too.
- **macOS**: unchanged, and must stay green.

**7.1.13 Names a backup may not use** (S)
- `ArchivePaths.isSafe` already allows only letters, digits, `.`, `_` and `-`, so no path can leave the media folders. It also refuses Windows' device names and a name ending in a dot, on every platform, with tests.
- The app never makes such a name. Only a hand-made archive could.

**7.1.14 Docs** (S)
- A Windows section in the README: installing, the SmartScreen step, Smart App Control, where the collection is, updating, removing. Building, testing and the Windows workflow go in `docs/BUILD.md`.
- CHANGELOG, and this plan's "as built".

### 7.2 Like a Windows app (milestone win-2)

In this order. Each is finished, tested and committed before the next begins.

**7.2.1 Wording** (M) · Mac plan 8.3.8
- `PlatformText`: one object for each platform. The phones' text doesn't change by a letter. The desktop's follows the system: "this PC", "this Mac".
- The two messages in `shared/data` (`BackupException.Damaged`, `NotEnoughSpace`) get their wording from the UI layer.
- A test that no desktop screen says "phone", and that every text exists for every platform.
- Licences for the desktop build: the Java runtime, JNA, OkHttp, Skia, and the new libraries.

**7.2.2 Commands, shortcuts and Back** (M) · 8.3.1, 8.3.2
- One list of commands: New watch, Look up, Find, the four tabs, Settings, Back, Zoom in, out and reset. Each knows when it is enabled: not while a form is open, not while locked.
- Keys at the window's root: Ctrl on Windows, ⌘ on a Mac. Esc, Alt+Left and the mouse's back button go back. A changed form still asks before discarding.
- Windows has no menu bar (W12). The Mac's menu bar is built from the same list if a desktop test can drive it; otherwise it stays in the Mac plan.

**7.2.3 The pointer** (M) · 8.3.3
- Right-click wherever a long press does something. Ctrl+C copies the focused value.
- Scroll bars in each design's colours.
- Hover and focus looks as `DirectionStyle` tokens: a hairline for Atelier, a 1 dp frame for Instrument, a soft tint for Gallery.
- Photos reorder by dragging straight away. The viewer zooms with Ctrl+scroll and a double-click.

**7.2.4 The title bar** (S) · 8.3.4
- The appearance hook from `WatchTheme`. On Windows 11 the title bar takes the design's ground colour and ink through `DwmSetWindowAttribute`, and follows a change of design or of Light and Dark.

**7.2.5 The window and zoom** (S) · 8.3.6, 8.3.7
- Size, place and maximised, and the zoom from 0.85 to 1.5, under `device.*` keys, so they never travel in a backup.

**7.2.6 The collection folder, and reduce motion** (S) · 8.3.9, 8.3.10
- "Show the collection folder" on the Privacy page opens Explorer or Finder.

**7.2.7 Instead of the share sheet** (S) · 8.3.12
- Open, Show in Explorer, and Save a copy… named after the document's title, with the characters Windows forbids taken out.

**7.2.8 PDF pages** (S) · 8.3.11
- `openPdf` through PDFBox on Windows and Linux (W13). The self-test draws a page inside the packaged app.

**7.2.9 Drag, drop and paste** (M) · 8.3.13
- Photos on a watch's Photos screen, files on Documents, a `.zip` anywhere for the restore preview, a pasted picture as a photo. All through `PhotoIntake`.

**7.2.10 The wide layout** (M) · 8.3.5, last
- The rail's label, and a maximum width for forms and settings.
- The three options of the Mac plan's 6.3 behind one device setting, each rendered in every design, light and dark, at 1280 and 1920 dp wide. "Grid, then side by side" is the default until you choose (W5). The other two are removed once you have.

---

## 8. Tests and CI

| Layer | Runs on | Covers |
| --- | --- | --- |
| Shared tests | Linux on every push; **Windows** on every push that touches shared or desktop code | Everything they cover today, now also on Windows' file system |
| Photo conformance | The same two, with libheif named | The desktop codec against the same photos as every other platform, HEIC included |
| Desktop tests and screenshots | Linux and Windows | The wiring, the shell's rules, commands and keys, the screenshots |
| The installed app | The Windows runner | The MSI installs without a prompt, the self-test passes inside it, uninstalling leaves the collection |
| Windows checklist | Your PC | What CI can't do (appendix A) |

New tests: `HeifLibraryTest`, a WebP and a P3 case for the desktop codec, `ArchivePaths` with reserved names, `AppPaths` for Windows, the second-start rule, `PlatformTextTest`, command enablement, the Back keys, zoom and window settings staying on the device, and the PDF page.

**The Windows runner is Windows Server 2025**, not Windows 11. It proves the code, the installer and the bundled libraries. It doesn't prove the title bar's colour, the file dialogs, SmartScreen or how the app feels.

---

## 9. Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| No Windows machine while building | Faults are found ten minutes at a time, or only on your PC | Tests on the Windows runner before any Windows code; the self-test inside the installed app; appendix A |
| File handling on Windows | A restore or a delete fails where it works on a phone | The whole backup suite runs on Windows; deletes that can't go now are left to the journal |
| The libheif binding | A wrong structure or constant crashes the app, with nothing to read | Read from `heif.h` at the pinned version; proven on Linux first, where it can be run and debugged |
| The DLL needs a runtime your PC lacks | Photos work in CI and not on your PC | CI reads the DLLs' import tables |
| GPL code in the build | The app couldn't be distributed as it is | Default features off; CI fails if x265 or x264 appears among the built files |
| Smart App Control | The app won't open at all | Appendix A checks first; signing is the real fix (section 10) |
| Shared changes reach the phones and the Mac | A regression on the phone you use every day | The phones' text must not change; Android, iOS and macOS workflows stay green; one item at a time |
| The Mac gets code no Mac has run | A shortcut or wording fault on the Mac | The macOS workflow's self-test; the next Mac build is yours to try |
| win-2 is large | The agent runs out of room part-way | Ordered by value, one finished item at a time; the pull request says what is and isn't done |
| Compose on Windows | Small differences in text, scrolling or scaling between monitors | Checked on your PC; updating the toolkit is routine |

---

## 10. Not in this plan

- **The app lock.** Windows Hello needs hand-written calls into Windows' runtime. It is the riskiest piece to build without a PC. The screen says it isn't here yet.
- **Reminders.** A notification with the app closed needs an identity Windows gives packaged apps. The Service tab lists what is due, as now.
- **Signing.** Microsoft's own signing service takes individuals only in the US and Canada. The free route for open-source projects signs under the foundation's name and approves each release by hand.
- **Windows on ARM**, as a build of its own. **Windows 10.**
- **The Microsoft Store, winget and MSIX.**
- **AVIF.** A camera on Windows. The system Share window. Opening a `.zip` with Watches from Explorer.
- **An update check.** It would be a network request outside Lookup.

---

## Appendix A: Windows checklist

Run on your PC with a downloaded build.

**Before installing**
- [ ] Windows Security › App & browser control › Smart App Control. If it is On, an unsigned Watches won't open; decide whether to turn it off before going further.

**Install and update**
- [ ] The MSI installs without asking for an administrator. Windows shows "Windows protected your PC"; More info › Run anyway.
- [ ] Watches is in the Start menu, opens to the welcome screen, and shows its own icon in the taskbar.
- [ ] Opening it twice gives one window, brought to the front.
- [ ] A later build installs over it and keeps the collection.
- [ ] Uninstalling in Settings › Apps leaves `%LOCALAPPDATA%\Watches` where it is.

**Moving a collection**
- [ ] A backup from each phone restores with Replace. The counts match. Photos, crops, documents and service history are all there, and the design, accent and Light or Dark are the phone's.
- [ ] A backup made on the PC restores on each phone, and on the Mac.
- [ ] The open and save dialogs are Windows' own.

**Photos**
- [ ] From Explorer: an iPhone HEIC, a portrait shot, a PNG, a WebP, then 20 at once. Each is upright, and its colours look as they do on the phone.
- [ ] Crop a photo that came from the iPhone. Restore the PC's backup on the iPhone: the crop shows the same region.
- [ ] A photo with a location, added on the PC, has none in the stored original.

**Like a Windows app** (win-2)
- [ ] Ctrl+N, Ctrl+F, Ctrl+L, Ctrl+1 to 4, Ctrl+comma. Esc, Alt+Left and the mouse's back button go back, and a changed form asks first.
- [ ] Right-click copies a spec value. Scroll bars show. Photos reorder by dragging.
- [ ] The title bar matches each design, in Light and Dark.
- [ ] The window remembers its size and place. Zoom in and out.
- [ ] A PDF shows its pages. Open, Show in Explorer and Save a copy… work.
- [ ] Drop photos, a document and a backup on the window.
- [ ] No screen says "phone" or "Mac".
- [ ] The three wide layouts, from the screenshots: choose one.

**Still to make** (the Mac plan's, unchanged)
- [ ] A real iPhone HEIC through the location removal, and one backup from each phone for the test fixtures.

---

## Appendix B: sources

Checked on 25 Nov 2026. Nothing below was tested on a Windows machine.

**Packaging**
- Compose native distributions: https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html
- jpackage's MSI upgrades allow both upgrade and downgrade: https://github.com/openjdk/jdk21u/blob/master/src/jdk.jpackage/windows/classes/jdk/jpackage/internal/resources/overrides.wxi
- Windows Installer won't upgrade between per-user and per-machine: https://learn.microsoft.com/en-us/windows/win32/msi/major-upgrades
- Open plugin issues: a folder chooser with a per-user install (CMP-9256), uninstall removing a chosen folder (CMP-4245), no upgrade despite an upgrade code, not reproduced (CMP-8683)
- The Windows launcher passes its exit code on and has no console: https://github.com/openjdk/jdk21u/blob/master/src/jdk.jpackage/windows/native/applauncher/WinLauncher.cpp

**Runners**
- `windows-latest` is Windows Server 2025; Git turns LF into CRLF there: https://github.com/actions/runner-images , https://github.com/actions/checkout/issues/135

**Unsigned builds**
- SmartScreen: https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation
- Smart App Control: https://support.microsoft.com/en-us/topic/what-is-smart-app-control-285ea03d-fa88-4d56-882e-6698afdb7003
- Artifact Signing, individuals in the US and Canada only: https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart
- SignPath Foundation's terms: https://signpath.org/terms

**Compose on Windows**
- No title bar API (CMP-3928); dark mode follows Windows live since 1.12.0-beta02 (CMP-1986); the window icon (CMP-2537)
- Accessibility needs `jdk.accessibility`: https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html , and the crash without it (CMP-373)

**Windows calls**
- Title bar colour: https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/ne-dwmapi-dwmwindowattribute
- Keep awake: https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-setthreadexecutionstate
- A second start and the foreground: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setforegroundwindow , https://devblogs.microsoft.com/oldnewthing/20090220-00/?p=19083
- File names: https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file

**Photos**
- Windows' own HEIC needs a codec that not every PC has: https://learn.microsoft.com/en-us/windows/win32/wic/native-wic-codecs , https://apps.microsoft.com/detail/9nmzlz57r3t7
- libheif: https://github.com/strukturag/libheif · the vcpkg port: https://github.com/microsoft/vcpkg/tree/master/ports/libheif
- LGPL 3: https://www.gnu.org/licenses/lgpl-3.0.html
- TwelveMonkeys WebP: https://github.com/haraldk/TwelveMonkeys/wiki/WebP-Plugin
- metadata-extractor: https://github.com/drewnoakes/metadata-extractor
- PDFBox 3: https://pdfbox.apache.org/
