package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FilmFolderManagerScreen : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { FilmFolderManagerScreenModel() }
        val state by screenModel.state.collectAsState()
        var renaming by remember { mutableStateOf<String?>(null) }
        var deleting by remember { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kelola folder") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize().padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                }
                state.folders.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                ) { Text("Belum ada folder film lokal.") }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                    items(state.folders, key = { it }) { folder ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                folder,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(onClick = { renaming = folder }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = { deleting = folder }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        renaming?.let { old ->
            var newName by remember(old) { mutableStateOf(old) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Ubah nama folder") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.rename(old, newName.trim())
                        renaming = null
                    }) { Text("Simpan") }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("Batal") } },
            )
        }

        deleting?.let { folder ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Hapus folder?") },
                text = { Text("Folder \"$folder\" beserta semua videonya akan dihapus permanen.") },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.delete(folder)
                        deleting = null
                    }) { Text("Hapus") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
            )
        }
    }
}

class FilmFolderManagerScreenModel(
    private val storageManager: StorageManager = Injekt.get(),
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
) : StateScreenModel<FilmFolderManagerScreenModel.State>(State()) {

    init {
        refresh()
    }

    fun refresh() {
        screenModelScope.launchIO {
            val baseDir = storageManager.getLocalAnimeSourceDirectory()
            val folders = baseDir?.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { it.name }
                ?.sortedBy { it.lowercase() }
                .orEmpty()
            mutableState.update { it.copy(folders = folders, isLoading = false) }
        }
    }

    fun rename(oldName: String, newName: String) {
        if (newName.isBlank() || newName == oldName) return
        screenModelScope.launchIO {
            try {
                val baseDir = storageManager.getLocalAnimeSourceDirectory() ?: return@launchIO
                val dir = baseDir.findFile(oldName) ?: return@launchIO
                dir.renameTo(newName)
                // Keep the library entry linked: update its url + title to the new folder name.
                getAnimeByUrlAndSourceId.await(oldName, LocalAnimeSource.ID)?.let { anime ->
                    animeRepository.updateAnime(AnimeUpdate(id = anime.id, url = newName, title = newName))
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
            refresh()
        }
    }

    fun delete(name: String) {
        screenModelScope.launchIO {
            try {
                val baseDir = storageManager.getLocalAnimeSourceDirectory() ?: return@launchIO
                baseDir.findFile(name)?.delete()
                // Remove it from the library (Saved) too.
                getAnimeByUrlAndSourceId.await(name, LocalAnimeSource.ID)?.let { anime ->
                    animeRepository.updateAnime(AnimeUpdate(id = anime.id, favorite = false))
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
            refresh()
        }
    }

    data class State(
        val folders: List<String> = emptyList(),
        val isLoading: Boolean = true,
    )
}
