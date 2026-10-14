package io.github.tunlezah.watches.ui.lookup

import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.search.http.CachedRobots
import io.github.tunlezah.watches.search.http.RobotsStore

/** Keeps each host's robots.txt in the app's cache folder, so it's fetched at most once a day across launches. */
class FileRobotsStore(private val files: FileStore) : RobotsStore {

    private fun path(host: String) = "${FileStore.CACHE_DIR}/robots/${host.filter { it.isLetterOrDigit() || it == '.' || it == '-' }}.txt"

    override suspend fun load(host: String): CachedRobots? {
        val p = path(host)
        if (!files.exists(p)) return null
        val text = runCatching { files.read(p).decodeToString() }.getOrNull() ?: return null
        val at = text.substringBefore('\n').toLongOrNull() ?: return null
        val rest = text.substringAfter('\n', "")
        return CachedRobots(at, if (rest.startsWith(NONE)) null else rest)
    }

    override suspend fun save(host: String, robots: CachedRobots) {
        files.write(path(host), "${robots.fetchedAtMillis}\n${robots.body ?: NONE}".encodeToByteArray())
    }

    private companion object {
        /** Marks a host with no robots.txt (any 4xx), which allows everything. */
        const val NONE = "\u0000none"
    }
}
