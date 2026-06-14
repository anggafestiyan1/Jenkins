package eu.kanade.tachiyomi.ui.stream

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document
import tachiyomi.core.common.util.lang.withIOContext

/**
 * Drakorid — full-length Korean (+ Asian) dramas, sub Indonesia. Custom site (verified live):
 * cards link /nonton/<slug>/, categories /kategori/<slug>/<page> (paginated), watch page hosts a
 * /embed player + episode picker. Browse is scraped; playback loads the watch page in a WebView.
 */
object DrakoridSource : StreamSource {

    override val id = "drakorid"
    override val name = "Drakorid · Korea"
    private const val DEFAULT_BASE = "https://drakorid.co"

    override var baseUrl: String
        get() = StreamPrefs.baseUrl(id, DEFAULT_BASE)
        set(value) = StreamPrefs.setBaseUrl(id, value)

    private suspend fun doc(url: String): Document = withIOContext {
        StreamHttp.client.newCall(GET(url)).awaitSuccess().asJsoup()
    }

    override suspend fun popular(page: Int): List<StreamItem> =
        parseCards(doc("$baseUrl/kategori/drama-korea/$page"))

    // The site's search is JS/AJAX only — fall back to the Korean-drama listing.
    override suspend fun search(query: String, page: Int): List<StreamItem> = popular(page)

    override suspend fun byGenre(slug: String): List<StreamItem> =
        if (slug.isBlank()) popular(1) else runCatching { parseCards(doc("$baseUrl/kategori/$slug/1")) }.getOrElse { popular(1) }

    private fun parseCards(d: Document): List<StreamItem> {
        val seen = HashSet<String>()
        return d.select("a[href*=\"/nonton/\"]").mapNotNull { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val img = a.selectFirst("img") ?: return@mapNotNull null
            if (href.isBlank() || !seen.add(href)) return@mapNotNull null
            val poster = img.absUrl("src").ifBlank { img.attr("src") }
            val title = (
                a.attr("title").ifBlank {
                    a.parent()?.selectFirst("h5, h3, .title")
                        ?.let { it.attr("data-original-title").ifBlank { it.text() } }
                } ?: slugToTitle(href)
                ).trim()
            StreamItem(sourceId = id, url = href, title = title, posterUrl = poster, isSeries = true)
        }
    }

    private fun slugToTitle(url: String): String =
        url.trimEnd('/').substringAfterLast("/")
            .replace('-', ' ')
            .split(' ')
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }

    override suspend fun detail(item: StreamItem): StreamDetail {
        val synopsis = runCatching {
            doc(item.url).selectFirst("div.sinopsis, [itemprop=description], .entry-content p, .desc, .description")
                ?.text().orEmpty()
        }.getOrDefault("")
        // The watch page holds the player + its own episode picker; play it directly in the WebView.
        return StreamDetail(synopsis = synopsis, episodes = listOf(StreamEpisode("Tonton", item.url)))
    }

    override suspend fun playTargets(episode: StreamEpisode): List<String> = listOf(episode.url)

    override suspend fun videos(episode: StreamEpisode): List<StreamVideo> = emptyList()
}
