package io.github.tunlezah.watches.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import io.github.tunlezah.watches.data.databaseFile
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.storedSchemaVersion
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The shell's own rules: where the collection lives, one copy at a time, a newer collection, the version. */
class ShellTest {

    private fun tempDir(): File = Files.createTempDirectory("watches-shell-test").toFile()

    @Test fun theCollectionLivesInApplicationSupportOnAMac() {
        val dir = AppPaths.dataDir(os = "Mac OS X", home = "/Users/someone", env = { null }, override = null)
        assertEquals(File("/Users/someone/Library/Application Support/Watches"), dir)
    }

    @Test fun otherSystemsUseTheirOwnPlaceAndAnOverrideWins() {
        assertEquals(File("/home/someone/.local/share/Watches"), AppPaths.dataDir(os = "Linux", home = "/home/someone", env = { null }, override = null))
        assertEquals(File("/data/Watches"), AppPaths.dataDir(os = "Linux", home = "/home/someone", env = { if (it == "XDG_DATA_HOME") "/data" else null }, override = null))
        assertEquals(File("/tmp/try"), AppPaths.dataDir(os = "Mac OS X", home = "/Users/someone", env = { null }, override = "/tmp/try"))
    }

    @Test fun aSecondCopyDoesNotGetTheCollection() {
        val dir = tempDir()
        val first = assertNotNull(SingleInstance.acquire(dir))
        assertNull(SingleInstance.acquire(dir), "a second copy must not open the same collection")
        first.close()
        // Once the first has gone, the next start gets it.
        assertNotNull(SingleInstance.acquire(dir)).close()
    }

    @Test fun anEmptyFolderAndACurrentCollectionAreUsable() {
        val dir = tempDir()
        assertNull(storedSchemaVersion(databaseFile(dir.path)))
        assertEquals(StoredCollection.Usable, StoredCollection.check(dir))
        writeDatabase(dir, AppDatabase.VERSION)
        assertEquals(AppDatabase.VERSION, storedSchemaVersion(databaseFile(dir.path)))
        assertEquals(StoredCollection.Usable, StoredCollection.check(dir))
    }

    @Test fun aNewerCollectionIsReportedAndLeftAlone() {
        val dir = tempDir()
        writeDatabase(dir, AppDatabase.VERSION + 1)
        val before = sha256(File(databaseFile(dir.path)))
        val found = assertIs<StoredCollection.Newer>(StoredCollection.check(dir))
        assertEquals(AppDatabase.VERSION + 1, found.found)
        assertTrue("newer version" in found.message)
        assertEquals(before, sha256(File(databaseFile(dir.path))), "checking must not change the file")
    }

    @Test fun theVersionIsTheOneEveryPlatformShares() {
        // Tests run in desktopApp; version.xcconfig is at the repository root.
        val shared = File("../version.xcconfig").readLines()
            .mapNotNull { Regex("""^\s*(\w+)\s*=\s*(\S+)\s*$""").find(it)?.destructured?.let { (k, v) -> k to v } }.toMap()
        assertEquals(shared["MARKETING_VERSION"], BuildInfo.VERSION)
        assertEquals(shared["CURRENT_PROJECT_VERSION"], BuildInfo.BUILD.toString())
    }

    private fun writeDatabase(dir: File, version: Int) {
        val connection = BundledSQLiteDriver().open(databaseFile(dir.path))
        try {
            connection.execSQL("CREATE TABLE IF NOT EXISTS t (x INTEGER)")
            connection.execSQL("PRAGMA user_version = $version")
        } finally {
            connection.close()
        }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
