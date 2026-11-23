package io.github.tunlezah.watches.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.tunlezah.watches.data.backup.AppInfo
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.files.JvmImageProcessor
import okio.Path.Companion.toPath
import org.koin.dsl.module
import java.io.File

/** The desktop app's data: [root] holds the database and the files folder (the app's own folder, or a test's). */
fun jvmDataModule(root: String, app: AppInfo) = module {
    single { Room.databaseBuilder<AppDatabase>(name = databaseFile(root)).configure() }
    single { FileStore("$root/files".toPath()) }
    single<ImageProcessor> { JvmImageProcessor() }
    single { app }
}

fun databaseFile(root: String): String = "$root/${AppDatabase.FILE_NAME}"

/**
 * The schema version of the database file at [path], or null when there is no file yet.
 *
 * A phone never meets a database newer than its app, because the system won't install an older
 * build over a newer one. A Mac will: nothing stops an older Watches.app being dragged over a newer
 * one. Room refuses such a database, so the app reads the version first and says what happened.
 */
fun storedSchemaVersion(path: String): Int? {
    if (!File(path).isFile) return null
    val connection = BundledSQLiteDriver().open(path)
    try {
        val statement = connection.prepare("PRAGMA user_version")
        try {
            return if (statement.step()) statement.getLong(0).toInt() else 0
        } finally {
            statement.close()
        }
    } finally {
        connection.close()
    }
}
