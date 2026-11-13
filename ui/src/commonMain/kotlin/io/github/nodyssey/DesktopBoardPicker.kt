package io.github.nodyssey

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.Board
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.desktop_boards_hint
import io.github.nodyssey.ui.resources.desktop_manage_boards
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopBoardPicker(boards: List<Board>, pinned: List<String>, onDismiss: () -> Unit, onApply: (List<String>) -> Unit) {
    var chosen by remember { mutableStateOf(pinned.filter { slug -> boards.any { it.slug == slug } }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desktop_manage_boards)) },
        text = {
            Column {
                Text(stringResource(Res.string.desktop_boards_hint), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    boards.filter { it.slug != null }.forEach { board ->
                        val slug = requireNotNull(board.slug)
                        val selected = slug in chosen
                        Row(Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox, onValueChange = { enabled -> chosen = if (enabled) (chosen + slug).distinct() else chosen - slug }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Text(board.title)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(chosen) }) { Text(stringResource(Res.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
