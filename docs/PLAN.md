# Watch Collection App: questions and plan (milestone 0)

Status: **milestone 3 built** (17 Oct 2026). Researched 12 October 2026. Versions were checked against Maven Central, Google Maven and services.gradle.org on that date.

## Decisions so far

On 14 Oct you reported that Ranfft DB granted permission for lookups and asked me to carry on. The open questions below were not answered yet, so milestone 1 uses the recommended defaults. Each one is easy to change.

| Question | Default used in M1 |
| --- | --- |
| Q1 iOS | GitHub Actions macOS runner builds, launches and screenshots the simulator app. |
| Q2 Repo visibility | Still public. Wikimedia fixtures (CC0 / CC BY-SA) are committed as fetched. Grail and Ranfft fixtures are committed only in reduced form: markup and facts, no descriptions or pictures. |
| Q3 Signing key | (a): I generated the key. It's kept outside the repo and sent to you privately. |
| Q4 Direction | All three ship, switchable in Settings, so you can choose on the phone. The other two are removed afterwards. |
| Q5 My Watch Memoir | Deferred to M4. |
| Q7 Ranfft | **Permission reported by you.** The adapter is in M2; see `docs/SOURCES.md`. |
| Q6 WatchBase | Connection test in Settings; no parser until you send saved pages. |
| Q8 User-Agent | `WatchCollection/1.0 (+https://github.com/tunlezah/Watches)`, as proposed. |
| Q9 Wikimedia robots.txt | API requests follow Wikimedia's API policy and are marked "(API)" in the request log. Everything else obeys robots.txt. |
| Q10 Result pictures | Fetched only for cards on screen, 1/s per host, kept in memory for that search only. |

Contents

1. Questions
2. Stack and pinned versions
3. Module structure
4. Data sources: feasibility report
5. Database schema (v1)
6. Search, merge and apply
7. Screens
8. Visual directions
9. Performance, accessibility and privacy approach
10. Backup archive (outline)
11. Milestones
12. Risks

---

## 1. Questions

### Need an answer before milestone 1

**Q1. iOS builds.** This build environment runs Linux, and iOS needs macOS and Xcode. My proposal: a GitHub Actions macOS runner builds the iOS app on every push, boots it in an iOS Simulator and saves a screenshot of the collection screen. That proves "builds and launches" without you needing a Mac. Do you have a Mac you'd also like to run it on? You won't need one until TestFlight.

**Q2. Repo visibility.** `tunlezah/Watches` is **public**. This matters in three ways:
- Parser fixtures are saved copies of third-party pages. I'd rather not publish WatchBase pages in a public repo, given its terms on reuse.
- A signed APK attached to GitHub Releases would be public.
- macOS CI minutes are free for public repos. For private repos they count against the free 2,000 min/month at a 10× rate, which is roughly 15 iOS builds a month.

My recommendation is to make the repo **private** and run iOS CI only on milestone tags or on demand. Which do you prefer?

