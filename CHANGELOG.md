# Changelog

## Unreleased

**One photo component for Android, iPhone and Mac (milestone mac-2, in progress)**

The Mac plan's section 5: each platform had its own image code, and nothing tested that they agreed. The rules are moving into shared code.
- **A photo's location is removed by shared code, on every platform.** It finds the GPS data in a file's EXIF and XMP and overwrites it where it lies, in JPEG, PNG, WebP and HEIC. The file keeps its length, and the picture, the capture date and everything else are untouched. Until now Android couldn't remove the location from a HEIC at all, the iPhone probably couldn't from a WebP, and a failure was silent.
- **An original that can't be cleaned isn't kept.** If a file's metadata can't be followed far enough to be sure, the app keeps a full-size JPEG made from the photo instead, which carries no metadata.
- **A second opinion, each time.** Once the shared code has removed a location, the platform's own reader is asked whether it still finds one (Android's `ExifInterface`, Apple's ImageIO). If it does, the photo is kept as a JPEG made from it instead.
- **One Apple image codec, for the iPhone and the Mac.** `AppleImageCodec` replaces the iPhone's image code. It reads a photo's size and rotation without decoding it and decodes straight to the size needed, where the old code decoded every photo at full size: a 48-megapixel photo's thumbnail now costs a few megabytes of memory, not about 190. Its crop and size come from the shared arithmetic, so an iPhone's renditions match Android's to the pixel. The Mac will reach the same code through a small native library.
- **Formats only some devices show are kept as JPEG.** A TIFF, GIF, BMP or camera raw file added as a photo is stored as a full-size JPEG made from it, so a restored copy shows on every device. Formats are told from a file's bytes by one shared function: JPEG, PNG, WebP and HEIC are stored as they are.
- **What a crop means in pixels is shared arithmetic** (`RenderPlan`): the rectangle to cut and the size to write come out the same on every platform. Android's rotation maths moved there, with tests for all eight orientations.
- **The Mac reaches that code through a bridge.** A new `macBridge` module builds it as a small native library, which the Mac app carries and loads, because Java can't call the Mac's own frameworks. Where there is none (Linux, where the tests and screenshots run) plain-Java image code stands in. Photos stay switched off on the Mac until the Crop screen and the way in use it too.
- **Each platform says which formats it can read.** No system reads them all: Android has no TIFF decoder, and AVIF depends on the system's version. A format a device can't read is now refused cleanly instead of failing part-way.
- **A HEIC's own crop and rotation are read by shared code.** A HEIC keeps its rotation in its container, and can also say that only part of the coded picture is the picture (small and odd-sized pictures are coded that way). Android's decoder ignores that part, and showed padding. The shared code now reads both, and Android cuts and turns by it. Found by the suite's first run on an Android emulator.
- **Tests with real photos.** Small photos with a known location, rotation and format are compiled into the shared tests, which run on the JVM, on an Android emulator, on the iPhone simulator and natively on a Mac. CI then reads the cleaned photos back with a different library (Pillow) to confirm the location has gone and the picture is unchanged. The same code was run on real photos from a Nikon camera and a Nokia phone, HEIF included, with the same result.
- Still to come in this milestone: the Crop screen on the same decoder, one way in for every photo, the one-time pass over photos already stored, and photos switched on for the Mac.

## 0.7.0: Mac, milestone mac-1 (23 Nov 2026)

**The Mac app**

The first step of the Mac plan (`docs/MACOS_PLAN.md`): Watches as a Mac app, from the same shared code as the phones. It needs an Apple silicon Mac on macOS 13 or later. README › Mac has how to install it.
- **A Mac app.** A new `desktopApp` module is the Mac's shell, as `androidApp` and `iosApp` are the phones'. Every screen, the three designs, the database and the backup engine are the shared code, unchanged.
- **Backups both ways.** Backup, restore and CSV export work as on the phones, through the Mac's own open and save panels. A backup's manifest names the Mac version that made it.
- **The collection** lives in `~/Library/Application Support/Watches/`. It stays when the app is updated or dragged to the Bin.
- **One copy at a time.** A second copy says Watches is already open, rather than opening the same database.
- **A collection from a newer build** is refused with a message and left untouched. A phone can't meet this, because the system won't install an older build over a newer one; a Mac can.
- **Quitting during a backup or restore** asks first. Stopping removes a half-written backup, as Cancel does.
- **What the Mac leaves out for now**, each with a sentence on the screen where it would be:
  - adding and cropping photos, saving a Lookup picture, and photos as documents. Photos from a backup show as usual. The desktop's image code doesn't yet follow the phones' rules for rotation, location removal and HEIC, so the Mac writes no photos until the shared photo component is done (plan, section 5);
  - reminders and the app lock;
  - PDF pages inside the app. "Open with another app" opens the PDF in Preview.
