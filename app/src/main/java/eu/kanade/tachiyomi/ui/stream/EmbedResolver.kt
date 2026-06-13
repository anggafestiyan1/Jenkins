package eu.kanade.tachiyomi.ui.stream

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.withIOContext

/**
 * Resolves a video host "embed" page into direct, playable URLs. Generic regex extraction handles
 * most JW-Player-style hosts; doodstream needs its pass_md5 token dance. Best-effort — hosts change
 * their markup periodically, so this is the most likely place to need on-device tuning.
 */
object EmbedResolver {

    suspend fun resolve(url: String): List<StreamVideo> = withIOContext { resolve(url, 0) }

    private suspend fun resolve(url: String, depth: Int): List<StreamVideo> {
        val host = url.toHttpUrlOrNull()?.host.orEmpty()
        return when {
            host.isBlank() -> emptyList()
            host.contains("dood") || host.contains("d000d") || host.contains("dooood") -> doodstream(url)
            else -> generic(url, depth)
        }
    }

    /** URL fragments that indicate a nested video-host iframe worth following. */
    private val hostHints = listOf(
        "dood", "mixdrop", "filemoon", "streamtape", "vidhide", "streamwish", "vidguard",
        "mp4upload", "filelions", "streamhub", "/embed", "/e/", "player",
    )

    private fun headersFor(url: String): Headers {
        val origin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: url
        return Headers.headersOf("Referer", origin, "Origin", origin)
    }

    private fun hostLabel(url: String): String = url.toHttpUrlOrNull()?.host.orEmpty().removePrefix("www.")

    private suspend fun generic(url: String, depth: Int): List<StreamVideo> {
        val html = StreamHttp.client.newCall(GET(url, headersFor(url))).awaitSuccess().body.string()
        val found = LinkedHashSet<String>()

        // "file":"https://...m3u8" / file: "https://...mp4"
        Regex(""""?file"?\s*:\s*"([^"]+\.(?:m3u8|mp4)[^"]*)"""").findAll(html).forEach {
            found.add(it.groupValues[1].replace("\\/", "/"))
        }
        // any bare media url in the page
        Regex("""https?:\\?/\\?/[^"'\s\\]+\.(?:m3u8|mp4)[^"'\s\\]*""").findAll(html).forEach {
            found.add(it.value.replace("\\/", "/"))
        }

        if (found.isNotEmpty()) {
            val label = hostLabel(url)
            return found.map { StreamVideo(label = label, url = it, isHls = it.contains(".m3u8")) }
        }

        // No media on this page — follow one level of nested video-host iframes/URLs.
        if (depth < 2) {
            val nested = LinkedHashSet<String>()
            Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html).forEach {
                var u = it.groupValues[1]
                if (u.startsWith("//")) u = "https:$u"
                if (u.startsWith("http") && hostHints.any { h -> u.contains(h, true) }) nested.add(u)
            }
            val out = ArrayList<StreamVideo>()
            for (n in nested) runCatching { out += resolve(n, depth + 1) }
            return out
        }
        return emptyList()
    }

    private suspend fun doodstream(url: String): List<StreamVideo> {
        val origin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: return emptyList()
        val html = StreamHttp.client.newCall(GET(url, headersFor(url))).awaitSuccess().body.string()
        val md5Path = Regex("/pass_md5/[^'\"]+").find(html)?.value ?: return emptyList()
        val token = md5Path.substringAfterLast("/")
        val base = StreamHttp.client.newCall(
            GET(origin + md5Path, Headers.headersOf("Referer", url)),
        ).awaitSuccess().body.string()
        if (base.isBlank()) return emptyList()

        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = (1..10).map { chars.random() }.joinToString("")
        val expiry = System.currentTimeMillis()
        val video = "$base$random?token=$token&expiry=$expiry"
        return listOf(
            StreamVideo(
                label = "doodstream",
                url = video,
                isHls = false,
                headers = mapOf("Referer" to origin),
            ),
        )
    }
}
