package eu.kanade.tachiyomi.ui.novel

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet
import uy.kohesive.injekt.injectLazy

/**
 * Preference-backed persistence for the Novel section (no database tables).
 *
 * - Saved (favorite) novels: string set, each entry encoded with [SEP].
 * - Reading history: a single newline-separated string, newest first.
 * - Last-read chapter url per novel + reader font size.
 */
object NovelStore {

    private val preferenceStore: PreferenceStore by injectLazy()

    // Field delimiter unlikely to appear in urls/titles.
    private const val SEP = "|#|"
    private const val MAX_HISTORY = 200

    private fun savedPref() = preferenceStore.getStringSet("novel_saved")
    private fun historyPref() = preferenceStore.getString("novel_history")
    private fun lastChapterKey(novelUrl: String) = "novel_last_chapter_${novelUrl.hashCode()}"

    fun fontSize() = preferenceStore.getInt("novel_font_size", 18)

    // ---- Reader appearance ----

    // 0 = follow app, 1 = dark, 2 = sepia, 3 = black (AMOLED)
    fun readerTheme() = preferenceStore.getInt("novel_reader_theme", 0)

    // line height as a percent of font size (120..240)
    fun lineSpacingPercent() = preferenceStore.getInt("novel_line_spacing", 150)

    // 0 = default, 1 = serif, 2 = sans-serif
    fun fontFamily() = preferenceStore.getInt("novel_font_family", 0)

    fun keepScreenOn() = preferenceStore.getBoolean("novel_keep_screen_on", true)

    // ---- Read chapters ----

    private fun readPref(novelUrl: String) = preferenceStore.getStringSet("novel_read_${novelUrl.hashCode()}")

    fun getReadChapters(novelUrl: String): Set<String> = readPref(novelUrl).get()

    fun markRead(novelUrl: String, chapterUrl: String) {
        readPref(novelUrl).getAndSet { it + chapterUrl }
    }

    // ---- Saved / favorite ----

    fun getSaved(): List<NovelItem> = savedPref().get()
        .mapNotNull { decodeItem(it) }
        .sortedBy { it.title.lowercase() }

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
                current + encodeItem(item)
            }
        }
        return nowSaved
    }

    // ---- Last read chapter ----

    fun setLastReadChapter(novelUrl: String, chapterUrl: String) {
        preferenceStore.getString(lastChapterKey(novelUrl)).set(chapterUrl)
    }

    fun getLastReadChapter(novelUrl: String): String? =
        preferenceStore.getString(lastChapterKey(novelUrl)).get().ifBlank { null }

    // ---- History ----

    fun recordHistory(novel: NovelItem, lastChapterUrl: String, lastChapterName: String) {
        val entry = listOf(novel.url, novel.title, novel.coverUrl, lastChapterUrl, lastChapterName)
            .joinToString(SEP) { it.replace("\n", " ") }
        historyPref().getAndSet { raw ->
            val others = raw.split("\n")
                .filter { it.isNotBlank() && it.substringBefore(SEP) != novel.url }
            (listOf(entry) + others).take(MAX_HISTORY).joinToString("\n")
        }
    }

    fun getHistory(): List<NovelHistoryEntry> = historyPref().get()
        .split("\n")
        .mapNotNull { decodeHistory(it) }

    fun clearHistory() = historyPref().set("")

    // ---- Encoding helpers ----

    private fun encodeItem(item: NovelItem): String =
        listOf(item.url, item.title, item.coverUrl).joinToString(SEP)

    private fun decodeItem(raw: String): NovelItem? {
        val parts = raw.split(SEP)
        if (parts.size < 2) return null
        return NovelItem(url = parts[0], title = parts[1], coverUrl = parts.getOrElse(2) { "" })
    }

    private fun decodeHistory(raw: String): NovelHistoryEntry? {
        val parts = raw.split(SEP)
        if (parts.size < 5) return null
        return NovelHistoryEntry(
            novel = NovelItem(url = parts[0], title = parts[1], coverUrl = parts[2]),
            lastChapterUrl = parts[3],
            lastChapterName = parts[4],
        )
    }
}

data class NovelHistoryEntry(
    val novel: NovelItem,
    val lastChapterUrl: String,
    val lastChapterName: String,
)