- **Not yet Mac-like:** no menus or shortcuts beyond About and Quit, and screens still say "this phone". Both are milestone mac-3.
- **The disk image isn't notarised yet**, so macOS needs one extra step to open each build (README › Mac › Installing).
- **One version for every platform.** `version.xcconfig` is now 0.7.0 (build 7) for Android, iPhone and Mac. The Mac's packaging tool refuses a bundle version that starts with 0, so until 1.0.0 Finder's Get Info shows 1.0.7 instead. About, the disk image's name and every backup carry 0.7.0.

**Mac: tests and CI**
- The Mac app is tested on Linux, on every push, because its code runs there. The Android workflow now compiles it, runs its tests, and keeps 18 screenshots: every design, light and dark, at phone, rail and two-pane widths.
- `Watches --self-test` checks a build without opening a window: wiring, a collection, a backup restored into itself, a CSV and a drawn frame. CI runs it inside the packaged app, on Linux and inside the Mac disk image, because the bundled Java runtime is trimmed and a missing piece shows only when the app runs.
- A new **macOS** workflow builds the disk image on an Apple silicon runner, runs the self-test inside it, and checks the bundle's identity, version and signature. On a `v*` tag it adds the disk image to the GitHub release, beside the APK.
- `checkNetworkIsolation` now covers the Mac shell, and looks for Java's own networking as well as the libraries and Apple's APIs.
- The Mac icon is the same watch at 10:10, drawn by `tools/RenderIosAppIcon.java` into the Mac's rounded-square shape.

**Shared code, for all three platforms**
- The camera button is asked for through one small function with a version per platform, because the camera launcher exists only on phones. Where there's no camera, the buttons aren't shown.
- A platform can say what it leaves out for now (`PlatformLimits`), and the screens show its sentence in place of the buttons. The phones leave nothing out.
- The schema version has one home, `AppDatabase.VERSION`, which the backup engine and the Mac's newer-collection check both read.

**Plans**
- `docs/MACOS_PLAN.md`: the Mac plan, and in its section 5 one photo component for Android, iPhone and Mac. Each platform has its own image code today and nothing tests that they agree; the plan moves the rules into shared code, with one test suite every platform must pass. It takes over the iPhone plan's photo items.

**Android releases**
- Every release APK is now signed, so Android installs it, with no setup. Without signing secrets it's signed with the build machine's debug key. Each CI run has a new debug key, and Android won't update an app signed with a different key, so moving to a newer build means: back up (Settings › Backup & restore), uninstall, install, restore. Your own key, which is optional, makes updates install over the top.
- Pushing a `v*` tag publishes the signed APK as a GitHub release, with that version's changelog entry as its notes, so it stays downloadable after the build's 14-day artifact expires. The release refuses an unsigned APK, and a tag that doesn't match `version.xcconfig`.
- Every build's APK is named for its version (`Watches-0.6.0-6.apk`). The run's summary says which key signed it, with the certificate's SHA-256. An unsigned APK would say so in its name, with a warning on the run.
- GitHub passes a secret that doesn't exist to the build as an empty value. An empty signing setting now counts as unset, so leaving out `WATCHES_KEY_ALIAS` or `WATCHES_KEY_PASSWORD` uses the defaults instead of failing the signed build with "No key with alias ''".
- A new **Make Android signing key** workflow, optional and run by hand, makes the key on GitHub, so nothing needs installing. A workflow can't save secrets, so it hands the key back as a download, locked with a password you add as a secret first and deleted after a day, for you to paste in and keep. It won't make a second key once one is saved.
- README › Release APK and signing explains the debug-key default, and, for your own key, both ways to make it and which secrets to add.

