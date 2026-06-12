package eu.kanade.tachiyomi.ui.youtube

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getAndSet
import uy.kohesive.injekt.injectLazy

/**
 * Tracks downloaded (offline) YouTube videos so the Offline tab can list and play them.
 * Each entry is encoded as animeId|episodeId|title|thumb (the video lives in the local film source).
 */
object YtStore {

    private val preferenceStore: PreferenceStore by injectLazy()
    private const val SEP = "|#|"

    private fun offlinePref() = preferenceStore.getStringSet("yt_offline")

    fun getOffline(): List<YtOffline> = offlinePref().get()
        .mapNotNull { decode(it) }
        .sortedBy { it.title.lowercase() }

    fun addOffline(entry: YtOffline) {
        offlinePref().getAndSet { current ->
            val without = current.filterNot { decode(it)?.animeId == entry.animeId }.toSet()
            without + encode(entry)
        }
    }

    fun removeOffline(animeId: Long) {
        offlinePref().getAndSet { current ->
            current.filterNot { decode(it)?.animeId == animeId }.toSet()
        }
    }

    private fun encode(e: YtOffline): String =
        listOf(e.animeId.toString(), e.episodeId.toString(), e.title, e.thumbnailUrl).joinToString(SEP)

    private fun decode(raw: String): YtOffline? {
        val parts = raw.split(SEP)
        if (parts.size < 4) return null
        val animeId = parts[0].toLongOrNull() ?: return null
        val episodeId = parts[1].toLongOrNull() ?: return null
        return YtOffline(animeId, episodeId, parts[2], parts[3])
    }
}

data class YtOffline(
    val animeId: Long,
    val episodeId: Long,
    val title: String,
    val thumbnailUrl: String,
)
