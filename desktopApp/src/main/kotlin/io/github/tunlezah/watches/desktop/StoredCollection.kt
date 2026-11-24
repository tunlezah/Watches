package io.github.tunlezah.watches.desktop

import io.github.tunlezah.watches.data.databaseFile
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.storedSchemaVersion
import java.io.File

/** What the collection folder holds, as far as this build is concerned. */
internal sealed interface StoredCollection {
    /** No collection yet, or one this build can open. */
    data object Usable : StoredCollection

    /** Made by a newer build: opening it would fail, so the app says so and leaves it alone. */
    data class Newer(val found: Int, val known: Int) : StoredCollection {
        val message: String
            get() = "This collection was made by a newer version of Watches, so this version can't open it. " +
                "Nothing has been changed. Install the newer version again to carry on."
    }

    companion object {
        fun check(dir: File): StoredCollection {
            val found = storedSchemaVersion(databaseFile(dir.path)) ?: return Usable
            return if (found > AppDatabase.VERSION) Newer(found, AppDatabase.VERSION) else Usable
        }
    }
}
