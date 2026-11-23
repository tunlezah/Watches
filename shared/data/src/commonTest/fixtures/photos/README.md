# Photo fixtures

Small real photos for the shared photo tests (`shared/data/src/commonTest/.../photos`). They are made by `tools/photo_fixtures.py make`, which needs Pillow and pillow-heif, and are committed so the tests need neither. Each shows the same 64 × 48 picture: a different flat colour in each corner.

| File | What it is for |
| --- | --- |
| `gps-exif-ii.jpg`, `gps-exif-mm.jpg` | A location in EXIF, in each byte order. The second is also stored on its side (orientation 6), as an iPhone writes a portrait photo. |
| `gps-xmp.jpg` | A location in XMP only, beside EXIF that has none |
| `gps.png`, `gps.webp`, `gps.heic` | A location in both EXIF and XMP, in the other three stored formats |
| `no-gps.jpg`, `plain.jpg` | Nothing to remove: EXIF without a location, and no metadata at all |
| `turned-1.jpg` to `turned-8.jpg` | The eight EXIF orientations. Each file's stored pixels are turned so that the upright picture is the same. |
| `turned-6.heic`, `turned-6-iphone.heic` | A HEIC keeps its turning in the container, not in EXIF. The second repeats it in EXIF (orientation 6), as an iPhone does, which a decoder must not apply a second time. |
| `alpha.png` | Transparent on its right half, which a rendition shows as black |
| `other.gif`, `other.bmp`, `other.tiff` | Formats only some systems open, which the app keeps as a JPEG made from them |

The location in them is the Royal Observatory at Greenwich.

`:shared:data:generatePhotoFixtures` compiles them into the tests as a Kotlin source, so the same tests read them on the JVM, on Android and on the iOS simulator.

The tests write the photos they have cleaned to `shared/data/build/outputs/cleaned-photos`. `tools/photo_fixtures.py check <that folder>` then reads them with Pillow, which shares no code with the app, and confirms the location has gone, the dates are kept and the pictures are untouched. CI runs it after the tests.
