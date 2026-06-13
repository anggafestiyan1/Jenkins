package eu.kanade.tachiyomi.ui.more

import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import uy.kohesive.injekt.injectLazy

/**
 * One-tap installer for a curated set of manga extensions. Android still shows a system install
 * dialog per extension (no silent install without root/Shizuku), so the user approves each.
 */
object RecommendedExtensions {

    private val extensionManager: MangaExtensionManager by injectLazy()

    private val KEYWORDS = listOf(
        "allmanga", "asura", "flame", "hades", "lunar", "mangadex",
        "mangakakalot", "manhwatop", "top manhua", "webtoon",
    )

    /** Returns how many extensions were matched and queued for install. */
    suspend fun installRecommended(): Int {
        extensionManager.findAvailableExtensions()
        val available = withTimeoutOrNull(20_000) {
            extensionManager.availableExtensionsFlow.first { it.isNotEmpty() }
        }.orEmpty()

        val seen = HashSet<String>()
        val targets = available.filter { ext ->
            KEYWORDS.any { kw -> ext.name.contains(kw, ignoreCase = true) } && seen.add(ext.pkgName)
        }
        targets.forEach { ext ->
            runCatching { extensionManager.installExtension(ext).collect() }
        }
        return targets.size
    }
}
