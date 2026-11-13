package io.github.nodyssey.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable

@Composable
internal expect fun DesktopScrollbars(state: LazyListState, reserveScrollbarSpace: Boolean = true, content: @Composable () -> Unit)

@Composable
internal expect fun DesktopPostContextMenu(url: String, content: @Composable () -> Unit)