**Q3. Signing key.** Sideloaded updates must be signed with the **same key forever**. If the key is lost, you'd have to uninstall the app (and its data) to install a new build. Two options:
- (a) I generate the keystore once. You store the file and passwords in your password manager and add them as four GitHub Actions secrets. CI then builds the signed APK.
- (b) You generate it yourself with one `keytool` command (I'll provide it) and add the same secrets.

Which do you prefer? (a) is simpler.

**Q4. Visual direction.** Pick A, B or C, or a mix (for example "B's layout with A's type"). See §8 and the mockup canvas.

**Q5. Migrating from My Watch Memoir.** Do you want your existing collection brought across? If My Watch Memoir can export (CSV, backup file or similar), send me a sample with a couple of rows anonymised. I'd add an importer, probably in M4. This shapes the field mapping, so it's worth knowing now.

### Can wait until milestone 2 (search)

**Q6. WatchBase.** From this cloud server every WatchBase URL returns a Cloudflare block ("Sorry, you have been blocked"), apart from `robots.txt`. That includes a real headless browser. So I could **not** read the terms page or take a sample parse (details in §4). It may work fine from your phone, but Cloudflare often blocks non-browser HTTP clients whatever the network. I will **not** make the app pretend to be a browser. Could you:
1. open <https://watchbase.com/terms> and confirm it matches the summary in the brief (personal, non-commercial use allowed; no substantial extraction or systematic reuse)?
2. save three pages as "HTML only" (one search-results page and two watch pages) and send them to me as fixtures?

If the honest client turns out to be blocked from your phone too, the fallback is one of these (your choice):
- (a) drop WatchBase;
- (b) an in-app browser where *you* browse WatchBase and tap "Import this page". The app only reads the page you're already looking at, one watch at a time.

**Q7. Ranfft.** The original Ranfft encyclopedia no longer exists (§4). Its successor, **Ranfft DB** (ranfft.org), is a separate project whose terms prohibit scraping. Following the brief, I'm skipping it. Do you want me to draft an email asking their permission for on-demand, single-caliber lookups?

**Q8. User-Agent contact.** Wikimedia gives clients that include contact details in the User-Agent 200 requests/min; clients without them get 10/min. Proposal: `WatchCollection/1.0 (+https://github.com/tunlezah/Watches)`. That works if the repo stays public. If it goes private, should I use an email address, and if so which one?

**Q9. Wikimedia robots.txt.** Wikipedia's and Wikidata's `robots.txt` disallow `/w/` and `/api/` for generic crawlers. Wikimedia's API policy, by contrast, explicitly welcomes identified API clients that follow its rate limits. Our requests are single, user-initiated lookups, not crawling. I read the brief's "use their public APIs" as the instruction that applies here. Agreed?

**Q10. Result thumbnails.** Every thumbnail costs one extra request to the source's image host. Proposal: only fetch thumbnails for cards on screen, under the same 1 request/second per-host limit (they'll trickle in), and keep them in memory for that search only. OK?

### Defaults I'll use unless you say otherwise

- **minSdk 30** (Android 11), target/compile SDK 36. iOS 17 minimum.
- **Prices:** each price is stored as an amount plus a currency code, AUD by default. There's no exchange-rate conversion, since that would need the network.
- **Photo privacy:** GPS location is stripped from photo EXIF on import. Everything else in the original file is kept.
- **Backups:** both Android cloud backup **and** device-to-device transfer are excluded. Export is the only backup path.
- **Condition:** Unworn, Mint, Excellent, Very good, Good, Fair, Poor, or blank.
- **Complications:** a fixed list plus custom entries. The list is date, day-date, big date, GMT/dual time, world time, chronograph, flyback, rattrapante, moon phase, power-reserve indicator, alarm, annual calendar, perpetual calendar, small seconds, jump hour, tourbillon and minute repeater.
- **Units:** sizes in mm. Water resistance in m, with ATM/bar alongside. Frequency in bph with Hz. Power reserve in hours, with days added at 72 h and above.
- **Dates:** DD/MM/YYYY.
- **Fonts:** all fonts are SIL Open Font License and bundled in the app.

---

## 2. Stack and pinned versions

All versions are the latest **stable** release as of 12 Oct 2026. They'll be pinned in `gradle/libs.versions.toml`.

