package eu.kanade.tachiyomi.ui.history.combined

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.history.HistoryDeleteAllDialog
import eu.kanade.presentation.history.HistoryDeleteDialog
import eu.kanade.presentation.history.combined.CombinedHistoryScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.history.resumeLastEpisodeSeenEvent
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.injectLazy

@Composable
fun Screen.combinedHistoryTab(
    context: Context,
    fromMore: Boolean,
    screenModel: CombinedHistoryScreenModel,
): TabContent {
    val snackbarHostState = SnackbarHostState()

    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    val filter by screenModel.filter.collectAsState()
    val searchQuery by screenModel.query.collectAsState()

    suspend fun openChapter(chapter: Chapter?) {
        if (chapter != null) {
            val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
            context.startActivity(intent)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }

    suspend fun openEpisode(episode: Episode?) {
        val playerPreferences: PlayerPreferences by injectLazy()
        val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
        if (episode != null) {
            MainActivity.startPlayerActivity(context, episode.animeId, episode.id, extPlayer)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(AYMR.strings.no_next_episode))
        }
    }

    val scope = rememberCoroutineScope()
    val navigateUp: (() -> Unit)? = if (fromMore) {
        {
            if (navigator.lastItem == HomeScreen) {
                scope.launch { HomeScreen.openTab(HomeScreen.Tab.AnimeLib()) }
            } else {
                navigator.pop()
            }
        }
    } else {
        null
    }

    return TabContent(
        titleRes = AYMR.strings.label_history,
        searchEnabled = true,
        content = { _, _ ->
            CombinedHistoryScreen(
                state = state,
                filter = filter,
                snackbarHostState = snackbarHostState,
                searchQuery = searchQuery,
                onFilterChange = screenModel::setFilter,
                onClickCover = { item ->
                    when (item) {
                        is CombinedHistoryItem.MangaItem -> navigator.push(MangaScreen(item.history.mangaId))
                        is CombinedHistoryItem.AnimeItem -> navigator.push(AnimeScreen(item.history.animeId))
                    }
                },
                onClickResume = { item ->
                    when (item) {
                        is CombinedHistoryItem.MangaItem -> screenModel.getNextChapterForManga(item)
                        is CombinedHistoryItem.AnimeItem -> screenModel.getNextEpisodeForAnime(item)
                    }
                },
                onClickDelete = { item ->
                    screenModel.setDialog(CombinedHistoryScreenModel.Dialog.Delete(item))
                },
            )

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                is CombinedHistoryScreenModel.Dialog.Delete -> {
                    HistoryDeleteDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = { all ->
                            if (all) {
                                screenModel.removeAllFromHistory(
                                    dialog.item.entryId,
                                    dialog.item.isManga,
                                )
                            } else {
                                screenModel.removeFromHistory(dialog.item)
                            }
                        },
                        isManga = dialog.item.isManga,
                    )
                }
                is CombinedHistoryScreenModel.Dialog.DeleteAll -> {
                    HistoryDeleteAllDialog(
                        onDismissRequest = onDismissRequest,
                        onDelete = screenModel::removeAllHistory,
                    )
                }
                null -> {}
            }

            LaunchedEffect(state.list) {
                if (state.list != null) {
                    (context as? MainActivity)?.ready = true
                }
            }

            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { e ->
                    when (e) {
                        CombinedHistoryScreenModel.Event.InternalError ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                        CombinedHistoryScreenModel.Event.HistoryCleared ->
                            snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                        is CombinedHistoryScreenModel.Event.OpenChapter -> openChapter(e.chapter)
                        is CombinedHistoryScreenModel.Event.OpenEpisode -> openEpisode(e.episode)
                    }
                }
            }

            LaunchedEffect(Unit) {
                resumeLastEpisodeSeenEvent.receiveAsFlow().collectLatest {
                    openEpisode(screenModel.getNextEpisode())
                }
            }
        },
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.pref_clear_history),
                icon = Icons.Outlined.DeleteSweep,
                onClick = { screenModel.setDialog(CombinedHistoryScreenModel.Dialog.DeleteAll) },
            ),
        ),
        navigateUp = navigateUp,
    )
}
