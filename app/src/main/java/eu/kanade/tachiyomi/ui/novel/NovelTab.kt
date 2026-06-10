package eu.kanade.tachiyomi.ui.novel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data object NovelTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 2u,
            title = stringResource(AYMR.strings.label_novel),
            icon = rememberVectorPainter(Icons.AutoMirrored.Outlined.MenuBook),
        )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { NovelBrowseScreenModel() }
        val state by screenModel.state.collectAsState()
        val gridState = rememberLazyGridState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (state.searchActive) {
                            TextField(
                                value = state.query,
                                onValueChange = screenModel::onQueryChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text(stringResource(AYMR.strings.label_novel_search_hint)) },
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onSearch = { screenModel.submitSearch() },
                                ),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    imeAction = ImeAction.Search,
                                ),
                            )
                        } else {
                            Text(stringResource(AYMR.strings.label_novel))
                        }
                    },
                    actions = {
                        IconButton(onClick = screenModel::toggleSearch) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = screenModel::toggleSavedMode) {
                            Icon(
                                imageVector = if (state.savedMode) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Saved",
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            val items = if (state.savedMode) state.saved else state.items
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(items, key = { it.url }) { item ->
                        NovelGridItem(
                            item = item,
                            onClick = { navigator.push(NovelDetailScreen(item.url, item.title, item.coverUrl)) },
                        )
                    }
                    if (!state.savedMode && state.canLoadMore && items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(
                                onClick = screenModel::loadMore,
                                enabled = !state.isLoading,
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            ) {
                                Text(stringResource(AYMR.strings.label_novel_load_more))
                            }
                        }
                    }
                }

                if (state.isLoading && items.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                if (!state.isLoading && items.isEmpty()) {
                    Text(
                        text = state.error ?: stringResource(AYMR.strings.label_novel_empty),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            }
        }
    }
}
