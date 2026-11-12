package io.github.nodyssey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.nodyssey.data.Board

/** A view of the home feed's state for the persistent desktop navigation, not a second repository. */
internal class DesktopBoardNavigation {
    var boards by mutableStateOf<List<Board>>(emptyList())
        private set
    var selectedSlug by mutableStateOf<String?>(null)
        private set
    var selectionGeneration by mutableIntStateOf(0)
        private set
    private var select: (String?) -> Unit = {}
    fun update(boards: List<Board>, selectedSlug: String?, select: (String?) -> Unit) {
        this.boards = boards
        this.selectedSlug = selectedSlug
        this.select = select
    }
    fun select(slug: String?) {
        selectionGeneration++
        select.invoke(slug)
    }
}
internal val LocalDesktopBoardNavigation = staticCompositionLocalOf<DesktopBoardNavigation?> { null }

@Composable
internal fun PublishDesktopBoards(boards: List<Board>, selectedSlug: String?, onSelect: (String?) -> Unit) {
    val navigation = LocalDesktopBoardNavigation.current ?: return
    SideEffect { navigation.update(boards, selectedSlug, onSelect) }
}
