package eu.kanade.tachiyomi.ui.stream

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

/**
 * Resolves a video host "embed" page into direct, playable URLs. Generic regex extraction handles
 * most JW-Player-style hosts; doodstream needs its pass_md5 token dance. Best-effort — hosts change
 * their markup periodically, so this is the most likely place to need on-device tuning.
 */
object EmbedResolver {

    private val network: NetworkHelper by injectLazy()

    suspend fun resolve(url: String): List<StreamVideo> = withIOContext {
        val host = url.toHttpUrlOrNull()?.host.orEmpty()
        when {
            host.isBlank() -> emptyList()
            host.contains("dood") || host.contains("d000d") || host.contains("dooood") -> doodstream(url)
            else -> generic(url)
        }
    }

    private fun headersFor(url: String): Headers {
        val origin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: url
        return Headers.headersOf("Referer", origin, "Origin", origin)
    }

    private fun hostLabel(url: String): String = url.toHttpUrlOrNull()?.host.orEmpty().removePrefix("www.")

    private suspend fun generic(url: String): List<StreamVideo> {
        val html = network.client.newCall(GET(url, headersFor(url))).awaitSuccess().body.string()
        val found = LinkedHashSet<String>()

        // "file":"https://...m3u8" / file: "https://...mp4"
        Regex(""""?file"?\s*:\s*"([^"]+\.(?:m3u8|mp4)[^"]*)"""").findAll(html).forEach {
            found.add(it.groupValues[1].replace("\\/", "/"))
        }
        // any bare media url in the page
        Regex("""https?:\\?/\\?/[^"'\s\\]+\.(?:m3u8|mp4)[^"'\s\\]*""").findAll(html).forEach {
            found.add(it.value.replace("\\/", "/"))
        }

        val label = hostLabel(url)
        return found.map { StreamVideo(label = label, url = it, isHls = it.contains(".m3u8")) }
    }

    private suspend fun doodstream(url: String): List<StreamVideo> {
        val origin = url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: return emptyList()
        val html = network.client.newCall(GET(url, headersFor(url))).awaitSuccess().body.string()
        val md5Path = Regex("/pass_md5/[^'\"]+").find(html)?.value ?: return emptyList()
        val token = md5Path.substringAfterLast("/")
        val base = network.client.newCall(
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
