package eu.kanade.tachiyomi.ui.youtube

import android.app.Application
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * File-backed storage for downloaded (offline) YouTube videos, kept separate from the Film library.
 * Each video lives in app-internal storage at filesDir/youtube/<id>/ (video.<ext> + meta.txt), so it
 * is independent of the configurable Film/local-source storage and is removed on uninstall.
 */
object YtStore {

    private val app: Application by injectLazy()

    private fun root(): File = File(app.filesDir, "youtube").apply { mkdirs() }

    fun dirFor(id: String): File = File(root(), id)

    fun videoFile(id: String): File? =
        dirFor(id).listFiles()?.firstOrNull { it.isFile && it.name.startsWith("video.") }

    fun saveMeta(id: String, title: String, thumbnailUrl: String) {
        val dir = dirFor(id).apply { mkdirs() }
        val safeTitle = title.replace("\n", " ").replace("\r", " ")
        File(dir, "meta.txt").writeText("$safeTitle\n$thumbnailUrl")
    }

    fun listOffline(): List<YtOffline> {
        return root().listFiles()?.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val meta = File(dir, "meta.txt")
            if (!meta.exists()) return@mapNotNull null
            val video = dir.listFiles()?.firstOrNull { it.isFile && it.name.startsWith("video.") }
                ?: return@mapNotNull null
            val lines = meta.readLines()
            YtOffline(
                id = dir.name,
                title = lines.getOrElse(0) { dir.name },
                thumbnailUrl = lines.getOrElse(1) { "" },
                videoPath = video.absolutePath,
            )
        }?.sortedBy { it.title.lowercase() }.orEmpty()
    }

    fun delete(id: String) {
        dirFor(id).deleteRecursively()
    }
}

data class YtOffline(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoPath: String,
)