## 0.6.0: iPhone, milestone iOS-1 (21 Nov 2026)

The first step of the iPhone plan (`docs/IOS_PLAN.md`): everything needed to put a signed build on your iPhone through TestFlight, and the shared tests running on iOS. The app itself behaves as in 0.5.0.

**Getting onto your iPhone**
- A new **iOS release** workflow archives a Release build and uploads it to TestFlight, on version tags and on demand. It needs a paid Apple Developer Program membership and seven repository secrets, but no Mac: the README's "Signing and TestFlight" section has every step, including making the certificate with OpenSSL. Until the secrets exist, the workflow builds and checks an unsigned archive, so the release path is already tested.
- An app icon, drawn from the Android icon by `tools/RenderIosAppIcon.java` so both phones show the same watch at 10:10.
- The launch screen is the design's ground colour, light or dark, rather than plain white. The first frame matches it, on Android too.
- A privacy manifest: nothing is tracked or collected, and the system APIs Apple asks apps to explain are declared. CI compares it with the built app (`tools/check-privacy-symbols.py`), since App Store Connect refuses uploads that leave one out.
- iPhone only for now, and one window at a time.
- The camera's purpose string now mentions papers, and a new one lets the share sheet's "Save Image" work: without it, saving a shared photo would have crashed the app.

**One version for both phones**
- `version.xcconfig` holds the version (0.6.0) and build number (6). Android's version name and code and the iPhone's version both come from it. TestFlight uploads add the workflow's run number, so each upload gets a new build number.

**Tests on iOS**
- 136 tests now run on the iPhone simulator, up from 53. The repository, care, backup and restore tests, the Lookup parsers against saved pages, the polite HTTP client, and the app lock, care, edit-form and colour-contrast rules moved into shared test code. They still run on the JVM, on every push.
- Checks that need `java.util.zip` (archives open in standard tools, re-zipped archives restore, ZIP64, JSON keys match the schema) stay JVM-only, as `ArchiveInteropTest`.
- New iOS tests: every definition the iPhone app wires up can be built, and Lookup's iOS network sessions keep no cookies and no cache.
- The Xcode project gains two test targets. `iosAppTests` runs inside the app and checks what the build put in it: the privacy manifest, purpose strings, one window, the icon and launch colour, the version and the bundled fonts. `iosAppUITests` launches the app and finds its welcome screen.
- Parser fixtures moved to `shared/search/src/commonTest/fixtures` and are compiled into the tests, so no platform reads files at run time.

**Build and CI**
- The iOS workflow uses Xcode 26, like the release build, and runs every module's shared tests, the app's own tests and the privacy check. It keeps Kotlin/Native's simulator build of every library between runs, so a run takes about 16 minutes rather than 26.
- The Android workflow also compiles every module's tests for iOS, runs the shared UI tests, and runs `checkIosProject`, which checks the iOS project spec, the version, the icon and the privacy manifest on Linux.
- `checkNetworkIsolation` also fails if core, data or ui calls Apple's networking APIs, which need no library.

**What you need to do**
- Join the Apple Developer Program and follow README › iPhone › Signing and TestFlight. Then run Actions › iOS release.
- Decisions still open from the plan, with their defaults: the minimum iOS version stays 17 (D5) until you say which iPhone you have; library photos stay HEIC (D7), handled in iOS-3; macOS minutes use GitHub's allowance (D10), which costs nothing while the repository is public.

## 0.5.0: Three designs, refined (18 Oct 2026)

All three designs are now permanent. Each has been refined as a complete visual system, with its own type, colour, shape, ornament and motion. You choose between them in **Settings › Appearance**. The brief for each is in `docs/DESIGN.md`.

**Choosing a design**
- Appearance shows each design as a live specimen, drawn in its own style: its ground, its type, its rule, a watch in miniature, and how it sets a spec line. Tap one to switch.
- Each design suggests four accents, with its house colour first. The other three are under "More colours".
- Theme (System, Light or Dark) is a segmented control.

**Atelier: the catalogue**
- Mastheads have an italic count line ("8 watches · 7 owned") and a thick–thin double rule, as in a printed auction catalogue.
- Tiles are tall catalogue plates numbered "No. 1", "No. 2"… in italic.
- A champagne accent dash marks the active tab, the active navigation item and every spec section.
- Buttons, tabs and navigation labels are set in tracked capitals. Square corners throughout, including the add button and the floating header buttons.
- Secondary actions such as "Look up specs" are hairline frames rather than tinted blocks.

