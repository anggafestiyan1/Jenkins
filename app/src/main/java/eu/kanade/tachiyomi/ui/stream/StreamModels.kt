package eu.kanade.tachiyomi.ui.stream

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder

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
    suspend fun byGenre(genreSlug: String): List<StreamItem>
    suspend fun detail(item: StreamItem): StreamDetail

    /** Direct/playable video sources. MP4 → playable in VideoView + downloadable. */
    suspend fun videos(episode: StreamEpisode): List<StreamVideo>

    /** Embed/watch-page URLs to load in the WebView when [videos] can't resolve direct media. */
    suspend fun playTargets(episode: StreamEpisode): List<String>
}

/** Registry of the built-in streaming sources the user can switch between. */
object StreamSources {
    val all: List<StreamSource> by lazy { listOf(RebahinSource, Lk21Source) }
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
 * Unified scraper for the modern Next.js movie/series aggregators (verified against the live site:
 * cards link to /movies/<slug> & /tv/<slug>, the page __NEXT_DATA__ carries a "playbackUrl" direct
 * MP4 that needs a Referer). Falls back to a generic DooPlay/embed path so a classic WordPress
 * mirror set via the domain editor still has a chance.
 */
abstract class StreamSiteSource : StreamSource {

    protected abstract val defaultBaseUrl: String

    override var baseUrl: String
        get() = StreamPrefs.baseUrl(id, defaultBaseUrl)
        set(value) = StreamPrefs.setBaseUrl(id, value)

    private suspend fun doc(url: String): Document = withIOContext {
        StreamHttp.client.newCall(GET(url)).awaitSuccess().asJsoup()
    }

    private suspend fun text(url: String): String = withIOContext {
        StreamHttp.client.newCall(GET(url)).awaitSuccess().body.string()
    }

    private fun abs(href: String): String =
        if (href.startsWith("http")) href else baseUrl.trimEnd('/') + "/" + href.trimStart('/')

    private fun unescape(s: String): String =
        s.replace("\\\"", "\"").replace("\\/", "/").replace("\\u0026", "&")

    private fun refererHeaders() = mapOf("Referer" to "$baseUrl/")

    // ---------------- Listing ----------------

    override suspend fun popular(page: Int): List<StreamItem> {
        if (page > 1) return emptyList()
        // Homepage carries a far richer mixed listing (movies + series) than /movies alone.
        val d = doc("$baseUrl/")
        return parseNextCards(d).ifEmpty {
            runCatching { parseNextCards(doc("$baseUrl/movies")) }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: parseGenericCards(d)
        }
    }

    override suspend fun byGenre(genreSlug: String): List<StreamItem> {
        val d = doc("$baseUrl/genre/$genreSlug")
        return parseNextCards(d).ifEmpty { parseGenericCards(d) }
    }

    override suspend fun search(query: String, page: Int): List<StreamItem> {
        if (page > 1) return emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        val d1 = runCatching { doc("$baseUrl/search?q=$q") }.getOrNull()
        val r1 = d1?.let { parseNextCards(it).ifEmpty { parseGenericCards(it) } }.orEmpty()
        if (r1.isNotEmpty()) return r1
        val d2 = runCatching { doc("$baseUrl/?s=$q") }.getOrNull() ?: return emptyList()
        return parseNextCards(d2).ifEmpty { parseGenericCards(d2) }
    }

    private val detailSlug = Regex("^/(movies|tv)/[a-z0-9-]+$")

    /** Next.js cards: <a href="/movies/slug"><img alt="Title" src="...tmdb..."></a> */
    private fun parseNextCards(d: Document): List<StreamItem> {
        return d.select("a[href*=\"/movies/\"], a[href*=\"/tv/\"]").mapNotNull { a ->
            val raw = a.attr("href")
            val path = raw.removePrefix(baseUrl).substringBefore("?").substringBefore("#")
            if (!detailSlug.matches(path)) return@mapNotNull null
            val img = a.selectFirst("img") ?: return@mapNotNull null
            val title = img.attr("alt").trim()
            if (title.isBlank()) return@mapNotNull null
            val poster = img.absUrl("src").ifBlank { img.attr("src") }
            StreamItem(
                sourceId = id,
                url = abs(raw),
                title = title,
                posterUrl = poster,
                isSeries = path.startsWith("/tv/"),
            )
        }.distinctBy { it.url }
    }

    // ---------------- Detail ----------------

