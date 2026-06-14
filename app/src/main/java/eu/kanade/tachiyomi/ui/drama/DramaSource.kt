package eu.kanade.tachiyomi.ui.drama

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.ui.stream.StreamHttp
import eu.kanade.tachiyomi.util.asJsoup
import org.jsoup.nodes.Document
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

/** A short-drama series. */
data class DramaItem(
    val sourceId: String,
    val url: String,
    val title: String,
    val posterUrl: String,
) {
    val key: String get() = "$sourceId|$url"
}

/** One episode (micro-drama episodes are short and numbered). */
data class DramaEpisode(
    val number: Int,
    val url: String,
)

/**
 * A switchable micro-drama source. Playback is handled by loading the source's own watch page in a
 * WebView, so a source only needs to provide its listing + per-episode watch URLs.
 */
interface DramaSource {
    val id: String
    val name: String

    /** label -> slug ("" = all). Empty list hides the genre filter. */
    val genres: List<Pair<String, String>>

    suspend fun popular(): List<DramaItem>
    suspend fun byGenre(slug: String): List<DramaItem>
    suspend fun episodes(item: DramaItem): List<DramaEpisode>
}

/** Registry of switchable drama sources (dropdown, like Stream). */
object DramaSources {
    val all: List<DramaSource> by lazy { listOf(DracinemaSource, JagoDramaSource) }
    fun byId(id: String?): DramaSource = all.firstOrNull { it.id == id } ?: all.first()
}

/** Persists the active drama source. */
object DramaPrefs {
    private val app: Application by injectLazy()
    private val sp by lazy { app.getSharedPreferences("drama_prefs", Context.MODE_PRIVATE) }
    fun activeSourceId(): String = sp.getString("active", null) ?: DramaSources.all.first().id
    fun setActiveSourceId(id: String) = sp.edit().putString("active", id).apply()
}

internal suspend fun dramaDoc(url: String): Document = withIOContext {
    StreamHttp.client.newCall(GET(url)).awaitSuccess().asJsoup()
}

internal suspend fun dramaText(url: String): String = withIOContext {
    StreamHttp.client.newCall(GET(url)).awaitSuccess().body.string()
}