**Instrument: the data sheet**
- Mastheads end in a minute-track scale with a lume pip at twelve.
- The hero photo carries focus brackets.
- Status lights are square LEDs.
- Secondary actions are 1 dp frames in the accent, so only the main action is a solid block.
- Tiles, spec sections and counts stay zero-padded and numbered.

**Gallery: the white room**
- Cards, tiles, stats and panels float on a wide, soft shadow in daylight.
- Captions read like a gallery wall card.
- Pills, soft tints and the floating navigation are kept, with no capitals anywhere.

**Across the app**
- Every top-level tab (Collection, Lookup, Service, Settings) uses the same masthead.
- Presses move in each design's own way: Atelier slow and slight, Instrument short and linear, Gallery on a soft spring.
- Text you typed (document titles, brand and watchmaker suggestions) keeps its own case on chips, even in the designs that set controls in capitals.
- Muted text in Atelier's light mode is slightly darker, to pass 4.5:1 on raised panels.
- A new test checks every design, mode and accent: text and accents at 4.5:1, and control edges at 3:1.

**Under the hood**
- Each design is now one style specification: case, tile shape and numbering, tab, field and button language, rules, markers, depth, motion and shapes. Components read these tokens instead of checking which design is active, so all three stay consistent as screens are added.
- Screenshot tests cover all three designs in light and dark, including the full spec sheet and the design picker.

## 0.4.0-m4: Milestone 4, backup and polish (18 Oct 2026)

**Backup & restore** (Settings › Backup & restore)
- **Back up now** saves one .zip file wherever you choose: on the phone, a USB drive or a cloud folder. It holds every watch, photo (with its crop), document, service record and saved source page, plus your settings.
- The screen shows when you last backed up and what a backup would hold. Settings warns when there's no backup, or the last one is over a month old.
- **Restore from a backup** first shows what the backup holds and when it was made, then offers:
  - **Merge** (recommended). Adds what's new, and lists every watch that differs and which version wins, before you commit. Nothing on the phone is deleted. For each watch, the more recently edited side wins, and service histories are combined.
  - **Replace**. Swaps this phone's collection and settings for the backup's, after a confirmation.
- A report afterwards lists what was added, updated and kept.
- Safe by design:
  - Every file is checked against its SHA-256 before the collection is touched, and the changes are saved in one step.
  - A damaged backup changes nothing.
  - A restore stopped halfway is undone at the next launch, or finished if it had already been saved.
  - Only files the restore itself replaced are removed, so a photo added while it runs is safe.
  - Restoring onto the same phone reuses the files already there.
- Long backups keep running if you leave the screen, show progress, can be cancelled, and keep the screen awake.
- A backup re-zipped on a computer, for example with macOS Archive Utility, still restores.
- The format is documented in `docs/ARCHIVE_FORMAT.md`. It's an ordinary ZIP with a JSON manifest, whose keys are the database's column names.

**Export as spreadsheet**
- A CSV file with one row per watch:
  - every spec, with units in the column names;
  - status, complications, last and next service, warranty end, box & papers, and counts of photos and documents.
- It opens correctly in Excel, Numbers and Google Sheets (UTF-8 with a byte-order mark, RFC 4180 quoting). Text that a spreadsheet would run as a formula is neutralised.

**App lock** (Settings › Privacy & security)
- Optional. Unlocks with your fingerprint, face or phone PIN. It locks again after a delay you choose (immediately, or after 1, 5 or 15 minutes), so a trip to the camera or file picker doesn't ask again.
- Turning it on needs one successful unlock, so nobody locks themselves out. If the phone's screen lock is later removed, the app lock pauses rather than locking you out.
- The lock screen covers any open dialog or sheet, and the back gesture can't get past it.
- On Android 13+ the app switcher shows a blank card while the lock is on.
- App lock and the last-backup date belong to the phone. They're never written to a backup or replaced by a restore.

**Settings, reorganised**
- Settings is now a single list of grouped rows, each showing its current value: Backup & restore, Appearance, Units & formats, Lookup sources, Service reminders, App lock, About.
- Each row opens its own page. The "coming later" notes are gone.

