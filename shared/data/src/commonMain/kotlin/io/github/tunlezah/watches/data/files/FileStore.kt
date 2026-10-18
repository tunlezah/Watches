package io.github.tunlezah.watches.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source

/**
 * App-private file storage. The database only ever stores paths relative to [root],
 * so the same rows work after a restore on another device or platform.
 */
class FileStore(val root: Path, val fileSystem: FileSystem = FileSystem.SYSTEM) {

    fun resolve(relative: String): Path = root / relative.toPath(normalize = true)

    /** Absolute path string, for image loaders. */
    fun absolute(relative: String): String = resolve(relative).toString()

    suspend fun write(relative: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val path = resolve(relative)
        path.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(path) { write(bytes) }
    }

    suspend fun read(relative: String): ByteArray = withContext(Dispatchers.IO) {
        fileSystem.read(resolve(relative)) { readByteArray() }
    }

    suspend fun exists(relative: String): Boolean = withContext(Dispatchers.IO) { fileSystem.exists(resolve(relative)) }

    suspend fun size(relative: String): Long = withContext(Dispatchers.IO) {
        fileSystem.metadataOrNull(resolve(relative))?.size ?: 0L
    }

    suspend fun delete(relative: String) = withContext(Dispatchers.IO) {
        fileSystem.delete(resolve(relative), mustExist = false)
    }

    suspend fun deleteTree(relative: String) = withContext(Dispatchers.IO) {
        val path = resolve(relative)
        if (fileSystem.exists(path)) fileSystem.deleteRecursively(path)
    }

    suspend fun copy(from: String, to: String) = withContext(Dispatchers.IO) {
        val target = resolve(to)
        target.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.copy(resolve(from), target)
    }

    suspend fun ensureDir(relative: String) = withContext(Dispatchers.IO) {
        fileSystem.createDirectories(resolve(relative))
    }

    /** Reads a file as a stream. The caller closes it; call off the main thread. */
    fun source(relative: String): Source = fileSystem.source(resolve(relative))

    /** Writes a file as a stream, creating its folder. The caller closes it; call off the main thread. */
    fun sink(relative: String): Sink {
        val path = resolve(relative)
        path.parent?.let { fileSystem.createDirectories(it) }
        return fileSystem.sink(path)
    }

    /** Free space on the volume holding the app's files, if the platform says. */
    suspend fun availableBytes(): Long? = withContext(Dispatchers.IO) { runCatching { freeSpace(root) }.getOrNull() }

    companion object {
        fun photoDir(watchId: String) = "photos/$watchId"
        fun documentDir(watchId: String) = "documents/$watchId"
        fun snapshotDir(watchId: String) = "snapshots/$watchId"

        /** Rebuildable data (robots.txt cache). Never part of a backup. */
        const val CACHE_DIR = "cache"
    }
}

/** Bytes free for the app on the volume holding [path], or null if unknown. */
internal expect fun freeSpace(path: Path): Long?
