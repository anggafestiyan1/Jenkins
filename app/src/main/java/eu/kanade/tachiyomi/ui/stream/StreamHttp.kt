package eu.kanade.tachiyomi.ui.stream

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import okhttp3.Protocol
import uy.kohesive.injekt.injectLazy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * A deliberately permissive OkHttp client for the Stream scrapers. These piracy mirror sites are
 * frequently reached by bare IP or use mismatched/self-signed TLS certs, so we bypass cert + host
 * verification here (and only here — the app-wide client stays strict). Personal use only.
 */
object StreamHttp {

    private val network: NetworkHelper by injectLazy()

    val client: OkHttpClient by lazy {
        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            },
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
        network.client.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true)
            .followSslRedirects(true)
            // Browser-like headers so Cloudflare's Browser Integrity Check lets the request through
            // (some mirrors, e.g. JagoDrama/Drakorid, reject bare OkHttp requests otherwise).
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder()
                if (req.header("Accept") == null) {
                    b.header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    )
                }
                if (req.header("Accept-Language") == null) {
                    b.header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                }
                b.header("Sec-Fetch-Dest", "document")
                b.header("Sec-Fetch-Mode", "navigate")
                b.header("Sec-Fetch-Site", "none")
                b.header("Sec-Fetch-User", "?1")
                b.header("Upgrade-Insecure-Requests", "1")
                chain.proceed(b.build())
            }
            .build()
    }

    /**
     * Client for large file downloads: forces HTTP/1.1 (some CDNs abort HTTP/2 multiplexed long
     * transfers — "software caused connection abort") and uses generous timeouts. Paired with the
     * Range-resume retry loop in StreamDownloadQueue.
     */
    val downloadClient: OkHttpClient by lazy {
        client.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }
}
