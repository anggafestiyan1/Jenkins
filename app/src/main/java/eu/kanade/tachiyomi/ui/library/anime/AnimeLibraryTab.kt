package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.history.combined.CombinedHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.combined.combinedHistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data object AnimeLibraryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val title = AYMR.strings.label_anime
            return TabOptions(
                index = 0u,
                title = stringResource(title),
                icon = rememberVectorPainter(Icons.Outlined.Movie),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current

        val historyScreenModel = rememberScreenModel { CombinedHistoryScreenModel() }
        LaunchedEffect(Unit) {
            historyScreenModel.setFilter(CombinedHistoryScreenModel.Filter.Anime)
        }

        val historySearchQuery by historyScreenModel.query.collectAsState()

        TabbedScreen(
            titleRes = AYMR.strings.label_anime_library,
            tabs = persistentListOf(
                // Saved: local library grid (categories, delete, move-to-category, continue watching)
                animeFavoriteInnerTab().copy(titleRes = AYMR.strings.label_saved),
                // Upload: copy local video files into the local source and index them
                animeUploadInnerTab(),
                // Recent: last watched, per entry (folder)
                combinedHistoryTab(context, fromMore = false, screenModel = historyScreenModel)
                    .copy(titleRes = AYMR.strings.label_recent),
            ),
            animeSearchQuery = historySearchQuery,
            onChangeAnimeSearchQuery = historyScreenModel::search,
        )

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }

    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
