package eu.kanade.tachiyomi.ui.stream

/**
 * LK21 — uses the same unified parser (Next.js primary, DooPlay fallback). LK21's classic domains
 * rotate/die often; default points at the verified mirror so it works out of the box. Change the
 * domain via the Stream domain dialog (⚙) to a live LK21 if you have one.
 */
object Lk21Source : StreamSiteSource() {
    override val id = "lk21"
    override val name = "LK21"
    override val defaultBaseUrl = "https://139.59.196.140"
}
