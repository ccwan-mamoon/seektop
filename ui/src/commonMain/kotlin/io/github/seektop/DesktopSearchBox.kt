package io.github.seektop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seektop.data.CategoryRepository
import io.github.seektop.model.SearchTarget
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_close
import io.github.seektop.ui.resources.action_search
import io.github.seektop.ui.resources.search_all_boards
import io.github.seektop.ui.resources.search_posts_tab
import io.github.seektop.ui.resources.search_users_tab
import io.github.seektop.ui.search.SearchUiState
import org.jetbrains.compose.resources.stringResource

/** One search control owns the query, target, scope and submit action on every desktop route. */
@Composable
internal fun DesktopSearchBox(session: DesktopSearchSession, focus: FocusRequester, modifier: Modifier = Modifier) {
    val vm = session.viewModel
    val state = vm?.uiState?.collectAsStateWithLifecycle()?.value ?: SearchUiState(boards = CategoryRepository.initialBoards.filter { it.slug != null })
    var focused by remember { mutableStateOf(false) }
    var targetsOpen by remember { mutableStateOf(false) }
    var boardsOpen by remember { mutableStateOf(false) }
    Surface(
        modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box {
                TextButton(onClick = { targetsOpen = true }, contentPadding = PaddingValues(start = 12.dp, end = 6.dp)) {
                    Text(stringResource(if (state.target == SearchTarget.POSTS) Res.string.search_posts_tab else Res.string.search_users_tab), color = MaterialTheme.colorScheme.onSurface)
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
                DropdownMenu(targetsOpen, onDismissRequest = { targetsOpen = false }) {
                    SearchTarget.entries.forEach { target ->
                        DropdownMenuItem(text = { Text(stringResource(if (target == SearchTarget.POSTS) Res.string.search_posts_tab else Res.string.search_users_tab)) }, onClick = {
                            targetsOpen = false
                            vm?.selectTarget(target)
                        })
                    }
                }
            }
            VerticalDivider(Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TextField(
                state = session.query, modifier = Modifier.weight(1f).height(44.dp).focusRequester(focus).onFocusChanged { focused = it.isFocused },
                textStyle = MaterialTheme.typography.bodyMedium, lineLimits = TextFieldLineLimits.SingleLine,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                placeholder = { Text(stringResource(Res.string.action_search), style = MaterialTheme.typography.bodyMedium) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), onKeyboardAction = { session.submit() },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            )
            if (session.query.text.isNotEmpty()) {
                IconButton(onClick = {
                    session.query.edit { replace(0, length, "") }
                    session.submit()
                }) { Icon(Icons.Default.Close, stringResource(Res.string.action_close), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (state.target == SearchTarget.POSTS) {
                Box {
                    TextButton(onClick = { boardsOpen = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(state.boards.firstOrNull { it.slug == state.selectedBoard }?.title ?: stringResource(Res.string.search_all_boards), modifier = Modifier.widthIn(max = 100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(boardsOpen, onDismissRequest = { boardsOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.search_all_boards)) }, onClick = {
                            boardsOpen = false
                            vm?.selectBoard(null)
                        })
                        state.boards.forEach { board ->
                            DropdownMenuItem(text = { Text(board.title) }, onClick = {
                                boardsOpen = false
                                vm?.selectBoard(board.slug)
                            })
                        }
                    }
                }
            }
            VerticalDivider(Modifier.height(20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            IconButton(onClick = { session.submit() }, modifier = Modifier.padding(end = 2.dp)) { Icon(Icons.Default.Search, stringResource(Res.string.action_search), tint = MaterialTheme.colorScheme.primary) }
        }
    }
}
