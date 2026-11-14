package io.github.seektop.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seektop.foundation.designsys.theme.SeekTopTheme
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_retry
import io.github.seektop.ui.resources.desktop_start_exit
import io.github.seektop.ui.resources.desktop_start_failed
import io.github.seektop.ui.resources.desktop_start_failed_detail
import io.github.seektop.ui.resources.desktop_starting
import io.github.seektop.ui.resources.desktop_starting_detail
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopStartupScreen(failed: Boolean, onRetry: () -> Unit, onExit: () -> Unit) {
    SeekTopTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
                if (!failed) CircularProgressIndicator()
                Text(stringResource(if (failed) Res.string.desktop_start_failed else Res.string.desktop_starting), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (failed) Res.string.desktop_start_failed_detail else Res.string.desktop_starting_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (failed) Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                TextButton(onClick = onExit) { Text(stringResource(Res.string.desktop_start_exit)) }
            }
        }
    }
}
