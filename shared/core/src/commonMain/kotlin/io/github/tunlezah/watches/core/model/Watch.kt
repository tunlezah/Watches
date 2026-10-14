package io.github.tunlezah.watches.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * One physical watch or wishlist entry. Every spec field is optional.
 * Where each value came from is tracked separately in [Provenance], keyed by spec field key.
 */
data class Watch(
    val id: String,
    val status: WatchStatus = WatchStatus.OWNED,
    val archivedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val coverPhotoId: String? = null,

    // Identity
    val brand: String? = null,
    val collection: String? = null,
    val modelName: String? = null,
    val reference: String? = null,
    val nickname: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val limitedNumber: Int? = null,
    val limitedTotal: Int? = null,

    // Case
    val caseMaterial: String? = null,
    val diameterMm: Double? = null,
    val thicknessMm: Double? = null,
    val lugToLugMm: Double? = null,
    val lugWidthMm: Double? = null,
    val bezel: String? = null,
    val crystal: String? = null,
    val caseBack: String? = null,
    val crown: String? = null,
    val waterResistanceM: Int? = null,

    // Dial
    val dialColour: String? = null,
    val dialFinish: String? = null,
    val indices: String? = null,
    val hands: String? = null,
    val lume: String? = null,

    // Movement
    val caliber: String? = null,
    val baseCaliber: String? = null,
    val movementType: MovementType? = null,
    val jewels: Int? = null,
    val frequencyBph: Int? = null,
    val powerReserveH: Double? = null,
    val hacking: Boolean? = null,
    val handWinding: Boolean? = null,
    val complications: Set<String> = emptySet(),

    // Strap / bracelet
    val strapType: String? = null,
    val strapMaterial: String? = null,
    val clasp: String? = null,
    val fittedStrap: String? = null,

    // Ownership
    val acquiredOn: LocalDate? = null,
    val acquiredFrom: String? = null,
    val pricePaid: Money? = null,
    val condition: Condition? = null,
    val serialNumber: String? = null,
    val privateNotes: String? = null,
    val story: String? = null,

    // Service and warranty settings (history and box & papers live in their own tables)
    val serviceIntervalMonths: Int? = null,
    val serviceReminder: Boolean = false,
    val warrantyStart: LocalDate? = null,
    val warrantyMonths: Int? = null,
) {
    val isArchived: Boolean get() = archivedAt != null

    /** "Omega Speedmaster Professional", falling back to whatever identity is known. */
    val title: String
        get() = listOfNotNull(brand, modelName ?: collection ?: reference)
            .joinToString(" ")
            .ifBlank { nickname ?: "Untitled watch" }
}

/** A user photo or opt-in reference image. File paths are relative to the app's files root. */
data class Photo(
    val id: String,
    val watchId: String,
    val position: Int,
    val originalFile: String,
    val displayFile: String,
    val thumbFile: String,
    val width: Int,
    val height: Int,
    val crop: NormalizedRect? = null,
    val kind: PhotoKind = PhotoKind.USER,
    val attribution: String? = null,
    val attributionUrl: String? = null,
    val addedAt: Instant,
)

/** A rectangle in 0..1 coordinates of the (orientation-corrected) original image. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) { "Out of range: $this" }
        require(right > left && bottom > top) { "Empty rect: $this" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val Full = NormalizedRect(0f, 0f, 1f, 1f)
    }
}

/** Where one field's value came from. */
data class Provenance(
    val fieldKey: String,
    val source: String,
    val sourceUrl: String? = null,
    val fetchedAt: Instant? = null,
    val userEdited: Boolean = false,
)

/** A spec a source provided that has no column of its own. Kept rather than dropped. */
data class OtherSpec(
    val id: String,
    val label: String,
    val value: String,
    val source: String? = null,
    val sourceUrl: String? = null,
    val fetchedAt: Instant? = null,
    val position: Int = 0,
)

object Sources {
    const val MANUAL = "manual"

    fun displayName(source: String): String = when (source) {
        MANUAL -> "Manual"
        "watchbase" -> "WatchBase"
        "grail" -> "Grail Watch Reference"
        "wikidata" -> "Wikidata"
        "wikipedia" -> "Wikipedia"
        "ranfft" -> "Ranfft DB"
        else -> source
    }

    fun shortName(source: String): String = when (source) {
        "grail" -> "Grail"
        "ranfft" -> "Ranfft"
        else -> displayName(source)
    }
}

/** Everything the detail screen needs for one watch. */
data class WatchDetail(
    val watch: Watch,
    val photos: List<Photo>,
    val provenance: Map<String, Provenance>,
    val otherSpecs: List<OtherSpec>,
) {
    val cover: Photo? get() = photos.firstOrNull { it.id == watch.coverPhotoId } ?: photos.firstOrNull()
}

/** The slim projection the collection grid and list are built from. */
data class WatchSummary(
    val id: String,
    val brand: String?,
    val modelName: String?,
    val collection: String?,
    val reference: String?,
    val nickname: String?,
    val status: WatchStatus,
    val movementType: MovementType?,
    val diameterMm: Double?,
    val acquiredOn: LocalDate?,
    val createdAt: Instant,
    val archived: Boolean,
    val complications: Set<String>,
    val coverThumb: String?,
    val coverDisplay: String?,
) {
    val title: String
        get() = (modelName ?: collection ?: reference ?: nickname ?: brand ?: "Untitled watch")
}
