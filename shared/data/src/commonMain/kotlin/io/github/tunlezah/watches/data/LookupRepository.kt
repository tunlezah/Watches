package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.Ids
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.RecentSearchEntity
import io.github.tunlezah.watches.data.db.SourceSnapshotEntity
import io.github.tunlezah.watches.data.files.FileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Recent lookups, and the source pages behind applied specs. */
class LookupRepository(
    db: AppDatabase,
    private val files: FileStore,
    private val time: TimeSource = TimeSource.System,
) {
    private val dao = db.lookupDao()

    fun observeRecent(limit: Int = RECENT_LIMIT): Flow<List<String>> = dao.observeRecent(limit).map { rows -> rows.map { it.query } }

    suspend fun remember(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        dao.upsertRecent(RecentSearchEntity(q, time.now().toEpochMilliseconds()))
        dao.trimRecent(RECENT_LIMIT)
    }

    suspend fun forget(query: String) = dao.deleteRecent(query)

    suspend fun clearRecent() = dao.clearRecent()

    /**
     * Keeps each applied record's page for this watch, so a parser fix can re-read it later without
     * another request. One snapshot per source page: a newer fetch replaces the older one.
     */
    suspend fun saveSnapshots(watchId: String, records: List<SourceRecord>) {
        for (record in records) {
            val raw = record.raw ?: continue
            val previous = dao.snapshotsFor(watchId, record.source, record.url)
            val id = Ids.new()
            val ext = if ("json" in raw.contentType) "json" else "html"
            val path = "${FileStore.snapshotDir(watchId)}/${record.source}-$id.$ext"
            files.write(path, raw.body.encodeToByteArray())
            dao.upsertSnapshot(SourceSnapshotEntity(id, watchId, record.source, record.url, record.fetchedAt.toEpochMilliseconds(), raw.contentType, path))
            for (old in previous) {
                dao.deleteSnapshot(old.id)
                files.delete(old.bodyFile)
            }
        }
    }

    /** The source pages this watch's specs came from, newest first. */
    suspend fun snapshotSources(watchId: String): List<Pair<String, String>> =
        dao.snapshots(watchId).map { it.source to it.url }.distinct()

    companion object {
        const val RECENT_LIMIT = 12
    }
}
