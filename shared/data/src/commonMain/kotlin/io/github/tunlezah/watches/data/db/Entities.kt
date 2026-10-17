package io.github.tunlezah.watches.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Schema v1. Column names are part of the backup format, so they are spelled out explicitly.
// Timestamps are epoch milliseconds, dates ISO "YYYY-MM-DD", money in minor units plus ISO currency.

@Entity(tableName = "watch", indices = [Index("brand"), Index("status")])
data class WatchEntity(
    @PrimaryKey val id: String,
    val status: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "cover_photo_id") val coverPhotoId: String?,
    val brand: String?,
    val collection: String?,
    @ColumnInfo(name = "model_name") val modelName: String?,
    val reference: String?,
    val nickname: String?,
    @ColumnInfo(name = "year_from") val yearFrom: Int?,
    @ColumnInfo(name = "year_to") val yearTo: Int?,
    @ColumnInfo(name = "limited_number") val limitedNumber: Int?,
    @ColumnInfo(name = "limited_total") val limitedTotal: Int?,
    @ColumnInfo(name = "case_material") val caseMaterial: String?,
    @ColumnInfo(name = "diameter_mm") val diameterMm: Double?,
    @ColumnInfo(name = "thickness_mm") val thicknessMm: Double?,
    @ColumnInfo(name = "lug_to_lug_mm") val lugToLugMm: Double?,
    @ColumnInfo(name = "lug_width_mm") val lugWidthMm: Double?,
    val bezel: String?,
    val crystal: String?,
    @ColumnInfo(name = "case_back") val caseBack: String?,
    val crown: String?,
    @ColumnInfo(name = "water_resistance_m") val waterResistanceM: Int?,
    @ColumnInfo(name = "dial_colour") val dialColour: String?,
    @ColumnInfo(name = "dial_finish") val dialFinish: String?,
    val indices: String?,
    val hands: String?,
    val lume: String?,
    val caliber: String?,
    @ColumnInfo(name = "base_caliber") val baseCaliber: String?,
    @ColumnInfo(name = "movement_type") val movementType: String?,
    val jewels: Int?,
    @ColumnInfo(name = "frequency_bph") val frequencyBph: Int?,
    @ColumnInfo(name = "power_reserve_h") val powerReserveH: Double?,
    val hacking: Boolean?,
    @ColumnInfo(name = "hand_winding") val handWinding: Boolean?,
    @ColumnInfo(name = "strap_type") val strapType: String?,
    @ColumnInfo(name = "strap_material") val strapMaterial: String?,
    val clasp: String?,
    @ColumnInfo(name = "fitted_strap") val fittedStrap: String?,
    @ColumnInfo(name = "acquired_on") val acquiredOn: String?,
    @ColumnInfo(name = "acquired_from") val acquiredFrom: String?,
    @ColumnInfo(name = "price_minor") val priceMinor: Long?,
    @ColumnInfo(name = "price_currency") val priceCurrency: String?,
    val condition: String?,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    @ColumnInfo(name = "private_notes") val privateNotes: String?,
    val story: String?,
    @ColumnInfo(name = "service_interval_months") val serviceIntervalMonths: Int?,
    @ColumnInfo(name = "service_reminder") val serviceReminder: Boolean,
    @ColumnInfo(name = "warranty_start") val warrantyStart: String?,
    @ColumnInfo(name = "warranty_months") val warrantyMonths: Int?,
)

