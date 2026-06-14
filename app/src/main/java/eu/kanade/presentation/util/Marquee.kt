package eu.kanade.presentation.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier

/**
 * Long single-line titles scroll horizontally (marquee) instead of being truncated with an ellipsis.
 * Use on a `Text(maxLines = 1, softWrap = false, modifier = Modifier.scrollingTitle())`.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.scrollingTitle(): Modifier = this.basicMarquee()
