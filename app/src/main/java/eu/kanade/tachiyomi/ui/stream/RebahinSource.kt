package eu.kanade.tachiyomi.ui.stream

/**
 * Rebahin — modern Next.js aggregator (film + series + drakor, sub Indonesia, direct MP4).
 * Verified live; domain rotates, editable via the Stream domain dialog.
 */
object RebahinSource : StreamSiteSource() {
    override val id = "rebahin"
    override val name = "Rebahin"
    override val defaultBaseUrl = "https://139.59.196.140"
}
