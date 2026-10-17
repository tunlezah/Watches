package io.github.tunlezah.watches.ui.diagnostics

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.data.PhotoRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.data.sample.SampleCatalog
import io.github.tunlezah.watches.ui.components.WatchLook
import io.github.tunlezah.watches.ui.components.drawWatch
import io.github.tunlezah.watches.ui.platform.encodeJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.data.BoxPapersRepository
import io.github.tunlezah.watches.data.ServiceRepository
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

/**
 * Creates sample watches with a drawn "photo" each, for the 500-watch performance check.
 * Photos go through the same pipeline as real ones (original, display rendition, thumbnail).
 */
class SampleGenerator(
    private val watches: WatchRepository,
    private val photos: PhotoRepository,
    private val time: TimeSource,
    private val services: ServiceRepository,
    private val papers: BoxPapersRepository,
) {
    /** Runs outside any screen, so leaving Diagnostics doesn't stop a long generation. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun generate(count: Int) = launchWork {
        val samples = SampleCatalog.generate(count, seed = (time.now().toEpochMilliseconds() % 100_000).toInt(), now = time.now())
        samples.forEachIndexed { i, sample ->
            val created = watches.create(sample.watch, id = sample.watch.id)
            photos.add(created.id, render(WatchLook.from(sample.look)))
            addCare(created, Random(i))
            _status.value = "Created ${i + 1} of $count"
        }
        _status.value = "Added $count sample watches"
    }

    /** Some history for the Service tab: a full service for about half, and a partly recorded box & papers. */
    private suspend fun addCare(w: Watch, random: Random) {
        val acquired = w.acquiredOn ?: return
        val today = time.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (w.serviceIntervalMonths != null && random.nextBoolean()) {
            val on = acquired.plus(random.nextInt(18, 66), DateTimeUnit.MONTH)
            if (on < today) {
                services.save(ServiceEntry("", w.id, on, servicedBy = "Sample Watchmaker", work = setOf(ServiceWork.FULL_SERVICE),
                    warrantyMonths = 24, createdAt = time.now(), updatedAt = time.now()))
            }
        }
        for (item in BoxItem.entries) {
            when (random.nextInt(5)) {
                0, 1, 2 -> papers.set(w.id, item, BoxItemState.PRESENT)
                3 -> papers.set(w.id, item, if (item == BoxItem.OTHER) BoxItemState.NOT_APPLICABLE else BoxItemState.MISSING)
                else -> Unit
            }
        }
    }

    fun removeAll() = launchWork {
        val ids = watches.sampleIds()
        ids.forEachIndexed { i, id ->
            watches.delete(id)
            _status.value = "Removed ${i + 1} of ${ids.size}"
        }
        _status.value = "Sample watches removed"
    }

    fun rebuildIndex() = launchWork {
        _status.value = "Rebuilding search index…"
        watches.rebuildSearchIndex()
        _status.value = "Search index rebuilt"
    }

    private fun launchWork(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        _running.value = true
        job = scope.launch {
            try {
                block()
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _status.value = "Stopped: ${e.message}"
            } finally {
                _running.value = false
            }
        }
    }

    private fun render(look: WatchLook): ByteArray {
        val w = 1200
        val h = 1500
        val bitmap = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(w.toFloat(), h.toFloat())) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF2B2926), Color(0xFF141312)), center = center, radius = h * 0.7f))
            drawWatch(look, Color(0x99000000))
        }
        return encodeJpeg(bitmap, 88)
    }
}
