package io.github.seektop.ui.common

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.seektop.ui.resources.action_copy_link
import io.github.seektop.ui.resources.action_open_in_external_browser
import kotlin.math.abs

@Composable
internal actual fun DesktopScrollbars(state: LazyListState, reserveScrollbarSpace: Boolean, content: @Composable () -> Unit) {
    // draggable's main-pass detector loses mouse drags to rich-text selection. Inspect slop
    // in the initial pass, consuming movement only after dragging starts, so clicks still work.
    Box(
        Modifier.fillMaxSize().padding(end = if (reserveScrollbarSpace) 10.dp else 0.dp).pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                var previous = down.position.y
                var distance = 0f
                var dragging = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.position.y - previous
                    distance += delta
                    if (!dragging && abs(distance) > viewConfiguration.touchSlop) {
                        dragging = true
                        state.dispatchRawDelta(-distance)
                        change.consume()
                    } else if (dragging) {
                        state.dispatchRawDelta(-delta)
                        change.consume()
                    }
                    previous = change.position.y
                } while (change.pressed)
            }
        },
    ) { content() }
    Box(Modifier.fillMaxSize()) {
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight(), style = LocalScrollbarStyle.current.copy(unhoverColor = LocalContentColor.current.copy(alpha = 0.25f), hoverColor = LocalContentColor.current.copy(alpha = 0.55f)))
    }
}

@Composable
internal actual fun DesktopPostContextMenu(url: String, content: @Composable () -> Unit) {
    val copyLabel = org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.action_copy_link)
    val openLabel = org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.action_open_in_external_browser)
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    androidx.compose.foundation.ContextMenuArea(items = {
        listOf(
            androidx.compose.foundation.ContextMenuItem(copyLabel) { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null) },
            androidx.compose.foundation.ContextMenuItem(openLabel) { uriHandler.openUri(url) },
        )
    }, content = content)
}