| Concern | Library | Version | Network behaviour |
| --- | --- | --- | --- |
| Build | Gradle wrapper | 9.8.1 | Build-time only |
| Build | Android Gradle Plugin | 9.4.1 | Build-time only |
| Language | Kotlin (Multiplatform) | 2.4.21 | None |
| Codegen | KSP | 2.3.12 | Build-time only |
| UI | Compose Multiplatform (runtime, foundation, resources) | 1.12.1 | None. Fonts and images are bundled resources. |
| UI | Material 3 for CMP | 1.9.0 | None |
| UI | material3-adaptive (+ adaptive-navigation3) | 1.3.0 | None |
| Navigation | Navigation 3 (JetBrains) | 1.1.2 | None |
| Lifecycle | lifecycle-viewmodel-compose / -navigation3 | 2.11.0 | None |
| Persistence | Room (KMP) + `androidx.sqlite:sqlite-bundled` | 2.8.5 / 2.7.1 | None |
| Networking | Ktor client (OkHttp engine on Android, Darwin on iOS) | 3.6.0 | **Yes: search module only, only when you press Search or Refresh specs** |
| HTML parsing | Ksoup (core only, no `ksoup-network`) | 0.2.6 | None |
| Serialisation | kotlinx.serialization JSON | 1.11.0 | None |
| Async | kotlinx.coroutines | 1.11.0 | None |
| Dates | kotlinx-datetime | 0.8.0 | None |
| Images | Coil 3 (`coil-compose` only, **no** `coil-network-*`) | 3.6.3 | None. Without a network module, Coil cannot fetch URLs. |
| Files, zip | Okio | 3.18.2 | None |
| DI | Koin (core, compose, compose-viewmodel) | 4.2.2 | None |
| Files, camera | FileKit (dialogs-compose, coil) | 0.16.0 | None |
| Android | activity-compose 1.13.0, core-ktx 1.19.1, exifinterface 1.4.2 | | None |
| Android | WorkManager (service reminders) | 2.12.0 | None. Local scheduling only. |
| Android | Biometric (app lock) | 1.1.0 | None |
| Android | profileinstaller / baselineprofile plugin / benchmark-macro | 1.4.1 / 1.5.0 / 1.5.0 | None. Writes the profile locally. |
| Tests | kotlin-test, Turbine 1.2.1, Room migration testing | | None |

Notes on the choices:

- **Room rather than SQLDelight.** It's first-party and its queries are observed as Flows. Auto-migrations come with exported schema JSON and a migration test harness. With bundled SQLite, Android and iOS run the *same* SQLite build, including FTS for full-text search.
- **Koin rather than manual DI.** About eight services differ per platform: image processing, PDF rendering, share sheet, reminders, biometrics, haptics, connectivity and file paths. Koin also handles ViewModel scoping in Compose Multiplatform. A `verify()` unit test catches a missing binding in CI rather than at runtime. Koin uses no reflection and no network.
- **Navigation 3 rather than Navigation-Compose 2.x.** The back stack is plain state, so it's easy to restore and test. Through `adaptive-navigation3` it has built-in list-detail layouts for tablets and foldables. Predictive back and shared-element transitions work with it.
- **Material 3 1.9.0.** This is the newest *stable* Material 3 for Compose Multiplatform, and it's what CMP 1.12.1 maps `compose.material3` to. Newer builds are alpha only. Material is only the base layer; the look comes from our own design system.
- **Okio for files and ZIP.** Coil already depends on it. On both Android and iOS it provides `ZipFileSystem`, `DeflaterSink` and `InflaterSource`. Archive *writing* uses a small in-house streaming ZIP64 writer (about 300 lines, fixture-tested), because no stable multiplatform zip-writing library exists. The output is a standard zip that any unzip tool can open.
- **Biometric 1.1.0** is old, but it's the latest stable. Newer releases are alpha. BiometricPrompt hasn't changed in a way that matters here.
- **Not included at all:** Firebase, Google Play Services, analytics, crash reporting, ads, remote config and runtime font downloads.

Enforcing "only the search module touches the network":

- Ktor is an `implementation` dependency of `:shared:search`, so it never appears on another module's compile classpath.
- A Gradle check, `checkNetworkIsolation`, fails the build if `io.ktor`, `okhttp` or `coil-network` shows up on the compile classpath of `core`, `data` or `ui`.
- At runtime, every request goes through one `PoliteHttpClient` in the search module, which logs it. Settings → Diagnostics shows that log on the device, and you can cross-check it with an on-device capture tool such as PCAPdroid for the v1 sign-off.

---

## 3. Module structure

```
shared/core     Domain models, the SpecField registry, units, formatting, use cases. Pure Kotlin.
shared/data     Room database, DAOs, repositories, photo/document storage, thumbnail pipeline,
                backup archive (zip + JSON), CSV export, settings.
shared/search   SpecSource interface, adapters, PoliteHttpClient (rate limit, robots, UA, timeout),
                merge and diff logic, fixtures and parser tests. The only module with Ktor.
shared/ui       Design system (tokens, type, components), screens, ViewModels, navigation.
                Also produces the iOS framework.
androidApp      Activity, manifest, platform services (biometrics, PdfRenderer, FileProvider,
                WorkManager reminders), release signing, Baseline Profile consumer.
androidApp/baselineprofile   Macrobenchmark and profile generator, run against 500 sample watches.
iosApp          Xcode project: a SwiftUI shell hosting the shared Compose UI.
docs/           PLAN.md, ARCHIVE_FORMAT.md, SOURCES.md, CHANGELOG.md.
```

