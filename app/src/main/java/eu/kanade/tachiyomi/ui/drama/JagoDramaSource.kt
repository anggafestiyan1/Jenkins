package eu.kanade.tachiyomi.ui.drama

/**
 * JagoDrama — micro-drama aggregator (DramaBox content: China + some Korea, sub Indo). Verified
 * live: cards /detail/<slug> with dramaboxdb posters, detail page shows "<N> Episode", watch page
 * /watch/<slug>/<n> plays in a WebView.
 */
object JagoDramaSource : DramaSource {

    override val id = "jagodrama"
    override val name = "JagoDrama · China/Korea"
    private const val BASE = "https://jagodrama.com"

    override val genres = emptyList<Pair<String, String>>()

    override suspend fun popular(): List<DramaItem> = parseCards()

    override suspend fun byGenre(slug: String): List<DramaItem> = parseCards()

    private suspend fun parseCards(): List<DramaItem> {
        val d = dramaDoc("$BASE/")
        val seen = HashSet<String>()
        return d.select("a[href*=\"/detail/\"]").mapNotNull { a ->
            val href = a.attr("href").substringBefore("?")
            if (!href.contains("/detail/") || !seen.add(href)) return@mapNotNull null
            val img = a.selectFirst("img") ?: return@mapNotNull null
            val title = img.attr("alt").trim()
            if (title.isBlank()) return@mapNotNull null
            val poster = listOf(
                img.absUrl("src"), img.attr("data-src"), img.attr("data-original"), img.attr("data-lazy-src"),
            ).firstOrNull { it.startsWith("http") && !it.contains("logo", true) } ?: img.absUrl("src")
            DramaItem(sourceId = id, url = if (href.startsWith("http")) href else BASE + href, title = title, posterUrl = poster)
        }.distinctBy { it.url }
    }

    override suspend fun episodes(item: DramaItem): List<DramaEpisode> {
        val html = dramaText(item.url)
        val total = Regex("(\\d{1,4})\\s*Episode").findAll(html)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull() ?: 1
        val slug = item.url.substringAfter("/detail/").substringBefore("?").substringBefore("/")
        return (1..total).map { DramaEpisode(number = it, url = "$BASE/watch/$slug/$it") }
    }
}
