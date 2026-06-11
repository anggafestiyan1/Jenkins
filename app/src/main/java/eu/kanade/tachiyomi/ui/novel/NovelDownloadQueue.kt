package eu.kanade.tachiyomi.ui.novel

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

/**
 * App-scoped sequential novel download queue with pause/resume and per-item removal.
 *
 * Downloads happen one chapter at a time, so pausing or deleting takes effect after the current
 * chapter (no coroutine cancellation needed). Deleting only removes the job from the queue; already
 * downloaded chapter files are kept.
 */
object NovelDownloadQueue {

    private val app: Application by injectLazy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<List<QueueItem>>(emptyList())
    val state: StateFlow<List<QueueItem>> = _state.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private var loopJob: Job? = null
    private var idCounter = 0L

    enum class Status { QUEUED, DOWNLOADING }

    data class QueueItem(
        val id: Long,
        val detail: NovelDetail,
        val indices: List<Int>,
        val total: Int,
        val done: Int,
        val status: Status,
    ) {
        val title: String get() = detail.title
        val coverUrl: String get() = detail.coverUrl
    }

    @Synchronized
    fun enqueue(detail: NovelDetail, indices: List<Int>) {
        if (indices.isEmpty()) return
        _state.update {
            it + QueueItem(idCounter++, detail, indices, indices.size, 0, Status.QUEUED)
        }
        ensureLoop()
    }

    fun pause() {
        _paused.value = true
    }

    fun resume() {
        _paused.value = false
        ensureLoop()
    }

    fun remove(id: Long) {
        _state.update { list -> list.filterNot { it.id == id } }
    }

    @Synchronized
    private fun ensureLoop() {
        if (_paused.value) return
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                if (_paused.value) return@launch
                val item = _state.value.firstOrNull() ?: return@launch
                if (item.done >= item.total) {
                    _state.update { list -> list.filterNot { it.id == item.id } }
                    continue
                }
                if (item.status != Status.DOWNLOADING) {
                    _state.update { list -> list.map { if (it.id == item.id) it.copy(status = Status.DOWNLOADING) else it } }
                }
                val chapterIndex = item.indices.getOrNull(item.done)
                if (chapterIndex != null) {
                    try {
                        NovelDownloader.downloadOne(app, item.detail, chapterIndex)
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e)
                    }
                }
                // Advance progress only if the item is still queued (not removed meanwhile).
                _state.update { list -> list.map { if (it.id == item.id) it.copy(done = it.done + 1) else it } }
            }
        }
    }
}
