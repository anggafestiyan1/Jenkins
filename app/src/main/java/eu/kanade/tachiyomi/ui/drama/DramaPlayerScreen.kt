package eu.kanade.tachiyomi.ui.drama

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

/** Vertical micro-drama player (HLS) with episode jump + auto-next. Streaming only (MVP). */
class DramaPlayerScreen(private val item: DramaItem) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { DramaPlayerScreenModel(item) }
        val state by model.state.collectAsState()
        val current = state.episodes.getOrNull(state.index)

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = item.title + (current?.let { "  •  Ep ${it.number}/${state.episodes.size}" } ?: ""),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(Modifier.fillMaxSize().padding(contentPadding).background(Color.Black)) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when {
                        state.error != null -> Text(state.error!!, color = Color.White, modifier = Modifier.padding(24.dp))
                        state.resolving || state.hlsUrl == null -> CircularProgressIndicator(color = Color.White)
                        else -> AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    keepScreenOn = true
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setOnCompletionListener { model.next() }
                                }
                            },
                            update = { vv ->
                                val url = state.hlsUrl
                                if (url != null && vv.tag != url) {
                                    vv.tag = url
                                    vv.setVideoURI(Uri.parse(url))
                                    vv.setOnPreparedListener { it.start() }
                                }
                            },
                        )
                    }
                }

                // Episode jump strip
                if (state.episodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { model.prev() }) {
                            Icon(Icons.Outlined.SkipPrevious, contentDescription = "Prev", tint = Color.White)
                        }
                        state.episodes.forEachIndexed { i, ep ->
                            FilterChip(
                                selected = i == state.index,
                                onClick = { model.goTo(i) },
                                label = { Text(ep.number.toString()) },
                            )
                        }
                        IconButton(onClick = { model.next() }) {
                            Icon(Icons.Outlined.SkipNext, contentDescription = "Next", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

class DramaPlayerScreenModel(private val item: DramaItem) :
    StateScreenModel<DramaPlayerScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            try {
                val eps = DramaSource.episodes(item)
                if (eps.isEmpty()) {
                    mutableState.update { it.copy(error = "Tidak ada episode") }
                    return@launchIO
                }
                mutableState.update { it.copy(episodes = eps) }
                resolve(0)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(error = "Gagal memuat: ${e.message}") }
            }
        }
    }

    fun goTo(index: Int) {
        if (index in state.value.episodes.indices) resolve(index)
    }

    fun next() = goTo(state.value.index + 1)
    fun prev() = goTo(state.value.index - 1)

    private fun resolve(index: Int) {
        val episode = state.value.episodes.getOrNull(index) ?: return
        screenModelScope.launchIO {
            mutableState.update { it.copy(index = index, resolving = true, hlsUrl = null, error = null) }
            try {
                val url = DramaSource.hlsUrl(episode)
                mutableState.update {
                    it.copy(resolving = false, hlsUrl = url, error = if (url == null) "Video tidak ditemukan" else null)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(resolving = false, error = "Gagal memutar: ${e.message}") }
            }
        }
    }

    data class State(
        val episodes: List<DramaEpisode> = emptyList(),
        val index: Int = 0,
        val resolving: Boolean = false,
        val hlsUrl: String? = null,
        val error: String? = null,
    )
}
