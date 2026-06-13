package eu.kanade.tachiyomi.ui.stream

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * File-backed storage for the Stream section, kept in app-internal storage (filesDir/stream),
 * fully separate from Film/Komik/YouTube and removed on uninstall.
 *  - favorites.tsv / history.tsv : small TSV indexes
 *  - offline/<id>/               : downloaded video + meta.txt
 */
object StreamStore {

    private val app: Application by injectLazy()

    private fun root(): File = File(app.filesDir, "stream").apply { mkdirs() }
    private fun favFile(): File = File(root(), "favorites.tsv")
    private fun histFile(): File = File(root(), "history.tsv")
    private fun offlineRoot(): File = File(root(), "offline").apply { mkdirs() }

    private fun sanitize(s: String) = s.replace("\t", " ").replace("\n", " ").replace("\r", " ")

    // ---------------- Favorites ----------------

    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<List<StreamItem>> = _favorites.asStateFlow()

    private fun loadFavorites(): List<StreamItem> = runCatching {
        if (!favFile().exists()) return emptyList()
        favFile().readLines().mapNotNull { parseItem(it) }
    }.getOrDefault(emptyList())

    private fun parseItem(line: String): StreamItem? {
        val p = line.split("\t")
        if (p.size < 6) return null
        return StreamItem(
            sourceId = p[0], url = p[1], title = p[2], posterUrl = p[3],
            year = p[4], isSeries = p[5].toBoolean(),
        )
    }

    private fun itemLine(i: StreamItem) =
        listOf(i.sourceId, i.url, sanitize(i.title), i.posterUrl, i.year, i.isSeries.toString()).joinToString("\t")

    fun isFavorite(item: StreamItem): Boolean = _favorites.value.any { it.key == item.key }

    fun toggleFavorite(item: StreamItem) {
        val list = _favorites.value
        val next = if (list.any { it.key == item.key }) list.filterNot { it.key == item.key } else list + item
        _favorites.value = next
        runCatching { favFile().writeText(next.joinToString("\n") { itemLine(it) }) }
    }

    // ---------------- History ----------------

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<StreamHistory>> = _history.asStateFlow()

    private fun loadHistory(): List<StreamHistory> = runCatching {
        if (!histFile().exists()) return emptyList()
        histFile().readLines().mapNotNull { line ->
            val p = line.split("\t")
            if (p.size < 9) return@mapNotNull null
            StreamHistory(
                item = StreamItem(p[0], p[1], p[2], p[3], p[4], p[5].toBoolean()),
                episodeName = p[6], episodeUrl = p[7], timestamp = p[8].toLongOrNull() ?: 0L,
            )
        }
    }.getOrDefault(emptyList())

    private fun histLine(h: StreamHistory) = listOf(
        h.item.sourceId, h.item.url, sanitize(h.item.title), h.item.posterUrl, h.item.year,
        h.item.isSeries.toString(), sanitize(h.episodeName), h.episodeUrl, h.timestamp.toString(),
    ).joinToString("\t")

    fun recordHistory(item: StreamItem, episode: StreamEpisode) {
        val entry = StreamHistory(item, episode.name, episode.url, System.currentTimeMillis())
        val next = (listOf(entry) + _history.value.filterNot { it.item.key == item.key }).take(100)
        _history.value = next
        runCatching { histFile().writeText(next.joinToString("\n") { histLine(it) }) }
    }

    fun removeHistory(item: StreamItem) {
        val next = _history.value.filterNot { it.item.key == item.key }
        _history.value = next
        runCatching { histFile().writeText(next.joinToString("\n") { histLine(it) }) }
    }

    // ---------------- Offline ----------------

    fun offlineId(item: StreamItem, episode: StreamEpisode): String =
        Integer.toHexString((item.key + "|" + episode.url).hashCode())

    fun offlineDir(id: String): File = File(offlineRoot(), id)

    fun offlineVideo(id: String): File? =
        offlineDir(id).listFiles()?.firstOrNull { it.isFile && it.name.startsWith("video.") }

    fun saveOfflineMeta(id: String, title: String, posterUrl: String) {
        val dir = offlineDir(id).apply { mkdirs() }
        File(dir, "meta.txt").writeText("${sanitize(title)}\n$posterUrl")
    }

    fun listOffline(): List<StreamOffline> {
        return offlineRoot().listFiles()?.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val meta = File(dir, "meta.txt")
            val video = dir.listFiles()?.firstOrNull { it.isFile && it.name.startsWith("video.") }
                ?: return@mapNotNull null
            val lines = if (meta.exists()) meta.readLines() else emptyList()
            StreamOffline(
                id = dir.name,
                title = lines.getOrElse(0) { dir.name },
                posterUrl = lines.getOrElse(1) { "" },
                videoPath = video.absolutePath,
            )
        }?.sortedBy { it.title.lowercase() }.orEmpty()
    }

    fun deleteOffline(id: String) {
        offlineDir(id).deleteRecursively()
    }
}

data class StreamHistory(
    val item: StreamItem,
    val episodeName: String,
    val episodeUrl: String,
    val timestamp: Long,
)

data class StreamOffline(
    val id: String,
    val title: String,
    val posterUrl: String,
    val videoPath: String,
)