Dependencies: `core ← data`, `core ← search`, and `data + search ← ui ← androidApp / iosApp`.

Under AGP 9, Android application code must live in its own module, which `androidApp` already does. The shared modules use AGP's KMP library plugin.

**One registry drives everything.** In `core`, each spec field is declared once, with its key, group, type, unit and label. That single definition drives the spec sheet, the edit form, the CSV columns, provenance keys, the diff screen and the search-to-field mapping. Adding a field means one registry entry, one column and one migration.

**Platform services** are interfaces in `core`, implemented per platform and bound with Koin: `ImageProcessor` (decode, downsample, EXIF rotate, crop, encode), `PdfRenderer` (Android `PdfRenderer` / iOS PDFKit), `ShareSheet`, `ReminderScheduler` (WorkManager / `UNUserNotificationCenter`), `BiometricGate`, `Haptics`, `Connectivity`, `AppFiles`.

---

## 4. Data sources: feasibility report

Every request in this check used an honest User-Agent and stayed at or under 1 request/second per host. Here is what I found:

| Source | robots.txt | Terms | Access | Sample parse | Verdict |
| --- | --- | --- | --- | --- | --- |
| **WatchBase** | Fetched OK. Only `Disallow: /honeypot`, so search and watch pages are allowed. | **Couldn't read.** `/terms` returns a Cloudflare block (403) from this server, including in a real headless browser. I'm relying on the brief's summary until you confirm. | HTML pages. A paid DataFeed API also exists (an interface slot is reserved; not built in v1). | **Not possible from here.** Every page returns 403. | **Unverified.** Build in M2 against fixtures you save (Q6). Add a one-tap "test connection" so we learn whether the honest client gets through from your phone. |
| **Wikidata + Wikipedia** | Generic crawlers are disallowed from `/w/` and `/api/` (see Q9). | Wikidata data is CC0. Wikipedia text is CC BY-SA 4.0, so attribution and a link are shown, which we do anyway. Commons images each carry their own licence. API policy requires a User-Agent with contact details, no more than 3 concurrent requests, and honouring `Retry-After`. | Action API (`wbsearchentities`, `wbgetentities`) and the REST page summary. | ✅ "Omega Speedmaster" → `Q1414495` "chronograph wristwatches produced by Omega SA". Its claims: manufacturer Omega SA, official website, a Commons image and a Wikipedia article. The summary returned a clean description and extract. "ETA 2824" → `Q109315562`, which has only "instance of" and "manufacturer". | **Use, for identity and history.** Good for brand and model-family descriptions, history text and the official site link. **Not** a spec source: reference-level specs are almost never on Wikidata. |
| **Ranfft** (update 14 Oct: permission granted, now in M2; see `docs/SOURCES.md`) | The original `www.ranfft.de` no longer resolves, and `ranfft.de` shows a parked hosting page. Dr Ranfft died in 2023 and his site's data was deleted. The successor `ranfft.org` disallows `?sort=` and `?CaliberSearch`. | Ranfft DB's terms §4 and §8: "scrape, crawl, or extract data in bulk without permission" is prohibited, and "scraping, automated bulk access" counts as abuse of anonymous access. | n/a | Not attempted, out of respect for the terms. | **Skip** (per the brief). Optionally ask permission (Q7). |
| **Grail Watch Reference** | Only `/wp-admin/` disallowed. | No terms, copyright or licence page. The About text says illustrations are manufacturer press photos used for commentary. | **Public WordPress REST API.** `movement` posts carry structured taxonomies, so no HTML scraping is needed. | ✅ ETA 7750: brand ETA/Valjoux, automatic (central rotor), jewels 17/24/25, power reserve 54 h, 28,800 A/h (28,800 bph, 4 Hz), chronograph, day, date, hacking seconds, production 1973 to current, Incabloc, Etachron, plus the history text. No dimensions. | **Use, for movement data.** Image saving is off for this source, because the photos aren't Grail's to license. Links only. |

