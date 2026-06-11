package eu.kanade.tachiyomi.ui.library.anime

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.hippo.unifile.UniFile
import mihon.core.archive.archiveReader
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Screen model for the local Film/Anime "Upload" tab. It copies user-picked video files into the
 * local anime source directory (one folder per title), then indexes the folder into the database so
 * it shows up under "Saved" and is playable with auto-play-next.
 */
class AnimeUploadScreenModel(
    private val storageManager: StorageManager = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
) : StateScreenModel<AnimeUploadScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            getAnimeCategories.subscribe().collectLatest { categories ->
                // Drop the default/system category (id 0); it is used to mean "no category".
                mutableState.update { it.copy(categories = categories.filterNot { c -> c.id == 0L }) }
            }
        }
    }

    fun setFolderName(name: String) {
        mutableState.update { it.copy(folderName = name) }
    }

    fun setSelectedCategory(categoryId: Long?) {
        mutableState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun setPickedUris(uris: List<Uri>) {
        mutableState.update { it.copy(pickedUris = uris) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    /**
     * Copies the picked files into localanime/<folder>/ then indexes the folder into the library.
     */
    fun upload(context: Context) {
        val current = state.value
        val name = current.folderName.trim()
        val uris = current.pickedUris
        if (name.isEmpty()) {
            mutableState.update { it.copy(message = Message.EmptyName) }
            return
        }
        if (uris.isEmpty()) {
            mutableState.update { it.copy(message = Message.NoFiles) }
            return
        }
        if (current.isUploading) return

        screenModelScope.launchIO {
            mutableState.update { it.copy(isUploading = true, progress = 0f) }
            try {
                val baseDir = storageManager.getLocalAnimeSourceDirectory()
                    ?: throw IllegalStateException("Storage location belum diatur")
                val animeDir = baseDir.findFile(name)
                    ?: baseDir.createDirectory(name)
                    ?: throw IllegalStateException("Gagal membuat folder")

                uris.forEachIndexed { index, uri ->
                    val src = UniFile.fromUri(context, uri)
                    if (src != null) {
                        val srcName = src.name ?: "file_${index + 1}"
                        if (isArchive(srcName)) {
                            // RAR/ZIP/7z etc: extract its video entries into the same folder.
                            extractArchiveVideos(context, src, animeDir)
                        } else {
                            copyInto(src, srcName.ifBlank { "video_${index + 1}.mp4" }, animeDir)
                        }
                    }
                    mutableState.update { it.copy(progress = (index + 1f) / uris.size) }
                }

                // Index the folder into the database via the local anime source.
                val source = sourceManager.get(LocalAnimeSource.ID)
                    ?: throw IllegalStateException("Local source tidak tersedia")
                val sAnime = SAnime.create().apply {
                    this.url = name
                    this.title = name
                }
                val anime = networkToLocalAnime.await(sAnime.toDomainAnime(LocalAnimeSource.ID))
                updateAnime.awaitUpdateFavorite(anime.id, true)
                current.selectedCategoryId?.let { categoryId ->
                    setAnimeCategories.await(anime.id, listOf(categoryId))
                }
                val sourceEpisodes = source.getEpisodeList(anime.toSAnime())
                syncEpisodesWithSource.await(sourceEpisodes, anime, source, false)

                mutableState.update {
                    it.copy(
                        isUploading = false,
                        progress = 1f,
                        pickedUris = emptyList(),
                        folderName = "",
                        message = Message.Success(name),
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                mutableState.update {
                    it.copy(isUploading = false, message = Message.Error(e.message ?: "unknown"))
                }
            }
        }
    }

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    private fun isArchive(name: String) = ext(name) in ARCHIVE_EXTENSIONS
    private fun isVideo(name: String) = ext(name) in VIDEO_EXTENSIONS

    private fun copyInto(src: UniFile, fileName: String, dir: UniFile) {
        if (dir.findFile(fileName) != null) return
        val target = dir.createFile(fileName) ?: return
        src.openInputStream().use { input ->
            target.openOutputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun extractArchiveVideos(context: Context, src: UniFile, dir: UniFile) {
        val reader = src.archiveReader(context)
        try {
            val videoEntries = reader.useEntries { seq ->
                seq.filter { it.isFile && isVideo(it.name) }.map { it.name }.toList()
            }
            videoEntries.forEach { entryName ->
                val outName = entryName.substringAfterLast('/').substringAfterLast('\\')
                if (outName.isNotBlank() && dir.findFile(outName) == null) {
                    reader.getInputStream(entryName)?.use { input ->
                        dir.createFile(outName)?.openOutputStream()?.use { output -> input.copyTo(output) }
                    }
                }
            }
        } finally {
            reader.close()
        }
    }

    @Immutable
    data class State(
        val categories: List<Category> = emptyList(),
        val pickedUris: List<Uri> = emptyList(),
        val folderName: String = "",
        val selectedCategoryId: Long? = null,
        val isUploading: Boolean = false,
        val progress: Float = 0f,
        val message: Message? = null,
    )

    sealed interface Message {
        data object EmptyName : Message
        data object NoFiles : Message
        data class Success(val folder: String) : Message
        data class Error(val reason: String) : Message
    }

    companion object {
        private val VIDEO_EXTENSIONS =
            setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v", "ts", "mpg", "mpeg", "3gp")
        private val ARCHIVE_EXTENSIONS = setOf("rar", "zip", "cbz", "cbr", "7z", "tar")
    }
}
