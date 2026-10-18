package io.github.tunlezah.watches.data.backup

import io.github.tunlezah.watches.data.db.WatchEntity
import io.github.tunlezah.watches.data.db.toModel

/** A watch as the restore screens name it. */
data class WatchName(val id: String, val title: String)

/** What a merge does with one watch from the backup. */
enum class WatchDecision {
    /** Not on this phone: added. */
    NEW,

    /** Edited more recently in the backup: the backup's version replaces this phone's. */
    UPDATE,

    /** Edited more recently (or at the same time) on this phone: this phone's version stays. */
    KEEP,

    /** Identical on both. */
    SAME,
}

/**
 * The outcome of merging a backup into this phone's collection, worked out before anything is
 * written so the restore screen can show it.
 *
 * Merge matches on IDs and never deletes. For each watch the more recently edited side wins its
 * details, specs, complications and box & papers. Photos, service entries, documents and source
 * snapshots are combined: ones only in the backup are added, and for a service entry on both sides
 * the more recently edited one wins. Photos and documents on both sides follow their watch.
 */
data class MergePlan(
    val decisions: Map<String, WatchDecision>,
    val added: List<WatchName>,
    val updated: List<WatchName>,
    val kept: List<WatchName>,
    val unchanged: Int,
    /** Watches on this phone that the backup doesn't have; a merge leaves them as they are. */
    val onlyOnPhone: Int,
    val photosAdded: Int,
    val serviceEntriesAdded: Int,
    val serviceEntriesUpdated: Int,
    val documentsAdded: Int,
    /** The rows to write: watches to add or replace, and every child row to add or replace. */
    internal val rows: CollectionData,
) {
    /** Watches that differ between the phone and the backup; the restore report lists each one. */
    val conflicts: Int get() = updated.size + kept.size
}

internal object MergePlanner {

    fun plan(local: CollectionData, incoming: CollectionData): MergePlan {
        val localWatches = local.watch.associateBy { it.id }
        val decisions = LinkedHashMap<String, WatchDecision>()
        val added = mutableListOf<WatchName>()
        val updated = mutableListOf<WatchName>()
        val kept = mutableListOf<WatchName>()
        var unchanged = 0

        val localParts = OwnedParts.of(local)
        val incomingParts = OwnedParts.of(incoming)
        for (w in incoming.watch) {
            val mine = localWatches[w.id]
            val decision = when {
                mine == null -> WatchDecision.NEW
                mine == w && localParts.of(w.id) == incomingParts.of(w.id) -> WatchDecision.SAME
                w.updatedAt > mine.updatedAt -> WatchDecision.UPDATE
                else -> WatchDecision.KEEP
            }
            decisions[w.id] = decision
            when (decision) {
                WatchDecision.NEW -> added += w.name()
                WatchDecision.UPDATE -> updated += w.name()
                WatchDecision.KEEP -> kept += mine!!.name()
                WatchDecision.SAME -> unchanged++
            }
        }
        val incomingIds = decisions.keys
        val takesBackup = decisions.filterValues { it == WatchDecision.NEW || it == WatchDecision.UPDATE }.keys

        val localPhotos = local.photo.associateBy { it.id }
        val photos = incoming.photo.filter { it.watchId in incomingIds }.filter { p ->
            val mine = localPhotos[p.id]
            mine == null || (p.watchId in takesBackup && mine != p)
        }
        val localEntries = local.serviceEntry.associateBy { it.id }
        val entries = incoming.serviceEntry.filter { it.watchId in incomingIds }.filter { e ->
            val mine = localEntries[e.id]
            mine == null || (mine != e && e.updatedAt > mine.updatedAt)
        }
        val localDocuments = local.document.associateBy { it.id }
        val documents = incoming.document.filter { it.watchId in incomingIds }.filter { d ->
            val mine = localDocuments[d.id]
            mine == null || (d.watchId in takesBackup && mine != d)
        }
        val localSnapshots = local.sourceSnapshot.map { it.id }.toSet()
        val snapshots = incoming.sourceSnapshot.filter { it.watchId in incomingIds && it.id !in localSnapshots }

        val rows = CollectionData(
            watch = incoming.watch.filter { it.id in takesBackup },
            watchComplication = incoming.watchComplication.filter { it.watchId in takesBackup },
            fieldProvenance = incoming.fieldProvenance.filter { it.watchId in takesBackup },
            otherSpec = incoming.otherSpec.filter { it.watchId in takesBackup },
            boxPapersItem = incoming.boxPapersItem.filter { it.watchId in takesBackup },
            photo = photos,
            serviceEntry = entries,
            document = documents,
            sourceSnapshot = snapshots,
        )
        return MergePlan(
            decisions = decisions,
            added = added,
            updated = updated,
            kept = kept,
            unchanged = unchanged,
            onlyOnPhone = local.watch.count { it.id !in incomingIds },
            photosAdded = photos.count { it.id !in localPhotos },
            serviceEntriesAdded = entries.count { it.id !in localEntries },
            serviceEntriesUpdated = entries.count { it.id in localEntries },
            documentsAdded = documents.count { it.id !in localDocuments },
            rows = rows,
        )
    }

    /** The lists that belong to a watch's own record, compared as sets. */
    private class OwnedParts(
        val complications: Map<String, Set<Any>>,
        val provenance: Map<String, Set<Any>>,
        val otherSpecs: Map<String, Set<Any>>,
        val boxPapers: Map<String, Set<Any>>,
    ) {
        fun of(watchId: String): List<Set<Any>> = listOf(
            complications[watchId].orEmpty(),
            provenance[watchId].orEmpty(),
            otherSpecs[watchId].orEmpty(),
            boxPapers[watchId].orEmpty(),
        )

        companion object {
            fun of(data: CollectionData) = OwnedParts(
                data.watchComplication.groupBy({ it.watchId }, { it.key as Any }).mapValues { it.value.toSet() },
                data.fieldProvenance.groupBy({ it.watchId }, { it as Any }).mapValues { it.value.toSet() },
                data.otherSpec.groupBy({ it.watchId }, { it as Any }).mapValues { it.value.toSet() },
                data.boxPapersItem.groupBy({ it.watchId }, { it as Any }).mapValues { it.value.toSet() },
            )
        }
    }
}

internal fun WatchEntity.name() = WatchName(id, toModel(emptyList()).title)