@Entity(
    tableName = "watch_complication",
    primaryKeys = ["watch_id", "key"],
    foreignKeys = [ForeignKey(WatchEntity::class, ["id"], ["watch_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("key")],
)
data class WatchComplicationEntity(
    @ColumnInfo(name = "watch_id") val watchId: String,
    val key: String,
)

@Entity(
    tableName = "field_provenance",
    primaryKeys = ["watch_id", "field_key"],
    foreignKeys = [ForeignKey(WatchEntity::class, ["id"], ["watch_id"], onDelete = ForeignKey.CASCADE)],
)
data class FieldProvenanceEntity(
    @ColumnInfo(name = "watch_id") val watchId: String,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    val source: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long?,
    @ColumnInfo(name = "user_edited") val userEdited: Boolean,
)

@Entity(
    tableName = "other_spec",
    foreignKeys = [ForeignKey(WatchEntity::class, ["id"], ["watch_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("watch_id")],
)
data class OtherSpecEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "watch_id") val watchId: String,
    val label: String,
    val value: String,
    val source: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long?,
    val position: Int,
)

@Entity(
    tableName = "photo",
    foreignKeys = [ForeignKey(WatchEntity::class, ["id"], ["watch_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("watch_id")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "watch_id") val watchId: String,
    val position: Int,
    @ColumnInfo(name = "original_file") val originalFile: String,
    @ColumnInfo(name = "display_file") val displayFile: String,
    @ColumnInfo(name = "thumb_file") val thumbFile: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "crop_left") val cropLeft: Float?,
    @ColumnInfo(name = "crop_top") val cropTop: Float?,
    @ColumnInfo(name = "crop_right") val cropRight: Float?,
    @ColumnInfo(name = "crop_bottom") val cropBottom: Float?,
    val kind: String,
    val attribution: String?,
    @ColumnInfo(name = "attribution_url") val attributionUrl: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(
    tableName = "service_entry",
    foreignKeys = [ForeignKey(WatchEntity::class, ["id"], ["watch_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("watch_id")],
)
data class ServiceEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "watch_id") val watchId: String,
    @ColumnInfo(name = "serviced_on") val servicedOn: String,
    @ColumnInfo(name = "serviced_by") val servicedBy: String?,
    /** Comma-separated [io.github.tunlezah.watches.core.model.ServiceWork] keys. */
    val work: String,
    @ColumnInfo(name = "cost_minor") val costMinor: Long?,
    @ColumnInfo(name = "cost_currency") val costCurrency: String?,
    @ColumnInfo(name = "warranty_months") val warrantyMonths: Int?,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "box_papers_item",
    primaryKeys = ["watch_id", "item"],
    foreignKeys = [ForeignKey(WatchEntity::class, ["id"], ["watch_id"], onDelete = ForeignKey.CASCADE)],
)
data class BoxPapersItemEntity(
    @ColumnInfo(name = "watch_id") val watchId: String,
    val item: String,
    val state: String,
    val label: String?,
    val note: String?,
)

@Entity(
    tableName = "document",
    foreignKeys = [
        ForeignKey(WatchEntity::class, ["id"], ["watch_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ServiceEntryEntity::class, ["id"], ["service_entry_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("watch_id"), Index("service_entry_id")],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "watch_id") val watchId: String,
    @ColumnInfo(name = "service_entry_id") val serviceEntryId: String?,
    val kind: String,
    val title: String,
    val file: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** A source's raw detail payload, kept only for saved watches so a fixed parser can re-read it later. */
@Entity(
    tableName = "source_snapshot",
    foreignKeys = [ForeignKey(WatchEntity::class, ["id"], ["watch_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("watch_id")],
)
data class SourceSnapshotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "watch_id") val watchId: String,
    val source: String,
    val url: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "body_file") val bodyFile: String,
)

@Entity(tableName = "recent_search")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    @ColumnInfo(name = "used_at") val usedAt: Long,
)

@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Row shape of the collection projection query. */
data class WatchSummaryRow(
    val id: String,
    val brand: String?,
    @ColumnInfo(name = "model_name") val modelName: String?,
    val collection: String?,
    val reference: String?,
    val nickname: String?,
    val status: String,
    @ColumnInfo(name = "movement_type") val movementType: String?,
    @ColumnInfo(name = "diameter_mm") val diameterMm: Double?,
    @ColumnInfo(name = "acquired_on") val acquiredOn: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    val complications: String?,
    @ColumnInfo(name = "cover_thumb") val coverThumb: String?,
    @ColumnInfo(name = "cover_display") val coverDisplay: String?,
    @ColumnInfo(name = "dial_colour") val dialColour: String?,
    @ColumnInfo(name = "case_material") val caseMaterial: String?,
)

/** One watch's service and warranty inputs, for the Service tab and reminders. */
data class CareRow(
    val id: String,
    val brand: String?,
    @ColumnInfo(name = "model_name") val modelName: String?,
    val collection: String?,
    val reference: String?,
    val nickname: String?,
    val status: String,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "acquired_on") val acquiredOn: String?,
    @ColumnInfo(name = "service_interval_months") val serviceIntervalMonths: Int?,
    @ColumnInfo(name = "service_reminder") val serviceReminder: Boolean,
    @ColumnInfo(name = "warranty_start") val warrantyStart: String?,
    @ColumnInfo(name = "warranty_months") val warrantyMonths: Int?,
    @ColumnInfo(name = "last_service") val lastService: String?,
    @ColumnInfo(name = "last_full_service") val lastFullService: String?,
    @ColumnInfo(name = "cover_thumb") val coverThumb: String?,
)

/** A manual or quick start guide, with the watch it belongs to. */
data class GuideRow(
    @androidx.room.Embedded val document: DocumentEntity,
    @ColumnInfo(name = "watch_brand") val watchBrand: String?,
    @ColumnInfo(name = "watch_model") val watchModel: String?,
    @ColumnInfo(name = "watch_collection") val watchCollection: String?,
    @ColumnInfo(name = "watch_caliber") val watchCaliber: String?,
)
