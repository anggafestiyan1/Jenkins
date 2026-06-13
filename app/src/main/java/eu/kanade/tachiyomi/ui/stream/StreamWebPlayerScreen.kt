package eu.kanade.tachiyomi.ui.stream

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import java.io.ByteArrayInputStream

/** Removes large fixed/absolute ad overlays and auto-clicks close buttons; keeps the player. */
private val CLEANUP_JS = """
(function(){
  function clean(){
    try {
      document.querySelectorAll('div,ins,aside,a').forEach(function(e){
        var s = getComputedStyle(e);
        if((s.position==='fixed'||s.position==='absolute') && (parseInt(s.zIndex||0) >= 9999)){
          var r = e.getBoundingClientRect();
          if(r.width > window.innerWidth*0.5 && r.height > window.innerHeight*0.4 && !e.querySelector('video,iframe')){
            e.style.display='none';
          }
        }
      });
      document.querySelectorAll('[class*=close],[id*=close],[class*=btn-close],[class*=skip]').forEach(function(b){
        try{ b.click(); }catch(e){}
      });
    } catch(e){}
  }
  clean();
  var n=0; var t=setInterval(function(){ clean(); if(++n>=6){ clearInterval(t); } }, 1000);
})();
""".trimIndent()

/**
 * Streaming player: loads the host's own web player (embed/watch page) in a WebView — far more
 * reliable than scraping the direct media URL out of obfuscated hosts. Blocks ad-network requests
 * and window.open popups, and forwards a Referer so referer-gated embeds (Cloudflare) load.
 */
class StreamWebPlayerScreen(
    private val url: String,
    private val title: String,
    private val referer: String = "",
) : Screen() {

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val target = url
        val ref = referer

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
                            // Block popups (window.open ad popunders).
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            userAgentString = userAgentString.replace("; wv", "")
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                if (AdBlock.isAd(request.url.toString())) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        ByteArrayInputStream(ByteArray(0)),
                                    )
                                }
                                return null
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                // Block only main-frame navigations to ad hosts; allow legit player
                                // redirects (e.g. interstitial → real player) to proceed.
                                return request.isForMainFrame && AdBlock.isAd(request.url.toString())
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                // Auto-close full-screen ad overlays + click obvious close buttons,
                                // a few times since ads load late. Preserves the actual player.
                                view.evaluateJavascript(CLEANUP_JS, null)
                            }
                        }
                        if (ref.isNotBlank()) {
                            loadUrl(target, mapOf("Referer" to ref))
                        } else {
                            loadUrl(target)
                        }
                    }
                },
            )
        }
    }
}
