package io.github.nodyssey.ui.common

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.action_copy_link
import io.github.nodyssey.ui.resources.action_open_in_external_browser

@Composable
internal actual fun DesktopScrollbars(state: LazyListState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(end = 10.dp)) { content() }
    Box(Modifier.fillMaxSize()) {
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
internal actual fun DesktopPostContextMenu(url: String, content: @Composable () -> Unit) {
    val copyLabel = org.jetbrains.compose.resources.stringResource(io.github.nodyssey.ui.resources.Res.string.action_copy_link)
    val openLabel = org.jetbrains.compose.resources.stringResource(io.github.nodyssey.ui.resources.Res.string.action_open_in_external_browser)
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    androidx.compose.foundation.ContextMenuArea(items = {
        listOf(
            androidx.compose.foundation.ContextMenuItem(copyLabel) { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null) },
            androidx.compose.foundation.ContextMenuItem(openLabel) { uriHandler.openUri(url) },
        )
    }, content = content)
}
