package eu.kanade.tachiyomi.ui.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Tab
import eu.kanade.presentation.util.scrollingTitle
import eu.kanade.tachiyomi.ui.more.MoreScreen
import kotlinx.coroutines.launch
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
        val pagerState = androidx.compose.foundation.pager.rememberPagerState { 5 }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val navigator = LocalNavigator.currentOrThrow
        val browseModel = rememberScreenModel { NovelBrowseScreenModel() }
        val titles = listOf(
            stringResource(AYMR.strings.label_recent),
            stringResource(AYMR.strings.label_favorite),
            stringResource(AYMR.strings.label_novel_browse),
            stringResource(AYMR.strings.label_novel_downloaded),
            stringResource(AYMR.strings.label_novel_queue),
        )

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(AYMR.strings.label_novel)) },
                        actions = {
                            IconButton(onClick = { navigator.push(MoreScreen()) }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "More")
                            }
                        },
                    )
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        titles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            ) { page ->
                val active = pagerState.currentPage == page
                when (page) {
                    0 -> NovelHistoryContent(active)
                    1 -> NovelFavoriteContent(active)
                    2 -> NovelBrowseContent(browseModel)
                    3 -> NovelDownloadContent(active)
                    else -> NovelQueueContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelBrowseContent(screenModel: NovelBrowseScreenModel) {
    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = state.query,
            onValueChange = screenModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(AYMR.strings.label_novel_search_hint)) },
            trailingIcon = {
                IconButton(onClick = screenModel::submitSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                }
            },
            keyboardActions = KeyboardActions(onSearch = { screenModel.submitSearch() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.lang == NovelLang.EN,
                onClick = { screenModel.setLang(NovelLang.EN) },
                label = { Text("English") },
            )
            FilterChip(
                selected = state.lang == NovelLang.ID,
                onClick = { screenModel.setLang(NovelLang.ID) },
                label = { Text("Indonesia") },
            )
        }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(state.items, key = { it.url }) { item ->
                    NovelGridItem(item) {
                        navigator.push(NovelDetailScreen(item.url, item.title, item.coverUrl))
                    }
                }
                if (state.canLoadMore && state.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedButton(
                            onClick = screenModel::loadMore,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                        ) { Text(stringResource(AYMR.strings.label_novel_load_more)) }
                    }
                }
            }
            if (state.isLoading && state.items.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            if (!state.isLoading && state.items.isEmpty()) {
                Text(
                    text = state.error ?: stringResource(AYMR.strings.label_novel_empty),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
    }
}

@Composable
private fun NovelFavoriteContent(active: Boolean) {
    val navigator = LocalNavigator.currentOrThrow
    var items by remember { mutableStateOf(NovelStore.getSaved()) }
    LaunchedEffect(active) { if (active) items = NovelStore.getSaved() }

    if (items.isEmpty()) {
        CenterMessage(stringResource(AYMR.strings.label_novel_empty))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(items, key = { it.url }) { item ->
                NovelGridItem(item) {
                    navigator.push(NovelDetailScreen(item.url, item.title, item.coverUrl))
                }
            }
        }
    }
}

@Composable
private fun NovelHistoryContent(active: Boolean) {
    val navigator = LocalNavigator.currentOrThrow
    var entries by remember { mutableStateOf(NovelStore.getHistory()) }
    LaunchedEffect(active) { if (active) entries = NovelStore.getHistory() }

    if (entries.isEmpty()) {
        CenterMessage(stringResource(AYMR.strings.label_novel_empty))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            columnItems(entries, key = { it.novel.url }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navigator.push(
                                NovelDetailScreen(
                                    entry.novel.url,
                                    entry.novel.title,
                                    entry.novel.coverUrl,
                                    autoResume = true,
                                ),
                            )
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = entry.novel.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(entry.novel.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false, modifier = Modifier.scrollingTitle())
                        Text(entry.lastChapterName, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false, modifier = Modifier.scrollingTitle())
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelDownloadContent(active: Boolean) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    var items by remember { mutableStateOf(NovelDownloader.getDownloaded(context)) }
    LaunchedEffect(active) { if (active) items = NovelDownloader.getDownloaded(context) }

    if (items.isEmpty()) {
        CenterMessage(stringResource(AYMR.strings.label_novel_empty))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(items, key = { it.url }) { item ->
                NovelGridItem(item) {
                    navigator.push(NovelDetailScreen(item.url, item.title, item.coverUrl))
                }
            }
        }
    }
}

@Composable
private fun NovelQueueContent() {
    val queue by NovelDownloadQueue.state.collectAsState()
    val paused by NovelDownloadQueue.paused.collectAsState()
    Column(Modifier.fillMaxSize()) {
        if (queue.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { if (paused) NovelDownloadQueue.resume() else NovelDownloadQueue.pause() },
                ) {
                    Icon(
                        imageVector = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = null,
                    )
                    Text(if (paused) "Resume" else "Pause")
                }
            }
        }
        if (queue.isEmpty()) {
            CenterMessage(stringResource(AYMR.strings.label_novel_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                columnItems(queue, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.scrollingTitle(),
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { if (item.total > 0) item.done.toFloat() / item.total else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "${item.done}/${item.total}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { NovelDownloadQueue.remove(item.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, modifier = Modifier.padding(24.dp))
    }
}
