package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.data.files.DocumentFormats
import io.github.tunlezah.watches.data.files.ImageFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Formats are told from a file's own bytes, the same way on every platform. */
class PhotoFormatTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun text(value: String) = value.encodeToByteArray()

    /** An ISO container's opening box: its size, "ftyp", the main brand, a version, then the compatible brands. */
    private fun ftyp(major: String, vararg compatible: String): ByteArray {
        val size = 16 + 4 * compatible.size
        return bytes(0, 0, 0, size) + text("ftyp") + text(major) + bytes(0, 0, 0, 0) + compatible.fold(ByteArray(0)) { acc, b -> acc + text(b) }
    }

    @Test fun theFourFormatsEveryDeviceShowsAreStored() {
        assertEquals(PhotoFormat.JPEG, PhotoFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE1)))
        assertEquals(PhotoFormat.PNG, PhotoFormat.detect(bytes(0x89) + text("PNG\r\n") + bytes(0x1A, 0x0A)))
        assertEquals(PhotoFormat.WEBP, PhotoFormat.detect(text("RIFF") + bytes(0x24, 0, 0, 0) + text("WEBPVP8 ")))
        assertEquals(PhotoFormat.HEIC, PhotoFormat.detect(ftyp("heic", "mif1", "heic")))
        assertEquals(listOf(PhotoFormat.JPEG, PhotoFormat.PNG, PhotoFormat.WEBP, PhotoFormat.HEIC), PhotoFormat.entries.filter { it.stored })
    }

    @Test fun heicIsToldFromAvifByItsBrands() {
        // An iPhone writes "heic"; some cameras write the container's own brand first.
        assertEquals(PhotoFormat.HEIC, PhotoFormat.detect(ftyp("mif1", "mif1", "heic", "hevc")))
        assertEquals(PhotoFormat.HEIC, PhotoFormat.detect(ftyp("heix", "mif1")))
        assertEquals(PhotoFormat.HEIC, PhotoFormat.detect(ftyp("mif1")))
        assertEquals(PhotoFormat.AVIF, PhotoFormat.detect(ftyp("avif", "mif1", "miaf")))
        assertEquals(PhotoFormat.AVIF, PhotoFormat.detect(ftyp("mif1", "avif", "miaf")))
        // A video, and a Canon raw file, use the same container and are neither.
        assertNull(PhotoFormat.detect(ftyp("isom", "mp42")))
        assertNull(PhotoFormat.detect(ftyp("crx ", "crx ", "isom")))
        // A header cut short still reads: only the brands that are there count.
        assertEquals(PhotoFormat.HEIC, PhotoFormat.detect(bytes(0, 0, 0, 0x18) + text("ftypheic")))
    }

    @Test fun formatsOnlySomeSystemsOpenAreRecognisedButNotStored() {
        assertEquals(PhotoFormat.GIF, PhotoFormat.detect(text("GIF89a") + ByteArray(8)))
        assertEquals(PhotoFormat.TIFF, PhotoFormat.detect(text("II") + bytes(42, 0, 8, 0, 0, 0)))
        assertEquals(PhotoFormat.TIFF, PhotoFormat.detect(text("MM") + bytes(0, 42, 0, 0, 0, 8)))
        assertEquals(PhotoFormat.BMP, PhotoFormat.detect(text("BM") + bytes(0x36, 0, 0, 0, 0, 0, 0, 0, 0x36, 0, 0, 0)))
        for (format in listOf(PhotoFormat.GIF, PhotoFormat.TIFF, PhotoFormat.BMP, PhotoFormat.AVIF)) assertEquals(false, format.stored, format.name)
    }

    @Test fun anythingElseIsNotAPicture() {
        assertNull(PhotoFormat.detect(ByteArray(0)))
        assertNull(PhotoFormat.detect(text("%PDF-1.7")))
        assertNull(PhotoFormat.detect(text("BMW is not a bitmap")))
        assertNull(PhotoFormat.detect(text("RIFF") + bytes(0x24, 0, 0, 0) + text("WAVEfmt ")))
        assertNull(PhotoFormat.detect(bytes(0xFF)))
    }

    @Test fun originalsKeepTheirFormatsExtensionAndTheRestAreImg() {
        assertEquals("jpg", ImageFormats.extension(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("heic", ImageFormats.extension(ftyp("heic")))
        assertEquals("img", ImageFormats.extension(text("GIF89a") + ByteArray(8)))
        assertEquals("img", ImageFormats.extension(text("nothing")))
        // Documents take the same four, and PDFs.
        assertEquals("image/webp" to "webp", DocumentFormats.detect(text("RIFF") + bytes(0x24, 0, 0, 0) + text("WEBPVP8 ")))
        assertNull(DocumentFormats.detect(text("GIF89a") + ByteArray(8)))
    }
}
