package eu.kanade.tachiyomi.ui.library.anime

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.animeUploadInnerTab(): TabContent {
    val screen = this
    return TabContent(
        titleRes = AYMR.strings.label_upload,
        searchEnabled = false,
        content = { contentPadding, snackbarHostState ->
            with(screen) {
                AnimeUploadContent(
                    contentPadding = contentPadding,
                    showMessage = { snackbarHostState.showSnackbar(it) },
                )
            }
        },
    )
}

@Composable
private fun Screen.AnimeUploadContent(
    contentPadding: PaddingValues,
    showMessage: suspend (String) -> Unit,
) {
    val context = LocalContext.current
    val screenModel = rememberScreenModel { AnimeUploadScreenModel() }
    val state by screenModel.state.collectAsState()

    val pickFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) screenModel.setPickedUris(uris)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val text = when (message) {
            is AnimeUploadScreenModel.Message.EmptyName -> "Nama folder belum diisi"
            is AnimeUploadScreenModel.Message.NoFiles -> "Belum ada file yang dipilih"
            is AnimeUploadScreenModel.Message.Success -> "Berhasil upload ke folder \"${message.folder}\""
            is AnimeUploadScreenModel.Message.Error -> "Gagal: ${message.reason}"
        }
        showMessage(text)
        screenModel.clearMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Upload film/anime dari penyimpanan. File akan disimpan ke dalam satu folder " +
                "dan otomatis muncul di tab Saved (autoplay lanjut ke file berikutnya dalam folder).",
        )

        OutlinedButton(
            onClick = { pickFiles.launch(arrayOf("video/*")) },
            enabled = !state.isUploading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Outlined.VideoLibrary, contentDescription = null)
            Text(
                text = if (state.pickedUris.isEmpty()) {
                    "Pilih video"
                } else {
                    "${state.pickedUris.size} file dipilih"
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        OutlinedTextField(
            value = state.folderName,
            onValueChange = screenModel::setFolderName,
            label = { Text("Nama folder") },
            singleLine = true,
            enabled = !state.isUploading,
            modifier = Modifier.fillMaxWidth(),
        )

        // Category picker (optional)
        var expanded by remember { mutableStateOf(false) }
        val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = !state.isUploading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Kategori: ${selectedCategory?.name ?: "Tanpa kategori"}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Tanpa kategori") },
                    onClick = {
                        screenModel.setSelectedCategory(null)
                        expanded = false
                    },
                )
                state.categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            screenModel.setSelectedCategory(category.id)
                            expanded = false
                        },
                    )
                }
            }
        }

        Button(
            onClick = { screenModel.upload(context) },
            enabled = !state.isUploading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(AYMR.strings.label_upload))
        }

        if (state.isUploading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
