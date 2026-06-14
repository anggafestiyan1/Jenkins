package eu.kanade.tachiyomi.ui.library.manga

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.download.manga.MangaDownloadQueueScreenModel
import eu.kanade.tachiyomi.ui.history.combined.CombinedHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.combined.combinedHistoryTab
import eu.kanade.tachiyomi.ui.history.downloads.DownloadsScreenModel
import eu.kanade.tachiyomi.ui.history.downloads.downloadsTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.manga.mangaUpdatesTab
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data object MangaLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = AYMR.strings.label_komik
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val historyScreenModel = rememberScreenModel { CombinedHistoryScreenModel() }
        LaunchedEffect(Unit) {
            historyScreenModel.setFilter(CombinedHistoryScreenModel.Filter.Manga)
        }
        val downloadsScreenModel = rememberScreenModel { DownloadsScreenModel() }
        LaunchedEffect(Unit) {
            downloadsScreenModel.setFilter(DownloadsScreenModel.Filter.Manga)
        }
        val queueScreenModel = rememberScreenModel { MangaDownloadQueueScreenModel() }

        val historySearchQuery by historyScreenModel.query.collectAsState()
        val downloadsSearchQuery by downloadsScreenModel.query.collectAsState()

        TabbedScreen(
            titleRes = AYMR.strings.label_komik_library,
            // Open on the Recent tab (index 2) by default.
            state = androidx.compose.foundation.pager.rememberPagerState(initialPage = 2) { 5 },
            tabs = persistentListOf(
                // index 0: Library (no search)
                mangaFavoriteInnerTab(),
                // index 1: Updates for favorited manga (relabel; fork's label_updates == "Manga")
                mangaUpdatesTab(context, fromMore = false).copy(titleRes = AYMR.strings.label_updates_title),
                // index 2 (even): History -> animeSearchQuery (relabel; fork's label_history == "Manga")
                combinedHistoryTab(context, fromMore = false, screenModel = historyScreenModel)
                    .copy(titleRes = AYMR.strings.label_recent),
                // index 3 (odd): Downloads -> mangaSearchQuery (relabel: "Offline")
                downloadsTab(context, fromMore = false, screenModel = downloadsScreenModel)
                    .copy(titleRes = AYMR.strings.label_offline),
                // index 4: Download queue (no search) (relabel: "Queue")
                mangaQueueInnerTab(queueScreenModel).copy(titleRes = AYMR.strings.label_queue),
            ),
            mangaSearchQuery = downloadsSearchQuery,
            onChangeMangaSearchQuery = downloadsScreenModel::search,
            animeSearchQuery = historySearchQuery,
            onChangeAnimeSearchQuery = historyScreenModel::search,
            scrollable = true,
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
