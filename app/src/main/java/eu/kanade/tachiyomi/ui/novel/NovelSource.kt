package eu.kanade.tachiyomi.ui.novel

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

enum class NovelLang { EN, ID }

/**
 * Built-in, multi-source text-novel backend. Browse is per-language (EN = novelfull, ID =
 * meionovels/Madara); details & chapter text route by the entry's host so saved/history/downloaded
 * novels keep working regardless of which language they came from.
 */
object NovelSource {

    private const val NOVELFULL = "https://novelfull.com"
    private const val MEI = "https://meionovels.com"

    private val network: NetworkHelper by injectLazy()

    private suspend fun fetchDoc(url: String): Document = withIOContext {
        network.client.newCall(GET(url)).awaitSuccess().asJsoup()
    }

    private suspend fun postDoc(url: String): Document = withIOContext {
        network.client.newCall(POST(url)).awaitSuccess().asJsoup()
    }

    // ---- Routing ----

    suspend fun popular(lang: NovelLang, page: Int): List<NovelItem> = when (lang) {
        NovelLang.EN -> novelfullList("$NOVELFULL/most-popular?page=$page")
        NovelLang.ID -> madaraList("$MEI/novel/page/$page/?m_orderby=views")
    }

    suspend fun search(lang: NovelLang, query: String, page: Int): List<NovelItem> {
        val q = query.trim().replace(" ", "+")
        return when (lang) {
            NovelLang.EN -> novelfullList("$NOVELFULL/search?keyword=$q&page=$page")
            NovelLang.ID -> madaraList("$MEI/page/$page/?s=$q&post_type=wp-manga")
        }
    }

    suspend fun details(url: String): NovelDetail =
        if (url.contains("meionovels")) madaraDetails(url) else novelfullDetails(url)

    suspend fun chapterText(url: String): String =
        if (url.contains("meionovels")) madaraChapterText(url) else novelfullChapterText(url)

    // ---- novelfull (English) ----

    private val thumbSizeRegex = Regex("-[a-f0-9]{32}(\\.[A-Za-z0-9]+)$")
    private const val DETAIL_COVER_SIZE = "-2239c49aee6b961904acf173b7e4602a"

    private fun upscaleCover(url: String): String =
        if (url.isBlank()) "" else thumbSizeRegex.replace(url) { "$DETAIL_COVER_SIZE${it.groupValues[1]}" }

    private fun abs(url: String): String = when {
        url.isBlank() -> ""
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$NOVELFULL$url"
        else -> "$NOVELFULL/$url"
    }

    private suspend fun novelfullList(url: String): List<NovelItem> {
        return fetchDoc(url).select("div.row").mapNotNull { row ->
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

    private suspend fun novelfullDetails(url: String): NovelDetail {
        val doc = fetchDoc(url)
        val title = doc.selectFirst("h3.title")?.text()?.trim().orEmpty()
        val cover = abs(doc.selectFirst("div.book img")?.attr("src").orEmpty())
        val description = doc.selectFirst("div.desc-text")?.text()?.trim().orEmpty()
        val novelId = doc.selectFirst("[data-novel-id]")?.attr("data-novel-id").orEmpty()
        val chapters = if (novelId.isNotBlank()) {
            fetchDoc("$NOVELFULL/ajax/chapter-option?novelId=$novelId").select("option").mapNotNull { opt ->
                val href = opt.attr("value")
                if (href.isBlank()) null else NovelChapter(opt.text().trim(), abs(href))
            }
        } else {
            doc.select("#list-chapter a, ul.list-chapter li a").mapNotNull { a ->
                val href = a.attr("href")
                if (href.isBlank()) null else NovelChapter(a.attr("title").ifBlank { a.text() }.trim(), abs(href))
            }
        }
        return NovelDetail(title.ifBlank { "Novel" }, url, cover, description, chapters)
    }

    private suspend fun novelfullChapterText(url: String): String {
        val content = fetchDoc(url).selectFirst("#chapter-content")
            ?: return ""
        content.select("script, ins, style, [class*=ads], [id*=ad-], div[align=left]").remove()
        return contentToText(content)
    }

    // ---- meionovels (Indonesian, Madara theme) ----

    private suspend fun madaraList(url: String): List<NovelItem> {
        return fetchDoc(url).select("div.page-item-detail, div.c-tabs-item__content").mapNotNull { el ->
            val a = el.selectFirst("div.post-title a") ?: el.selectFirst("h3 a") ?: return@mapNotNull null
            val href = a.attr("abs:href")
            if (href.isBlank()) return@mapNotNull null
            NovelItem(title = a.text().trim(), url = href, coverUrl = madaraCover(el.selectFirst("img")))
        }
    }

    private fun madaraCover(img: Element?): String {
        img ?: return ""
        return img.attr("data-src").ifBlank { img.attr("src") }
            .ifBlank { img.attr("srcset").substringBefore(" ") }
    }

    private suspend fun madaraDetails(url: String): NovelDetail {
        val doc = fetchDoc(url)
        val title = doc.selectFirst("div.post-title h1, h1.entry-title")?.text()?.trim().orEmpty()
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: madaraCover(doc.selectFirst("div.summary_image img"))
        val description = doc.selectFirst("div.summary__content, div.description-summary")?.text()?.trim().orEmpty()
        val chaptersUrl = if (url.endsWith("/")) "${url}ajax/chapters/" else "$url/ajax/chapters/"
        val chapters = postDoc(chaptersUrl).select("li.wp-manga-chapter a").mapNotNull { a ->
            val href = a.attr("abs:href")
            if (href.isBlank()) null else NovelChapter(a.text().trim(), href)
        }.reversed()
        return NovelDetail(title.ifBlank { "Novel" }, url, cover, description, chapters)
    }

    private suspend fun madaraChapterText(url: String): String {
        val content = fetchDoc(url).selectFirst("div.reading-content div.text-left")
            ?: fetchDoc(url).selectFirst("div.reading-content")
            ?: return ""
        content.select("script, ins, style, .adsbygoogle, [class*=ads], [class*=code-block]").remove()
        return contentToText(content)
    }

    private fun contentToText(content: Element): String {
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
