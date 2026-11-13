package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_cancel
import io.github.nodyssey.ui.resources.action_done
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.desktop_page_range
import io.github.nodyssey.ui.resources.desktop_page_title
import org.jetbrains.compose.resources.stringResource

internal fun parseDesktopPage(value: String, total: Int): Int? = value.trim().toIntOrNull()?.takeIf { it in 1..total.coerceAtLeast(1) }

@Composable
internal fun DesktopPageJumpDialog(page: Int, totalPages: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val total = totalPages.coerceAtLeast(1)
    val input = remember {
        val initial = page.coerceIn(1, total).toString()
        androidx.compose.foundation.text.input.TextFieldState(initialText = initial, initialSelection = androidx.compose.ui.text.TextRange(0, initial.length))
    }
    val target = parseDesktopPage(input.text.toString(), total)
    val focus = remember { FocusRequester() }
    fun submit() {
        parseDesktopPage(input.text.toString(), total)?.let(onGo)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.desktop_page_title)) },
        text = {
            LaunchedEffect(Unit) { focus.requestFocus() }
            OutlinedTextField(
                state = input,
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                isError = target == null,
                supportingText = { Text(stringResource(Res.string.desktop_page_range, total)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                onKeyboardAction = { submit() },
            )
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = target != null) { Text(stringResource(Res.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
internal fun DesktopRefreshButton(onRefresh: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SmallFloatingActionButton(
        onClick = { if (enabled) onRefresh() },
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Icon(Icons.Default.Refresh, stringResource(Res.string.action_refresh))
    }
}
