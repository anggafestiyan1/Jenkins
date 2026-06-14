package eu.kanade.tachiyomi.ui.drama

import org.jsoup.nodes.Document

/**
 * Dracinema — Next.js micro-drama aggregator (China, sub Indo hardsub). Verified live: cards
 * /movie/<key>, episodes /play/<key>/<n>, watch page plays HLS in a WebView. Full catalog sits
 * behind a 403 API, so "Semua" merges every server-rendered listing for the widest set.
 */
object DracinemaSource : DramaSource {

    override val id = "dracinema"
    override val name = "Dracinema · China"
    private const val BASE = "https://www.dracinema.com"

    override val genres = listOf(
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

    override suspend fun popular(): List<DramaItem> {
        val urls = listOf("$BASE/", "$BASE/collections") +
            genres.mapNotNull { (_, slug) -> slug.takeIf { it.isNotBlank() }?.let { "$BASE/genre/$it" } }
        val merged = LinkedHashMap<String, DramaItem>()
        for (u in urls) {
            runCatching { parseCards(dramaDoc(u)) }.getOrNull()?.forEach { merged.putIfAbsent(it.url, it) }
        }
        return merged.values.toList()
    }

    override suspend fun byGenre(slug: String): List<DramaItem> =
        if (slug.isBlank()) popular() else parseCards(dramaDoc("$BASE/genre/$slug"))

    private fun parseCards(d: Document): List<DramaItem> {
        val seen = HashSet<String>()
        return d.select("a[href^=\"/movie/\"]").mapNotNull { a ->
            val href = a.attr("href")
            if (href.isBlank() || !seen.add(href)) return@mapNotNull null
            val img = a.selectFirst("img") ?: return@mapNotNull null
            val title = img.attr("alt").trim()
            if (title.isBlank()) return@mapNotNull null
            val poster = img.absUrl("src").ifBlank { img.attr("src") }
            DramaItem(sourceId = id, url = BASE + href, title = title, posterUrl = poster)
        }
    }

    override suspend fun episodes(item: DramaItem): List<DramaEpisode> {
        val d = dramaDoc(item.url)
        return d.select("a[href*=\"/play/\"]").mapNotNull { a ->
            val path = a.attr("href").substringBefore("?")
            val n = Regex("/play/.+/(\\d+)$").find(path)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            DramaEpisode(number = n, url = if (path.startsWith("http")) path else BASE + path)
        }.distinctBy { it.number }.sortedBy { it.number }
    }
}
