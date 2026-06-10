package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ItemCover
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private val CARD_WIDTH = 96.dp

@Composable
fun MangaContinueReadingRow(
    history: List<MangaHistoryWithRelations>,
    onClickItem: (MangaHistoryWithRelations) -> Unit,
) {
    if (history.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(AYMR.strings.label_continue_reading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
                top = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.extraSmall,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
        ) {
            items(items = history, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .width(CARD_WIDTH)
                        .clickable { onClickItem(item) }
                        .padding(end = MaterialTheme.padding.small),
                ) {
                    ItemCover.Book(
                        data = item.coverData,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(
                            AYMR.strings.label_continue_reading_chapter,
                            item.chapterNumber.toString(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
    }
}

@Composable
fun AnimeContinueWatchingRow(
    history: List<AnimeHistoryWithRelations>,
    onClickItem: (AnimeHistoryWithRelations) -> Unit,
) {
    if (history.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(AYMR.strings.label_continue_watching),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
                top = MaterialTheme.padding.small,
                bottom = MaterialTheme.padding.extraSmall,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
        ) {
            items(items = history, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .width(CARD_WIDTH)
                        .clickable { onClickItem(item) }
                        .padding(end = MaterialTheme.padding.small),
                ) {
                    ItemCover.Book(
                        data = item.coverData,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(
                            AYMR.strings.label_continue_watching_episode,
                            item.episodeNumber.toString(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
    }
}
