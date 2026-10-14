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
               w.diameter_mm, w.acquired_on, w.created_at, w.archived_at,
               (SELECT group_concat(c.key, '|') FROM watch_complication c WHERE c.watch_id = w.id) AS complications,
               p.thumb_file AS cover_thumb, p.display_file AS cover_display
        FROM watch w
        LEFT JOIN photo p ON p.id = COALESCE(
            (SELECT p1.id FROM photo p1 WHERE p1.id = w.cover_photo_id),
            (SELECT p2.id FROM photo p2 WHERE p2.watch_id = w.id ORDER BY p2.position LIMIT 1))
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
