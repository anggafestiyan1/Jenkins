package eu.kanade.tachiyomi.ui.library.anime

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
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadQueueScreenModel
import eu.kanade.tachiyomi.ui.history.combined.CombinedHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.combined.combinedHistoryTab
import eu.kanade.tachiyomi.ui.history.downloads.DownloadsScreenModel
import eu.kanade.tachiyomi.ui.history.downloads.downloadsTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data object AnimeLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = AYMR.strings.label_anime
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(
                R.drawable.anim_animelibrary_leave,
            )
            return TabOptions(
                index = 0u,
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

        val historyScreenModel = rememberScreenModel { CombinedHistoryScreenModel() }
        LaunchedEffect(Unit) {
            historyScreenModel.setFilter(CombinedHistoryScreenModel.Filter.Anime)
        }
        val downloadsScreenModel = rememberScreenModel { DownloadsScreenModel() }
        LaunchedEffect(Unit) {
            downloadsScreenModel.setFilter(DownloadsScreenModel.Filter.Anime)
        }
        val queueScreenModel = rememberScreenModel { AnimeDownloadQueueScreenModel() }

        val historySearchQuery by historyScreenModel.query.collectAsState()
        val downloadsSearchQuery by downloadsScreenModel.query.collectAsState()

        TabbedScreen(
            titleRes = AYMR.strings.label_anime_library,
            tabs = persistentListOf(
                combinedHistoryTab(context, fromMore = false, screenModel = historyScreenModel),
                animeFavoriteInnerTab(),
                downloadsTab(context, fromMore = false, screenModel = downloadsScreenModel),
                animeQueueInnerTab(queueScreenModel),
            ),
            mangaSearchQuery = downloadsSearchQuery,
            onChangeMangaSearchQuery = downloadsScreenModel::search,
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