**Polish**
- **First run.** A welcome screen with Add a watch, Look up a reference and Restore from a backup. Search, tabs and filters appear with the first watch.
- **Collection:**
  - Tiles have a legible status pill, the reference and the size/movement on fixed lines, and respond to a press.
  - Placeholder tiles show while loading.
  - "Add watch" is the same in every design and shrinks to its icon as you scroll.
  - The search field has an icon, and the tabs fade at the edges where there's more.
  - "No matches" offers Clear search or Clear filters.
- **Watch detail:**
  - Edit is in the header, with one "Look up specs" action.
  - Photos open from a camera button on the picture, or "Add photo" when there are none.
  - The header turns solid with the watch's name as you scroll.
- **Undo.** Archive and delete show an Undo snackbar. A deleted watch is only removed for good once Undo expires.
- **Controls:**
  - Fields, outlined buttons, chips and switches have clearer edges (about 3:1 contrast).
  - Box & papers and the sort order use segmented controls.
  - Main actions sit in a pinned bar at the bottom instead of floating over lists.
  - New icons for Settings, filters, box & papers and documents.
- **Type.** A header style for each design, lining figures in Atelier titles, tabular figures in Instrument specs, and nothing smaller than 11 sp.
- **Accessibility:**
  - Switches and checkboxes are single controls for TalkBack.
  - Chips report what they are (tab, choice, filter or button), and touch targets are at least 48 dp.
  - Spec rows only offer an action when there is one, and headings are marked.
  - At large text sizes, figures go two to a row and spec labels sit above their values. A test checks the detail screen at 200% text size.
- **Forms.** Keyboard capitalisation suits each field, with no autocorrect on references, serials or calibres. A Save with mistakes scrolls to the first one.
- **Feel.** Subtle haptics on tab changes, switches and finished backups. List changes animate. Reduced motion is respected and re-checked when you return to the app.

**Repository and privacy**
- No secrets were ever committed. Signing material is ignored by Git, and CI only decodes the key into a temporary folder.
- Ready to go private:
  - iOS CI runs for iOS changes, version tags and on demand.
  - CI artifacts are kept for 14 days.
  - The Lookup User-Agent links to the owner's GitHub profile instead of the repo.
- New Android permission: biometric prompt. It's used only for the optional app lock, and Android shows no permission prompt for it.

**Tests**
- 150+ automated tests:
  - a byte-exact export, wipe and restore;
  - merge rules;
  - damaged, foreign and re-zipped archives;
  - interrupted restores and ZIP64;
  - JSON keys checked against the database schema;
  - a strict CSV parse;
  - app-lock timing.
- The ZIP tests also run on the iOS simulator in CI, so the iOS decompression is checked on iOS itself.

## 0.3.0-m3: Milestone 3, service, box & papers and documents (17 Oct 2026)

**Service**
- Each watch has a Service screen: its interval (none, or 1 to 10 years), when the next service is due, and the full history.
- Log a service with:
  - the date and who did it;
  - the work done: full service, regulation, crystal, gaskets, battery, polish or other;
  - cost and currency;
  - warranty on the work;
  - notes;
  - the invoice or other paperwork attached as PDFs or photos.
- Attaching or removing paperwork takes effect when you tap Save, so Discard leaves the stored files as they were. If a file can't be attached, the service is still saved and the file stays listed to remove or try again.
- Only a full service restarts the interval. Without one, the next service is counted from the date acquired. Smaller jobs, such as a battery or regulation, show as "last serviced" but don't move the due date.
- The detail view shows the last service and the next due date, in red once overdue.
- The Service tab lists the collection by urgency:
  - Overdue, Due soon (within 3 months), At the watchmaker and Later.
  - Warranties ending: watch warranties ending within 3 months or ended in the last 3 months, plus service warranties ending within 3 months.
  - Watches that need a start date.
- Sold, wishlist, incoming and archived watches are left out.
- "Serviced by" suggests watchmakers you've used before.

