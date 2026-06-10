package eu.kanade.tachiyomi.ui.history.combined

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.interactor.RemoveMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.model.Episode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

class CombinedHistoryScreenModel(
    private val getMangaHistory: GetMangaHistory = Injekt.get(),
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val getNextEpisodes: GetNextEpisodes = Injekt.get(),
    private val removeMangaHistory: RemoveMangaHistory = Injekt.get(),
    private val removeAnimeHistory: RemoveAnimeHistory = Injekt.get(),
) : StateScreenModel<CombinedHistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _query: MutableStateFlow<String?> = MutableStateFlow(null)
    val query: StateFlow<String?> = _query.asStateFlow()

    private val _filter: MutableStateFlow<Filter> = MutableStateFlow(Filter.All)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    init {
        screenModelScope.launch {
            _query
                .flatMapLatest { q ->
                    val query = q ?: ""
                    combine(
                        getMangaHistory.subscribe(query),
                        getAnimeHistory.subscribe(query),
                        _filter,
                    ) { manga, anime, filter ->
                        merge(manga, anime, filter)
                    }
                }
                .distinctUntilChanged()
                .catch { error ->
                    logcat(LogPriority.ERROR, error)
                    _events.send(Event.InternalError)
                }
                .flowOn(Dispatchers.IO)
                .collectLatest { newList -> mutableState.update { it.copy(list = newList) } }
        }
    }

    private fun merge(
        manga: List<MangaHistoryWithRelations>,
        anime: List<AnimeHistoryWithRelations>,
        filter: Filter,
    ): List<CombinedHistoryUiModel> {
        val mangaItems = if (filter == Filter.Anime) {
            emptyList()
        } else {
            manga.map { CombinedHistoryItem.MangaItem(it) }
        }
        val animeItems = if (filter == Filter.Manga) {
            emptyList()
        } else {
            anime.map { CombinedHistoryItem.AnimeItem(it) }
        }
        val combined = (mangaItems + animeItems)
            .sortedByDescending { it.timestamp }
            .map { CombinedHistoryUiModel.Item(it) }
        return combined
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.timestamp?.toLocalDate()
                val afterDate = after?.item?.timestamp?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> CombinedHistoryUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    fun search(query: String?) {
        screenModelScope.launchIO {
            _query.emit(query)
        }
    }

    fun setFilter(filter: Filter) {
        _filter.value = filter
    }

    suspend fun getNextEpisode(): Episode? {
        return getNextEpisodes.await(onlyUnseen = false).firstOrNull()
    }

    fun getNextChapterForManga(item: CombinedHistoryItem.MangaItem) {
        screenModelScope.launchIO {
            val chapters = getNextChapters.await(
                item.history.mangaId,
                item.history.chapterId,
                onlyUnread = false,
            )
            _events.send(Event.OpenChapter(chapters.firstOrNull()))
        }
    }

    fun getNextEpisodeForAnime(item: CombinedHistoryItem.AnimeItem) {
        screenModelScope.launchIO {
            val episodes = getNextEpisodes.await(
                item.history.animeId,
                item.history.episodeId,
                onlyUnseen = false,
            )
            _events.send(Event.OpenEpisode(episodes.firstOrNull()))
        }
    }

    fun removeFromHistory(item: CombinedHistoryItem) {
        screenModelScope.launchIO {
            when (item) {
                is CombinedHistoryItem.MangaItem -> removeMangaHistory.await(item.history)
                is CombinedHistoryItem.AnimeItem -> removeAnimeHistory.await(item.history)
            }
        }
    }

    fun removeAllFromHistory(entryId: Long, isManga: Boolean) {
        screenModelScope.launchIO {
            if (isManga) {
                removeMangaHistory.await(entryId)
            } else {
                removeAnimeHistory.await(entryId)
            }
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val mangaResult = removeMangaHistory.awaitAll()
            val animeResult = removeAnimeHistory.awaitAll()
            if (!mangaResult && !animeResult) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    @Immutable
    data class State(
        val list: List<CombinedHistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object DeleteAll : Dialog
        data class Delete(val item: CombinedHistoryItem) : Dialog
    }

    sealed interface Event {
        data class OpenChapter(val chapter: Chapter?) : Event
        data class OpenEpisode(val episode: Episode?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }

    enum class Filter { All, Manga, Anime }
}

sealed interface CombinedHistoryItem {
    val timestamp: Long
    val title: String
    val entryId: Long
    val isManga: Boolean

    data class MangaItem(val history: MangaHistoryWithRelations) : CombinedHistoryItem {
        override val timestamp: Long get() = history.readAt?.time ?: 0L
        override val title: String get() = history.title
        override val entryId: Long get() = history.mangaId
        override val isManga: Boolean get() = true
    }

    data class AnimeItem(val history: AnimeHistoryWithRelations) : CombinedHistoryItem {
        override val timestamp: Long get() = history.seenAt?.time ?: 0L
        override val title: String get() = history.title
        override val entryId: Long get() = history.animeId
        override val isManga: Boolean get() = false
    }
}

sealed interface CombinedHistoryUiModel {
    data class Header(val date: LocalDate) : CombinedHistoryUiModel
    data class Item(val item: CombinedHistoryItem) : CombinedHistoryUiModel
}
