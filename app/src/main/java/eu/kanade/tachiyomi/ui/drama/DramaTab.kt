package eu.kanade.tachiyomi.ui.drama

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Theaters
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Tab
import eu.kanade.presentation.util.scrollingTitle
import eu.kanade.tachiyomi.ui.more.MoreScreen
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

data object DramaTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 6u,
            title = "Drama",
            icon = rememberVectorPainter(Icons.Outlined.Theaters),
        )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { DramaScreenModel() }
        val state by model.state.collectAsState()
        var genre by remember { mutableStateOf("") }

        LaunchedEffect(Unit) { model.load("") }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("Drama Pendek") },
                        actions = {
                            IconButton(onClick = { navigator.push(MoreScreen()) }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "More")
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DramaSource.GENRES.forEach { (label, slug) ->
                            FilterChip(
                                selected = genre == slug,
                                onClick = { genre = slug; model.load(slug) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.items.isEmpty() -> Text(
                        text = state.error ?: "Kosong.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(state.items, key = { it.key }) { item ->
                            Column(
                                modifier = Modifier.padding(4.dp).clickable { navigator.push(DramaPlayerScreen(item)) },
                            ) {
                                AsyncImage(
                                    model = item.posterUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                                )
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(top = 4.dp).scrollingTitle(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class DramaScreenModel : StateScreenModel<DramaScreenModel.State>(State()) {

    fun load(slug: String) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val items = DramaSource.byGenre(slug)
                mutableState.update {
                    it.copy(loading = false, items = items, error = if (items.isEmpty()) "Kosong (cek koneksi/domain)." else null)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(loading = false, error = "Gagal memuat: ${e.message}") }
            }
        }
    }

    data class State(
        val loading: Boolean = true,
        val items: List<DramaItem> = emptyList(),
        val error: String? = null,
    )
}
