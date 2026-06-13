package eu.kanade.tachiyomi.ui.stream

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Streaming player: loads the host's own web player (embed/watch page) in a WebView. Far more
 * reliable than scraping the direct media URL out of obfuscated hosts. Blocks cross-host top-level
 * navigations and window.open popups to cut down on the ad redirects these sites are full of.
 */
class StreamWebPlayerScreen(
    private val url: String,
    private val title: String,
) : Screen() {

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val target = url
        val initialHost = url.toHttpUrlOrNull()?.host.orEmpty()

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { contentPadding ->
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                factory = { context ->
                    WebView(context).apply {
                        keepScreenOn = true
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            @Suppress("DEPRECATION")
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            userAgentString = userAgentString.replace("; wv", "")
                        }
                        // Enables HTML5 video / fullscreen handling.
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                // Block top-level navigations to a different host (ad popups/redirects);
                                // allow the player's own host + sub-resources to load normally.
                                if (request.isForMainFrame) {
                                    val host = request.url.host.orEmpty()
                                    if (initialHost.isNotEmpty() && host.isNotEmpty() && host != initialHost) {
                                        return true
                                    }
                                }
                                return false
                            }
                        }
                        loadUrl(target)
                    }
                },
            )
        }
    }
}