Request plan per source:

- **Grail search:** `GET /wp-json/wp/v2/movement?search=<q>&orderby=relevance&per_page=10&_fields=id,slug,title,link`
- **Grail detail:** `GET /wp-json/wp/v2/movement?slug=<slug>&_embed=wp:term`
- **Wikidata search:** `wbsearchentities`
- **Wikidata detail:** `wbgetentities`, plus a Wikipedia REST `page/summary`. That summary is a second host, so still one request per host.
- **WatchBase:** one search page and one watch page, pending Q6.
- **robots.txt:** fetched at most once per host per 24 h and cached. Any URL it disallows is refused before sending.

A note on the 429s: Wikidata returned HTTP 429 to one of my test requests even at under 1 request/s. This server shares its IP with other cloud traffic, which is normal for Wikimedia. I don't expect it on a phone. The adapter still honours `Retry-After` and otherwise shows "Couldn't reach Wikidata".

The upshot: **Grail is the only spec source confirmed working today**, and it covers movements only. Case, dial and strap specs depend on WatchBase, and WatchBase is unverified. This is the biggest product risk; see §12.

---

## 5. Database schema (v1)

Conventions:

- IDs are UUID text. That makes "merge on internal IDs" trivial and keeps them stable across devices and iOS.
- Timestamps are epoch milliseconds. Dates are ISO `YYYY-MM-DD`.
- Money is stored in minor units plus an ISO 4217 currency code.
- Enums are stored as stable text keys.
- Every child row cascades on watch delete.
- Room exports schema JSON to `shared/data/schemas/`, and migration tests run from v1 onwards.

```text
watch                     one row per physical watch or wishlist entry
  id PK, status(owned|wishlist|incoming|in_service|sold), archived_at?, created_at, updated_at, cover_photo_id?
  -- identity
  brand?, collection?, model_name?, reference?, nickname?, year_from?, year_to?, limited_number?, limited_total?
  -- case
  case_material?, diameter_mm REAL?, thickness_mm REAL?, lug_to_lug_mm REAL?, lug_width_mm REAL?,
  bezel?, crystal?, case_back?, crown?, water_resistance_m INT?
  -- dial
  dial_colour?, dial_finish?, indices?, hands?, lume?
  -- movement
  caliber?, base_caliber?, movement_type(automatic|manual|quartz|solar|spring_drive|kinetic|other)?,
  jewels INT?, frequency_bph INT?, power_reserve_h REAL?, hacking BOOL?, hand_winding BOOL?
  -- strap / bracelet
  strap_type?, strap_material?, clasp?, fitted_strap?
  -- ownership
  acquired_on?, acquired_from?, price_minor INT?, price_currency?, condition?, serial_number?,
  private_notes?, story?
  -- service, box and papers
  service_interval_months INT?, service_reminder BOOL, warranty_start?, warranty_months INT?

watch_complication        (watch_id, key) PK         -- fixed keys, or "custom:<label>"
field_provenance          (watch_id, field_key) PK, source ("manual" | source id), source_url?,
                          fetched_at?, user_edited BOOL
other_spec                id PK, watch_id, label, value, source?, source_url?, fetched_at?, position
photo                     id PK, watch_id, position, original_file, display_file, thumb_file,
                          width, height, crop_rect?, kind(user|reference), attribution?, attribution_url?, added_at
service_entry             id PK, watch_id, serviced_on, serviced_by?, work (set of: full_service,
                          regulation, crystal, gaskets, battery, polish, other), cost_minor?, cost_currency?,
                          warranty_months?, notes?, created_at, updated_at
box_papers_item           (watch_id, item) PK, state(present|missing|not_applicable), label?, note?
                          -- items: inner_box, outer_box, warranty_card, manual, hang_tags, extra_links, receipt, other
document                  id PK, watch_id, service_entry_id?, kind(manual|quick_start|receipt|invoice|warranty|certificate|other),
                          title, file, mime_type, size_bytes, added_at
source_snapshot           id PK, watch_id, source, url, fetched_at, content_type, body_file
                          -- cached detail payload, kept only for saved watches; lets us re-parse later
recent_search             query PK, used_at
setting                   key PK, value
watch_fts (FTS4, unicode61, remove_diacritics=2)
                          watch_id UNINDEXED, text   -- all fields, other specs, notes, story, service notes,
                                                     -- document titles; rewritten on each save of that watch
```

