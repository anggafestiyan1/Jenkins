package eu.kanade.tachiyomi.ui.browse.manga.extension

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.ui.more.RecommendedExtensions
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.mangaRecommendedTab(): TabContent {
    val screenModel = rememberScreenModel { MangaRecommendedScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_recommended,
        searchEnabled = false,
        content = { contentPadding, _ ->
            when {
                state.loading && state.items.isEmpty() -> {
                    LoadingScreen(Modifier.padding(contentPadding))
                }
                state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                        Text("Tidak ada rekomendasi (cek internet / repo extension)", Modifier.padding(24.dp))
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                        items(state.items, key = { it.pkgName }) { ext ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = ext.iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        ext.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = ext.lang.uppercase() + if (ext.isNsfw) " • 18+" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                when {
                                    ext.pkgName in state.installed -> {
                                        Icon(Icons.Outlined.Check, contentDescription = "Terpasang")
                                    }
                                    ext.pkgName in state.installing -> {
                                        CircularProgressIndicator(Modifier.size(24.dp))
                                    }
                                    else -> {
                                        IconButton(onClick = { screenModel.install(ext) }) {
                                            Icon(Icons.Outlined.Download, contentDescription = "Install")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

class MangaRecommendedScreenModel(
    private val extensionManager: MangaExtensionManager = Injekt.get(),
) : StateScreenModel<MangaRecommendedScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO { runCatching { extensionManager.findAvailableExtensions() } }
        screenModelScope.launchIO {
            combine(
                extensionManager.availableExtensionsFlow,
                extensionManager.installedExtensionsFlow,
            ) { available, installed ->
                val recommended = available
                    .filter { RecommendedExtensions.isRecommended(it.name) }
                    .distinctBy { it.pkgName }
                    .sortedWith(compareBy({ it.lang }, { it.name.lowercase() }))
                recommended to installed.map { it.pkgName }.toSet()
            }.collectLatest { (recommended, installedPkgs) ->
                mutableState.update { it.copy(items = recommended, installed = installedPkgs, loading = false) }
            }
        }
    }

    fun install(extension: MangaExtension.Available) {
        screenModelScope.launchIO {
            mutableState.update { it.copy(installing = it.installing + extension.pkgName) }
            runCatching { extensionManager.installExtension(extension).collect() }
            mutableState.update { it.copy(installing = it.installing - extension.pkgName) }
        }
    }

    data class State(
        val items: List<MangaExtension.Available> = emptyList(),
        val installed: Set<String> = emptySet(),
        val installing: Set<String> = emptySet(),
        val loading: Boolean = true,
    )
}
