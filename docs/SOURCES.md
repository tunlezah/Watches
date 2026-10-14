# Spec sources: terms, robots.txt and access

Every request uses an honest User-Agent, at most 1 request per second per host, a 10 s timeout, and only happens when you press Search or Refresh specs. Results from each source are kept per watch with the source name, URL and fetch date.

| Source | Status | Access | Covers |
| --- | --- | --- | --- |
| Grail Watch Reference | ✅ Built (M2) | Public WordPress REST API (`/wp-json/wp/v2/movement`) | Movements: winding, jewels, frequency, power reserve, complications, production years, regulator, shock protection |
| Ranfft DB (ranfft.org) | ✅ Built (M2), **with the site owner's permission** | Server-rendered HTML: `/calibers?quick=<text>` and `/caliber/{id}-{slug}` | Calibres: type, jewels, beat rate, power reserve, base calibre, complications; size, height, lift angle, escapement, shock protection as extra specs |
| Wikidata + Wikipedia | ✅ Built (M2) | Action API (`wbsearchentities`, `wbgetentities`), REST page summary, Commons `imageinfo` when you save a picture | Brand, collection, introduction year, official site, history text |
| WatchBase | ⚠️ Connection test only | HTML. A paid DataFeed API also exists (interface slot reserved, not built) | Full watch specs, once fixtures arrive |

**What the app maps where.** Movement data never fills case fields: Ranfft's "26 mm" is the movement's size and is kept as an extra spec, not the case diameter. A movement's maker (ETA, Valjoux) is never written as the watch's brand.

**Requests per lookup.** A search is one request per enabled source. Opening a result is one request: Grail and Ranfft one each; Wikidata up to three (the entity and its brand and maker labels from Wikidata, a second apart, then the Wikipedia summary). Result pictures are fetched only while their card is on screen. The first request to a host each day also fetches its `robots.txt`.

**Fixtures.** Parser tests run against saved pages in `shared/search/src/jvmTest/resources/fixtures`. While the repository is public, the Grail and Ranfft fixtures are *reduced*: markup and facts only, with descriptions, glossary text and pictures removed. See the README in that folder.

## Ranfft DB

**Permission.** The project owner reported on 14 Oct 2026 that Ranfft DB has granted permission for on-demand, single-calibre lookups. The email will be added here when available. The adapter stays strictly per-lookup (no bulk, crawling, prefetching or background refresh) and keeps to the rules above.

**robots.txt** (checked 14 Oct 2026):
```
Disallow: /*?*sort=
Disallow: /*?*CaliberSearch
```
The adapter never sends either parameter. The quick-search URL `/calibers?quick=…` is allowed.

**Feasibility check** (14 Oct 2026: 5 requests, ≥2 s apart, honest UA, all HTTP 200, no challenge pages):

*Search page.* Results are server-rendered; no JavaScript is needed.
- Each result is a `tbody tr[wire:key^="caliber-"]` row.
- The name and link are `a[href*="/caliber/"]`, and the thumbnail is an `img`.
- The other columns are Manufacturer, Movement, Dimensions and Year.

*Detail page.* Also server-rendered.
- Title: `h1`, with the manufacturer in `span.text-muted`.
- Stat tiles: label in `div.text-xs`, value in `div.rd-mono`.
- Spec tables: `tr > th[scope=row]` for the label and `td` for the value. Glossary pop-overs inside the `td` must be skipped.
- Image URLs are a JSON array in the carousel's `x-data` attribute.

*Sample parse.* ETA 2824-2:
- Diameter 26 mm (11½‴), height 4.60 mm, 25 jewels.
- Beat rate 28,800 bph (4 Hz), power reserve 40 h, lift angle 50°.
- Base calibre ETA 2824-1, Incabloc, central rotor, launched 1982.

Omega 1861 parsed equally cleanly.

*Gating.* Cloudflare sits in front, and session cookies are set but not required. Supporter-only features (parts catalogue, donor finder) are not used.

**Images.** No image licence or photo credits appear on the pages, and contributors license their uploads to Ranfft DB only. The app therefore links to Ranfft images instead of saving them, unless the owner's permission covers images.

## Grail Watch Reference
- robots.txt allows everything except `/wp-admin/`. There's no terms page.
- Its illustrations are manufacturer press photos, so the app links rather than saves them.
- Sample (ETA 7750): brand ETA/Valjoux, automatic, 17/24/25 jewels, 54 h, 28,800 A/h, chronograph/day/date/hacking, 1973–current.
- No dimensions.

## Wikidata and Wikipedia
- The API policy gives clients that identify themselves with contact details in the User-Agent 200 requests/min. The app sends at most 1 request per second per host.
- The data is licensed CC0 (Wikidata) and CC BY-SA 4.0 (Wikipedia text). Wikipedia text is shown with attribution.
- robots.txt disallows `/w/` and `/api/` for crawlers. The app is not a crawler, and the brief directs use of the public APIs, so these requests follow the API policy instead (`RobotsPolicy.ApiPolicy`). The request log marks them "(API)". Picture downloads from `upload.wikimedia.org` obey that host's robots.txt like any other.
- Saved Commons pictures keep their author and licence (from `imageinfo`) as the photo's credit, and a link to the file page.

## WatchBase
- Every page except `robots.txt` (which only disallows `/honeypot`) returns a Cloudflare block to this build server, so its terms and page structure are unverified here.
- The brief's summary of the terms: personal, non-commercial use is allowed; extracting a substantial part of the database or systematically reusing it is not.
- The adapter waits for fixtures saved from a normal browser and a connection test from the phone (Settings › Lookup sources › Test connection). It will never disguise itself as a browser.
