package eu.kanade.presentation.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import eu.kanade.tachiyomi.ui.more.RecommendedExtensions
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickQuickBackup: () -> Unit,
    onClickRestore: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    Scaffold { contentPadding ->
        MoreScreenContent(
            contentPadding = contentPadding,
            downloadQueueStateProvider = downloadQueueStateProvider,
            downloadedOnly = downloadedOnly,
            onDownloadedOnlyChange = onDownloadedOnlyChange,
            incognitoMode = incognitoMode,
            onIncognitoModeChange = onIncognitoModeChange,
            onClickDownloadQueue = onClickDownloadQueue,
            onClickQuickBackup = onClickQuickBackup,
            onClickRestore = onClickRestore,
            onClickSettings = onClickSettings,
            onClickAbout = onClickAbout,
        )
    }
}

/**
 * The body of the More screen, without its own [Scaffold] — so it can be reused both as a
 * standalone screen and embedded as a tab (e.g. inside Browse) using the provided [contentPadding].
 */
@Composable
fun MoreScreenContent(
    contentPadding: PaddingValues,
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickQuickBackup: () -> Unit,
    onClickRestore: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chapterCache = remember { Injekt.get<ChapterCache>() }

    ScrollbarLazyColumn(
        modifier = Modifier.padding(contentPadding),
    ) {
        item { LogoHeader() }

        item {
            SwitchPreferenceWidget(
                title = stringResource(MR.strings.label_downloaded_only),
                subtitle = stringResource(MR.strings.downloaded_only_summary),
                icon = Icons.Outlined.CloudOff,
                checked = downloadedOnly,
                onCheckedChanged = onDownloadedOnlyChange,
            )
        }
        item {
            SwitchPreferenceWidget(
                title = stringResource(MR.strings.pref_incognito_mode),
                subtitle = stringResource(AYMR.strings.pref_incognito_mode_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                checked = incognitoMode,
                onCheckedChanged = onIncognitoModeChange,
            )
        }

        item { HorizontalDivider() }

        item {
            val downloadQueueState = downloadQueueStateProvider()
            TextPreferenceWidget(
                title = stringResource(MR.strings.label_download_queue),
                subtitle = when (downloadQueueState) {
                    DownloadQueueState.Stopped -> null
                    is DownloadQueueState.Paused -> {
                        val pending = downloadQueueState.pending
                        if (pending == 0) {
                            stringResource(MR.strings.paused)
                        } else {
                            "${stringResource(MR.strings.paused)} • ${
                                pluralStringResource(MR.plurals.download_queue_summary, count = pending, pending)
                            }"
                        }
                    }
                    is DownloadQueueState.Downloading -> {
                        val pending = downloadQueueState.pending
                        pluralStringResource(MR.plurals.download_queue_summary, count = pending, pending)
                    }
                },
                icon = Icons.Outlined.GetApp,
                onPreferenceClick = onClickDownloadQueue,
            )
        }
        item {
            TextPreferenceWidget(
                title = "Backup ke Download (1-klik)",
                subtitle = "Simpan history + library favorit ke folder Download",
                icon = Icons.Outlined.SaveAlt,
                onPreferenceClick = onClickQuickBackup,
            )
        }
        item {
            TextPreferenceWidget(
                title = stringResource(MR.strings.pref_restore_backup),
                icon = Icons.Outlined.SettingsBackupRestore,
                onPreferenceClick = onClickRestore,
            )
        }
        item {
            TextPreferenceWidget(
                title = "Hapus cache",
                subtitle = "Hapus gambar yang sudah dibaca (history & download tetap aman)",
                icon = Icons.Outlined.DeleteSweep,
                onPreferenceClick = {
                    scope.launch {
                        val deleted = withIOContext { chapterCache.clear() }
                        context.toast("Cache dihapus: $deleted file")
                    }
                },
            )
        }

        item {
            TextPreferenceWidget(
                title = "Install extension komik — English",
                subtitle = "Asura, MangaDex, Flame, Hades, Lunar, Webtoon, dll",
                icon = Icons.Outlined.Extension,
                onPreferenceClick = {
                    scope.launch {
                        context.toast("Mencari extension…")
                        val count = RecommendedExtensions.installEnglish()
                        context.toast(installResultMessage(count))
                    }
                },
            )
        }
        item {
            TextPreferenceWidget(
                title = "Install extension komik — Indonesia",
                subtitle = "Kiryuu, KomikIndo, Komiku, ManhwaDesu, Shinigami, dll",
                icon = Icons.Outlined.Extension,
                onPreferenceClick = {
                    scope.launch {
                        context.toast("Mencari extension…")
                        val count = RecommendedExtensions.installIndonesian()
                        context.toast(installResultMessage(count))
                    }
                },
            )
        }

        item { HorizontalDivider() }

        item {
            PreferenceGroupHeader(title = stringResource(AYMR.strings.label_app))
        }
        item {
            TextPreferenceWidget(
                title = stringResource(MR.strings.label_settings),
                icon = Icons.Outlined.Settings,
                onPreferenceClick = onClickSettings,
            )
        }
        item {
            TextPreferenceWidget(
                title = stringResource(MR.strings.pref_category_about),
                icon = Icons.Outlined.Info,
                onPreferenceClick = onClickAbout,
            )
        }
    }
}

private fun installResultMessage(count: Int): String =
    if (count > 0) {
        "Memulai install $count extension — setujui tiap dialog"
    } else {
        "Tidak ada extension cocok (cek internet / repo extension)"
    }
