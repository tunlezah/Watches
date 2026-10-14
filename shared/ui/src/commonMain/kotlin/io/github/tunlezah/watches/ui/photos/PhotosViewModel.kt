package io.github.tunlezah.watches.ui.photos

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.Photo
import io.github.tunlezah.watches.data.PhotoRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.data.files.FileStore
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PhotoTile(val id: String, val thumb: String, val display: String, val isCover: Boolean, val cropped: Boolean)

@Immutable
data class PhotosUiState(
    val loaded: Boolean = false,
    val title: String = "",
    val photos: List<PhotoTile> = emptyList(),
    val importing: Int = 0,
    val message: String? = null,
)

class PhotosViewModel(
    private val watchId: String,
    watches: WatchRepository,
    private val photos: PhotoRepository,
    private val files: FileStore,
) : ViewModel() {

    private val progress = MutableStateFlow(0 to null as String?)

    val state: StateFlow<PhotosUiState> = combine(watches.observeDetail(watchId), progress) { detail, (importing, message) ->
        val cover = detail?.cover?.id
        PhotosUiState(
            loaded = true,
            title = detail?.watch?.title.orEmpty(),
            photos = detail?.photos.orEmpty().sortedBy { it.position }.map {
                PhotoTile(it.id, files.absolute(it.thumbFile), files.absolute(it.displayFile), it.id == cover, it.crop != null)
            },
            importing = importing,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotosUiState())

    fun addFiles(picked: List<PlatformFile>) {
        if (picked.isEmpty()) return
        viewModelScope.launch {
            progress.update { (n, _) -> n + picked.size to null }
            var failed = 0
            for (file in picked) {
                runCatching { photos.add(watchId, file.readBytes()) }.onFailure { failed++ }
                progress.update { (n, m) -> (n - 1) to m }
            }
            if (failed > 0) progress.update { (n, _) -> n to "$failed ${if (failed == 1) "photo" else "photos"} couldn't be read" }
        }
    }

    fun setCover(id: String) = viewModelScope.launch { photos.setCover(watchId, id) }

    fun move(id: String, delta: Int) = viewModelScope.launch {
        val ids = state.value.photos.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (from < 0 || from == to) return@launch
        ids.add(to, ids.removeAt(from))
        photos.reorder(watchId, ids)
    }

    fun delete(id: String) = viewModelScope.launch { photos.delete(id) }

    fun dismissMessage() = progress.update { (n, _) -> n to null }
}

@Immutable
data class CropUiState(
    val loaded: Boolean = false,
    val original: String? = null,
    val width: Int = 1,
    val height: Int = 1,
    val crop: NormalizedRect = NormalizedRect.Full,
    val saving: Boolean = false,
)

class CropViewModel(
    private val photoId: String,
    private val photos: PhotoRepository,
    private val files: FileStore,
) : ViewModel() {
    private val _state = MutableStateFlow(CropUiState())
    val state: StateFlow<CropUiState> = _state

    init {
        viewModelScope.launch {
            val photo: Photo = photos.byId(photoId) ?: return@launch
            _state.value = CropUiState(true, files.absolute(photo.originalFile), photo.width, photo.height, photo.crop ?: NormalizedRect.Full)
        }
    }

    fun save(rect: NormalizedRect, onDone: () -> Unit) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            photos.crop(photoId, rect)
            onDone()
        }
    }
}
