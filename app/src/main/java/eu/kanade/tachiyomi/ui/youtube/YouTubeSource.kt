package eu.kanade.tachiyomi.ui.youtube

import eu.kanade.tachiyomi.network.NetworkHelper
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin YouTube search/extraction wrapper over NewPipeExtractor. MVP: combined video+audio streams
 * (typically up to 720p), no muxing. Personal/offline use only.
 */
object YouTubeSource {

    private val network: NetworkHelper by injectLazy()
    private val initialized = AtomicBoolean(false)

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(NewPipeDownloader(network.client))
        }
    }

    private val service get() = ServiceList.YouTube

    suspend fun search(query: String): List<YtItem> = withIOContext {
        ensureInit()
        val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
        val info = SearchInfo.getInfo(service, handler)
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { item ->
                YtItem(
                    title = item.name.orEmpty(),
                    url = item.url.orEmpty(),
                    uploader = item.uploaderName.orEmpty(),
                    durationSeconds = item.duration,
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url.orEmpty(),
                )
            }
            .filter { it.url.isNotBlank() }
    }

    /** Returns the best combined (video+audio) stream <= ~720p, or null if none. */
    suspend fun getStream(url: String): YtStream? = withIOContext {
        ensureInit()
        val info = StreamInfo.getInfo(service, url)
        val best = info.videoStreams
            .filter { !it.content.isNullOrBlank() }
            .maxByOrNull { parseResolution(it.resolution) }
            ?: return@withIOContext null
        YtStream(
            title = info.name.orEmpty().ifBlank { "video" },
            streamUrl = best.content,
            thumbnailUrl = info.thumbnails.firstOrNull()?.url.orEmpty(),
            extension = best.format?.suffix ?: "mp4",
        )
    }

    private fun parseResolution(res: String?): Int =
        res?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
}

data class YtItem(
    val title: String,
    val url: String,
    val uploader: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
)

data class YtStream(
    val title: String,
    val streamUrl: String,
    val thumbnailUrl: String,
    val extension: String,
)