    override suspend fun detail(item: StreamItem): StreamDetail {
        val raw = text(item.url)
        val html = unescape(raw)
        val synopsis = Regex(""""(?:description|overview|synopsis)"\s*:\s*"([^"]{0,800})"""")
            .find(html)?.groupValues?.get(1)?.trim().orEmpty()
        val isNext = html.contains("playbackUrl")
        val d = Jsoup.parse(raw, item.url)

        val episodes = when {
            item.isSeries -> {
                val eps = d.select("a[href*=\"/season-\"]").mapNotNull { a ->
                    val href = a.attr("href")
                    if (!href.contains("/episode-")) return@mapNotNull null
                    StreamEpisode(name = a.text().ifBlank { "Episode" }.trim(), url = abs(href))
                }.distinctBy { it.url }
                eps.ifEmpty { parseGenericEpisodes(d) }
            }
            isNext -> listOf(StreamEpisode(name = "Tonton", url = item.url))
            else -> parseGenericEpisodes(d).ifEmpty { listOf(StreamEpisode(name = "Tonton", url = item.url)) }
        }
        return StreamDetail(synopsis = synopsis, episodes = episodes)
    }

    private fun parseGenericEpisodes(d: Document): List<StreamEpisode> {
        val sel = "#seasons .episodios li a, .episodios li a, div.episode-list a, .serie a, " +
            ".eps a, .ep-item a, .eplister a, .les-content a"
        return d.select(sel).mapNotNull { a ->
            val href = a.absUrl("href").ifBlank { return@mapNotNull null }
            StreamEpisode(name = a.text().ifBlank { "Episode" }.trim(), url = href)
        }.distinctBy { it.url }.reversed()
    }

    // ---------------- Videos ----------------

    override suspend fun videos(episode: StreamEpisode): List<StreamVideo> {
        val html = unescape(text(episode.url))
        val ref = refererHeaders()

        // Next.js: paired "label":"...","playbackUrl":"...mp4"
        val pairs = Regex(""""label"\s*:\s*"([^"]+)"\s*,\s*"playbackUrl"\s*:\s*"([^"]+)"""")
            .findAll(html).toList()
        val direct = if (pairs.isNotEmpty()) {
            pairs.map {
                val u = it.groupValues[2].trim()
                StreamVideo(it.groupValues[1].trim(), u, u.contains(".m3u8"), ref)
            }
        } else {
            Regex(""""playbackUrl"\s*:\s*"([^"]+)"""").findAll(html).mapIndexed { i, m ->
                val u = m.groupValues[1].trim()
                StreamVideo("Server ${i + 1}", u, u.contains(".m3u8"), ref)
            }.toList()
        }
        if (direct.isNotEmpty()) return direct.distinctBy { it.url }

        // Fallback: DooPlay AJAX / embed hosts.
        val out = ArrayList<StreamVideo>()
        for (e in embedCandidates(episode)) {
            runCatching { out += EmbedResolver.resolve(e) }
        }
        return out.distinctBy { it.url }
    }

    override suspend fun playTargets(episode: StreamEpisode): List<String> {
        return embedCandidates(episode).ifEmpty { listOf(episode.url) }
    }

    // ---------------- Generic DooPlay / embed fallback ----------------

    private fun parseGenericCards(d: Document): List<StreamItem> {
        val byContainer = extract(
            d.select("div.search-item, article.mega-item, div.item-article, div.ml-item, div.grid-item, article"),
        )
        if (byContainer.isNotEmpty()) return byContainer
        return extract(d.select("a:has(img)"))
    }

    private fun extract(elements: Elements): List<StreamItem> {
        val seen = HashSet<String>()
        return elements.mapNotNull { el ->
            val a = if (el.tagName() == "a") el else el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (href.isBlank() || !href.startsWith("http") || !seen.add(href)) return@mapNotNull null
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
            StreamItem(id, href, title, poster, isSeries = href.contains("series", true) || href.contains("/tv", true))
        }
    }

    private val hostHints = listOf(
        "dood", "d000d", "dooood", "mixdrop", "filemoon", "streamtape", "vidhide", "streamwish",
        "vidguard", "mp4upload", "filelions", "streamhub", "wibufile", "abysscdn", "pixeldrain",
        "/embed", "/e/", "player", ".m3u8", ".mp4",
    )

    private fun looksLikeEmbed(url: String) = hostHints.any { url.contains(it, ignoreCase = true) }

    private fun decodeBase64Url(s: String): String? = runCatching {
        val decoded = String(android.util.Base64.decode(s, android.util.Base64.DEFAULT))
        if (decoded.startsWith("http")) decoded.trim() else null
    }.getOrNull()

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
                    .add("action", "doo_player_ajax").add("post", post).add("nume", nume).add("type", type)
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
        val rawUrl = Regex(""""embed_url"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)?.groupValues?.get(1)
            ?: return null
        val u = rawUrl.replace("\\/", "/").replace("\\\"", "\"").replace("\\u0026", "&")
        return if (u.contains("<iframe", ignoreCase = true)) {
            Regex("""src=["']([^"']+)["']""").find(u)?.groupValues?.get(1)
        } else {
            u.takeIf { it.startsWith("http") }
        }
    }

    private suspend fun embedCandidates(episode: StreamEpisode): List<String> {
        val d = doc(episode.url)
        val html = d.html()
        val urls = LinkedHashSet<String>()
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
        Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach {
            var u = it.groupValues[1]
            if (u.startsWith("//")) u = "https:$u"
            if (u.startsWith("http")) urls.add(u)
        }
        Regex("""https?://[^\s"'<>\\)]+""").findAll(html).forEach {
            val u = it.value.replace("\\/", "/")
            if (looksLikeEmbed(u)) urls.add(u)
        }
        return (dooplay + urls.sortedByDescending { looksLikeEmbed(it) }).distinct()
    }
}
