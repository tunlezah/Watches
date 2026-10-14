package io.github.tunlezah.watches.ui.photos

import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.PhotoKind
import io.github.tunlezah.watches.data.PhotoRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportProgress(val pending: Int = 0, val failed: Int = 0)

/**
 * Runs photo work outside any screen: leaving the Photos or Crop screen mid-way must not cancel
 * an import half-written. Screens watch [progress] and await results while they're open.
 */
class PhotoImporter(private val photos: PhotoRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<Map<String, ImportProgress>>(emptyMap())

    fun progress(watchId: String): Flow<ImportProgress> = state.map { it[watchId] ?: ImportProgress() }

    fun import(watchId: String, picked: List<PlatformFile>) {
        if (picked.isEmpty()) return
        bump(watchId) { it.copy(pending = it.pending + picked.size, failed = 0) }
        scope.launch {
            for (file in picked) {
                val ok = try {
                    photos.add(watchId, file.readBytes())
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                bump(watchId) { it.copy(pending = it.pending - 1, failed = it.failed + if (ok) 0 else 1) }
            }
        }
    }

    /** Saves a picture fetched by a lookup, with its credit. */
    fun importReference(watchId: String, bytes: ByteArray, attribution: String, attributionUrl: String): Deferred<Unit> = scope.async {
        photos.add(watchId, bytes, PhotoKind.REFERENCE, attribution, attributionUrl)
        Unit
    }

    fun crop(photoId: String, rect: NormalizedRect): Deferred<Unit> = scope.async { photos.crop(photoId, rect) }

    fun clearFailures(watchId: String) = bump(watchId) { it.copy(failed = 0) }

    private fun bump(watchId: String, change: (ImportProgress) -> ImportProgress) =
        state.update { it + (watchId to change(it[watchId] ?: ImportProgress())) }
}
