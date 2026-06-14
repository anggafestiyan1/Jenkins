package eu.kanade.tachiyomi.ui.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Tab
import eu.kanade.presentation.util.scrollingTitle
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import eu.kanade.tachiyomi.ui.history.combined.CombinedHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.combined.combinedHistoryTab
import eu.kanade.tachiyomi.ui.library.anime.animeFavoriteInnerTab
import eu.kanade.tachiyomi.ui.library.anime.animeUploadInnerTab
import eu.kanade.tachiyomi.ui.more.MoreScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data object StreamTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 5u,
            title = "Stream",
            icon = rememberVectorPainter(Icons.Outlined.Movie),
        )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        var film by remember { mutableStateOf(false) }

        // Stream state
        val streamPager = rememberPagerState { 5 }
        val streamTitles = listOf("Stream", "Favorite", "History", "Offline", "Queue")
        var activeSourceId by remember { mutableStateOf(StreamPrefs.activeSourceId()) }
        var showSourceMenu by remember { mutableStateOf(false) }
        var showDomainDialog by remember { mutableStateOf(false) }
        var reloadTick by remember { mutableStateOf(0) }
        val source = StreamSources.byId(activeSourceId)
        val searchModel = rememberScreenModel { StreamSearchScreenModel() }

        // Film state — reuses the local video library tabs (Saved / Upload / Recent)
        val filmHistoryModel = rememberScreenModel { CombinedHistoryScreenModel() }
        LaunchedEffect(Unit) { filmHistoryModel.setFilter(CombinedHistoryScreenModel.Filter.Anime) }
        val filmTabs = persistentListOf(
            animeFavoriteInnerTab().copy(titleRes = AYMR.strings.label_saved),
            animeUploadInnerTab(),
            combinedHistoryTab(context, fromMore = false, screenModel = filmHistoryModel)
                .copy(titleRes = AYMR.strings.label_recent),
        )
        val filmPager = rememberPagerState { filmTabs.size }
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ModeTab("Stream", selected = !film) { film = false }
                                Spacer(Modifier.width(16.dp))
                                ModeTab("Film", selected = film) { film = true }
                            }
                        },
                        actions = {
                            if (!film) {
                                TextButton(onClick = { showSourceMenu = true }) {
                                    Text(source.name)
                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Ganti sumber")
                                }
                                DropdownMenu(expanded = showSourceMenu, onDismissRequest = { showSourceMenu = false }) {
                                    StreamSources.all.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text(s.name) },
                                            onClick = {
                                                activeSourceId = s.id
                                                StreamPrefs.setActiveSourceId(s.id)
                                                showSourceMenu = false
                                                searchModel.reset()
                                            },
                                        )
                                    }
                                }
                                IconButton(onClick = { showDomainDialog = true }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit domain")
                                }
                            }
                            IconButton(onClick = { navigator.push(MoreScreen()) }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "More")
                            }
                        },
                    )
                    val pager = if (film) filmPager else streamPager
                    val titles = if (film) filmTabs.map { stringResource(it.titleRes) } else streamTitles
                    ScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 0.dp) {
                        titles.forEachIndexed { index, title ->
                            Tab(
                                selected = pager.currentPage == index,
                                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                                text = { Text(title, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            if (film) {
                HorizontalPager(
                    state = filmPager,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                ) { page ->
                    filmTabs[page].content(PaddingValues(0.dp), snackbarHostState)
                }
            } else {
                HorizontalPager(
                    state = streamPager,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                ) { page ->
                    val active = streamPager.currentPage == page
                    when (page) {
                        0 -> SearchContent(searchModel, source, reloadTick)
                        1 -> FavoriteContent()
                        2 -> HistoryContent()
                        3 -> OfflineContent(active)
                        else -> QueueContent()
                    }
                }
            }
        }

        if (showDomainDialog) {
            DomainDialog(
                source = source,
                onDismiss = { showDomainDialog = false },
                onSaved = {
                    searchModel.reset()
                    reloadTick++
                    scope.launch { streamPager.animateScrollToPage(0) }
                },
            )
        }
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainDialog(source: StreamSource, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var value by remember { mutableStateOf(source.baseUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Domain ${source.name}") },
        text = {
            Column {
                Text("Kalau situs ganti domain, ubah di sini.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                source.baseUrl = value
                onDismiss()
                onSaved()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private enum class TypeFilter { ALL, MOVIE, SERIES }

// Curated genres (slug = the site's /genre/<slug>).
private val STREAM_GENRES = listOf(
    "Action" to "action", "Adventure" to "adventure", "Animation" to "animation",
    "Comedy" to "comedy", "Crime" to "crime", "Documentary" to "documentary",
    "Drama" to "drama", "Family" to "family", "Fantasy" to "fantasy",
    "Horror" to "horror", "Mystery" to "mystery", "Romance" to "romance",
    "Sci-Fi" to "science-fiction", "Thriller" to "thriller", "War" to "war",
    "Western" to "western", "Action & Adventure" to "action-and-adventure",
    "Sci-Fi & Fantasy" to "sci-fi-and-fantasy", "Kids" to "kids",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(model: StreamSearchScreenModel, source: StreamSource, reloadTick: Int) {
    val state by model.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow

    var type by remember { mutableStateOf(TypeFilter.ALL) }
    var genre by remember { mutableStateOf<Pair<String, String>?>(null) }
    var genreMenu by remember { mutableStateOf(false) }

    // Reset filters + reload on source switch / domain change.
    LaunchedEffect(source.id, reloadTick) {
        type = TypeFilter.ALL
        genre = null
        model.loadPopular(source)
    }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = state.query,
            onValueChange = model::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            singleLine = true,
            placeholder = { Text("Cari film / series / drakor…") },
            trailingIcon = {
                IconButton(onClick = { genre = null; model.submit(source) }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                }
            },
            keyboardActions = KeyboardActions(onSearch = { genre = null; model.submit(source) }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        // Filters: type (All/Movie/Series) + genre dropdown. Horizontally scrollable, no clipping.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = type == TypeFilter.ALL, onClick = { type = TypeFilter.ALL }, label = { Text("Semua") })
            FilterChip(selected = type == TypeFilter.MOVIE, onClick = { type = TypeFilter.MOVIE }, label = { Text("Movie") })
            FilterChip(selected = type == TypeFilter.SERIES, onClick = { type = TypeFilter.SERIES }, label = { Text("Series") })
            Box {
                FilterChip(
                    selected = genre != null,
                    onClick = { genreMenu = true },
                    label = { Text(genre?.first ?: "Genre") },
                    trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = genreMenu, onDismissRequest = { genreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Semua genre") },
                        onClick = { genre = null; genreMenu = false; model.loadPopular(source) },
                    )
                    STREAM_GENRES.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.first) },
                            onClick = { genre = g; genreMenu = false; model.loadGenre(source, g.second) },
                        )
                    }
                }
            }
        }

        val shown = remember(state.results, type) {
            state.results.filter {
                when (type) {
                    TypeFilter.ALL -> true
                    TypeFilter.MOVIE -> !it.isSeries
                    TypeFilter.SERIES -> it.isSeries
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                shown.isEmpty() -> Text(
                    text = state.error ?: "Tidak ada hasil untuk filter ini.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    items(shown, key = { it.url }) { item ->
                        PosterRow(
                            title = item.title,
                            subtitle = item.year,
                            posterUrl = item.posterUrl,
                            onClick = { navigator.push(StreamDetailScreen(item)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteContent() {
    val navigator = LocalNavigator.currentOrThrow
    val favorites by StreamStore.favorites.collectAsState()

    if (favorites.isEmpty()) {
        EmptyState("Belum ada favorit.")
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            items(favorites, key = { it.key }) { item ->
                PosterRow(
                    title = item.title,
                    subtitle = item.year,
                    posterUrl = item.posterUrl,
                    onClick = { navigator.push(StreamDetailScreen(item)) },
                    trailing = {
                        IconButton(onClick = { StreamStore.toggleFavorite(item) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Hapus")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryContent() {
    val navigator = LocalNavigator.currentOrThrow
    val history by StreamStore.history.collectAsState()

    if (history.isEmpty()) {
        EmptyState("Belum ada riwayat.")
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            items(history, key = { it.item.key }) { h ->
                PosterRow(
                    title = h.item.title,
                    subtitle = if (h.item.isSeries) h.episodeName else h.item.year,
                    posterUrl = h.item.posterUrl,
                    onClick = { navigator.push(StreamDetailScreen(h.item)) },
                    trailing = {
                        IconButton(onClick = { StreamStore.removeHistory(h.item) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Hapus")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OfflineContent(active: Boolean) {
    val navigator = LocalNavigator.currentOrThrow
    var items by remember { mutableStateOf(StreamStore.listOffline()) }
    LaunchedEffect(active) { if (active) items = StreamStore.listOffline() }

    if (items.isEmpty()) {
        EmptyState("Belum ada video offline.")
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            items(items, key = { it.id }) { offline ->
                PosterRow(
                    title = offline.title,
                    subtitle = "",
                    posterUrl = offline.posterUrl,
                    onClick = { navigator.push(StreamPlayerScreen(offline.videoPath, offline.title)) },
                    trailing = {
                        IconButton(onClick = {
                            StreamStore.deleteOffline(offline.id)
                            items = StreamStore.listOffline()
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Hapus")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueContent() {
    val queue by StreamDownloadQueue.state.collectAsState()
    val paused by StreamDownloadQueue.paused.collectAsState()

    Column(Modifier.fillMaxSize()) {
        if (queue.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { if (paused) StreamDownloadQueue.resume() else StreamDownloadQueue.pause() }) {
                    Icon(
                        imageVector = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = null,
                    )
                    Text(if (paused) "Resume" else "Pause")
                }
            }
        }
        if (queue.isEmpty()) {
            EmptyState("Antrian kosong.")
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
                                softWrap = false,
                                modifier = Modifier.scrollingTitle(),
                            )
                            Spacer(Modifier.height(4.dp))
                            if (item.status == StreamDownloadQueue.Status.ERROR) {
                                Text(
                                    text = "Gagal: ${item.error}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (item.status == StreamDownloadQueue.Status.ERROR) {
                            IconButton(onClick = { StreamDownloadQueue.retry(item.id) }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
                            }
                        }
                        IconButton(onClick = { StreamDownloadQueue.remove(item.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterRow(
    title: String,
    subtitle: String,
    posterUrl: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(80.dp, 115.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.scrollingTitle(),
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, Modifier.padding(24.dp))
    }
}

class StreamSearchScreenModel : StateScreenModel<StreamSearchScreenModel.State>(State()) {

    fun onQueryChange(q: String) = mutableState.update { it.copy(query = q) }

    fun reset() = mutableState.update { State() }

    fun loadPopular(source: StreamSource) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, error = null) }
            try {
                val results = source.popular(1)
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        results = results,
                        error = if (results.isEmpty()) {
                            "Daftar kosong — coba Search, atau selector belum cocok untuk domain ini."
                        } else {
                            null
                        },
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(isLoading = false, error = "Gagal memuat: ${e.message}") }
            }
        }
    }

    fun loadGenre(source: StreamSource, genreSlug: String) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, error = null, query = "") }
            try {
                val results = source.byGenre(genreSlug)
                mutableState.update {
                    it.copy(isLoading = false, results = results, error = if (results.isEmpty()) "Genre kosong." else null)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(isLoading = false, error = "Gagal memuat genre: ${e.message}") }
            }
        }
    }

    fun submit(source: StreamSource) {
        val q = state.value.query.trim()
        if (q.isBlank()) {
            loadPopular(source)
            return
        }
        screenModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, error = null, results = emptyList()) }
            try {
                val results = source.search(q, 1)
                mutableState.update {
                    it.copy(isLoading = false, results = results, error = if (results.isEmpty()) "Tidak ada hasil." else null)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(isLoading = false, error = "Gagal mencari: ${e.message}") }
            }
        }
    }

    data class State(
        val query: String = "",
        val results: List<StreamItem> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )
}