**Reminders**
- An optional reminder per watch, off by default, sent as a notification at 9 am on the due date.
- Tapping the notification opens that watch's service history. If a form is open, it waits until you've saved or discarded it.
- On iOS, reminders also show while the app is open.
- Android 13+ asks for notification permission the first time you switch one on. Settings › Service reminders shows whether notifications are allowed.
- Reminders are scheduled on the phone with WorkManager on Android and local notifications on iOS. No server is involved.
- Reminders move when you log a service or change the interval. They are cancelled when a watch is sold, archived or deleted.
- On Android, a reminder held back by battery saving still arrives, even if you open the app first.

**Box & papers**
- A checklist covering inner box, outer box, warranty card and papers, manual, hang tags, extra links, receipt and other.
- Each item is marked present, missing or not applicable, with an optional note. Tap the chosen state again to clear it.
- "Rest not applicable" marks every unrecorded item at once.
- A Full set badge appears on the detail view when every item is recorded, none is missing and at least one is present.
- Watch warranty: a start date and length (none, or 1, 2, 3, 5 or 8 years; other lengths can be typed in the edit form). The detail view and the checklist show when it ends, such as "Warranty until 01/12/2026 (in 2 months)" or "Warranty ended 01/12/2025".

**Documents, manuals and quick start guides**
- Attach PDFs and photos to a watch or to a service entry. You can choose a file or photograph a paper copy.
- Document kinds: Manual, Quick start guide, Receipt, Invoice, Warranty, Certificate and Other.
- Manuals and quick start guides are listed first on the watch, and open with one tap. "Add manual" and "Add quick start" appear when one is missing.
- A manual or guide already stored for another watch can be reused. Matches from the same brand are listed first.
- Duplicating a watch copies its manual and quick start guide.
- The box & papers checklist notes when an electronic manual is stored.
- The built-in viewer shows PDFs page by page, and photos, with pinch to zoom. Share opens the system share sheet, with "Open with another app" as a fallback when a PDF can't be shown.
- Documents can be renamed, have their kind changed, or be deleted.
- Files are kept in app-private storage and will be included in backups (milestone 4).
- The file type is checked from its content: PDF, JPEG, PNG, WebP or HEIC, up to 50 MB. HEIC photos are stored as JPEG, so every viewer and app can open them.
- Location data is removed from photos.
- Search now also finds watches by watchmaker, service notes, work done and document titles.

**Sample data**
- About half the generated sample watches get a full service, and each sample watch gets a partly filled box & papers checklist, so the Service tab has something to show.

**Privacy**
- New Android permission: notifications, for service reminders only, and only requested when you switch a reminder on.
- Shared files are offered to other apps one at a time through a FileProvider limited to the documents folder.
- WorkManager starts only when a reminder is scheduled, not at launch. Its foreground-service permission is removed because reminders never need it.

**Tests**
- 127 automated tests, including:
  - the due-date, warranty and full-set rules;
  - repositories for service, box & papers and documents, with file storage;
  - which watches get reminders and how the Service tab groups them.
- Screenshot tests cover the Service tab, service history, the service editor, box & papers, documents and the detail view's new section.

## 0.2.0-m2: Milestone 2, Lookup (14 Oct 2026)

**Lookup**
- A Lookup tab, plus "Look up specs" on every watch and in the edit form.
- Sources:
  - Wikidata and Wikipedia: brand, collection, history, official site.
  - Ranfft DB: calibre specs, with the site owner's permission.
  - Grail Watch Reference: movement specs.
- Results stream in per source. A slow or broken source never holds up the others, and shows its own message with Retry.
- Each source can be switched off, here or in Settings. Recent searches are kept on the phone and can be cleared.
- Offline, Lookup says so and sends nothing.
- Result preview: the specs and extra specs a source offers, its summary with credit, and a link to the page.
- "Use these specs" combines results from several sources.

**Review before anything changes**
- One screen shows every field with its current value and each source's value.
- Values you typed or edited start unticked and are marked "Your value", so a lookup can't replace them without your tick.
- When sources disagree, you pick one.
- Applied values record their source, page and fetch date. Extra specs replace earlier ones from the same source. Each source page is kept with the watch so it can be re-read later.
- "Add to collection" opens the edit form pre-filled. Nothing is saved until you tap Save.
- "Refresh from sources" on a watch re-reads the pages its specs came from and shows what changed.

**Pictures**
- Ranfft pictures are shown in the preview and never saved.
- Freely licensed Wikimedia Commons pictures can be saved to a watch with their credit. They never become the cover unless you choose it.

