package eu.kanade.tachiyomi.ui.stream

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

class StreamDetailScreen(private val item: StreamItem) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val model = rememberScreenModel { StreamDetailScreenModel(item) }
        val state by model.state.collectAsState()
        val favorites by StreamStore.favorites.collectAsState()
        val isFav = favorites.any { it.key == item.key }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        IconButton(onClick = { StreamStore.toggleFavorite(item) }) {
                            Icon(
                                imageVector = if (isFav) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error!!, Modifier.padding(24.dp)) }

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = contentPadding.calculateTopPadding() + 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 8.dp,
                        start = 12.dp, end = 12.dp,
                    ),
                ) {
                    item {
                        Row {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(110.dp, 160.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                if (item.year.isNotBlank()) {
                                    Text(item.year, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (state.synopsis.isNotBlank()) {
                            Text(state.synopsis, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                        }
                        Text("Episode / Tonton", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                    }

                    items(state.episodes, key = { it.url }) { ep ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                ep.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.resolving == ep.url) {
                                CircularProgressIndicator(Modifier.size(22.dp))
                            } else {
                                IconButton(onClick = { model.play(ep) }) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Play")
                                }
                            }
                            IconButton(onClick = {
                                StreamDownloadQueue.enqueue(item, ep)
                                context.toast("Ditambahkan ke antrian unduhan")
                            }) {
                                Icon(Icons.Outlined.Download, contentDescription = "Download")
                            }
                        }
                    }
                }
            }
        }

        // Open the WebView player when a play target is ready (main-thread safe).
        val pending = state.pending
        LaunchedEffect(pending) {
            if (pending != null) {
                StreamStore.recordHistory(item, pending.episode)
                val playTitle = if (item.isSeries) "${item.title} — ${pending.episode.name}" else item.title
                if (pending.web) {
                    navigator.push(StreamWebPlayerScreen(pending.url, playTitle, pending.referer))
                } else {
                    navigator.push(StreamPlayerScreen(pending.url, playTitle, HashMap(pending.headers)))
                }
                model.clearPending()
            }
        }

        // Surface resolve errors as toasts.
        val resolveError = state.resolveError
        if (resolveError != null) {
            context.toast(resolveError)
            model.clearResolveError()
        }
    }
}

class StreamDetailScreenModel(private val item: StreamItem) :
    StateScreenModel<StreamDetailScreenModel.State>(State()) {

    private val source = StreamSources.byId(item.sourceId)

    init {
        screenModelScope.launchIO {
            try {
                val detail = source.detail(item)
                mutableState.update {
                    it.copy(loading = false, synopsis = detail.synopsis, episodes = detail.episodes)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(loading = false, error = "Gagal memuat: ${e.message}") }
            }
        }
    }

    fun play(episode: StreamEpisode) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(resolving = episode.url) }
            try {
                // Prefer a direct media URL (MP4) → in-app VideoView with its required headers.
                val videos = source.videos(episode)
                val direct = videos.firstOrNull { !it.isHls } ?: videos.firstOrNull()
                val pending = if (direct != null) {
                    Pending(episode, direct.url, web = false, referer = "", headers = direct.headers)
                } else {
                    // No direct media → load the host's web player in a WebView.
                    val target = source.playTargets(episode).firstOrNull() ?: episode.url
                    val referer = if (target == episode.url) source.baseUrl else episode.url
                    Pending(episode, target, web = true, referer = referer, headers = emptyMap())
                }
                mutableState.update { it.copy(resolving = null, pending = pending) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(resolving = null, resolveError = "Gagal memutar: ${e.message}") }
            }
        }
    }

    fun clearPending() = mutableState.update { it.copy(pending = null) }

    fun clearResolveError() = mutableState.update { it.copy(resolveError = null) }

    data class Pending(
        val episode: StreamEpisode,
        val url: String,
        val web: Boolean,
        val referer: String,
        val headers: Map<String, String>,
    )

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val synopsis: String = "",
        val episodes: List<StreamEpisode> = emptyList(),
        val resolving: String? = null,
        val resolveError: String? = null,
        val pending: Pending? = null,
    )
}
