package eu.kanade.tachiyomi.ui.stream

import java.net.URLEncoder

/**
 * LK21 / LayarKaca21 family (film barat + series + drakor, sub Indonesia hardsub).
 * Domains rotate often — the base URL is user-editable in the Stream tab.
 */
object Lk21Source : BaseStreamSource() {

    override val id = "lk21"
    override val name = "LK21"
    override val defaultBaseUrl = "https://tv6.lk21official.cc"

    override fun listSelector(): String =
        "div.search-item, article.mega-item, div.item-article, div.grid-item, div.ml-item"

    override suspend fun popular(page: Int): List<StreamItem> {
        val url = if (page <= 1) "$baseUrl/populer/" else "$baseUrl/populer/page/$page/"
        return parseCards(doc(url))
    }

    override suspend fun search(query: String, page: Int): List<StreamItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        return parseCards(doc("$baseUrl/?s=$q"))
    }
}
