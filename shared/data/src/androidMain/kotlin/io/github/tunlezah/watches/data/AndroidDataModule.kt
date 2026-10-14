package io.github.tunlezah.watches.data

import android.content.Context
import androidx.room.Room
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.AndroidImageProcessor
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageProcessor
import okio.Path.Companion.toOkioPath
import org.koin.dsl.module

fun androidDataModule(context: Context) = module {
    single {
        Room.databaseBuilder<AppDatabase>(
            context = context.applicationContext,
            name = context.getDatabasePath(AppDatabase.FILE_NAME).absolutePath,
        ).configure()
    }
    single { FileStore(context.filesDir.toOkioPath()) }
    single<ImageProcessor> { AndroidImageProcessor() }
}
