package eu.kanade.tachiyomi.ui.youtube

import android.app.Application
import com.hippo.unifile.UniFile
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.model.SAnime
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
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.injectLazy

/**
 * Downloads YouTube videos (resolved via [YouTubeSource]) into the local film source folder, then
 * indexes them as Film entries so they play offline. Sequential, with pause/resume and per-item
 * delete (delete only removes the queue job).
 */
object YtDownloadQueue {

    private val app: Application by injectLazy()
    private val network: NetworkHelper by injectLazy()
    private val storageManager: StorageManager by injectLazy()
    private val sourceManager: AnimeSourceManager by injectLazy()
    private val networkToLocalAnime: NetworkToLocalAnime by injectLazy()
    private val updateAnime: UpdateAnime by injectLazy()
    private val syncEpisodesWithSource: SyncEpisodesWithSource by injectLazy()
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId by injectLazy()

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

    fun retry(id: Long) {
        _state.update { list -> list.map { if (it.id == id) it.copy(status = Status.QUEUED, error = null, progress = 0f) else it } }
        ensureLoop()
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

    @Synchronized
    private fun ensureLoop() {
        if (_paused.value) return
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                if (_paused.value) return@launch
                // Skip items that already errored (kept visible for the user).
                val item = _state.value.firstOrNull { it.status != Status.ERROR } ?: return@launch
                setStatus(item.id, Status.DOWNLOADING)
                try {
                    val completed = process(item)
                    if (completed) {
                        _state.update { list -> list.filterNot { it.id == item.id } }
                    } else if (_paused.value) {
                        return@launch // aborted by pause; resume() restarts
                    }
                } catch (e: Exception) {
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

    /** True = downloaded + indexed; false = aborted (paused/removed). Throws on real failure. */
    private suspend fun process(item: Item): Boolean {
        val stream = YouTubeSource.getStream(item.video.url)
            ?: throw IllegalStateException("Stream tidak tersedia")
        val folder = sanitize(item.video.title)
        val baseDir = storageManager.getLocalAnimeSourceDirectory()
            ?: throw IllegalStateException("Lokasi storage belum diatur")
        val dir = baseDir.findFile(folder) ?: baseDir.createDirectory(folder)
            ?: throw IllegalStateException("Gagal membuat folder")
        // Use the real extension (mp4/webm) — SAF rejects unknown extensions like ".tmp".
        val extension = stream.extension.ifBlank { "mp4" }
        val fileName = "video.$extension"

        // Always download fresh to avoid leftover partial files being treated as complete.
        dir.findFile(fileName)?.delete()
        val target = dir.createFile(fileName) ?: throw IllegalStateException("Gagal membuat file ($fileName)")
        val ok = download(stream.streamUrl, target, item.id)
        if (!ok) {
            target.delete()
            // Distinguish abort (pause/remove) from a real download failure.
            if (_paused.value || _state.value.none { it.id == item.id }) return false
            throw IllegalStateException("Download gagal")
        }

        // Index into the local film library so it plays offline.
        val source = sourceManager.get(LocalAnimeSource.ID)
            ?: throw IllegalStateException("Local source tidak tersedia")
        val sAnime = SAnime.create().apply {
            this.url = folder
            this.title = item.video.title
        }
        val anime = networkToLocalAnime.await(sAnime.toDomainAnime(LocalAnimeSource.ID))
        updateAnime.awaitUpdateFavorite(anime.id, true)
        val sourceEpisodes = source.getEpisodeList(anime.toSAnime())
        syncEpisodesWithSource.await(sourceEpisodes, anime, source, false)
        val episodeId = getEpisodesByAnimeId.await(anime.id).firstOrNull()?.id
            ?: throw IllegalStateException("Episode tidak terindeks")
        YtStore.addOffline(YtOffline(anime.id, episodeId, item.video.title, item.video.thumbnailUrl))
        return true
    }

    private fun download(url: String, target: UniFile, id: Long): Boolean {
        val response = network.client.newCall(GET(url)).execute()
        if (!response.isSuccessful) {
            response.close()
            return false
        }
        val body = response.body
        val total = body.contentLength()
        var downloaded = 0L
        body.byteStream().use { input ->
            target.openOutputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    // Abort if paused or this job was removed from the queue.
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

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "video" }
}
