package eu.kanade.tachiyomi.ui.novel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelDetailScreen(
    private val novelUrl: String,
    private val fallbackTitle: String,
    private val fallbackCover: String,
    private val autoResume: Boolean = false,
) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { NovelDetailScreenModel(novelUrl) }
        val state by screenModel.state.collectAsState()
        var showDownloadDialog by remember { mutableStateOf(false) }

        var resumed by remember { mutableStateOf(false) }
        LaunchedEffect(state) {
            val s = state
            if (autoResume && !resumed && s is NovelDetailScreenModel.State.Success && s.detail.chapters.isNotEmpty()) {
                resumed = true
                val lastUrl = screenModel.lastReadChapterUrl()
                val index = s.detail.chapters.indexOfFirst { it.url == lastUrl }.takeIf { it >= 0 } ?: 0
                navigator.push(
                    NovelReaderScreen(s.detail.url, s.detail.title, s.detail.coverUrl, s.detail.chapters, index),
                )
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = (state as? NovelDetailScreenModel.State.Success)?.detail?.title ?: fallbackTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { contentPadding ->
            when (val s = state) {
                is NovelDetailScreenModel.State.Loading -> {
                    Column(Modifier.fillMaxSize().padding(contentPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(48.dp))
                        CircularProgressIndicator()
                    }
                }
                is NovelDetailScreenModel.State.Error -> {
                    Column(Modifier.fillMaxSize().padding(contentPadding).padding(24.dp)) {
                        Text(stringResource(AYMR.strings.label_novel_load_failed))
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = screenModel::load) {
                            Text(stringResource(AYMR.strings.label_novel_retry))
                        }
                    }
                }
                is NovelDetailScreenModel.State.Success -> {
                    val detail = s.detail
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                        item {
                            Column(Modifier.padding(16.dp)) {
                                Row {
                                    AsyncImage(
                                        model = detail.coverUrl.ifBlank { fallbackCover },
                                        contentDescription = detail.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(110.dp, 160.dp).clip(RoundedCornerShape(6.dp)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(detail.title, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(AYMR.strings.label_novel_chapter_count, detail.chapters.size),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val lastUrl = screenModel.lastReadChapterUrl()
                                            val index = detail.chapters.indexOfFirst { it.url == lastUrl }
                                                .takeIf { it >= 0 } ?: 0
                                            if (detail.chapters.isNotEmpty()) {
                                                navigator.push(
                                                    NovelReaderScreen(
                                                        detail.url, detail.title, detail.coverUrl, detail.chapters, index,
                                                    ),
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                                        Text(stringResource(AYMR.strings.label_novel_continue))
                                    }
                                    OutlinedButton(onClick = screenModel::toggleSave) {
                                        Icon(
                                            imageVector = if (s.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = null,
                                        )
                                        Text(
                                            if (s.saved) {
                                                stringResource(AYMR.strings.label_novel_saved)
                                            } else {
                                                stringResource(AYMR.strings.label_novel_save)
                                            },
                                        )
                                    }
                                    OutlinedButton(onClick = { showDownloadDialog = true }) {
                                        Icon(
                                            imageVector = if (s.isDownloaded) Icons.Outlined.DownloadDone else Icons.Outlined.Download,
                                            contentDescription = null,
                                        )
                                        Text(stringResource(AYMR.strings.label_novel_downloaded))
                                    }
                                }
                                if (detail.description.isNotBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(detail.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            HorizontalDivider()
                        }
                        items(detail.chapters, key = { it.url }) { chapter ->
                            Text(
                                text = chapter.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val index = detail.chapters.indexOf(chapter)
                                        navigator.push(
                                            NovelReaderScreen(
                                                detail.url, detail.title, detail.coverUrl, detail.chapters, index,
                                            ),
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }

                    if (showDownloadDialog) {
                        DownloadRangeDialog(
                            total = detail.chapters.size,
                            onDismiss = { showDownloadDialog = false },
                            onConfirm = { from, to ->
                                NovelDownloadQueue.enqueue(detail, (from - 1..to - 1).toList())
                                context.toast("Ditambahkan ke antrian unduhan")
                                showDownloadDialog = false
                            },
                            onAll = {
                                NovelDownloadQueue.enqueue(detail, detail.chapters.indices.toList())
                                context.toast("Mengunduh semua chapter…")
                                showDownloadDialog = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRangeDialog(
    total: Int,
    onDismiss: () -> Unit,
    onConfirm: (from: Int, to: Int) -> Unit,
    onAll: () -> Unit,
) {
    var fromText by remember { mutableStateOf("1") }
    var toText by remember { mutableStateOf(total.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unduh chapter") },
        text = {
            Column {
                Text("Total $total chapter")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { fromText = it.filter(Char::isDigit) },
                        label = { Text("Dari") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { toText = it.filter(Char::isDigit) },
                        label = { Text("Sampai") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val from = (fromText.toIntOrNull() ?: 1).coerceIn(1, total)
                val to = (toText.toIntOrNull() ?: total).coerceIn(from, total)
                onConfirm(from, to)
            }) { Text("Unduh range") }
        },
        dismissButton = {
            TextButton(onClick = onAll) { Text("Semua") }
        },
    )
}

class NovelDetailScreenModel(
    private val novelUrl: String,
) : StateScreenModel<NovelDetailScreenModel.State>(State.Loading) {

    private val app: android.app.Application = Injekt.get()

    init {
        load()
    }

    fun load() {
        mutableState.update { State.Loading }
        screenModelScope.launchIO {
            try {
                val detail = NovelSource.details(novelUrl)
                mutableState.update {
                    State.Success(
                        detail = detail,
                        saved = NovelStore.isSaved(novelUrl),
                        isDownloaded = NovelDownloader.isNovelDownloaded(app, novelUrl),
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { State.Error }
            }
        }
    }

    fun toggleSave() {
        val current = state.value as? State.Success ?: return
        val detail = current.detail
        val nowSaved = NovelStore.toggleSaved(
            NovelItem(title = detail.title, url = detail.url, coverUrl = detail.coverUrl),
        )
        mutableState.update { (it as? State.Success)?.copy(saved = nowSaved) ?: it }
    }

    fun lastReadChapterUrl(): String? = NovelStore.getLastReadChapter(novelUrl)

    sealed interface State {
        data object Loading : State
        data object Error : State
        data class Success(
            val detail: NovelDetail,
            val saved: Boolean,
            val isDownloaded: Boolean = false,
        ) : State
    }
}
