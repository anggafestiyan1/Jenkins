package eu.kanade.tachiyomi.ui.library.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.entries.components.LibraryBottomActionMenu
import eu.kanade.presentation.library.DeleteLibraryEntryDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.manga.MangaLibraryContent
import eu.kanade.presentation.library.manga.MangaLibrarySettingsDialog
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.manga.isLocal

@Composable
fun Screen.mangaFavoriteInnerTab(): TabContent {
    val screen = this
    return TabContent(
        titleRes = AYMR.strings.label_favorite,
        searchEnabled = false,
        content = { contentPadding, _ ->
            with(screen) {
                MangaFavoriteInnerContent(contentPadding = contentPadding)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen.MangaFavoriteInnerContent(
    contentPadding: PaddingValues,
) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val screenModel = rememberScreenModel { MangaLibraryScreenModel() }
    val settingsScreenModel = rememberScreenModel { MangaLibrarySettingsScreenModel() }
    val state by screenModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val onClickRefresh: (Category?) -> Boolean = { category ->
        val started = MangaLibraryUpdateJob.startNow(context, category)
        scope.launch {
            val msgRes = if (started) MR.strings.updating_category else MR.strings.update_already_running
            snackbarHostState.showSnackbar(context.stringResource(msgRes))
        }
        started
    }

    val defaultTitle = stringResource(AYMR.strings.label_komik_library)

    Scaffold(
        topBar = { scrollBehavior ->
            val title = state.getToolbarTitle(
                defaultTitle = defaultTitle,
                defaultCategoryTitle = stringResource(MR.strings.label_default),
                page = screenModel.activeCategoryIndex,
            )
            val tabVisible = state.showCategoryTabs && state.categories.size > 1
            LibraryToolbar(
                hasActiveFilters = state.hasActiveFilters,
                selectedCount = state.selection.size,
                // Title text removed (redundant with the Komik tab header).
                title = title.copy(text = ""),
                onClickUnselectAll = screenModel::clearSelection,
                onClickSelectAll = { screenModel.selectAll(screenModel.activeCategoryIndex) },
                onClickInvertSelection = {
                    screenModel.invertSelection(screenModel.activeCategoryIndex)
                },
                onClickFilter = screenModel::showSettingsDialog,
                onClickRefresh = {
                    onClickRefresh(state.categories[screenModel.activeCategoryIndex])
                },
                onClickGlobalUpdate = { onClickRefresh(null) },
                onClickOpenRandomEntry = {
                    scope.launch {
                        val randomItem = screenModel.getRandomLibraryItemForCurrentCategory()
                        if (randomItem != null) {
                            navigator.push(MangaScreen(randomItem.libraryManga.manga.id))
                        } else {
                            snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.information_no_entries_found),
                            )
                        }
                    }
                },
                searchQuery = state.searchQuery,
                onSearchQueryChange = screenModel::search,
                scrollBehavior = scrollBehavior.takeIf { !tabVisible },
                downloadedOnly = screenModel.downloadedOnly,
                onToggleDownloadedOnly = { screenModel.downloadedOnly = !screenModel.downloadedOnly },
                incognitoMode = screenModel.incognitoMode,
                onToggleIncognitoMode = { screenModel.incognitoMode = !screenModel.incognitoMode },
                navigateUp = null,
            )
        },
        bottomBar = {
            LibraryBottomActionMenu(
                visible = state.selectionMode,
                onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                onMarkAsViewedClicked = { screenModel.markReadSelection(true) },
                onMarkAsUnviewedClicked = { screenModel.markReadSelection(false) },
                onDownloadClicked = screenModel::runDownloadActionSelection
                    .takeIf { state.selection.fastAll { !it.manga.isLocal() } },
                onDeleteClicked = screenModel::openDeleteMangaDialog,
                isManga = true,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(innerPadding))
            state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty -> {
                val handler = LocalUriHandler.current
                EmptyScreen(
                    stringRes = MR.strings.information_empty_library,
                    modifier = Modifier.padding(innerPadding),
                    actions = persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.getting_started_guide,
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            onClick = { handler.openUri(GETTING_STARTED_URL) },
                        ),
                    ),
                )
            }
            else -> {
                MangaLibraryContent(
                    categories = state.categories,
                    searchQuery = state.searchQuery,
                    selection = state.selection,
                    contentPadding = innerPadding,
                    currentPage = { screenModel.activeCategoryIndex },
                    hasActiveFilters = state.hasActiveFilters,
                    showPageTabs = state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                    onChangeCurrentPage = { screenModel.activeCategoryIndex = it },
                    onMangaClicked = { navigator.push(MangaScreen(it)) },
                    onContinueReadingClicked = { it: LibraryManga ->
                        scope.launchIO {
                            val chapter = screenModel.getNextUnreadChapter(it.manga)
                            if (chapter != null) {
                                context.startActivity(
                                    ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                                )
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.no_next_chapter),
                                )
                            }
                        }
                        Unit
                    }.takeIf { state.showMangaContinueButton },
                    onToggleSelection = screenModel::toggleSelection,
                    onToggleRangeSelection = {
                        screenModel.toggleRangeSelection(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onRefresh = onClickRefresh,
                    onGlobalSearchClicked = {
                        navigator.push(
                            GlobalMangaSearchScreen(screenModel.state.value.searchQuery ?: ""),
                        )
                    },
                    getNumberOfMangaForCategory = { state.getMangaCountForCategory(it) },
                    getDisplayMode = { screenModel.getDisplayMode() },
                    getColumnsForOrientation = {
                        screenModel.getColumnsPreferenceForCurrentOrientation(it)
                    },
                    getLibraryForPage = { state.getLibraryItemsByPage(it) },
                    continueReadingHistory = state.continueReadingHistory,
                    onContinueReadingHistoryClicked = { history ->
                        scope.launchIO {
                            val chapter = screenModel.getNextChapterForHistory(
                                history.mangaId,
                                history.chapterId,
                            )
                            if (chapter != null) {
                                context.startActivity(
                                    ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                                )
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.no_next_chapter),
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    val onDismissRequest = screenModel::closeDialog
    when (val dialog = state.dialog) {
        is MangaLibraryScreenModel.Dialog.SettingsSheet -> run {
            val category = state.categories.getOrNull(screenModel.activeCategoryIndex)
            if (category == null) {
                onDismissRequest()
                return@run
            }
            MangaLibrarySettingsDialog(
                onDismissRequest = onDismissRequest,
                screenModel = settingsScreenModel,
                category = category,
            )
        }
        is MangaLibraryScreenModel.Dialog.ChangeCategory -> {
            ChangeCategoryDialog(
                initialSelection = dialog.initialSelection,
                onDismissRequest = onDismissRequest,
                onEditCategories = {
                    screenModel.clearSelection()
                    navigator.push(CategoriesTab)
                    CategoriesTab.showMangaCategory()
                },
                onConfirm = { include, exclude ->
                    screenModel.clearSelection()
                    screenModel.setMangaCategories(dialog.manga, include, exclude)
                },
            )
        }
        is MangaLibraryScreenModel.Dialog.DeleteManga -> {
            DeleteLibraryEntryDialog(
                containsLocalEntry = dialog.manga.any(Manga::isLocal),
                onDismissRequest = onDismissRequest,
                onConfirm = { deleteManga, deleteChapter ->
                    screenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                    screenModel.clearSelection()
                },
                isManga = true,
            )
        }
        null -> {}
    }

    BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
        when {
            state.selectionMode -> screenModel.clearSelection()
            state.searchQuery != null -> screenModel.search(null)
        }
    }

    LaunchedEffect(state.selectionMode, state.dialog) {
        HomeScreen.showBottomNav(!state.selectionMode)
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            (context as? MainActivity)?.ready = true
        }
    }
}
