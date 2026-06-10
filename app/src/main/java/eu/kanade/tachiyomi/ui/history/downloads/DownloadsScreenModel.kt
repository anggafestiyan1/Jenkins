package eu.kanade.tachiyomi.ui.history.downloads

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DownloadsScreenModel(
    private val mangaDownloadCache: MangaDownloadCache = Injekt.get(),
    private val animeDownloadCache: AnimeDownloadCache = Injekt.get(),
    private val mangaDownloadManager: MangaDownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
) : StateScreenModel<DownloadsScreenModel.State>(State()) {

    private val _query: MutableStateFlow<String?> = MutableStateFlow(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    private val _filter: MutableStateFlow<Filter> = MutableStateFlow(Filter.All)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    init {
        screenModelScope.launch {
            combine(
                mangaDownloadCache.changes,
                animeDownloadCache.changes,
                _query,
                _filter,
            ) { _, _, query, filter ->
                Triple(query, filter, Unit)
            }.collect { (query, filter, _) ->
                loadEntries(query, filter)
            }
        }
    }

    private suspend fun loadEntries(query: String?, filter: Filter) {
        val mangaEntries = if (filter == Filter.Anime) {
            emptyList()
        } else {
            collectMangaEntries()
        }
        val animeEntries = if (filter == Filter.Manga) {
            emptyList()
        } else {
            collectAnimeEntries()
        }
        val all = (mangaEntries + animeEntries)
        val filtered = if (!query.isNullOrBlank()) {
            all.filter { it.title.contains(query, ignoreCase = true) }
        } else {
            all
        }.sortedBy { it.title.lowercase() }
        mutableState.update { it.copy(entries = filtered) }
    }

    private suspend fun collectMangaEntries(): List<DownloadedEntry.MangaEntry> {
        // Enumerate ALL manga (favorites + non-favorites) so removed-from-library
        // entries still appear here as long as their files remain on disk.
        return mangaRepository.getAllManga()
            .mapNotNull { manga ->
                val count = mangaDownloadManager.getDownloadCount(manga)
                if (count <= 0) return@mapNotNull null
                val size = mangaDownloadManager.getDownloadSize(manga)
                DownloadedEntry.MangaEntry(manga, count, size)
            }
    }

    private suspend fun collectAnimeEntries(): List<DownloadedEntry.AnimeEntry> {
        // Enumerate ALL anime (favorites + non-favorites) so removed-from-library
        // entries still appear here as long as their files remain on disk.
        // Also include entries whose source is in STREAMING_AUTO_DOWNLOADED_SOURCES
        // (Google Drive / Jellyfin) since the user wants them treated as always
        // accessible from their own content sources.
        return animeRepository.getAllAnime()
            .mapNotNull { anime ->
                val count = animeDownloadManager.getDownloadCount(anime)
                val isStreamingAutoDownloaded =
                    anime.favorite && anime.source in STREAMING_AUTO_DOWNLOADED_SOURCES
                if (count <= 0 && !isStreamingAutoDownloaded) return@mapNotNull null
                val size = if (count > 0) animeDownloadManager.getDownloadSize(anime) else 0L
                DownloadedEntry.AnimeEntry(anime, count, size)
            }
    }

    companion object {
        private val STREAMING_AUTO_DOWNLOADED_SOURCES = setOf(
            4222017068256633289L,
            7558489246571085102L,
            1100359934660540567L,
            5716273013801838310L,
            7066619062139039107L,
        )
    }

    fun search(query: String?) {
        screenModelScope.launchIO {
            _query.emit(query)
        }
    }

    fun setFilter(filter: Filter) {
        _filter.value = filter
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun deleteEntry(entry: DownloadedEntry) {
        screenModelScope.launchIO {
            when (entry) {
                is DownloadedEntry.MangaEntry -> {
                    val source = mangaSourceManager.getOrStub(entry.manga.source)
                    mangaDownloadManager.deleteManga(entry.manga, source)
                }
                is DownloadedEntry.AnimeEntry -> {
                    val source = animeSourceManager.getOrStub(entry.anime.source)
                    animeDownloadManager.deleteAnime(entry.anime, source)
                }
            }
        }
    }

    @Immutable
    data class State(
        val entries: List<DownloadedEntry>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class Delete(val entry: DownloadedEntry) : Dialog
    }

    enum class Filter { All, Manga, Anime }
}

sealed interface DownloadedEntry {
    val title: String
    val id: Long
    val count: Int
    val sizeBytes: Long
    val isManga: Boolean

    data class MangaEntry(
        val manga: Manga,
        override val count: Int,
        override val sizeBytes: Long,
    ) : DownloadedEntry {
        override val title: String get() = manga.title
        override val id: Long get() = manga.id
        override val isManga: Boolean get() = true
    }

    data class AnimeEntry(
        val anime: Anime,
        override val count: Int,
        override val sizeBytes: Long,
    ) : DownloadedEntry {
        override val title: String get() = anime.title
        override val id: Long get() = anime.id
        override val isManga: Boolean get() = false
    }
}
