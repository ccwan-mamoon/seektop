package io.github.seektop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import io.github.seektop.data.NotificationDestination
import io.github.seektop.ui.navigation.TopLevelDestination

/** Switching tabs restores their stack. A repeated selection explicitly opens the tab root. */
internal fun MutableList<NavKey>.selectDesktopTab(current: TopLevelDestination, destination: TopLevelDestination, exitSearch: () -> Unit) {
    if (destination == TopLevelDestination.HOME) exitSearch()
    if (destination == TopLevelDestination.HOME || destination == current) returnToDesktopRoot()
}

internal val LocalDesktopCloseDetail = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalDesktopSelectedPost = staticCompositionLocalOf<Long?> { null }

/** A rail destination opens its root even when settings were pushed on that same tab. */
internal fun MutableList<NavKey>.returnToDesktopRoot() {
    while (size > 1) removeAt(lastIndex)
}

/** The notification stack is selected by the caller; keep Back anchored to its inbox. */
internal fun MutableList<NavKey>.openDesktopNotification(target: NotificationDestination, currentAccount: Int): Boolean {
    returnToDesktopRoot()
    if (target.account != null && target.account != currentAccount) return false
    val postId = target.postId
    val uid = target.uid
    when {
        postId != null && postId > 0 -> add(PostDetailKey(postId, target.floor))
        uid != null && uid > 0 -> add(MessageThreadKey(uid, target.userName))
        else -> return false
    }
    return true
}

/** Toggle the current reader; replacing another reader never grows the navigation history. */
internal fun MutableList<NavKey>.toggleDesktopDetail(detail: PostDetailKey) {
    val current = lastOrNull() as? PostDetailKey
    closeDesktopDetails()
    if (current?.postId != detail.postId) add(detail)
}

internal fun MutableList<NavKey>.closeDesktopDetails() {
    while (size > 1 && lastOrNull() is PostDetailKey) removeAt(lastIndex)
}
