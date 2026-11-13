package io.github.nodyssey

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

internal val LocalDesktopCloseDetail = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalDesktopSelectedPost = staticCompositionLocalOf<Long?> { null }

/** Toggle the current reader; replacing another reader never grows the navigation history. */
internal fun MutableList<NavKey>.toggleDesktopDetail(detail: PostDetailKey) {
    val current = lastOrNull() as? PostDetailKey
    closeDesktopDetails()
    if (current?.postId != detail.postId) add(detail)
}

internal fun MutableList<NavKey>.closeDesktopDetails() {
    while (size > 1 && lastOrNull() is PostDetailKey) removeAt(lastIndex)
}
