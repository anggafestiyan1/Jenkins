package eu.kanade.tachiyomi.ui.stream

import java.net.URLEncoder

/**
 * Rebahin family (film + series + drakor, sub Indonesia). Domains rotate — base URL is editable.
 */
object RebahinSource : BaseStreamSource() {

    override val id = "rebahin"
    override val name = "Rebahin"
    override val defaultBaseUrl = "https://rebahinxxi.lol"

    override fun listSelector(): String =
        "div.ml-item, div.movie-item, article.mega-item, div.search-item, div.item"

    override suspend fun popular(page: Int): List<StreamItem> {
        val url = if (page <= 1) "$baseUrl/" else "$baseUrl/page/$page/"
        return parseCards(doc(url))
    }

    override suspend fun search(query: String, page: Int): List<StreamItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        return parseCards(doc("$baseUrl/?s=$q"))
    }
}
