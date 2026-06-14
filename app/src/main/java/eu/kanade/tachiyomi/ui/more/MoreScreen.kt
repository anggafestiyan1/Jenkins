package eu.kanade.tachiyomi.ui.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.MoreScreenContent
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.QuickBackup
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.util.system.toast

/** Standalone "More" screen opened by the top-right gear on every page. */
class MoreScreen : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { MoreScreenModel() }
        val downloadQueueState by screenModel.downloadQueueState.collectAsState()

        val restorePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) navigator.push(RestoreBackupScreen(uri.toString()))
        }

        fun runQuickBackup() {
            val name = QuickBackup.toDownloads(context)
            context.toast(if (name != null) "Backup dibuat di folder Download" else "Gagal membuat backup")
        }

        val writePermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) runQuickBackup() else context.toast("Izin penyimpanan ditolak")
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("More") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { contentPadding ->
            MoreScreenContent(
                contentPadding = contentPadding,
                downloadQueueStateProvider = { downloadQueueState },
                downloadedOnly = screenModel.downloadedOnly,
                onDownloadedOnlyChange = { screenModel.downloadedOnly = it },
                incognitoMode = screenModel.incognitoMode,
                onIncognitoModeChange = { screenModel.incognitoMode = it },
                onClickDownloadQueue = { navigator.push(DownloadsTab) },
                onClickQuickBackup = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        runQuickBackup()
                    } else {
                        writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
                onClickRestore = { restorePicker.launch("*/*") },
                onClickSettings = { navigator.push(SettingsScreen()) },
                onClickAbout = { navigator.push(SettingsScreen(SettingsScreen.Destination.About)) },
            )
        }
    }
}
