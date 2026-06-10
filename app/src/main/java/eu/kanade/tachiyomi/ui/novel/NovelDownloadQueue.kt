package eu.kanade.tachiyomi.ui.novel

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

/**
 * App-scoped sequential download queue for novels. Jobs are processed one at a time; when a job
 * finishes, its chapters live on disk (via [NovelDownloader]) and it leaves the queue, appearing in
 * the "Download" tab. State is in-memory (cleared on process death).
 */
object NovelDownloadQueue {

    private val app: Application by injectLazy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<List<QueueItem>>(emptyList())
    val state: StateFlow<List<QueueItem>> = _state.asStateFlow()

    private var running = false
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
        val item = QueueItem(
            id = idCounter++,
            detail = detail,
            indices = indices,
            total = indices.size,
            done = 0,
            status = Status.QUEUED,
        )
        _state.update { it + item }
        startProcessing()
    }

    @Synchronized
    private fun startProcessing() {
        if (running) return
        running = true
        scope.launch {
            while (true) {
                val next = _state.value.firstOrNull() ?: break
                _state.update { list -> list.map { if (it.id == next.id) it.copy(status = Status.DOWNLOADING) else it } }
                try {
                    NovelDownloader.downloadChapters(app, next.detail, next.indices) { done, _ ->
                        _state.update { list -> list.map { if (it.id == next.id) it.copy(done = done) else it } }
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
                _state.update { list -> list.filterNot { it.id == next.id } }
            }
            running = false
        }
    }
}
