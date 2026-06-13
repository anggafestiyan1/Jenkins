package eu.kanade.tachiyomi.ui.stream

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
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads streaming videos into app-internal storage (filesDir/stream/offline/<id>/) for offline
 * playback. Sequential, with pause/resume and per-item delete/retry. HLS (.m3u8) download is not
 * supported in the MVP (streaming-only); only progressive MP4 hosts can be saved offline.
 */
object StreamDownloadQueue {

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
        val item: StreamItem,
        val episode: StreamEpisode,
        val progress: Float,
        val status: Status,
        val error: String? = null,
    ) {
        val title: String get() = if (item.isSeries) "${item.title} — ${episode.name}" else item.title
    }

    @Synchronized
    fun enqueue(item: StreamItem, episode: StreamEpisode) {
        val offlineId = StreamStore.offlineId(item, episode)
        if (_state.value.any { StreamStore.offlineId(it.item, it.episode) == offlineId }) return
        _state.update { it + Item(idCounter++, item, episode, 0f, Status.QUEUED) }
        ensureLoop()
    }

    fun pause() { _paused.value = true }

    fun resume() {
        _paused.value = false
        ensureLoop()
    }

    fun remove(id: Long) {
        _state.update { list -> list.filterNot { it.id == id } }
    }

    fun retry(id: Long) {
        _state.update { list ->
            list.map { if (it.id == id) it.copy(status = Status.QUEUED, error = null, progress = 0f) else it }
        }
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
                            if (it.id == item.id) {
                                it.copy(status = Status.ERROR, error = e.message ?: e.javaClass.simpleName)
                            } else {
                                it
                            }
                        }
                    }
                }
            }
        }
    }

    /** True = downloaded; false = aborted (paused/removed). Throws on real failure. */
    private suspend fun process(queued: Item): Boolean {
        val source = StreamSources.byId(queued.item.sourceId)
        val videos = source.videos(queued.episode)
        if (videos.isEmpty()) throw IllegalStateException("Tidak ada video yang bisa diambil")
        val video = videos.firstOrNull { !it.isHls }
            ?: throw IllegalStateException("Hanya HLS yang tersedia — download HLS belum didukung")

        val offlineId = StreamStore.offlineId(queued.item, queued.episode)
        val dir = StreamStore.offlineDir(offlineId).apply { mkdirs() }
        val title = if (queued.item.isSeries) "${queued.item.title} — ${queued.episode.name}" else queued.item.title
        StreamStore.saveOfflineMeta(offlineId, title, queued.item.posterUrl)

        val file = File(dir, "video.mp4")
        if (file.exists()) file.delete()

        val ok = download(video, file, queued.id)
        if (!ok) {
            file.delete()
            if (_paused.value || _state.value.none { it.id == queued.id }) return false
            throw IllegalStateException("Download gagal")
        }
        return true
    }

    private fun download(video: StreamVideo, file: File, id: Long): Boolean {
        val headers = Headers.Builder().apply {
            video.headers.forEach { (k, v) -> add(k, v) }
        }.build()
        val response = network.client.newCall(GET(video.url, headers)).execute()
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
}
