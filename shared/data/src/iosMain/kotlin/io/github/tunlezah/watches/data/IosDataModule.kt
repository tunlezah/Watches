package io.github.tunlezah.watches.data

import androidx.room.Room
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.files.IosImageProcessor
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/** App data lives in Application Support, excluded from iCloud backup: export is the backup. */
@OptIn(ExperimentalForeignApi::class)
private fun appDataDir(): String {
    val fm = NSFileManager.defaultManager
    val base = fm.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
        ?: error("No Application Support directory")
    val dir: NSURL = base.URLByAppendingPathComponent("Watches") ?: base
    fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)
    dir.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return dir.path ?: error("No path")
}

fun iosDataModule() = module {
    val root = appDataDir()
    single { Room.databaseBuilder<AppDatabase>(name = "$root/${AppDatabase.FILE_NAME}").configure() }
    single { FileStore("$root/files".toPath()) }
    single<ImageProcessor> { IosImageProcessor() }
}
