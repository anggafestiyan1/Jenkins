package eu.kanade.tachiyomi.ui.youtube

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

data object YouTubeTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 3u,
            title = "YouTube",
            icon = rememberVectorPainter(Icons.Outlined.SmartDisplay),
        )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val pagerState = rememberPagerState { 3 }
        val scope = rememberCoroutineScope()
        val titles = listOf("Search", "Offline", "Queue")
        val searchModel = rememberScreenModel { YouTubeSearchScreenModel() }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(title = { Text("YouTube") })
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        titles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(title) },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            ) { page ->
                val active = pagerState.currentPage == page
                when (page) {
                    0 -> SearchContent(searchModel)
                    1 -> OfflineContent(active)
                    else -> QueueContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(model: YouTubeSearchScreenModel) {
    val state by model.state.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = state.query,
            onValueChange = model::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            placeholder = { Text("Cari video YouTube…") },
            trailingIcon = {
                IconButton(onClick = model::submit) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                }
            },
            keyboardActions = KeyboardActions(onSearch = { model.submit() }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Box(Modifier.fillMaxSize()) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (state.results.isEmpty()) {
                Text(
                    text = state.error ?: "Ketik kata kunci lalu cari.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    items(state.results, key = { it.url }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${item.uploader} • ${formatDuration(item.durationSeconds)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = {
                                YtDownloadQueue.enqueue(item)
                                context.toast("Ditambahkan ke antrian unduhan")
                            }) {
                                Icon(Icons.Outlined.Download, contentDescription = "Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineContent(active: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(YtStore.getOffline()) }
    androidx.compose.runtime.LaunchedEffect(active) { if (active) items = YtStore.getOffline() }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada video offline.", Modifier.padding(24.dp))
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            items(items, key = { it.animeId }) { offline ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                MainActivity.startPlayerActivity(context, offline.animeId, offline.episodeId, false)
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = offline.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        offline.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueContent() {
    val queue by YtDownloadQueue.state.collectAsState()
    val paused by YtDownloadQueue.paused.collectAsState()

    Column(Modifier.fillMaxSize()) {
        if (queue.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = { if (paused) YtDownloadQueue.resume() else YtDownloadQueue.pause() }) {
                    Icon(
                        imageVector = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = null,
                    )
                    Text(if (paused) "Resume" else "Pause")
                }
            }
        }
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Antrian kosong.", Modifier.padding(24.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(queue, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { item.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        IconButton(onClick = { YtDownloadQueue.remove(item.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "-"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

class YouTubeSearchScreenModel : StateScreenModel<YouTubeSearchScreenModel.State>(State()) {

    fun onQueryChange(q: String) = mutableState.update { it.copy(query = q) }

    fun submit() {
        val q = state.value.query.trim()
        if (q.isBlank()) return
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, error = null, results = emptyList()) }
            try {
                val results = YouTubeSource.search(q)
                mutableState.update { it.copy(isLoading = false, results = results) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(isLoading = false, error = "Gagal mencari: ${e.message}") }
            }
        }
    }

    data class State(
        val query: String = "",
        val results: List<YtItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )
}
