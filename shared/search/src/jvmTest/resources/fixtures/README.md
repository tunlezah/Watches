# Parser fixtures

Saved responses that the parser tests in `src/jvmTest` read. Each was fetched once, by hand, with the
app's User-Agent and at least a second between requests, then trimmed to what the parsers read.

| Folder | What it is | Licence and handling |
| --- | --- | --- |
| `wikidata/` | Wikidata API and Wikipedia REST responses for "Omega Speedmaster", and Commons `imageinfo` for its picture. `labels.json` was assembled from `Special:EntityData` because the build server's shared IP was rate-limited by the API. | Wikidata is CC0. The Wikipedia extract is CC BY-SA 4.0, from [Omega Speedmaster](https://en.wikipedia.org/wiki/Omega_Speedmaster). Commons metadata as published by Wikimedia. |
| `grail/` | Grail Watch Reference REST responses (`movement` search for "7750" and the ETA 7750 detail). | **Reduced.** Only identifiers, titles, links and taxonomy terms (facts) are kept. The description text is replaced with a one-line stand-in. |
| `ranfft/` | Ranfft DB quick search for "ETA 2824" and the ETA 2824-2 and Omega 1861 calibre pages. | **Reduced**, with the site owner's permission for lookups. The page markup the parser reads is kept as served: header, stat tiles, timegrapher and spec tables. Glossary pop-over text, notes, family text, navigation and pictures are removed or replaced with stand-ins. |

There are no WatchBase fixtures yet. Its pages couldn't be fetched from the build server (see `docs/SOURCES.md`).

To refresh a fixture after a site changes, save the page from a browser and reduce it the same way. Then
run `./gradlew :shared:search:jvmTest` and update the parser until the tests pass.
