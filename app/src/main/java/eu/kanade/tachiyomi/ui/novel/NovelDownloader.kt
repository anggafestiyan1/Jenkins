package eu.kanade.tachiyomi.ui.novel

import android.content.Context
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Simple offline download for novels: stores each chapter's text as a file under
 * filesDir/novel_dl/<hash>/. No background service — downloads run in the caller's coroutine.
 */
object NovelDownloader {

    private const val SEP = "|#|"

    private fun rootDir(context: Context): File =
        File(context.filesDir, "novel_dl").apply { mkdirs() }

    private fun novelDir(context: Context, novelUrl: String): File =
        File(rootDir(context), novelUrl.hashCode().toString())

    private fun metaFile(context: Context, novelUrl: String) = File(novelDir(context, novelUrl), "meta.txt")

    private fun chapterFile(context: Context, novelUrl: String, index: Int) =
        File(novelDir(context, novelUrl), "$index.txt")

    fun isNovelDownloaded(context: Context, novelUrl: String): Boolean =
        metaFile(context, novelUrl).exists()

    fun localChapterText(context: Context, novelUrl: String, index: Int): String? {
        val file = chapterFile(context, novelUrl, index)
        return if (file.exists()) file.readText() else null
    }

    fun downloadedCount(context: Context, novelUrl: String): Int {
        val dir = novelDir(context, novelUrl)
        if (!dir.exists()) return 0
        return dir.listFiles { f -> f.name.endsWith(".txt") && f.name != "meta.txt" }?.size ?: 0
    }

    /**
     * Downloads the given chapter [indices] (positions into [NovelDetail.chapters]). Skips chapters
     * already present. Calls [onProgress] with (completed, total) after each. Honors cancellation.
     */
    suspend fun downloadChapters(
        context: Context,
        detail: NovelDetail,
        indices: List<Int>,
        onProgress: (Int, Int) -> Unit,
    ) {
        val dir = novelDir(context, detail.url).apply { mkdirs() }
        metaFile(context, detail.url).writeText(
            listOf(detail.url, detail.title, detail.coverUrl).joinToString(SEP),
        )
        val total = indices.size
        indices.forEachIndexed { position, index ->
            coroutineContext.ensureActive()
            val chapter = detail.chapters.getOrNull(index)
            if (chapter != null) {
                val file = File(dir, "$index.txt")
                if (!file.exists()) {
                    val text = runCatching { NovelSource.chapterText(chapter.url) }.getOrDefault("")
                    if (text.isNotBlank()) file.writeText(text)
                }
            }
            onProgress(position + 1, total)
        }
    }

    suspend fun downloadAll(
        context: Context,
        detail: NovelDetail,
        onProgress: (Int, Int) -> Unit,
    ) = downloadChapters(context, detail, detail.chapters.indices.toList(), onProgress)

    /** Downloads a single chapter (by index into [NovelDetail.chapters]). Skips if already present. */
    suspend fun downloadOne(context: Context, detail: NovelDetail, index: Int): Boolean {
        val dir = novelDir(context, detail.url).apply { mkdirs() }
        val meta = metaFile(context, detail.url)
        if (!meta.exists()) {
            meta.writeText(listOf(detail.url, detail.title, detail.coverUrl).joinToString(SEP))
        }
        val chapter = detail.chapters.getOrNull(index) ?: return false
        val file = File(dir, "$index.txt")
        if (file.exists()) return true
        val text = runCatching { NovelSource.chapterText(chapter.url) }.getOrDefault("")
        return if (text.isNotBlank()) {
            file.writeText(text)
            true
        } else {
            false
        }
    }

    fun getDownloaded(context: Context): List<NovelItem> {
        val root = rootDir(context)
        return root.listFiles()?.mapNotNull { dir ->
            val meta = File(dir, "meta.txt")
            if (!meta.exists()) return@mapNotNull null
            val parts = meta.readText().split(SEP)
            if (parts.size < 2) return@mapNotNull null
            NovelItem(url = parts[0], title = parts[1], coverUrl = parts.getOrElse(2) { "" })
        }?.sortedBy { it.title.lowercase() }.orEmpty()
    }

    fun delete(context: Context, novelUrl: String) {
        novelDir(context, novelUrl).deleteRecursively()
    }
}
