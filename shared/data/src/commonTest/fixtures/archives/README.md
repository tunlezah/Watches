# Archive fixtures

Backups that the shared tests restore on every platform (`ReferenceArchiveTest`). `:shared:data:generateArchiveFixtures` compiles them into the tests as a Kotlin source, the way the photo fixtures are, so the same test reads them on the JVM, on an Android emulator, on the iOS simulator and on a Mac.

| File | What it is |
| --- | --- |
| `mac.zip` | A backup made on a Mac, by the Mac's own image code through the bridge. One watch with four photos (a HEIC turned in its container, a cropped JPEG that arrived with a location, a half-transparent PNG, and a TIFF, which is kept as a JPEG) and two documents (a PDF, and a receipt photographed as a HEIC). |

`mac.zip` isn't made by hand. The macOS workflow runs `ReferenceArchiveMaker` on every run and keeps the result as the `reference-archive` artifact. To renew the fixture, download that artifact from a run and commit it here.

Backups made on a real iPhone and a real Android phone belong here too (docs/MACOS_PLAN.md, 9.5). They need the phones: make each from sample watches (Settings › Diagnostics), so nothing personal is committed.
