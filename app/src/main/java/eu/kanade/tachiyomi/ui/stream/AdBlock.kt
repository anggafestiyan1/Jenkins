package eu.kanade.tachiyomi.ui.stream

/**
 * Tiny substring blocklist for the Stream WebView player — kills the ad-network / gambling popups
 * these mirror sites inject. Matched against the full request URL (host + path), case-insensitive.
 * Not exhaustive (ad domains rotate), but covers the common networks.
 */
object AdBlock {

    private val blocked = listOf(
        // Ad networks / popunders commonly used by ID piracy sites
        "doubleclick", "googlesyndication", "googleadservices", "google-analytics",
        "googletagmanager", "googletagservices", "adservice.google", "/pagead/",
        "adnxs", "popads", "popcash", "pop.cash", "popunder", "poptm", "popmyads",
        "propellerads", "propeller-ads", "adsterra", "exoclick", "exosrv", "realsrv",
        "juicyads", "trafficjunky", "trafficfactory", "adcash", "hilltopads", "clickadu",
        "onclickads", "onclkds", "clickaine", "mgid", "revcontent", "taboola", "outbrain",
        "bidvertiser", "a-ads", "adskeeper", "monetize", "hexagram", "vidstat", "sape.ru",
        "histats", "statcounter", "mc.yandex", "yandex.ru/metrika", "connect.facebook",
        "facebook.net", "amung.us", "adserver", "adsystem", "/ads/", "/ads.", "/adv/",
        "/advertisement", "luckybet", "gacor", "slot88", "judi", "betvisa", "/banner",
    )

    fun isAd(url: String): Boolean {
        val u = url.lowercase()
        return blocked.any { u.contains(it) }
    }
}
