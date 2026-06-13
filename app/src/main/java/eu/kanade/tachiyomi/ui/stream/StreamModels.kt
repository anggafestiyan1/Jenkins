package eu.kanade.tachiyomi.ui.stream

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

/** A movie/series entry from a streaming source. */
data class StreamItem(
    val sourceId: String,
    val url: String,
    val title: String,
    val posterUrl: String,
    val year: String = "",
    val isSeries: Boolean = false,
) {
    /** Stable identity across sources (used for favorites/history/offline keys). */
    val key: String get() = "$sourceId|$url"
}

/** A single playable unit — a movie is one "episode", a series has many. */
data class StreamEpisode(
    val name: String,
    val url: String,
)

data class StreamDetail(
    val synopsis: String,
    val episodes: List<StreamEpisode>,
)

/** A resolved, playable video URL. */
data class StreamVideo(
    val label: String,
    val url: String,
    val isHls: Boolean,
    val headers: Map<String, String> = emptyMap(),
)

interface StreamSource {
    val id: String
    val name: String
    var baseUrl: String

    suspend fun popular(page: Int): List<StreamItem>
    suspend fun search(query: String, page: Int): List<StreamItem>
    suspend fun detail(item: StreamItem): StreamDetail
    suspend fun videos(episode: StreamEpisode): List<StreamVideo>
}

/** Registry of the built-in streaming sources the user can switch between. */
object StreamSources {
    val all: List<StreamSource> by lazy { listOf(Lk21Source, RebahinSource) }
    fun byId(id: String?): StreamSource = all.firstOrNull { it.id == id } ?: all.first()
}

/** Persists the active source + a per-source base-URL override (domains rotate frequently). */
object StreamPrefs {
    private val app: Application by injectLazy()
    private val sp by lazy { app.getSharedPreferences("stream_prefs", Context.MODE_PRIVATE) }

    fun activeSourceId(): String = sp.getString("active_source", null) ?: StreamSources.all.first().id
    fun setActiveSourceId(id: String) = sp.edit().putString("active_source", id).apply()

    fun baseUrl(sourceId: String, default: String): String = sp.getString("base_$sourceId", null) ?: default
    fun setBaseUrl(sourceId: String, url: String) =
        sp.edit().putString("base_$sourceId", url.trim().trimEnd('/')).apply()
}

/**
 * Shared HTML-scraping base. Concrete sources only provide URLs + list selector; detail/videos
 * extraction is generic (synopsis + iframe/embed collection) so it survives minor theme changes.
 * Selectors are best-effort and may need tuning per live site.
 */
abstract class BaseStreamSource : StreamSource {

    protected abstract val defaultBaseUrl: String

    override var baseUrl: String
        get() = StreamPrefs.baseUrl(id, defaultBaseUrl)
        set(value) = StreamPrefs.setBaseUrl(id, value)

    protected suspend fun doc(url: String): Document = withIOContext {
        StreamHttp.client.newCall(GET(url)).awaitSuccess().asJsoup()
    }

    /** Containers that each wrap one result card. Override per source. */
    protected open fun listSelector(): String =
        "div.search-item, article.mega-item, div.item-article, div.ml-item, div.grid-item, article"

    protected open fun episodeSelector(): String =
        "div.episode-list a, .serie a, #episode a, .episodios a, ul.episodios li a, .eps a"

    protected fun parseCards(doc: Document): List<StreamItem> {
        val byContainer = extract(doc.select(listSelector()))
        if (byContainer.isNotEmpty()) return byContainer
        // Fallback: generic — any anchor that wraps a poster image (resilient to theme changes).
        return extract(doc.select("a:has(img)"))
    }

    private fun extract(elements: Elements): List<StreamItem> {
        val seen = HashSet<String>()
        return elements.mapNotNull { el ->
            val a = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (href.isBlank() || !href.startsWith("http") || !seen.add(href)) return@mapNotNull null
            // Skip obvious navigation/taxonomy links.
            if (href.contains("/genre") || href.contains("/country") || href.contains("/year") ||
                href.contains("/networks") || href.contains("javascript") || href.endsWith("#")
            ) {
                return@mapNotNull null
            }
            val img = el.selectFirst("img")
            val poster = (
                img?.absUrl("src")?.ifBlank { img.absUrl("data-src") }?.ifBlank { img.absUrl("data-original") }
                ).orEmpty()
            val title = (
                el.selectFirst("h2, h3, .title, .entry-title, .grid-title, .ml-title")?.text()
                    ?: a.attr("title").ifBlank { img?.attr("alt").orEmpty() }
                ).trim()
            if (title.isBlank() || poster.isBlank()) return@mapNotNull null
            StreamItem(
                sourceId = id,
                url = href,
                title = title,
                posterUrl = poster,
                isSeries = href.contains("series", true) || href.contains("/tv", true),
            )
        }
    }

    override suspend fun detail(item: StreamItem): StreamDetail {
        val d = doc(item.url)
        val synopsis = d.selectFirst(
            "div[itemprop=description], .entry-content p, .desc, .description, blockquote",
        )?.text().orEmpty()

        val epLinks = d.select(episodeSelector())
        val episodes = if (epLinks.isNotEmpty()) {
            epLinks.mapNotNull { a ->
                val href = a.absUrl("href").ifBlank { return@mapNotNull null }
                StreamEpisode(name = a.text().ifBlank { "Episode" }, url = href)
            }.reversed()
        } else {
            listOf(StreamEpisode(name = "Tonton", url = item.url))
        }
        return StreamDetail(synopsis = synopsis, episodes = episodes)
    }

    override suspend fun videos(episode: StreamEpisode): List<StreamVideo> {
        val d = doc(episode.url)
        val embeds = LinkedHashSet<String>()
        d.select("iframe[src]").forEach { embeds.add(it.absUrl("src")) }
        d.select("[data-frame], [data-src], [data-video], [data-player]").forEach { el ->
            for (attr in listOf("data-frame", "data-src", "data-video", "data-player")) {
                val v = el.attr(attr)
                if (v.startsWith("http")) embeds.add(v)
            }
        }
        val out = ArrayList<StreamVideo>()
        for (e in embeds) {
            runCatching { out += EmbedResolver.resolve(e) }
        }
        return out.distinctBy { it.url }
    }
}