How the provenance rules map onto this:

- When you edit a field by hand, its `field_provenance` row becomes `user_edited = 1`, keeping any original source details.
- When a lookup is applied, each field writes its source, URL and fetch date.
- In the apply diff, fields with `user_edited = 1` **start unticked**. Ticking one is the explicit confirmation.
- Any field a source provides that doesn't fit the schema goes into `other_spec`, with its source kept.

**Photos.** Each photo has three files under app-private storage:

- the untouched original (GPS stripped);
- a `display` rendition, cropped, up to 2560 px;
- a `thumb`, cropped, up to 640 px.

Cropping is non-destructive (`crop_rect`), so you can re-crop from the original.

**Designed for later, not built in v1.** `wear_event`, `accuracy_reading` and `valuation` tables will arrive through ordinary migrations. Navigation reserves routes for Wear, Accuracy, Value, Glossary, Reference clock and Identify-from-photo. The watch detail screen is built as a list of sections, so new sections slot in without a redesign.

---

## 6. Search, merge and apply

```kotlin
interface SpecSource {
    val id: SourceId
    val displayName: String
    val homepage: String
    val provides: Set<Coverage>                  // WATCH, MOVEMENT, BRAND_HISTORY
    val requiresApiKey: Boolean get() = false    // slot for a paid DataFeed-style adapter later
    suspend fun search(query: String): List<SearchHit>      // exactly 1 request
    suspend fun detail(hit: SearchHit): SourceRecord         // exactly 1 request (per host)
    fun parseSearch(body: String): List<SearchHit>           // pure; fixture-tested
    fun parseDetail(body: String, url: String): SourceRecord // pure; fixture-tested
}
```

- **PoliteHttpClient** (search module) applies the per-host 1 request/s limit, the robots.txt check, the User-Agent, a 10 s timeout and the request log. It has no cookie jar and does no background work.
- **Streaming.** Searching starts every enabled source in parallel inside a `channelFlow`. Each source emits Loading, then Results or Failed, so cards appear as each source returns. If a parser throws, that source shows "Couldn't read ⟨source⟩" and the others carry on. With no connectivity, search shows its offline state immediately and sends nothing.
- **Normalisation.** Every adapter maps into a `SourceRecord`: a map of `FieldKey` to a typed value, plus the raw text, `otherSpecs` and the source URL. Units are converted at this step (for example A/h to bph, lignes to mm, ATM or ft to m).
- **Preview → Add or Apply.** *Add to collection* creates a watch and opens the edit form, pre-filled and ready for photos. *Apply to existing* opens the **diff**: one row per field showing current and new values, with a checkbox. Rows for user-edited fields start unticked. When two sources disagree, the row shows both values as choices.
- **Reference images** are opt-in per watch. They're stored locally with attribution, as `kind = reference`, and never become the cover unless you choose it. Your own photos stay first.
- **Fixtures.** Each source has search and detail fixtures, with a parser test per field. A failing parser fails CI.

---

## 7. Screens

Phone navigation is a bottom bar with **Collection · Lookup · Service · Settings**. Wide screens get a navigation rail and list-detail layouts.

