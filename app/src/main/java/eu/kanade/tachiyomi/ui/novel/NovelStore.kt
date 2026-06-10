package eu.kanade.tachiyomi.ui.novel

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet
import uy.kohesive.injekt.injectLazy

/**
 * Preference-backed persistence for the Novel section (no database tables).
 *
 * - Saved novels are stored as a string set, each entry encoded as url + SEP + title + SEP + cover.
 * - The last-read chapter url is stored per novel.
 * - Reader font size is a single int.
 */
object NovelStore {

    private val preferenceStore: PreferenceStore by injectLazy()

    // Unit separator (U+001F): safe delimiter that won't appear in urls/titles.
    private const val SEP = ""

    private fun savedPref() = preferenceStore.getStringSet("novel_saved")
    private fun lastChapterKey(novelUrl: String) = "novel_last_chapter_${novelUrl.hashCode()}"

    fun fontSize() = preferenceStore.getInt("novel_font_size", 18)

    fun getSaved(): List<NovelItem> {
        return savedPref().get()
            .mapNotNull { decode(it) }
            .sortedBy { it.title.lowercase() }
    }

    fun isSaved(url: String): Boolean = savedPref().get().any { it.substringBefore(SEP) == url }

    fun toggleSaved(item: NovelItem): Boolean {
        var nowSaved = false
        savedPref().getAndSet { current ->
            val existing = current.firstOrNull { it.substringBefore(SEP) == item.url }
            if (existing != null) {
                nowSaved = false
                current - existing
            } else {
                nowSaved = true
                current + encode(item)
            }
        }
        return nowSaved
    }

    fun setLastReadChapter(novelUrl: String, chapterUrl: String) {
        preferenceStore.getString(lastChapterKey(novelUrl)).set(chapterUrl)
    }

    fun getLastReadChapter(novelUrl: String): String? {
        return preferenceStore.getString(lastChapterKey(novelUrl)).get().ifBlank { null }
    }

    private fun encode(item: NovelItem): String =
        listOf(item.url, item.title, item.coverUrl).joinToString(SEP)

    private fun decode(raw: String): NovelItem? {
        val parts = raw.split(SEP)
        if (parts.size < 2) return null
        return NovelItem(
            url = parts[0],
            title = parts[1],
            coverUrl = parts.getOrElse(2) { "" },
        )
    }
}
