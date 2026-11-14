package io.github.seektop.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_back
import io.github.seektop.ui.resources.action_create_post
import io.github.seektop.ui.resources.action_search
import io.github.seektop.ui.resources.desktop_help_body
import io.github.seektop.ui.resources.help_title
import io.github.seektop.ui.resources.settings_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopHelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.help_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(Res.string.desktop_help_body), style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider()
        listOf("Ctrl K" to Res.string.action_search, "Ctrl N" to Res.string.action_create_post, "Alt ←" to Res.string.action_back, "Ctrl ," to Res.string.settings_title).forEach { (shortcut, label) ->
            Row(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                Text(stringResource(label), Modifier.weight(1f))
                Text(shortcut, color = MaterialTheme.colorScheme.primary)
            }
        }
        TextButton(onClick = onBack) { Text(stringResource(Res.string.action_back)) }
    }
}
