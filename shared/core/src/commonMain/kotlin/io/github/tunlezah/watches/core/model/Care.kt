package io.github.tunlezah.watches.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Instant

/** One visit to a watchmaker, or a job done at home. */
data class ServiceEntry(
    val id: String,
    val watchId: String,
    val servicedOn: LocalDate,
    val servicedBy: String? = null,
    val work: Set<ServiceWork> = emptySet(),
    val cost: Money? = null,
    /** Warranty on the work itself, separate from the watch's own warranty. */
    val warrantyMonths: Int? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isFullService: Boolean get() = ServiceWork.FULL_SERVICE in work
    val warrantyUntil: LocalDate? get() = warrantyMonths?.takeIf { it > 0 }?.let { servicedOn.plus(it, DateTimeUnit.MONTH) }
}

/** One line of the full-set checklist. An item with no row hasn't been recorded yet. */
data class BoxPapersItem(val item: BoxItem, val state: BoxItemState, val note: String? = null)

/** A PDF or image kept with a watch, optionally tied to one service entry (an invoice, say). */
data class WatchDocument(
    val id: String,
    val watchId: String,
    val serviceEntryId: String? = null,
    val kind: DocumentKind,
    val title: String,
    /** Relative to the app's files root. */
    val file: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedAt: Instant,
) {
    val isPdf: Boolean get() = mimeType == MIME_PDF
    val isImage: Boolean get() = mimeType.startsWith("image/")

    companion object {
        const val MIME_PDF = "application/pdf"
    }
}
