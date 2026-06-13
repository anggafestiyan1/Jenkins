package eu.kanade.tachiyomi.ui.youtube

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
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
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads YouTube videos into app-internal storage (filesDir/youtube/<id>/video.<ext>) for offline
 * playback — kept entirely separate from the Film library. Sequential, with pause/resume and
 * per-item delete (delete removes only the queue job; finished videos appear in the Offline tab).
 */
object YtDownloadQueue {

    private val network: NetworkHelper by injectLazy()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<List<Item>>(emptyList())
    val state: StateFlow<List<Item>> = _state.asStateFlow()
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()
    private var loopJob: Job? = null
    private var idCounter = 0L

    enum class Status { QUEUED, DOWNLOADING, ERROR }

    data class Item(
        val id: Long,
        val video: YtItem,
        val progress: Float,
        val status: Status,
        val error: String? = null,
    ) {
        val title: String get() = video.title
        val thumbnailUrl: String get() = video.thumbnailUrl
    }

    @Synchronized
    fun enqueue(video: YtItem) {
        if (_state.value.any { it.video.url == video.url }) return
        _state.update { it + Item(idCounter++, video, 0f, Status.QUEUED) }
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

    fun retry(id: Long) {
        _state.update { list -> list.map { if (it.id == id) it.copy(status = Status.QUEUED, error = null, progress = 0f) else it } }
        ensureLoop()
    }

    @Synchronized
    private fun ensureLoop() {
        if (_paused.value) return
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                if (_paused.value) return@launch
                val item = _state.value.firstOrNull { it.status != Status.ERROR } ?: return@launch
                setStatus(item.id, Status.DOWNLOADING)
                try {
                    val completed = process(item)
                    if (completed) {
                        _state.update { list -> list.filterNot { it.id == item.id } }
                    } else if (_paused.value) {
                        return@launch
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e)
                    _state.update { list ->
                        list.map {
                            if (it.id == item.id) it.copy(status = Status.ERROR, error = e.message ?: e.javaClass.simpleName) else it
                        }
                    }
                }
            }
        }
    }

    /** True = downloaded; false = aborted (paused/removed). Throws on real failure. */
    private suspend fun process(item: Item): Boolean {
        val stream = YouTubeSource.getStream(item.video.url)
            ?: throw IllegalStateException("Stream tidak tersedia")
        val id = extractVideoId(item.video.url) ?: item.video.url.hashCode().toString()
        val dir = YtStore.dirFor(id).apply { mkdirs() }
        YtStore.saveMeta(id, item.video.title, item.video.thumbnailUrl)

        val extension = stream.extension.ifBlank { "mp4" }
        val file = File(dir, "video.$extension")
        if (file.exists()) file.delete()

        val ok = download(stream.streamUrl, file, item.id)
        if (!ok) {
            file.delete()
            if (_paused.value || _state.value.none { it.id == item.id }) return false
            throw IllegalStateException("Download gagal")
        }
        return true
    }

    private fun download(url: String, file: File, id: Long): Boolean {
        val response = network.client.newCall(GET(url)).execute()
        if (!response.isSuccessful) {
            response.close()
            return false
        }
        val body = response.body
        val total = body.contentLength()
        var downloaded = 0L
        body.byteStream().use { input ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    if (_paused.value || _state.value.none { it.id == id }) return false
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) updateProgress(id, downloaded.toFloat() / total)
                }
            }
        }
        return true
    }

    private fun setStatus(id: Long, status: Status) {
        _state.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }

    private fun updateProgress(id: Long, progress: Float) {
        _state.update { list -> list.map { if (it.id == id) it.copy(progress = progress) else it } }
    }

    private fun extractVideoId(url: String): String? {
        Regex("[?&]v=([A-Za-z0-9_-]{6,})").find(url)?.let { return it.groupValues[1] }
        Regex("youtu\\.be/([A-Za-z0-9_-]{6,})").find(url)?.let { return it.groupValues[1] }
        Regex("/shorts/([A-Za-z0-9_-]{6,})").find(url)?.let { return it.groupValues[1] }
        Regex("/embed/([A-Za-z0-9_-]{6,})").find(url)?.let { return it.groupValues[1] }
        return null
    }
}
