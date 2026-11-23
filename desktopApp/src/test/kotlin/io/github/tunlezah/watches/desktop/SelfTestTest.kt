package io.github.tunlezah.watches.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The self-test, run here against the code. CI runs it again inside the packaged app, which is the
 * run that can fail for a missing piece of the bundled Java runtime.
 */
class SelfTestTest {

    @Test fun aWorkingBuildPasses() {
        val dir = Files.createTempDirectory("watches-self-test").toFile()
        assertEquals(0, SelfTest.run(dir))
        assertTrue(File(dir, "self-test-backup.zip").length() > 0, "it wrote a backup")
        assertTrue(File(dir, "self-test.csv").length() > 0, "it wrote a spreadsheet")
        assertTrue(File(dir, "self-test.png").length() > 0, "it drew the app")
        assertTrue(File(dir, "watches.db").isFile, "the collection is where the app keeps it")
    }
}
