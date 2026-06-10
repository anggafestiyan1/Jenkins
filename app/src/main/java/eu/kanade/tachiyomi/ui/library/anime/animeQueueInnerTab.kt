package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadHeaderItem
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadQueueScreen
import eu.kanade.tachiyomi.ui.download.anime.AnimeDownloadQueueScreenModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun animeQueueInnerTab(
    screenModel: AnimeDownloadQueueScreenModel,
): TabContent {
    val downloadList by screenModel.state.collectAsState()
    val downloadCount by remember {
        derivedStateOf { downloadList.sumOf { it.subItems.size } }
    }

    return TabContent(
        titleRes = MR.strings.label_download_queue,
        searchEnabled = false,
        content = { contentPadding, snackbarHostState ->
            AnimeQueueInnerContent(
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
                screenModel = screenModel,
                downloadList = downloadList,
            )
        },
        numberTitle = downloadCount,
    )
}

@Composable
private fun AnimeQueueInnerContent(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    screenModel: AnimeDownloadQueueScreenModel,
    downloadList: List<AnimeDownloadHeaderItem>,
) {
    val scope = rememberCoroutineScope()
    val isRunning by screenModel.isDownloaderRunning.collectAsState()
    var fabExpanded by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                fabExpanded = available.y >= 0
                return Offset.Zero
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (downloadList.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = {
                        val res = if (isRunning) AYMR.strings.action_stop else AYMR.strings.action_continue
                        Text(text = stringResource(res))
                    },
                    icon = {
                        Icon(
                            imageVector = if (isRunning) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        if (isRunning) screenModel.pauseDownloads() else screenModel.startDownloads()
                    },
                    expanded = fabExpanded,
                )
            }
        },
    ) { innerPadding ->
        AnimeDownloadQueueScreen(
            contentPadding = innerPadding,
            scope = scope,
            screenModel = screenModel,
            downloadList = downloadList,
            nestedScrollConnection = nestedScrollConnection,
        )
    }
}
