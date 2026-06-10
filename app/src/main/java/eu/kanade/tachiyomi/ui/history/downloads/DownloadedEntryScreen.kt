package eu.kanade.tachiyomi.ui.history.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.service.getChapterSort
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

data class DownloadedEntryScreen(
    private val entryId: Long,
    private val isManga: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val screenModel = rememberScreenModel { DownloadedEntryScreenModel(entryId, isManga) }
        val state by screenModel.state.collectAsState()

        var showDeleteAllDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                AppBar(
                    title = state.title,
                    navigateUp = navigator::pop,
                    actions = {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(MR.strings.action_delete),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            if (state.loading) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }
            Column(modifier = Modifier.padding(contentPadding)) {
                ResumeButton(
                    enabled = state.items.isNotEmpty(),
                    label = stringResource(
                        if (isManga) AYMR.strings.action_resume_reading else AYMR.strings.action_resume_watching,
                    ),
                    onClick = {
                        scope.launch {
                            val item = screenModel.resumeItem() ?: return@launch
                            openItem(context, item, isManga)
                        }
                    },
                )
                if (state.items.isEmpty()) {
                    EmptyScreen(stringRes = AYMR.strings.no_downloaded_items)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(
                            items = state.items,
                            key = { it.id },
                        ) { item ->
                            DownloadedItemRow(
                                item = item,
                                onClick = {
                                    scope.launch {
                                        openItem(context, item, isManga)
                                    }
                                },
                                onClickDelete = { screenModel.deleteSingle(item) },
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text(stringResource(MR.strings.action_remove_everything)) },
                text = { Text(stringResource(MR.strings.clear_history_confirmation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteAllDialog = false
                            screenModel.deleteAll()
                        },
                    ) { Text(stringResource(MR.strings.action_ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        LaunchedEffect(Unit) {
            screenModel.load()
        }
    }

    private suspend fun openItem(
        context: android.content.Context,
        item: DownloadedItem,
        isManga: Boolean,
    ) {
        if (isManga) {
            context.startActivity(ReaderActivity.newIntent(context, item.entryId, item.id))
        } else {
            val playerPreferences: PlayerPreferences by injectLazy()
            val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            MainActivity.startPlayerActivity(context, item.entryId, item.id, extPlayer)
        }
    }
}

@Composable
private fun ResumeButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.medium),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = MaterialTheme.padding.small),
            )
        }
    }
}

@Composable
private fun DownloadedItemRow(
    item: DownloadedItem,
    onClick: () -> Unit,
    onClickDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (item.numberLabel.isNotBlank()) {
                Text(
                    text = item.numberLabel,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
            )
        }
    }
}

private class DownloadedEntryScreenModel(
    private val entryId: Long,
    private val isManga: Boolean,
    private val getManga: GetManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val mangaDownloadCache: MangaDownloadCache = Injekt.get(),
    private val animeDownloadCache: AnimeDownloadCache = Injekt.get(),
    private val mangaDownloadManager: MangaDownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaHistoryRepository: MangaHistoryRepository = Injekt.get(),
    private val animeHistoryRepository: AnimeHistoryRepository = Injekt.get(),
) : StateScreenModel<DownloadedEntryScreenModel.State>(State()) {

    private var manga: Manga? = null
    private var anime: Anime? = null
    private val allChapters = MutableStateFlow<List<Chapter>>(emptyList())
    private val allEpisodes = MutableStateFlow<List<Episode>>(emptyList())

    fun load() {
        screenModelScope.launchIO {
            if (isManga) {
                val m = getManga.await(entryId) ?: return@launchIO
                manga = m
                val source = mangaSourceManager.getOrStub(m.source)
                val chapters = getChaptersByMangaId.await(m.id)
                    .sortedWith(getChapterSort(m, sortDescending = false))
                allChapters.value = chapters
                val downloaded = chapters.filter {
                    mangaDownloadManager.isChapterDownloaded(it.name, it.scanlator, m.title, source.id)
                }
                mutableState.update {
                    it.copy(
                        loading = false,
                        title = m.title,
                        items = downloaded.map { c -> chapterToItem(c) },
                    )
                }
            } else {
                val a = getAnime.await(entryId) ?: return@launchIO
                anime = a
                val source = animeSourceManager.getOrStub(a.source)
                val episodes = getEpisodesByAnimeId.await(a.id)
                    .sortedWith(getEpisodeSort(a, sortDescending = false))
                allEpisodes.value = episodes
                val downloaded = episodes.filter {
                    animeDownloadManager.isEpisodeDownloaded(it.name, it.scanlator, a.title, source.id)
                }
                mutableState.update {
                    it.copy(
                        loading = false,
                        title = a.title,
                        items = downloaded.map { e -> episodeToItem(e) },
                    )
                }
            }
        }
    }

    suspend fun resumeItem(): DownloadedItem? {
        val items = state.value.items
        if (items.isEmpty()) return null
        if (isManga) {
            val lastHistory = mangaHistoryRepository.getHistoryByMangaId(entryId)
                .maxByOrNull { it.readAt?.time ?: 0L }
            val targetId = lastHistory?.chapterId
            return items.firstOrNull { it.id == targetId } ?: items.firstOrNull { !it.read } ?: items.first()
        } else {
            val lastHistory = animeHistoryRepository.getHistoryByAnimeId(entryId)
                .maxByOrNull { it.seenAt?.time ?: 0L }
            val targetId = lastHistory?.episodeId
            return items.firstOrNull { it.id == targetId } ?: items.firstOrNull { !it.read } ?: items.first()
        }
    }

    fun deleteSingle(item: DownloadedItem) {
        screenModelScope.launchIO {
            if (isManga) {
                val m = manga ?: return@launchIO
                val source = mangaSourceManager.getOrStub(m.source)
                val chapter = allChapters.value.firstOrNull { it.id == item.id } ?: return@launchIO
                mangaDownloadManager.deleteChapters(listOf(chapter), m, source)
            } else {
                val a = anime ?: return@launchIO
                val source = animeSourceManager.getOrStub(a.source)
                val episode = allEpisodes.value.firstOrNull { it.id == item.id } ?: return@launchIO
                animeDownloadManager.deleteEpisodes(listOf(episode), a, source)
            }
            load()
        }
    }

    fun deleteAll() {
        screenModelScope.launchIO {
            if (isManga) {
                val m = manga ?: return@launchIO
                val source = mangaSourceManager.getOrStub(m.source)
                mangaDownloadManager.deleteManga(m, source)
            } else {
                val a = anime ?: return@launchIO
                val source = animeSourceManager.getOrStub(a.source)
                animeDownloadManager.deleteAnime(a, source)
            }
            load()
        }
    }

    private fun chapterToItem(c: Chapter): DownloadedItem = DownloadedItem(
        id = c.id,
        entryId = c.mangaId,
        name = c.name,
        numberLabel = if (c.chapterNumber >= 0) "Ch. ${c.chapterNumber}" else "",
        read = c.read,
    )

    private fun episodeToItem(e: Episode): DownloadedItem = DownloadedItem(
        id = e.id,
        entryId = e.animeId,
        name = e.name,
        numberLabel = if (e.episodeNumber >= 0) "Ep. ${e.episodeNumber}" else "",
        read = e.seen,
    )

    @Immutable
    data class State(
        val loading: Boolean = true,
        val title: String = "",
        val items: List<DownloadedItem> = emptyList(),
    )
}

@Immutable
data class DownloadedItem(
    val id: Long,
    val entryId: Long,
    val name: String,
    val numberLabel: String,
    val read: Boolean,
)
