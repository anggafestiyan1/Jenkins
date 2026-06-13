package eu.kanade.tachiyomi.ui.stream

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen

/**
 * In-app player for a Stream video — a local offline file path or a remote stream URL (with optional
 * headers, e.g. Referer). VideoView + MediaController, plus a fullscreen/landscape toggle.
 */
class StreamPlayerScreen(
    private val url: String,
    private val title: String,
    private val headers: HashMap<String, String> = HashMap(),
) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val activity = remember(context) { context.findActivity() }
        val target = url
        val hdrs = headers

        var fullscreen by remember { mutableStateOf(false) }

        fun applyFullscreen(value: Boolean) {
            fullscreen = value
            activity?.requestedOrientation = if (value) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_USER
            }
            activity?.window?.let { window ->
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                if (value) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        // Back exits fullscreen first, then leaves the screen.
        BackHandler(enabled = fullscreen) { applyFullscreen(false) }

        // Restore orientation + system bars when leaving the player.
        DisposableEffect(Unit) {
            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                activity?.window?.let { window ->
                    WindowInsetsControllerCompat(window, window.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                if (!fullscreen) {
                    TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { applyFullscreen(true) }) {
                                Icon(Icons.Outlined.Fullscreen, contentDescription = "Fullscreen")
                            }
                        },
                    )
                }
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (fullscreen) PaddingValues(0.dp) else contentPadding)
                    .background(Color.Black),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val frame = FrameLayout(ctx)
                        val videoView = VideoView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                Gravity.CENTER,
                            )
                            keepScreenOn = true
                        }
                        frame.addView(videoView)
                        val controller = MediaController(ctx)
                        controller.setAnchorView(videoView)
                        videoView.setMediaController(controller)
                        if (hdrs.isEmpty()) {
                            videoView.setVideoURI(Uri.parse(target))
                        } else {
                            videoView.setVideoURI(Uri.parse(target), hdrs)
                        }
                        videoView.setOnPreparedListener { it.start() }
                        frame
                    },
                )

                if (fullscreen) {
                    IconButton(
                        onClick = { applyFullscreen(false) },
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Icon(
                            Icons.Outlined.FullscreenExit,
                            contentDescription = "Exit fullscreen",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
