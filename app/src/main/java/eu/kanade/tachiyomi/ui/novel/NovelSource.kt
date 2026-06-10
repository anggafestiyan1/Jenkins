package eu.kanade.tachiyomi.ui.novel

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

/**
 * Lightweight, single-source text-novel reader backend.
 *
 * Scrapes a free novel aggregator (novelfull) over HTTP with Jsoup. There are no installable
 * "novel extensions" in the Tachiyomi/Aniyomi ecosystem (extensions only serve images/video),
 * so this is a built-in, hardcoded source dedicated to reading prose chapters as text.
 */
object NovelSource {

    const val BASE_URL = "https://novelfull.com"

    private val network: NetworkHelper by injectLazy()

    private suspend fun fetchDoc(url: String): Document = withIOContext {
        network.client.newCall(GET(url)).awaitSuccess().asJsoup()
    }

    private fun abs(url: String): String = when {
        url.isBlank() -> ""
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$BASE_URL$url"
        else -> "$BASE_URL/$url"
    }

    // novelfull serves multiple thumbnail sizes; list rows use a small variant that looks
    // cropped/letterboxed. The detail page ("book") cover uses this fixed larger-size hash, so
    // swap the trailing size hash to fetch the full-size portrait cover for the grid too.
    private val thumbSizeRegex = Regex("-[a-f0-9]{32}(\\.[A-Za-z0-9]+)$")
    private const val DETAIL_COVER_SIZE = "-2239c49aee6b961904acf173b7e4602a"

    private fun upscaleCover(url: String): String =
        if (url.isBlank()) "" else thumbSizeRegex.replace(url) { "$DETAIL_COVER_SIZE${it.groupValues[1]}" }

    private fun parseList(doc: Document): List<NovelItem> {
        return doc.select("div.row").mapNotNull { row ->
            val a = row.selectFirst("h3.truyen-title a") ?: return@mapNotNull null
            val href = a.attr("href")
            if (href.isBlank()) return@mapNotNull null
            NovelItem(
                title = a.text().trim(),
                url = abs(href),
                coverUrl = upscaleCover(abs(row.selectFirst("img.cover")?.attr("src").orEmpty())),
            )
        }
    }

    suspend fun popular(page: Int): List<NovelItem> =
        parseList(fetchDoc("$BASE_URL/most-popular?page=$page"))

    suspend fun search(query: String, page: Int): List<NovelItem> {
        val keyword = query.trim().replace(" ", "+")
        return parseList(fetchDoc("$BASE_URL/search?keyword=$keyword&page=$page"))
    }

    suspend fun details(url: String): NovelDetail {
        val doc = fetchDoc(url)
        val title = doc.selectFirst("h3.title")?.text()?.trim().orEmpty()
        val cover = abs(doc.selectFirst("div.book img")?.attr("src").orEmpty())
        val description = doc.selectFirst("div.desc-text")?.text()?.trim().orEmpty()
        val novelId = doc.selectFirst("[data-novel-id]")?.attr("data-novel-id").orEmpty()

        val chapters = if (novelId.isNotBlank()) {
            fetchDoc("$BASE_URL/ajax/chapter-option?novelId=$novelId")
                .select("option")
                .mapNotNull { opt ->
                    val href = opt.attr("value")
                    if (href.isBlank()) null else NovelChapter(opt.text().trim(), abs(href))
                }
        } else {
            doc.select("#list-chapter a, ul.list-chapter li a").mapNotNull { a ->
                val href = a.attr("href")
                if (href.isBlank()) null else {
                    NovelChapter(a.attr("title").ifBlank { a.text() }.trim(), abs(href))
                }
            }
        }

        return NovelDetail(
            title = title.ifBlank { "Novel" },
            url = url,
            coverUrl = cover,
            description = description,
            chapters = chapters,
        )
    }

    suspend fun chapterText(url: String): String {
        val doc = fetchDoc(url)
        val content = doc.selectFirst("#chapter-content")
            ?: doc.selectFirst("div.chapter-c")
            ?: return ""
        content.select("script, ins, style, [class*=ads], [id*=ad-], div[align=left]").remove()
        val paragraphs = content.select("p")
        return if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n\n") { it.text().trim() }.trim()
        } else {
            content.wholeText().trim()
        }
    }
}

data class NovelItem(
    val title: String,
    val url: String,
    val coverUrl: String,
)

data class NovelChapter(
    val name: String,
    val url: String,
)

data class NovelDetail(
    val title: String,
    val url: String,
    val coverUrl: String,
    val description: String,
    val chapters: List<NovelChapter>,
)
