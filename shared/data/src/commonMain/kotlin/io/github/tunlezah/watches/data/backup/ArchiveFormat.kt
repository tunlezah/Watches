package io.github.tunlezah.watches.data.backup

import io.github.tunlezah.watches.data.db.BoxPapersItemEntity
import io.github.tunlezah.watches.data.db.DocumentEntity
import io.github.tunlezah.watches.data.db.FieldProvenanceEntity
import io.github.tunlezah.watches.data.db.OtherSpecEntity
import io.github.tunlezah.watches.data.db.PhotoEntity
import io.github.tunlezah.watches.data.db.ServiceEntryEntity
import io.github.tunlezah.watches.data.db.SettingEntity
import io.github.tunlezah.watches.data.db.SourceSnapshotEntity
import io.github.tunlezah.watches.data.db.WatchComplicationEntity
import io.github.tunlezah.watches.data.db.WatchEntity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/*
 * The backup archive, version 1. docs/ARCHIVE_FORMAT.md is the full description; in short:
 *
 *   manifest.json      always the first entry: format, versions, counts, and every other file's
 *                      size and SHA-256
 *   collection.json    every table as a JSON array, keys = database column names, IDs preserved
 *   files/<path>       each photo (original and both renditions), document and source snapshot,
 *                      at the same relative path the database rows name
 */

const val ARCHIVE_FORMAT_ID = "io.github.tunlezah.watches.backup"
const val ARCHIVE_FORMAT_VERSION = 1
const val ARCHIVE_MANIFEST = "manifest.json"
const val ARCHIVE_COLLECTION = "collection.json"
const val ARCHIVE_FILES_PREFIX = "files/"

@Serializable
data class Manifest(
    val format: String,
    val formatVersion: Int,
    /** The database schema the rows in collection.json follow. */
    val schemaVersion: Int,
    /** ISO-8601, UTC. */
    val createdAt: String,
    /** The app and platform that wrote the archive, for support only. */
    val app: String,
    val counts: ArchiveCounts,
    /** collection.json and every file under files/, in archive order. */
    val files: List<ManifestFile>,
)

@Serializable
data class ArchiveCounts(
    val watches: Int,
    val photos: Int,
    val serviceEntries: Int,
    val documents: Int,
    val snapshots: Int,
)

@Serializable
data class ManifestFile(val path: String, val size: Long, val sha256: String)

/** Every table a backup carries. The search index and recent searches are left out: one is rebuilt, the other is ephemeral. */
@Serializable
data class CollectionData(
    val watch: List<WatchEntity> = emptyList(),
    val watchComplication: List<WatchComplicationEntity> = emptyList(),
    val fieldProvenance: List<FieldProvenanceEntity> = emptyList(),
    val otherSpec: List<OtherSpecEntity> = emptyList(),
    val photo: List<PhotoEntity> = emptyList(),
    val serviceEntry: List<ServiceEntryEntity> = emptyList(),
    val boxPapersItem: List<BoxPapersItemEntity> = emptyList(),
    val document: List<DocumentEntity> = emptyList(),
    val sourceSnapshot: List<SourceSnapshotEntity> = emptyList(),
    val setting: List<SettingEntity> = emptyList(),
) {
    val counts: ArchiveCounts
        get() = ArchiveCounts(watch.size, photo.size, serviceEntry.size, document.size, sourceSnapshot.size)

    /** Every stored file a row points at, relative to the app's files folder. */
    val referencedFiles: Set<String>
        get() = buildSet {
            photo.forEach { add(it.originalFile); add(it.displayFile); add(it.thumbFile) }
            document.forEach { add(it.file) }
            sourceSnapshot.forEach { add(it.bodyFile) }
        }
}

/**
 * Snake-case keys, so every JSON key is exactly the database column (or table) name. Nulls are
 * left out, and missing keys read as null, so later versions can add nullable columns freely.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val ArchiveJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    explicitNulls = false
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

/**
 * Stored files live at `photos|documents|snapshots/<watch id>/<file name>`. Anything else in an
 * archive (an absolute path, `..`, another folder) is refused, so a crafted archive can't write
 * outside the app's media folders.
 */
internal object ArchivePaths {
    private val SAFE = Regex("^(photos|documents|snapshots)/[A-Za-z0-9][A-Za-z0-9._-]{0,127}/[A-Za-z0-9][A-Za-z0-9._-]{0,191}$")

    fun isSafe(relative: String): Boolean = SAFE.matches(relative)

    /**
     * A sibling name for an incoming file whose usual path is taken by different content.
     * A previous suffix is replaced rather than stacked, so names don't grow with every restore.
     */
    fun variant(relative: String, tag: String): String {
        val slash = relative.lastIndexOf('/')
        val dot = relative.lastIndexOf('.').takeIf { it > slash } ?: relative.length
        val stem = relative.substring(0, dot).replace(SUFFIX, "")
        return "$stem-r$tag${relative.substring(dot)}"
    }

    private val SUFFIX = Regex("-r[0-9a-z]{6}$")
}
