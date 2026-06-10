package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

enum class HideLibraryTab(val titleRes: StringResource) {
    NONE(AYMR.strings.pref_hide_library_tab_none),
    MANGA(AYMR.strings.pref_hide_library_tab_manga),
    ANIME(AYMR.strings.pref_hide_library_tab_anime),
}
