package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.backup.ZipStreamReader
import io.github.tunlezah.watches.data.backup.ZipWriter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.buffer
import okio.use
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Backups against an independent ZIP implementation, java.util.zip: what any unzip tool can open,
 * and what a computer writes when it re-zips a backup. JVM only; BackupRepositoryTest runs everywhere.
 */
class ArchiveInteropTest {

    private val phones = TestPhones()

    @AfterTest
    fun tearDown() = phones.close()

    @Test fun archivesAreOrdinaryZipFilesWithTheManifestFirst() = runBlocking<Unit> {
        val phone = phones.new()
        phone.seed()
        val archive = phone.archive().toFile()
        ZipFile(archive).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(listOf("manifest.json", "collection.json"), names.take(2))
            assertTrue(names.drop(2).all { it.startsWith("files/photos/") || it.startsWith("files/documents/") || it.startsWith("files/snapshots/") })
            val manifest = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("manifest.json")).readBytes().decodeToString()).jsonObject
            assertEquals("io.github.tunlezah.watches.backup", manifest["format"]!!.jsonPrimitive.content)
            assertEquals(names.size - 1, manifest["files"]!!.jsonArray.size)
        }
    }

    @Test fun jsonKeysAreTheDatabaseColumnNames() = runBlocking<Unit> {
        val phone = phones.new()
        phone.seed()
        val schema = Json.parseToJsonElement(File("schemas/io.github.tunlezah.watches.data.db.AppDatabase/1.json").readText())
            .jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .associate { e -> e.jsonObject["tableName"]!!.jsonPrimitive.content to e.jsonObject["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }.toSet() }
        val collection = ZipFile(phone.archive().toFile()).use { it.getInputStream(it.getEntry("collection.json")).readBytes().decodeToString() }
        val tables = Json.parseToJsonElement(collection).jsonObject
        for ((table, rows) in tables) {
            val columns = schema[table] ?: error("$table isn't a table")
            for (row in rows.jsonArray) assertTrue(columns.containsAll(row.jsonObject.keys), "$table: ${row.jsonObject.keys - columns}")
        }
        assertTrue(tables["watch"]!!.jsonArray.first().jsonObject.keys.containsAll(listOf("model_name", "diameter_mm", "price_minor", "service_interval_months")))
    }

    @Test fun archivesRezippedOnAComputerStillRestore() = runBlocking<Unit> {
        val source = phones.new()
        source.seed()
        val before = source.everything()
        // Like macOS's Archive Utility: deflated, data descriptors, a top folder and resource forks.
        val rezipped = (source.dir / "rezipped.zip").toFile()
        ZipInputStream(source.archive().toFile().inputStream()).use { input ->
            ZipOutputStream(rezipped.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("__MACOSX/Watches backup/._manifest.json")); out.write(byteArrayOf(1, 2, 3))
                val entries = generateSequence { input.nextEntry }.map { it.name to input.readBytes() }.toList()
                for ((name, data) in entries.reversed()) {
                    out.putNextEntry(ZipEntry("Watches backup/$name"))
                    out.write(data)
                }
            }
        }
        val target = phones.new()
        val rezippedPath = source.dir / "rezipped.zip"
        target.backup.restore({ FileSystem.SYSTEM.source(rezippedPath) }, target.backup.inspect { FileSystem.SYSTEM.source(rezippedPath) }, RestoreMode.REPLACE)
        assertEquals(before, target.everything())
    }

    @Test fun zip64KicksInPastSixtyFiveThousandEntries() {
        val out = phones.scratchDir() / "many.zip"
        FileSystem.SYSTEM.sink(out).buffer().use { sink ->
            val zip = ZipWriter(sink)
            repeat(70_000) { zip.add("f$it.txt", "x$it".encodeToByteArray()) }
            zip.finish()
        }
        ZipFile(out.toFile()).use { assertEquals(70_000, it.size()) }
        val reader = ZipStreamReader(FileSystem.SYSTEM.source(out))
        var count = 0
        while (reader.next()?.also { reader.skip(it) } != null) count++
        reader.close()
        assertEquals(70_000, count)
    }
}
