package io.github.seektop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.seektop.data.Board

/** A view of the home feed's state for the persistent desktop navigation, not a second repository. */
internal class DesktopBoardNavigation {
    var boards by mutableStateOf(io.github.seektop.data.CategoryRepository.initialBoards)
        private set
    var selectedSlug by mutableStateOf<String?>(null)
        private set
    var selectionGeneration by mutableIntStateOf(0)
        private set
    var pendingSelection by mutableStateOf<DesktopBoardSelection?>(null)
        private set
    fun update(boards: List<Board>, selectedSlug: String?) {
        this.boards = boards
        this.selectedSlug = selectedSlug
    }
    fun select(slug: String?) {
        selectionGeneration++
        pendingSelection = DesktopBoardSelection(selectionGeneration, slug)
    }
    fun acknowledge(selection: DesktopBoardSelection) {
        if (pendingSelection == selection) pendingSelection = null
    }
}
internal data class DesktopBoardSelection(val generation: Int, val slug: String?)
internal val LocalDesktopBoardNavigation = staticCompositionLocalOf<DesktopBoardNavigation?> { null }

@Composable
internal fun PublishDesktopBoards(boards: List<Board>, selectedSlug: String?) {
    val navigation = LocalDesktopBoardNavigation.current ?: return
    SideEffect { navigation.update(boards, selectedSlug) }
}

/** Retain rail clicks while the feed is absent, then refresh only the requested board's page one. */
@Composable
internal fun ConsumeDesktopBoardSelection(selectedSlug: String?, startPage: Int, onSelect: (String?) -> Unit, onRefresh: suspend () -> Unit) {
    val navigation = LocalDesktopBoardNavigation.current ?: return
    val request = navigation.pendingSelection
    LaunchedEffect(navigation, request, selectedSlug, startPage) {
        if (request == null) return@LaunchedEffect
        if (selectedSlug != request.slug || startPage != 1) {
            onSelect(request.slug)
            return@LaunchedEffect
        }
        onRefresh()
        navigation.acknowledge(request)
    }
}
