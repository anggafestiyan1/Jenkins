package eu.kanade.presentation.history.downloads

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.entries.manga.model.asMangaCover
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.tachiyomi.ui.history.downloads.DownloadedEntry
import eu.kanade.tachiyomi.ui.history.downloads.DownloadsScreenModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

private val DOWNLOAD_ITEM_HEIGHT = 96.dp

@Composable
fun DownloadsScreen(
    state: DownloadsScreenModel.State,
    filter: DownloadsScreenModel.Filter,
    snackbarHostState: SnackbarHostState,
    onFilterChange: (DownloadsScreenModel.Filter) -> Unit,
    onClickEntry: (DownloadedEntry) -> Unit,
    onClickDelete: (DownloadedEntry) -> Unit,
    searchQuery: String? = null,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            FilterChips(
                filter = filter,
                onFilterChange = onFilterChange,
            )
            state.entries.let { list ->
                if (list == null) {
                    LoadingScreen(Modifier.fillMaxWidth())
                } else if (list.isEmpty()) {
                    val msg = if (!searchQuery.isNullOrEmpty()) {
                        MR.strings.no_results_found
                    } else {
                        AYMR.strings.no_downloaded_items
                    }
                    EmptyScreen(
                        stringRes = msg,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DownloadsContent(
                        entries = list,
                        onClickEntry = onClickEntry,
                        onClickDelete = onClickDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    filter: DownloadsScreenModel.Filter,
    onFilterChange: (DownloadsScreenModel.Filter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == DownloadsScreenModel.Filter.All,
            onClick = { onFilterChange(DownloadsScreenModel.Filter.All) },
            label = { Text(stringResource(MR.strings.all)) },
        )
        FilterChip(
            selected = filter == DownloadsScreenModel.Filter.Manga,
            onClick = { onFilterChange(DownloadsScreenModel.Filter.Manga) },
            label = { Text(stringResource(AYMR.strings.label_history)) },
        )
        FilterChip(
            selected = filter == DownloadsScreenModel.Filter.Anime,
            onClick = { onFilterChange(DownloadsScreenModel.Filter.Anime) },
            label = { Text(stringResource(AYMR.strings.label_anime_history)) },
        )
    }
}

@Composable
private fun DownloadsContent(
    entries: List<DownloadedEntry>,
    onClickEntry: (DownloadedEntry) -> Unit,
    onClickDelete: (DownloadedEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(),
    ) {
        items(
            items = entries,
            key = { "downloaded-${if (it.isManga) "m" else "a"}-${it.id}" },
        ) { entry ->
            DownloadedRow(
                entry = entry,
                onClick = { onClickEntry(entry) },
                onClickDelete = { onClickDelete(entry) },
            )
        }
    }
}

@Composable
private fun DownloadedRow(
    entry: DownloadedEntry,
    onClick: () -> Unit,
    onClickDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(DOWNLOAD_ITEM_HEIGHT)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (entry) {
            is DownloadedEntry.MangaEntry -> {
                ItemCover.Book(
                    modifier = Modifier.fillMaxHeight(),
                    data = entry.manga.asMangaCover(),
                    onClick = onClick,
                )
            }
            is DownloadedEntry.AnimeEntry -> {
                ItemCover.Book(
                    modifier = Modifier.fillMaxHeight(),
                    data = entry.anime.asAnimeCover(),
                    onClick = onClick,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            Text(
                text = entry.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            val pluralRes = if (entry.isManga) {
                MR.plurals.manga_num_chapters
            } else {
                AYMR.plurals.anime_num_episodes
            }
            val countLabel = pluralStringResource(pluralRes, count = entry.count, entry.count)
            Text(
                text = "$countLabel • ${Formatter.formatShortFileSize(context, entry.sizeBytes)}",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(MR.strings.action_delete)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onClickDelete()
                    },
                )
            }
        }
    }
}