**Being a good citizen**
- One request per second per site at most, an honest User-Agent, no cookies and a 10-second timeout.
- `robots.txt` is checked and obeyed (Wikimedia's APIs follow its API policy instead). "Please wait" responses are honoured.
- Bot-protection pages are reported, never bypassed.
- Settings › Request log lists every request since the app opened, including ones it refused to send.
- WatchBase: a one-tap connection test only. Search arrives once its pages can be checked (see `docs/SOURCES.md`).

**Fixes from review of milestone 1**
- Crop no longer crashes when a corner handle is dragged to the far edge.
- The size filter no longer crashes when a saved range falls outside the collection's sizes.
- Back on a changed edit form asks before discarding. Tabs hide while a form is open.
- Saving the form only rewrites fields you changed, so stored values keep their precision and source.
- Photo imports and crops finish even if you leave the screen.
- One-finger swipes page between photos in the viewer again.
- On 600–839 dp screens the detail view has its back arrow. Opening a watch never stacks two details.
- The brand filter ignores capitalisation.
- Extra specs on a watch are headed "Other specs".

**Privacy**
- The Android app now declares network access, needed for Lookup.
- Nothing network-related starts at launch: the HTTP client is built on first use.
- A build check keeps network libraries out of every module except search.
- Still no accounts, analytics or crash reporting.

**Tests**
- 105 automated tests, including parser tests for every source against saved pages.
- The politeness rules are tested: spacing, robots.txt, back-off, timeouts and blocks.
- Screenshot tests cover the Lookup, Review and Request log screens.
- An opt-in live test (`WATCHES_LIVE_TESTS=1`) runs each source against the real site. It was run against Ranfft DB, Grail and Wikidata/Wikipedia for this release.

## 0.1.0-m1: Milestone 1, foundation (14 Oct 2026)

**Collection**
- Photo grid and compact list, toggled from the header.
- Instant local search across every field, including notes and serial number. It ignores accents and matches references typed without punctuation.
- Status tabs: All, Owned, Wishlist, Incoming, In service, Sold, Archived.
- Sort by brand, date acquired, case size or recently added, with a reverse option.
- Filter by brand, movement type, complications and case-size range.

**Watch detail**
- Swipeable hero photos with a full-screen zoomable viewer.
- A spec sheet grouped as in the data model. Empty groups are hidden.
- Each value shows its source, or "Edited" when you changed an imported value.
- Long-press any spec to copy it.
- The serial number stays hidden until tapped.
- Next service and warranty dates are worked out from your settings.

**Add and edit**
- One form driven by the spec registry.
- Unit-aware number fields with live conversions (ATM/bar, Hz, days).
- Brand autocompletes from your collection, with one-tap suggestions for common answers.
- Date pickers, price with currency (AUD default), and complications including custom ones.
- A "Look up specs" button, which leads to the milestone 2 placeholder for now.

**Photos**
- Camera or gallery import (several at once).
- Set the cover, reorder, delete.
- Non-destructive crop with aspect presets.
- Location data is stripped from originals.
- Each photo keeps the original plus a display rendition and a thumbnail.

**Managing watches**
- Duplicate (copies specs, not ownership details or photos).
- Archive and unarchive.
- Delete, with confirmation.

**Design**
- All three candidate directions (Atelier, Instrument, Gallery) ship in this build. Switch them in Settings.
- Dark and light themes, and seven accent palettes.
- Fonts are bundled, with tabular figures for every measurement.

**Motion**
- Photo shared-element transition from grid to detail.
- Spring transitions and predictive back.
- Reduced-motion settings are respected.

**Adaptive layout**
- Navigation rail plus list-detail on tablets, foldables and landscape.

**Privacy**
- No network permission.
- Excluded from Android cloud backup and device transfer.
- No Google Play services hooks.
- A build check keeps network libraries out of every module except search.

**Diagnostics**
- On-device timings against the targets.
- A generator for 500 sample watches with photos.

**iOS**
- The shared code compiles for iOS.
- CI builds the simulator app, launches it and screenshots the collection.

**Database**
- Schema v1 includes the tables for service history, box & papers, documents and source snapshots, so milestones 2–4 need no migration.
