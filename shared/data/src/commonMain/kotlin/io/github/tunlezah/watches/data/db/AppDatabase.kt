package io.github.tunlezah.watches.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        WatchEntity::class,
        WatchComplicationEntity::class,
        FieldProvenanceEntity::class,
        OtherSpecEntity::class,
        PhotoEntity::class,
        ServiceEntryEntity::class,
        BoxPapersItemEntity::class,
        DocumentEntity::class,
        SourceSnapshotEntity::class,
        RecentSearchEntity::class,
        SettingEntity::class,
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchDao(): WatchDao
    abstract fun photoDao(): PhotoDao
    abstract fun settingDao(): SettingDao
    abstract fun lookupDao(): LookupDao
    abstract fun serviceDao(): ServiceDao
    abstract fun boxPapersDao(): BoxPapersDao
    abstract fun documentDao(): DocumentDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val FILE_NAME = "watches.db"
    }
}

// Room generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/**
 * Same SQLite build on every platform (bundled), queries off the main thread.
 * Foreign keys are enforced so deleting a watch removes everything that hangs off it.
 */
fun RoomDatabase.Builder<AppDatabase>.configure(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(SearchIndex.Callback)
        .build()

/**
 * The full-text index is a plain FTS4 virtual table managed here rather than a Room entity:
 * Room's FTS entity processing fails for Kotlin/Native targets, and the table is a rebuildable
 * cache anyway (it is not part of backups). One row per watch holds every searchable word.
 */
object SearchIndex {
    const val TABLE = "watch_fts"

    const val CREATE_SQL =
        "CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE USING fts4(" +
            "watch_id, body, notindexed=watch_id, tokenize=unicode61 \"remove_diacritics=2\")"

    const val DELETE_SQL = "DELETE FROM $TABLE WHERE watch_id = ?"
    const val INSERT_SQL = "INSERT INTO $TABLE(watch_id, body) VALUES (?, ?)"
    const val MATCH_SQL = "SELECT watch_id FROM $TABLE WHERE $TABLE MATCH ?"

    object Callback : RoomDatabase.Callback() {
        override fun onOpen(connection: SQLiteConnection) {
            connection.execSQL(CREATE_SQL)
        }
    }
}
