package io.github.tunlezah.watches.data.backup

import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Runs on every platform, so the iOS zlib path is exercised too (iOS CI runs these). */
class ZipTest {

    @Test fun crcMatchesTheStandardCheckValue() {
        assertEquals(0xCBF43926L, Crc32().apply { update("123456789".encodeToByteArray()) }.value)
    }

    @Test fun writtenArchivesReadBack() {
        val out = Buffer()
        val zip = ZipWriter(out, DosTime.of(2026, 10, 18, 9, 30, 0))
        zip.add("manifest.json", """{"ok":true}""".encodeToByteArray())
        zip.add("files/photos/w/p.jpg", ByteArray(200_000) { (it % 251).toByte() })
        zip.finish()

        val reader = ZipStreamReader(out)
        val first = reader.next()!!
        assertEquals("manifest.json", first.name)
        assertEquals("""{"ok":true}""", reader.readBytes(first).decodeToString())
        val second = reader.next()!!
        assertEquals(200_000L, second.size)
        val data = reader.readBytes(second)
        assertEquals((199_999 % 251).toByte(), data.last())
        assertNull(reader.next())
    }

    @Test fun deflatedEntriesWithDataDescriptorsAreRead() {
        // Written by Python's zipfile to a stream that can't seek: deflated, sizes in trailing descriptors.
        val archive = Buffer().write(PYTHON_STREAMED.decodeBase64()!!)
        val reader = ZipStreamReader(archive)
        val manifest = reader.next()!!
        assertEquals("Backup/manifest.json", manifest.name)
        assertEquals("{\"hello\": \"world\"}\n".repeat(20), reader.readBytes(manifest).decodeToString())
        val file = reader.next()!!
        assertEquals("Tick tock. ".repeat(500), reader.readBytes(file).decodeToString())
        assertNull(reader.next())
    }

    @Test fun storedEntriesWithADescriptorUseTheSizesInTheirHeader() {
        // Some writers set the descriptor flag on stored entries and still fill in the header.
        val archive = Buffer()
        val entries = listOf("a.txt" to "first, stored", "b.txt" to "second")
        for ((name, text) in entries) {
            val data = text.encodeToByteArray()
            val crc = Crc32().apply { update(data) }.value.toInt()
            archive.writeIntLe(0x04034b50).writeShortLe(20).writeShortLe(0x0008).writeShortLe(0).writeShortLe(0).writeShortLe(0)
            archive.writeIntLe(crc).writeIntLe(data.size).writeIntLe(data.size).writeShortLe(name.length).writeShortLe(0)
            archive.writeUtf8(name).write(data)
            archive.writeIntLe(0x08074b50).writeIntLe(crc).writeIntLe(data.size).writeIntLe(data.size)
        }
        archive.writeIntLe(0x02014b50)

        val reader = ZipStreamReader(archive)
        val first = reader.next()!!
        assertEquals("first, stored", reader.readBytes(first).decodeToString())
        reader.skip(reader.next()!!)
        assertNull(reader.next(), "the stream stays in step past each descriptor")
    }

    @Test fun skippingADeflatedEntryLandsOnTheNext() {
        val reader = ZipStreamReader(Buffer().write(PYTHON_STREAMED.decodeBase64()!!))
        reader.next()
        assertEquals("Backup/files/a.txt", reader.next()!!.name)
    }

    @Test fun corruptDataIsCaught() {
        val out = Buffer()
        val zip = ZipWriter(out)
        zip.add("a.txt", "hello world".encodeToByteArray())
        zip.finish()
        val bytes = out.readByteArray()
        bytes[30 + "a.txt".length + 1] = 'X'.code.toByte()
        val reader = ZipStreamReader(Buffer().write(bytes))
        val entry = reader.next()!!
        assertFailsWith<ZipFormatException> { reader.readBytes(entry) }
    }

    @Test fun pathsOutsideTheMediaFoldersAreRefused() {
        val safe = listOf("photos/abc-123/p1-original.jpg", "documents/w/d.pdf", "snapshots/w/grail-1.json")
        val unsafe = listOf("../etc/passwd", "/photos/a/b.jpg", "photos/../x/y.jpg", "photos/a/../../b", "other/a/b.jpg", "photos/a", "photos/a/b/c.jpg", "photos/.hidden/b.jpg")
        safe.forEach { assertEquals(true, ArchivePaths.isSafe(it), it) }
        unsafe.forEach { assertEquals(false, ArchivePaths.isSafe(it), it) }
        assertEquals("photos/w/p-original-rabc123.jpg", ArchivePaths.variant("photos/w/p-original.jpg", "abc123"))
        assertEquals("photos/w/p-original-rzzz999.jpg", ArchivePaths.variant("photos/w/p-original-rabc123.jpg", "zzz999"))
    }

    private companion object {
        const val PYTHON_STREAMED =
            "UEsDBBQACAAIAAAAIQAAAAAAAAAAAAAAAAAUAAAAQmFja3VwL21hbmlmZXN0Lmpzb26rVspIzcnJV7JSUCrPL8pJUarlqh4Voo8QAFBLBwhL6IXmGgAAAHwBAABQSwMEFAAIAAgAAAAhAAAAAAAAAAAAAAAAABIAAABCYWNrdXAvZmlsZXMvYS50eHTtxjENADAIADArKJgaHHARjj3zn5ngbK9m18S7NSdSVVVVVVVVVVVVVXf6AVBLBwihDznRLAAAAHwVAABQSwECFAMUAAgACAAAACEAS+iF5hoAAAB8AQAAFAAAAAAAAAAAAAAAgAEAAAAAQmFja3VwL21hbmlmZXN0Lmpzb25QSwECFAMUAAgACAAAACEAoQ850SwAAAB8FQAAEgAAAAAAAAAAAAAAgAFcAAAAQmFja3VwL2ZpbGVzL2EudHh0UEsFBgAAAAACAAIAggAAAMgAAAAAAA=="
    }
}
