# Changelog

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
- 100 automated tests, including a parser test per source against saved pages.
- The politeness rules are tested: spacing, robots.txt, back-off, timeouts and blocks.
- Screenshot tests cover the Lookup, Review and Request log screens.

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
