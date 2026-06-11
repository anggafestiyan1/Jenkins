package eu.kanade.tachiyomi.ui.novel

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
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
        var showSettings by remember { mutableStateOf(false) }

        val (rawBg, rawText) = novelReaderColors(state.theme)
        val bgColor = if (rawBg == Color.Unspecified) MaterialTheme.colorScheme.background else rawBg
        val textColor = if (rawText == Color.Unspecified) MaterialTheme.colorScheme.onBackground else rawText

        LaunchedEffect(state.index) { scope.launch { scrollState.scrollTo(0) } }

        // Keep the screen awake while reading, if enabled.
        val view = LocalView.current
        DisposableEffect(state.keepScreenOn) {
            view.keepScreenOn = state.keepScreenOn
            onDispose { view.keepScreenOn = false }
        }

        // Text-to-speech (read aloud).
        val context = androidx.compose.ui.platform.LocalContext.current
        var isSpeaking by remember { mutableStateOf(false) }
        val tts = remember { NovelTts(context) { isSpeaking = false } }
        DisposableEffect(Unit) { onDispose { tts.shutdown() } }
        LaunchedEffect(state.index) {
            tts.stop()
            isSpeaking = false
        }

        Scaffold(
            containerColor = bgColor,
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
                    actions = {
                        IconButton(
                            enabled = !state.isLoading && state.text.isNotBlank(),
                            onClick = {
                                if (isSpeaking) {
                                    tts.stop()
                                    isSpeaking = false
                                } else {
                                    tts.configure(state.ttsSpeed, state.ttsPitch, state.ttsLang)
                                    tts.speak(state.text)
                                    isSpeaking = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Outlined.Stop else Icons.Outlined.VolumeUp,
                                contentDescription = "Read aloud",
                            )
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
                        ) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous") }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                        IconButton(
                            onClick = screenModel::next,
                            enabled = state.index < chapters.lastIndex && !state.isLoading,
                        ) { Icon(Icons.Outlined.ChevronRight, contentDescription = "Next") }
                    }
                }
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor).padding(contentPadding),
            ) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        text = stringResource(AYMR.strings.label_novel_load_failed),
                        color = textColor,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> Text(
                        text = state.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = state.fontSize.sp,
                            lineHeight = (state.fontSize * state.lineSpacing / 100f).sp,
                            fontFamily = novelReaderFontFamily(state.fontFamily),
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }

        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                ReaderSettings(state = state, model = screenModel)
            }
        }
    }
}

@Composable
private fun ReaderSettings(state: NovelReaderScreenModel.State, model: NovelReaderScreenModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tema", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Default", "Gelap", "Sepia", "Hitam").forEachIndexed { i, label ->
                FilterChip(selected = state.theme == i, onClick = { model.setTheme(i) }, label = { Text(label) })
            }
        }
        Text("Font", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Default", "Serif", "Sans").forEachIndexed { i, label ->
                FilterChip(selected = state.fontFamily == i, onClick = { model.setFontFamily(i) }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ukuran teks", modifier = Modifier.weight(1f))
            TextButton(onClick = { model.changeFontSize(-1) }) { Text("A-") }
            Text("${state.fontSize}")
            TextButton(onClick = { model.changeFontSize(+1) }) { Text("A+") }
        }
        Text("Jarak baris: ${state.lineSpacing}%", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = state.lineSpacing.toFloat(),
            onValueChange = { model.setLineSpacing(it.toInt()) },
            valueRange = 120f..240f,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Layar tetap nyala", modifier = Modifier.weight(1f))
            Switch(checked = state.keepScreenOn, onCheckedChange = { model.setKeepScreenOn(it) })
        }

        Text("Suara (TTS)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Default" to "", "English" to "en", "Indonesia" to "id").forEach { (label, tag) ->
                FilterChip(
                    selected = state.ttsLang == tag,
                    onClick = { model.setTtsLang(tag) },
                    label = { Text(label) },
                )
            }
        }
        Text("Kecepatan suara: ${state.ttsSpeed}%")
        Slider(
            value = state.ttsSpeed.toFloat(),
            onValueChange = { model.setTtsSpeed(it.toInt()) },
            valueRange = 50f..200f,
        )
        Text("Nada suara: ${state.ttsPitch}%")
        Slider(
            value = state.ttsPitch.toFloat(),
            onValueChange = { model.setTtsPitch(it.toInt()) },
            valueRange = 50f..200f,
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun novelReaderColors(theme: Int): Pair<Color, Color> = when (theme) {
    1 -> Color(0xFF121212) to Color(0xFFE0E0E0)
    2 -> Color(0xFFFBF0D9) to Color(0xFF5B4636)
    3 -> Color(0xFF000000) to Color(0xFFCFCFCF)
    else -> Color.Unspecified to Color.Unspecified
}

private fun novelReaderFontFamily(family: Int): FontFamily = when (family) {
    1 -> FontFamily.Serif
    2 -> FontFamily.SansSerif
    else -> FontFamily.Default
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
        mutableState.update {
            it.copy(
                fontSize = NovelStore.fontSize().get(),
                theme = NovelStore.readerTheme().get(),
                lineSpacing = NovelStore.lineSpacingPercent().get(),
                fontFamily = NovelStore.fontFamily().get(),
                keepScreenOn = NovelStore.keepScreenOn().get(),
                ttsSpeed = NovelStore.ttsSpeed().get(),
                ttsPitch = NovelStore.ttsPitch().get(),
                ttsLang = NovelStore.ttsLang().get(),
            )
        }
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

    fun setTheme(theme: Int) {
        NovelStore.readerTheme().set(theme)
        mutableState.update { it.copy(theme = theme) }
    }

    fun setFontFamily(family: Int) {
        NovelStore.fontFamily().set(family)
        mutableState.update { it.copy(fontFamily = family) }
    }

    fun setLineSpacing(percent: Int) {
        val p = percent.coerceIn(120, 240)
        NovelStore.lineSpacingPercent().set(p)
        mutableState.update { it.copy(lineSpacing = p) }
    }

    fun setKeepScreenOn(value: Boolean) {
        NovelStore.keepScreenOn().set(value)
        mutableState.update { it.copy(keepScreenOn = value) }
    }

    fun setTtsSpeed(percent: Int) {
        val p = percent.coerceIn(50, 200)
        NovelStore.ttsSpeed().set(p)
        mutableState.update { it.copy(ttsSpeed = p) }
    }

    fun setTtsPitch(percent: Int) {
        val p = percent.coerceIn(50, 200)
        NovelStore.ttsPitch().set(p)
        mutableState.update { it.copy(ttsPitch = p) }
    }

    fun setTtsLang(tag: String) {
        NovelStore.ttsLang().set(tag)
        mutableState.update { it.copy(ttsLang = tag) }
    }

    private fun loadChapter(index: Int) {
        val chapter = chapters.getOrNull(index) ?: return
        screenModelScope.launchIO {
            mutableState.update { it.copy(index = index, isLoading = true, error = null, text = "") }
            try {
                val text = NovelDownloader.localChapterText(app, novelUrl, index)
                    ?: NovelSource.chapterText(chapter.url)
                NovelStore.setLastReadChapter(novelUrl, chapter.url)
                NovelStore.markRead(novelUrl, chapter.url)
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
        val theme: Int = 0,
        val lineSpacing: Int = 150,
        val fontFamily: Int = 0,
        val keepScreenOn: Boolean = true,
        val ttsSpeed: Int = 100,
        val ttsPitch: Int = 100,
        val ttsLang: String = "",
        val isLoading: Boolean = true,
        val error: String? = null,
    )
}
