package eu.kanade.tachiyomi.ui.browse.manga.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Standalone Manga Extensions screen, reachable from Settings → Browse (moved out of the Browse
 * tabs). Reuses the existing extensions tab + its search wiring.
 */
class MangaExtensionsSettingsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MangaExtensionsScreenModel() }
        val state by screenModel.state.collectAsState()

        TabbedScreen(
            titleRes = AYMR.strings.label_manga_extensions,
            tabs = persistentListOf(
                mangaExtensionsTab(screenModel).copy(navigateUp = { navigator.pop() }),
            ),
            mangaSearchQuery = state.searchQuery,
            onChangeMangaSearchQuery = screenModel::search,
            animeSearchQuery = state.searchQuery,
            onChangeAnimeSearchQuery = screenModel::search,
        )
    }
}