1. **Collection** (home). Photo grid, with a toggle to a compact list. Sort, a filter sheet (status, brand, movement type, complications, case-size range) and an instant local search field.
2. **Watch detail.** Hero photo carousel with a shared-element transition from the grid. Title block, then badges (Full set, Service due, Warranty). The spec sheet follows, in data-model groups; empty groups collapse and long-press copies a value. After that come other specs, ownership (serial number hidden until tapped), service summary, box & papers, documents and source credits.
3. **Photo viewer.** Full-screen, with zoom, pan and swipe.
4. **Add / Edit watch.** A sectioned form with smart pickers (brand autocompletes from your collection), unit-aware number fields and a complications multi-select. A *Look up specs* button opens Lookup pre-filled.
5. **Manage photos.** Camera or gallery, reorder, set cover, delete. **Crop** is its own screen.
6. **Lookup (spec search).** A single field with recent searches. Results stream in per source. Offline and per-source error states are built in.
7. **Result preview.** The source's spec sheet, with *Add to collection* or *Apply to existing* (which leads to a watch picker).
8. **Apply diff.** Field-by-field checkboxes and conflict choices.
9. **Service history** (per watch) and the **service entry editor**, including an invoice attachment.
10. **Service due.** A collection-wide list of upcoming and overdue services, plus warranty expiries.
11. **Box & papers.** The checklist, warranty start, length and expiry, and the Full set badge.
12. **Documents** (per watch) and the **document viewer** for PDFs and images, with Share.
13. **Settings.** Appearance (theme, accent), units, currency, date format, sources on/off, app lock, reminders, Backup & restore (export zip, import zip with replace or merge, export CSV), Diagnostics (request log, performance timings) and open-source licences.
14. **Import report.** What merged, and any conflicts.
15. **Lock screen**, shown when app lock is enabled.

---

## 8. Visual directions

Three directions are mocked up on a canvas: <https://claude.ai/artifact/BSSuH2D5Q6dp5aamMcdh3Y> (private to your account). Each direction has a style tile, the collection grid, and the watch detail spec sheet in dark and light. The grid and detail boards have **Theme** and **Accent** tweaks, so you can try all seven accents. The watch drawings stand in for your photos.

| | A · Atelier | B · Instrument | C · Gallery |
| --- | --- | --- | --- |
| Mood | Auction-house catalogue: quiet, warm and editorial | Tool-watch data sheet: precise, technical, OLED black | Scandinavian minimal: airy, soft, Bauhaus calm |
| Display face | Cormorant Garamond | Archivo (expanded) | Manrope (light) |
| Spec face | Instrument Sans (tabular figures) | IBM Plex Sans + Plex Mono for references | Manrope (tabular figures) |
| Ground | Warm graphite `#121110` | True black `#000000` | Midnight `#0D1117` |
| Default accent | Champagne | Lume | Sunburst blue |

I checked each spec face's font files for tabular figures: Instrument Sans, Manrope and Archivo have the `tnum` feature, and IBM Plex Sans/Mono have tabular figures by default.

All three share the same **seven accent palettes**: Champagne, Lume, Ice blue, Racing green, Bordeaux, Sunburst blue and Tropical amber. Each has dark and light variants that are checked against WCAG AA when the design system is built.

---

## 9. Performance, accessibility and privacy approach

**Targets.** Measured on the Motorola Edge 60 Fusion, whose 120 Hz panel means an 8.3 ms frame budget.

- **Cold start.** No work on the main thread before the first frame. The grid's first query reads a slim projection (id, brand, model, reference, thumbnail path, status). Room opens on a background dispatcher. Koin modules load lazily.
- **Smooth grid.**
  - Pre-sized 640 px thumbnails decoded off the main thread, with a Coil memory cache sized for about two screens.
  - Stable keys and `contentType` in `LazyVerticalGrid`.
  - Immutable UI state.
  - No layout work in the scroll path.
  - R8 full mode.
- **Baseline Profiles.** Generated by a macrobenchmark covering startup, grid scroll, search and opening a watch, run against **500 generated sample watches with photos**. That sample comes from a debug-only generator and needs no network.
- **Search under 100 ms.** FTS4 prefix query on a background dispatcher, debounced by one frame. 500 rows is small for FTS.
- **Detail under 150 ms.** Single-row query plus the cover's display rendition, which is already in memory from the shared-element transition.
- **On-device measurement.** Settings → Diagnostics shows the measured cold-start time, search latency and JankStats frame stats on the phone itself, so you can check targets without a developer setup. I'll also provide the macrobenchmark command.

