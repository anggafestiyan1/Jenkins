package eu.kanade.tachiyomi.ui.stream

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
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

    /** Candidate web-player/embed page URLs to load in the WebView for streaming. */
    suspend fun playTargets(episode: StreamEpisode): List<String>

    /** Resolved direct media URLs for offline download (best-effort, MP4 only is downloadable). */
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
        "#seasons .episodios li a, .episodios li a, div.episode-list a, .serie a, #episode a, " +
            ".eps a, .ep-item a, .eplister a, .les-content a"

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
            // Movie: the player lives on the detail page itself (DooPlay-style server tabs/iframes);
            // following the on-page "Nonton" button tends to hit promo/interstitial redirects.
            listOf(StreamEpisode(name = "Tonton", url = item.url))
        }
        return StreamDetail(synopsis = synopsis, episodes = episodes)
    }

    /** Hints that a URL is a video host/embed (not an ad or page chrome). */
    private val hostHints = listOf(
        "dood", "d000d", "dooood", "mixdrop", "filemoon", "streamtape", "vidhide", "streamwish",
        "vidguard", "mp4upload", "filelions", "streamhub", "wibufile", "abysscdn", "pixeldrain",
        "/embed", "/e/", "player", ".m3u8", ".mp4", "gdriveplayer", "hxfile", "lbx",
    )

    private fun looksLikeEmbed(url: String) = hostHints.any { url.contains(it, ignoreCase = true) }

    private fun decodeBase64Url(s: String): String? = runCatching {
        val decoded = String(android.util.Base64.decode(s, android.util.Base64.DEFAULT))
        if (decoded.startsWith("http")) decoded.trim() else null
    }.getOrNull()

    /**
     * DooPlay theme (LK21/Rebahin): the player "server" tabs load their iframe via an AJAX call to
     * wp-admin/admin-ajax.php — the iframe is NOT in the static HTML. Resolve each option here.
     */
    private suspend fun dooplayEmbeds(d: Document, pageUrl: String): List<String> {
        val options = d.select("li.dooplay_player_option, .dooplay_player_option, #playeroptionsul li[data-post]")
        if (options.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        for (li in options) {
            val post = li.attr("data-post")
            if (post.isBlank()) continue
            val nume = li.attr("data-nume").ifBlank { "1" }
            val type = li.attr("data-type").ifBlank { "movie" }
            runCatching {
                val body = FormBody.Builder()
                    .add("action", "doo_player_ajax")
                    .add("post", post)
                    .add("nume", nume)
                    .add("type", type)
                    .build()
                val req = Request.Builder()
                    .url("$baseUrl/wp-admin/admin-ajax.php")
                    .post(body)
                    .header("Referer", pageUrl)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                val json = StreamHttp.client.newCall(req).awaitSuccess().body.string()
                extractEmbedUrl(json)?.let { out.add(it) }
            }
        }
        return out.toList()
    }

    private fun extractEmbedUrl(json: String): String? {
        val raw = Regex(""""embed_url"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)?.groupValues?.get(1)
            ?: return null
        val unescaped = raw.replace("\\/", "/").replace("\\\"", "\"").replace("\\u0026", "&")
        return if (unescaped.contains("<iframe", ignoreCase = true)) {
            Regex("""src=["']([^"']+)["']""").find(unescaped)?.groupValues?.get(1)
        } else {
            unescaped.takeIf { it.startsWith("http") }
        }
    }

    /** Collects every plausible embed/player URL from a watch page (DooPlay AJAX, lazy attrs, base64). */
    protected open suspend fun embedCandidates(episode: StreamEpisode): List<String> {
        val d = doc(episode.url)
        val html = d.html()
        val urls = LinkedHashSet<String>()

        // DooPlay AJAX servers — the real, reliable embeds. Kept first (highest priority).
        val dooplay = dooplayEmbeds(d, episode.url)

        val attrs = listOf(
            "src", "data-src", "data-frame", "data-video", "data-player", "data-embed",
            "data-litespeed-src", "data-lazy-src",
        )
        d.select("iframe, [data-frame], [data-src], [data-video], [data-player], [data-embed], [data-litespeed-src], [data-lazy-src]")
            .forEach { el ->
                for (attr in attrs) {
                    val v = el.attr(attr).trim()
                    when {
                        v.startsWith("http") -> urls.add(v)
                        v.startsWith("//") -> urls.add("https:$v")
                        v.length > 24 -> decodeBase64Url(v)?.let { urls.add(it) }
                    }
                }
            }

        // <iframe src> that Jsoup may have left inside scripts/comments.
        Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach {
            var u = it.groupValues[1]
            if (u.startsWith("//")) u = "https:$u"
            if (u.startsWith("http")) urls.add(u)
        }
        // Any bare URL that looks like a video host.
        Regex("""https?://[^\s"'<>\\)]+""").findAll(html).forEach {
            val u = it.value.replace("\\/", "/")
            if (looksLikeEmbed(u)) urls.add(u)
        }
        // Long base64 blobs in scripts that decode to a URL.
        Regex("""[A-Za-z0-9+/]{40,}={0,2}""").findAll(html).forEach { m ->
            decodeBase64Url(m.value)?.let { if (it.startsWith("http")) urls.add(it) }
        }

        // DooPlay embeds first (most reliable), then embed-looking URLs, then the rest.
        return (dooplay + urls.sortedByDescending { looksLikeEmbed(it) }).distinct()
    }

    override suspend fun playTargets(episode: StreamEpisode): List<String> {
        val candidates = embedCandidates(episode)
        // If nothing embed-like was found, fall back to the watch page itself (WebView still plays it).
        return candidates.ifEmpty { listOf(episode.url) }
    }

    override suspend fun videos(episode: StreamEpisode): List<StreamVideo> {
        val out = ArrayList<StreamVideo>()
        for (e in embedCandidates(episode)) {
            runCatching { out += EmbedResolver.resolve(e) }
        }
        return out.distinctBy { it.url }
    }
}
