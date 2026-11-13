package io.github.nodyssey.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable

@Composable
internal actual fun DesktopScrollbars(state: LazyListState, reserveScrollbarSpace: Boolean, content: @Composable () -> Unit) {
    content()
}

@Composable
internal actual fun DesktopPostContextMenu(url: String, content: @Composable () -> Unit) {
    content()
}
