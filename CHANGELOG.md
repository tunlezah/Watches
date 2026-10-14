# Changelog

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
