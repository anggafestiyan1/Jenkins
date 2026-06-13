package eu.kanade.tachiyomi.ui.more

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.preference.plusAssign
import uy.kohesive.injekt.injectLazy

/**
 * One-tap installer for curated manga extensions. Android still shows a system install dialog per
 * extension (no silent install without root/Shizuku), so the user approves each.
 */
object RecommendedExtensions {

    private val extensionManager: MangaExtensionManager by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()

    private val ENGLISH = listOf(
        "allmanga", "asura", "flame", "hades", "lunar", "mangadex",
        "mangakakalot", "manhwatop", "top manhua", "webtoon",
    )

    private val INDONESIAN = listOf(
        "kiryuu", "komik cast", "komikindo", "komiku", "west manga", "manhwadesu",
        "shinigami", "sekte komik", "maid - manga", "bacakomik", "komik station", "softkomik",
    )

    val ALL: List<String> = ENGLISH + INDONESIAN

    fun isRecommended(name: String): Boolean = ALL.any { name.contains(it, ignoreCase = true) }

    suspend fun installEnglish(): Int {
        sourcePreferences.enabledLanguages() += "en"
        return install(ENGLISH)
    }

    suspend fun installIndonesian(): Int {
        // Enable Indonesian so installed Indonesian sources show up in the source list.
        sourcePreferences.enabledLanguages() += "id"
        return install(INDONESIAN)
    }

    private suspend fun install(keywords: List<String>): Int {
        extensionManager.findAvailableExtensions()
        val available = withTimeoutOrNull(20_000) {
            extensionManager.availableExtensionsFlow.first { it.isNotEmpty() }
        }.orEmpty()

        val seen = HashSet<String>()
        val targets = available.filter { ext ->
            keywords.any { kw -> ext.name.contains(kw, ignoreCase = true) } && seen.add(ext.pkgName)
        }
        targets.forEach { ext ->
            runCatching { extensionManager.installExtension(ext).collect() }
        }
        return targets.size
    }
}
