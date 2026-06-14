package eu.kanade.tachiyomi.ui.drama

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.ui.stream.StreamHttp
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document
import tachiyomi.core.common.util.lang.withIOContext

/** A short-drama series. */
data class DramaItem(
    val url: String,
    val title: String,
    val posterUrl: String,
) {
    val key: String get() = url
}

/** One episode (micro-drama episodes are short and numbered). */
data class DramaEpisode(
    val number: Int,
    val url: String,
)

/**
 * Dracinema — viral micro-drama (短剧) aggregator, Indonesian hardsub (China-heavy, some Korea).
 * Next.js site: cards link /movie/<key>, episodes are /play/<key>/<n>, and each play page embeds a
 * signed HLS URL (cdn.dramabos.video/api/flickreels/hls?...). Verified live. Reuses [StreamHttp].
 */
object DramaSource {

    const val BASE = "https://www.dracinema.com"

    /** Indonesian genre slugs from the site (/genre/<slug>). */
    val GENRES = listOf(
        "Semua" to "",
        "Aksi" to "aksi",
        "Balas Dendam" to "balas-dendam",
        "CEO / Bos" to "ceo--bos",
        "Fantasi" to "fantasi",
        "Identitas Tersembunyi" to "identitas-tersembunyi",
        "Keluarga" to "keluarga",
        "Komedi" to "komedi",
        "Komedi Ringan" to "komedi-ringan",
    )

    private suspend fun doc(url: String): Document = withIOContext {
        StreamHttp.client.newCall(GET(url)).awaitSuccess().asJsoup()
    }

    private suspend fun text(url: String): String = withIOContext {
        StreamHttp.client.newCall(GET(url)).awaitSuccess().body.string()
    }

    suspend fun popular(): List<DramaItem> = parseCards(doc("$BASE/"))

    suspend fun byGenre(slug: String): List<DramaItem> =
        if (slug.isBlank()) popular() else parseCards(doc("$BASE/genre/$slug"))

    private fun parseCards(d: Document): List<DramaItem> {
        val seen = HashSet<String>()
        return d.select("a[href^=\"/movie/\"]").mapNotNull { a ->
            val href = a.attr("href")
            if (href.isBlank() || !seen.add(href)) return@mapNotNull null
            val img = a.selectFirst("img") ?: return@mapNotNull null
            val title = img.attr("alt").trim()
            if (title.isBlank()) return@mapNotNull null
            val poster = img.absUrl("src").ifBlank { img.attr("src") }
            DramaItem(url = BASE + href, title = title, posterUrl = poster)
        }
    }

    suspend fun episodes(item: DramaItem): List<DramaEpisode> {
        val d = doc(item.url)
        return d.select("a[href*=\"/play/\"]").mapNotNull { a ->
            val path = a.attr("href").substringBefore("?")
            val n = Regex("/play/.+/(\\d+)$").find(path)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            DramaEpisode(number = n, url = if (path.startsWith("http")) path else BASE + path)
        }.distinctBy { it.number }.sortedBy { it.number }
    }

    /** Resolves the signed HLS (.m3u8) URL embedded in a /play/<key>/<n> page. */
    suspend fun hlsUrl(episode: DramaEpisode): String? {
        val html = text(episode.url).replace("\\u0026", "&").replace("\\/", "/")
        return Regex("https://cdn\\.dramabos\\.video/api/flickreels/hls\\?[^\"'<> \\\\]+")
            .find(html)?.value
    }
}
