package io.github.nodyssey.ui.postlist

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.Board
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_show_all_boards
import io.github.nodyssey.ui.resources.desktop_board_next
import io.github.nodyssey.ui.resources.desktop_board_previous
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopBoardStrip(
    boards: List<Board>,
    selectedSlug: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { 48.dp.toPx() }
    val pageStep = with(LocalDensity.current) { 240.dp.toPx() }.toInt()
    var menuOpen by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.action_show_all_boards)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { scope.launch { scrollState.animateScrollTo((scrollState.value - pageStep).coerceAtLeast(0)) } }, enabled = scrollState.canScrollBackward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.desktop_board_previous))
        }
        // ScrollableTabRow handles layout, selection reveal and touch scrolling. Its desktop
        // defaults do not translate vertical wheels or enable primary-button mouse dragging.
        PrimaryScrollableTabRow(
            selectedTabIndex = boards.indexOfFirst { it.slug == selectedSlug }.coerceAtLeast(0),
            scrollState = scrollState,
            edgePadding = 0.dp,
            minTabWidth = 76.dp,
            modifier = Modifier.weight(1f).semantics { contentDescription = label }
                .draggable(rememberDraggableState { delta -> scrollState.dispatchRawDelta(-delta) }, Orientation.Horizontal)
                .pointerInput(scrollState, wheelStep) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.firstOrNull()?.scrollDelta ?: continue
                                val distance = (if (delta.x != 0f) delta.x else delta.y) * wheelStep
                                if (scrollState.dispatchRawDelta(distance) != 0f) event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
        ) {
            boards.forEach { board ->
                Tab(selected = board.slug == selectedSlug, onClick = { onSelect(board.slug) }, text = { Text(board.title, maxLines = 1) })
            }
        }
        IconButton(onClick = { scope.launch { scrollState.animateScrollTo((scrollState.value + pageStep).coerceAtMost(scrollState.maxValue)) } }, enabled = scrollState.canScrollForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.desktop_board_next))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.KeyboardArrowDown, label) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                boards.forEach { board ->
                    DropdownMenuItem(text = { Text(board.title) }, onClick = {
                        menuOpen = false
                        onSelect(board.slug)
                    })
                }
            }
        }
    }
}
