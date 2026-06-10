package eu.kanade.tachiyomi.ui.history.downloads

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.history.downloads.DownloadsScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.downloadsTab(
    context: Context,
    fromMore: Boolean,
    screenModel: DownloadsScreenModel,
): TabContent {
    val snackbarHostState = SnackbarHostState()

    val navigator = LocalNavigator.currentOrThrow
    val state by screenModel.state.collectAsState()
    val filter by screenModel.filter.collectAsState()
    val searchQuery by screenModel.query.collectAsState()

    val scope = rememberCoroutineScope()
    val navigateUp: (() -> Unit)? = if (fromMore) {
        {
            if (navigator.lastItem == HomeScreen) {
                scope.launch { HomeScreen.openTab(HomeScreen.Tab.AnimeLib()) }
            } else {
                navigator.pop()
            }
        }
    } else {
        null
    }

    return TabContent(
        titleRes = AYMR.strings.label_downloads_history,
        searchEnabled = true,
        content = { _, _ ->
            DownloadsScreen(
                state = state,
                filter = filter,
                searchQuery = searchQuery,
                snackbarHostState = snackbarHostState,
                onFilterChange = screenModel::setFilter,
                onClickEntry = { entry ->
                    navigator.push(DownloadedEntryScreen(entry.id, entry.isManga))
                },
                onClickDelete = { entry ->
                    screenModel.setDialog(DownloadsScreenModel.Dialog.Delete(entry))
                },
            )

            when (val dialog = state.dialog) {
                is DownloadsScreenModel.Dialog.Delete -> {
                    AlertDialog(
                        onDismissRequest = { screenModel.setDialog(null) },
                        title = { Text(stringResource(MR.strings.action_remove)) },
                        text = { Text(dialog.entry.title) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    screenModel.deleteEntry(dialog.entry)
                                    screenModel.setDialog(null)
                                },
                            ) { Text(stringResource(MR.strings.action_ok)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { screenModel.setDialog(null) }) {
                                Text(stringResource(MR.strings.action_cancel))
                            }
                        },
                    )
                }
                null -> {}
            }

            LaunchedEffect(state.entries) {
                if (state.entries != null) {
                    (context as? MainActivity)?.ready = true
                }
            }
        },
        actions = persistentListOf(),
        navigateUp = navigateUp,
    )
}
