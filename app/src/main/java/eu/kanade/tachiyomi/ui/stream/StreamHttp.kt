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
