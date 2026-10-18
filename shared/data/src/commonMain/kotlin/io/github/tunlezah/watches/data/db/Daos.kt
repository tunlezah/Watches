package io.github.tunlezah.watches.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {

    @Query(
        """
        SELECT w.id, w.brand, w.model_name, w.collection, w.reference, w.nickname, w.status, w.movement_type,
               w.diameter_mm, w.acquired_on, w.created_at, w.archived_at, w.dial_colour, w.case_material,
               (SELECT group_concat(c.key, '|') FROM watch_complication c WHERE c.watch_id = w.id) AS complications,
               p.thumb_file AS cover_thumb, p.display_file AS cover_display
        FROM watch w
        LEFT JOIN photo p ON p.id = COALESCE(
            (SELECT p1.id FROM photo p1 WHERE p1.id = w.cover_photo_id),
            (SELECT p2.id FROM photo p2 WHERE p2.watch_id = w.id AND p2.kind = 'user' ORDER BY p2.position LIMIT 1))
        """,
    )
    fun observeSummaries(): Flow<List<WatchSummaryRow>>

    @Query("SELECT * FROM watch WHERE id = :id")
    fun observeWatch(id: String): Flow<WatchEntity?>

    @Query("SELECT * FROM watch WHERE id = :id")
    suspend fun getWatch(id: String): WatchEntity?

    @Query("SELECT key FROM watch_complication WHERE watch_id = :id")
    fun observeComplications(id: String): Flow<List<String>>

    @Query("SELECT key FROM watch_complication WHERE watch_id = :id")
    suspend fun getComplications(id: String): List<String>

    @Query("SELECT * FROM field_provenance WHERE watch_id = :id")
    fun observeProvenance(id: String): Flow<List<FieldProvenanceEntity>>

    @Query("SELECT * FROM field_provenance WHERE watch_id = :id")
    suspend fun getProvenance(id: String): List<FieldProvenanceEntity>

    @Query("SELECT * FROM other_spec WHERE watch_id = :id ORDER BY position")
    fun observeOtherSpecs(id: String): Flow<List<OtherSpecEntity>>

    @Query("SELECT * FROM other_spec WHERE watch_id = :id ORDER BY position")
    suspend fun getOtherSpecs(id: String): List<OtherSpecEntity>

    @Query("SELECT DISTINCT brand FROM watch WHERE brand IS NOT NULL AND brand != '' ORDER BY brand COLLATE NOCASE")
    fun observeBrands(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM watch")
    suspend fun count(): Int

    @Query("SELECT id FROM watch WHERE id LIKE 'sample-%'")
    suspend fun sampleIds(): List<String>

    @Upsert
    suspend fun upsertWatch(watch: WatchEntity)

    @Query("DELETE FROM watch_complication WHERE watch_id = :id")
    suspend fun clearComplications(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplications(rows: List<WatchComplicationEntity>)

    @Upsert
    suspend fun upsertProvenance(rows: List<FieldProvenanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOtherSpecs(rows: List<OtherSpecEntity>)

    @Query("DELETE FROM watch WHERE id = :id")
    suspend fun deleteWatch(id: String)

    @Query("UPDATE watch SET archived_at = :archivedAt, updated_at = :now WHERE id = :id")
    suspend fun setArchived(id: String, archivedAt: Long?, now: Long)

    @Query("UPDATE watch SET cover_photo_id = :photoId, updated_at = :now WHERE id = :id")
    suspend fun setCover(id: String, photoId: String?, now: Long)

    @Query("DELETE FROM other_spec WHERE watch_id = :id")
    suspend fun clearOtherSpecs(id: String)
}

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photo WHERE watch_id = :watchId ORDER BY position")
    fun observeForWatch(watchId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photo WHERE watch_id = :watchId ORDER BY position")
    suspend fun getForWatch(watchId: String): List<PhotoEntity>

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun get(id: String): PhotoEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM photo WHERE watch_id = :watchId")
    suspend fun maxPosition(watchId: String): Int

    @Upsert
    suspend fun upsert(photo: PhotoEntity)

    @Query("UPDATE photo SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("DELETE FROM photo WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> setPosition(id, index) }
    }
}

@Dao
interface SettingDao {

    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Upsert
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM setting WHERE `key` = :key")
    suspend fun remove(key: String)
}

@Dao
interface LookupDao {

    @Query("SELECT * FROM recent_search ORDER BY used_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentSearchEntity>>

    @Upsert
    suspend fun upsertRecent(row: RecentSearchEntity)

    @Query("DELETE FROM recent_search WHERE `query` = :query")
    suspend fun deleteRecent(query: String)

    @Query("DELETE FROM recent_search")
    suspend fun clearRecent()

    @Query("DELETE FROM recent_search WHERE `query` NOT IN (SELECT `query` FROM recent_search ORDER BY used_at DESC LIMIT :keep)")
    suspend fun trimRecent(keep: Int)

    @Query("SELECT * FROM source_snapshot WHERE watch_id = :watchId ORDER BY fetched_at DESC")
    suspend fun snapshots(watchId: String): List<SourceSnapshotEntity>

    @Query("SELECT * FROM source_snapshot WHERE watch_id = :watchId AND source = :source AND url = :url")
    suspend fun snapshotsFor(watchId: String, source: String, url: String): List<SourceSnapshotEntity>

    @Upsert
    suspend fun upsertSnapshot(row: SourceSnapshotEntity)

    @Query("DELETE FROM source_snapshot WHERE id = :id")
    suspend fun deleteSnapshot(id: String)
}

@Dao
interface ServiceDao {

    @Query("SELECT * FROM service_entry WHERE watch_id = :watchId ORDER BY serviced_on DESC, created_at DESC")
    fun observeForWatch(watchId: String): Flow<List<ServiceEntryEntity>>

    @Query("SELECT * FROM service_entry WHERE watch_id = :watchId ORDER BY serviced_on DESC, created_at DESC")
    suspend fun forWatch(watchId: String): List<ServiceEntryEntity>

    @Query("SELECT * FROM service_entry WHERE id = :id")
    suspend fun get(id: String): ServiceEntryEntity?

    @Upsert
    suspend fun upsert(entry: ServiceEntryEntity)

    @Query("DELETE FROM service_entry WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT DISTINCT serviced_by FROM service_entry WHERE serviced_by IS NOT NULL AND serviced_by != '' ORDER BY serviced_by COLLATE NOCASE")
    fun observeServicers(): Flow<List<String>>

    /** Service entries whose work carries its own warranty, newest first. */
    @Query("SELECT * FROM service_entry WHERE warranty_months IS NOT NULL AND warranty_months > 0 ORDER BY serviced_on DESC")
    fun observeWithWarranty(): Flow<List<ServiceEntryEntity>>

    @Query(
        """
        SELECT w.id, w.brand, w.model_name, w.collection, w.reference, w.nickname, w.status, w.archived_at,
               w.acquired_on, w.service_interval_months, w.service_reminder, w.warranty_start, w.warranty_months,
               (SELECT MAX(s.serviced_on) FROM service_entry s WHERE s.watch_id = w.id) AS last_service,
               (SELECT MAX(s.serviced_on) FROM service_entry s WHERE s.watch_id = w.id
                    AND (',' || s.work || ',') LIKE '%,full_service,%') AS last_full_service,
               p.thumb_file AS cover_thumb
        FROM watch w
        LEFT JOIN photo p ON p.id = COALESCE(
            (SELECT p1.id FROM photo p1 WHERE p1.id = w.cover_photo_id),
            (SELECT p2.id FROM photo p2 WHERE p2.watch_id = w.id AND p2.kind = 'user' ORDER BY p2.position LIMIT 1))
        """,
    )
    fun observeCare(): Flow<List<CareRow>>
}

@Dao
interface BoxPapersDao {

    @Query("SELECT * FROM box_papers_item WHERE watch_id = :watchId")
    fun observe(watchId: String): Flow<List<BoxPapersItemEntity>>

    @Query("SELECT * FROM box_papers_item WHERE watch_id = :watchId")
    suspend fun get(watchId: String): List<BoxPapersItemEntity>

    @Upsert
    suspend fun upsert(rows: List<BoxPapersItemEntity>)

    @Query("DELETE FROM box_papers_item WHERE watch_id = :watchId AND item = :item")
    suspend fun delete(watchId: String, item: String)
}

@Dao
interface DocumentDao {

    @Query("SELECT * FROM document WHERE watch_id = :watchId ORDER BY added_at DESC")
    fun observeForWatch(watchId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document WHERE watch_id = :watchId")
    suspend fun forWatch(watchId: String): List<DocumentEntity>

    @Query("SELECT * FROM document WHERE service_entry_id = :entryId ORDER BY added_at")
    suspend fun forEntry(entryId: String): List<DocumentEntity>

    @Query("SELECT * FROM document WHERE id = :id")
    suspend fun get(id: String): DocumentEntity?

    @Query("SELECT * FROM document WHERE id = :id")
    fun observe(id: String): Flow<DocumentEntity?>

    @Upsert
    suspend fun upsert(row: DocumentEntity)

    @Query("DELETE FROM document WHERE id = :id")
    suspend fun delete(id: String)

    /** Manuals and quick start guides kept with other watches, for "use the manual from another watch". */
    @Query(
        """
        SELECT d.*, w.brand AS watch_brand, w.model_name AS watch_model, w.collection AS watch_collection, w.caliber AS watch_caliber
        FROM document d JOIN watch w ON w.id = d.watch_id
        WHERE d.kind IN ('manual', 'quick_start') AND d.watch_id != :exceptWatchId
        ORDER BY w.brand COLLATE NOCASE, w.model_name COLLATE NOCASE, d.added_at DESC
        """,
    )
    fun observeGuidesElsewhere(exceptWatchId: String): Flow<List<GuideRow>>
}

/** Whole-table reads and writes for backup export and import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM watch ORDER BY created_at, id") suspend fun watches(): List<WatchEntity>
    @Query("SELECT * FROM watch_complication ORDER BY watch_id, `key`") suspend fun complications(): List<WatchComplicationEntity>
    @Query("SELECT * FROM field_provenance ORDER BY watch_id, field_key") suspend fun provenance(): List<FieldProvenanceEntity>
    @Query("SELECT * FROM other_spec ORDER BY watch_id, position") suspend fun otherSpecs(): List<OtherSpecEntity>
    @Query("SELECT * FROM photo ORDER BY watch_id, position") suspend fun photos(): List<PhotoEntity>
    @Query("SELECT * FROM service_entry ORDER BY watch_id, serviced_on") suspend fun serviceEntries(): List<ServiceEntryEntity>
    @Query("SELECT * FROM box_papers_item ORDER BY watch_id, item") suspend fun boxPapers(): List<BoxPapersItemEntity>
    @Query("SELECT * FROM document ORDER BY watch_id, added_at") suspend fun documents(): List<DocumentEntity>
    @Query("SELECT * FROM source_snapshot ORDER BY watch_id, fetched_at") suspend fun snapshots(): List<SourceSnapshotEntity>
    @Query("SELECT * FROM setting ORDER BY `key`") suspend fun settings(): List<SettingEntity>

    @Upsert suspend fun upsertWatches(rows: List<WatchEntity>)
    @Upsert suspend fun upsertComplications(rows: List<WatchComplicationEntity>)
    @Upsert suspend fun upsertProvenance(rows: List<FieldProvenanceEntity>)
    @Upsert suspend fun upsertOtherSpecs(rows: List<OtherSpecEntity>)
    @Upsert suspend fun upsertPhotos(rows: List<PhotoEntity>)
    @Upsert suspend fun upsertServiceEntries(rows: List<ServiceEntryEntity>)
    @Upsert suspend fun upsertBoxPapers(rows: List<BoxPapersItemEntity>)
    @Upsert suspend fun upsertDocuments(rows: List<DocumentEntity>)
    @Upsert suspend fun upsertSnapshots(rows: List<SourceSnapshotEntity>)
    @Upsert suspend fun upsertSettings(rows: List<SettingEntity>)

    /** Removes every watch; foreign keys cascade to everything that hangs off one. */
    @Query("DELETE FROM watch") suspend fun deleteAllWatches()
    @Query("DELETE FROM setting") suspend fun deleteAllSettings()

    // A watch's own lists, replaced as a whole when a merge takes the backup's version of it.
    @Query("DELETE FROM watch_complication WHERE watch_id = :watchId") suspend fun deleteComplications(watchId: String)
    @Query("DELETE FROM field_provenance WHERE watch_id = :watchId") suspend fun deleteProvenance(watchId: String)
    @Query("DELETE FROM other_spec WHERE watch_id = :watchId") suspend fun deleteOtherSpecs(watchId: String)
    @Query("DELETE FROM box_papers_item WHERE watch_id = :watchId") suspend fun deleteBoxPapers(watchId: String)

    /** A cover must be one of the watch's own photos; anything else falls back to the first photo. */
    @Query("UPDATE watch SET cover_photo_id = NULL WHERE cover_photo_id IS NOT NULL AND cover_photo_id NOT IN (SELECT p.id FROM photo p WHERE p.watch_id = watch.id)")
    suspend fun clearDanglingCovers()

    @Query("SELECT original_file FROM photo UNION ALL SELECT display_file FROM photo UNION ALL SELECT thumb_file FROM photo UNION ALL SELECT file FROM document UNION ALL SELECT body_file FROM source_snapshot")
    suspend fun referencedFiles(): List<String>
}
