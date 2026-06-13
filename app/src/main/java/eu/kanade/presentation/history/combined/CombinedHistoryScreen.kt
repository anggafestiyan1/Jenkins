package eu.kanade.presentation.history.combined

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.anime.components.AnimeHistoryItem
import eu.kanade.presentation.history.manga.components.MangaHistoryItem
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.history.combined.CombinedHistoryItem
import eu.kanade.tachiyomi.ui.history.combined.CombinedHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.combined.CombinedHistoryUiModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun CombinedHistoryScreen(
    state: CombinedHistoryScreenModel.State,
    filter: CombinedHistoryScreenModel.Filter,
    snackbarHostState: SnackbarHostState,
    onFilterChange: (CombinedHistoryScreenModel.Filter) -> Unit,
    onClickCover: (CombinedHistoryItem) -> Unit,
    onClickResume: (CombinedHistoryItem) -> Unit,
    onClickDelete: (CombinedHistoryItem) -> Unit,
    searchQuery: String? = null,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            // Anime/Manga filter chips removed: Film and Komik each have their own Recent tab.
            state.list.let { list ->
                if (list == null) {
                    LoadingScreen(Modifier.fillMaxWidth())
                } else if (list.isEmpty()) {
                    val msg = if (!searchQuery.isNullOrEmpty()) {
                        MR.strings.no_results_found
                    } else {
                        MR.strings.information_no_recent_manga
                    }
                    EmptyScreen(
                        stringRes = msg,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CombinedHistoryContent(
                        history = list,
                        contentPadding = PaddingValues(),
                        onClickCover = onClickCover,
                        onClickResume = onClickResume,
                        onClickDelete = onClickDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    filter: CombinedHistoryScreenModel.Filter,
    onFilterChange: (CombinedHistoryScreenModel.Filter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == CombinedHistoryScreenModel.Filter.All,
            onClick = { onFilterChange(CombinedHistoryScreenModel.Filter.All) },
            label = { Text(stringResource(MR.strings.all)) },
        )
        FilterChip(
            selected = filter == CombinedHistoryScreenModel.Filter.Manga,
            onClick = { onFilterChange(CombinedHistoryScreenModel.Filter.Manga) },
            label = { Text(stringResource(AYMR.strings.label_history)) },
        )
        FilterChip(
            selected = filter == CombinedHistoryScreenModel.Filter.Anime,
            onClick = { onFilterChange(CombinedHistoryScreenModel.Filter.Anime) },
            label = { Text(stringResource(AYMR.strings.label_anime_history)) },
        )
    }
}

@Composable
private fun CombinedHistoryContent(
    history: List<CombinedHistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (CombinedHistoryItem) -> Unit,
    onClickResume: (CombinedHistoryItem) -> Unit,
    onClickDelete: (CombinedHistoryItem) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = history,
            key = { "combined-history-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is CombinedHistoryUiModel.Header -> "header"
                    is CombinedHistoryUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is CombinedHistoryUiModel.Header -> {
                    ListGroupHeader(
                        modifier = Modifier.animateItemFastScroll(),
                        text = relativeDateText(item.date),
                    )
                }
                is CombinedHistoryUiModel.Item -> {
                    when (val value = item.item) {
                        is CombinedHistoryItem.MangaItem -> {
                            MangaHistoryItem(
                                modifier = Modifier.animateItemFastScroll(),
                                history = value.history,
                                onClickCover = { onClickCover(value) },
                                onClickResume = { onClickResume(value) },
                                onClickDelete = { onClickDelete(value) },
                                onClickFavorite = {},
                            )
                        }
                        is CombinedHistoryItem.AnimeItem -> {
                            AnimeHistoryItem(
                                modifier = Modifier.animateItemFastScroll(),
                                history = value.history,
                                onClickCover = { onClickCover(value) },
                                onClickResume = { onClickResume(value) },
                                onClickDelete = { onClickDelete(value) },
                                onClickFavorite = {},
                            )
                        }
                    }
                }
            }
        }
    }
}
