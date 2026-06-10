package eu.kanade.domain.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.novel.NovelTab
import tachiyomi.i18n.aniyomi.AYMR

enum class NavStyle(
    val titleRes: StringResource,
    val moreTab: Tab?,
) {
    DEFAULT(titleRes = AYMR.strings.pref_bottom_nav_default, moreTab = null),
    MOVE_MANGA_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_manga, moreTab = MangaLibraryTab),
    MOVE_UPDATES_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_updates, moreTab = null),
    MOVE_BROWSE_TO_MORE(titleRes = AYMR.strings.pref_bottom_nav_no_browse, moreTab = BrowseTab),
    ;

    val moreIcon: ImageVector?
        @Composable
        get() = when (this) {
            DEFAULT -> null
            MOVE_MANGA_TO_MORE -> Icons.Outlined.CollectionsBookmark
            MOVE_UPDATES_TO_MORE -> ImageVector.vectorResource(id = R.drawable.ic_updates_outline_24dp)
            MOVE_BROWSE_TO_MORE -> Icons.Outlined.Explore
        }

    val tabs: List<Tab>
        get() {
            return mutableListOf(
                AnimeLibraryTab,
                MangaLibraryTab,
                NovelTab,
                BrowseTab,
                MoreTab,
            ).apply { moreTab?.let { remove(it) } }
        }

    fun tabs(hideLibraryTab: HideLibraryTab): List<Tab> {
        return tabs.filterNot {
            when (hideLibraryTab) {
                HideLibraryTab.MANGA -> it == MangaLibraryTab
                HideLibraryTab.ANIME -> it == AnimeLibraryTab
                HideLibraryTab.NONE -> false
            }
        }
    }
}