**Accessibility.**

- Content descriptions on every image and icon button. Spec rows read as "Diameter, 39.7 millimetres".
- Text scales to 200%: rows wrap rather than clip, with label above value at large scales.
- 48 dp touch targets and AA contrast, checked for every accent in both themes.
- Haptics and motion respect "Remove animations"; spring animations are cut short when it's on.

**Privacy.**

- `allowBackup="false"` plus data-extraction rules that exclude everything.
- No accounts, analytics or crash SDKs.
- Serial numbers are masked until tapped.
- Optional biometric app lock, with an option to also hide app content in the recents screen.

---

## 10. Backup archive (outline)

The full spec goes in `docs/ARCHIVE_FORMAT.md` in M4.

```
watch-collection-YYYYMMDD-HHMM.zip
  manifest.json      format id, formatVersion: 1, schemaVersion, createdAt, app version, counts,
                     and one entry per file with size and SHA-256
  collection.json    every table as JSON arrays (kotlinx.serialization), IDs preserved
  media/photos/<photoId>.<ext>      originals; renditions are regenerated on import
  media/documents/<documentId>.<ext>
  snapshots/<snapshotId>.<ext>      cached source payloads
```

- Media is STORED (JPEG and PDF are already compressed) and JSON is DEFLATEd.
- ZIP64 is supported, because 500 watches' worth of photos can pass 4 GB. The archive is written **streaming** through the system file picker, so memory stays flat.
- **Import** checks every hash before touching the database, then applies everything in one transaction.
  - *Replace* wipes and restores.
  - *Merge* matches on IDs. The newer `updated_at` wins, and every conflict is listed in the import report.
- CSV export writes one row per watch with every registry field, in UTF-8 with a BOM so Excel opens it correctly.
- Round-trip test in CI: generate, export, wipe, import, then deep-compare, on Android (JVM) and the iOS simulator.

---

## 11. Milestones

| # | Scope | What you'll test on the phone |
| --- | --- | --- |
| **M1 Foundation** | KMP project, design system (chosen direction), Room v1, manual add/edit, grid + list, sort/filter/local search, watch detail, photos (camera, gallery, reorder, cover, crop), duplicate/archive/delete, 500-watch sample generator. iOS CI build plus simulator screenshot. | Install the APK, add watches with photos, browse 500 sample watches, check speed |
| **M2 Search** | PoliteHttpClient, Grail + Wikidata/Wikipedia adapters (+ WatchBase per Q6), fixtures and tests, streamed results, preview, add, diff-and-apply, offline state, request log | Look up your own watches, apply specs, try airplane mode |
| **M3 Service, box & papers, documents** | Service history and due list, reminders, box & papers checklist and warranty, documents with viewer and share | Log a service, attach an invoice PDF, get a reminder |
| **M4 Backup and polish** | Zip export/import (replace/merge), CSV, the remaining settings, app lock, performance pass against targets, accessibility pass, README, archive format doc, signed release APK | Export, wipe, import; the full v1 checklist |

Each milestone ends with a signed APK sent here, a changelog entry, and green CI on Android tests, the iOS build and the parser fixtures.

---

## 12. Risks

1. **Spec coverage depends on WatchBase**, which is unverified (§4, Q6). Grail covers movements well; Wikidata covers identity and history. If WatchBase is out, case, dial and strap specs are manual entry. Lookup would still fill movement data and history.
2. **iOS needs macOS CI** (Q1). I can't run Xcode here. iOS problems only surface in CI, so iOS CI runs on every push to catch Android-only code early.
3. **There's no physical device here.** I can measure the targets on an emulator and with benchmarks, but the final numbers come from your Edge 60 Fusion. The Diagnostics screen exists for exactly that.
4. **Scrapers break.** Fixtures, per-source isolation and the "Couldn't read ⟨source⟩" state contain the damage. Cached snapshots allow a re-parse after a parser fix without a new request.
5. **Large backups.** Streaming ZIP64 plus hash verification keeps memory flat. They'll still take a while for very large photo libraries, so a progress bar is shown.
