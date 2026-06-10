package eu.kanade.tachiyomi.ui.novel

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelReaderScreen(
    private val novelUrl: String,
    private val novelTitle: String,
    private val novelCover: String,
    private val chapters: List<NovelChapter>,
    private val startIndex: Int,
) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            NovelReaderScreenModel(novelUrl, novelTitle, novelCover, chapters, startIndex)
        }
        val state by screenModel.state.collectAsState()
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(state.index) {
            scope.launch { scrollState.scrollTo(0) }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = chapters.getOrNull(state.index)?.name ?: novelTitle,
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
            bottomBar = {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = screenModel::previous,
                            enabled = state.index > 0 && !state.isLoading,
                        ) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { screenModel.changeFontSize(-1) }) { Text("A-") }
                            Text("${state.fontSize}", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { screenModel.changeFontSize(+1) }) { Text("A+") }
                        }
                        IconButton(
                            onClick = screenModel::next,
                            enabled = state.index < chapters.lastIndex && !state.isLoading,
                        ) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next")
                        }
                    }
                }
            },
        ) { contentPadding ->
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(AYMR.strings.label_novel_load_failed)) }
                else -> Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = state.fontSize.sp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(contentPadding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

class NovelReaderScreenModel(
    private val novelUrl: String,
    private val novelTitle: String,
    private val novelCover: String,
    private val chapters: List<NovelChapter>,
    startIndex: Int,
) : StateScreenModel<NovelReaderScreenModel.State>(
    State(index = startIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))),
) {

    private val app: Application = Injekt.get()

    init {
        mutableState.update { it.copy(fontSize = NovelStore.fontSize().get()) }
        loadChapter(state.value.index)
    }

    fun next() {
        if (state.value.index < chapters.lastIndex) loadChapter(state.value.index + 1)
    }

    fun previous() {
        if (state.value.index > 0) loadChapter(state.value.index - 1)
    }

    fun changeFontSize(delta: Int) {
        val newSize = (state.value.fontSize + delta).coerceIn(12, 32)
        NovelStore.fontSize().set(newSize)
        mutableState.update { it.copy(fontSize = newSize) }
    }

    private fun loadChapter(index: Int) {
        val chapter = chapters.getOrNull(index) ?: return
        screenModelScope.launchIO {
            mutableState.update { it.copy(index = index, isLoading = true, error = null, text = "") }
            try {
                val text = NovelDownloader.localChapterText(app, novelUrl, index)
                    ?: NovelSource.chapterText(chapter.url)
                NovelStore.setLastReadChapter(novelUrl, chapter.url)
                NovelStore.recordHistory(
                    NovelItem(title = novelTitle, url = novelUrl, coverUrl = novelCover),
                    chapter.url,
                    chapter.name,
                )
                mutableState.update { it.copy(isLoading = false, text = text) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                mutableState.update { it.copy(isLoading = false, error = e.message ?: "error") }
            }
        }
    }

    data class State(
        val index: Int = 0,
        val text: String = "",
        val fontSize: Int = 18,
        val isLoading: Boolean = true,
        val error: String? = null,
    )
}
