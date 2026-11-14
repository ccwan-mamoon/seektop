package io.github.seektop.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.seektop.data.AttendanceMode
import io.github.seektop.foundation.designsys.component.SeekTopIcons
import io.github.seektop.foundation.designsys.component.TonalTile
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_cancel
import io.github.seektop.ui.resources.assets_sign_in_choice_hint
import io.github.seektop.ui.resources.assets_sign_in_choice_title
import io.github.seektop.ui.resources.assets_sign_in_fixed_label
import io.github.seektop.ui.resources.assets_sign_in_fixed_value
import io.github.seektop.ui.resources.assets_sign_in_random_label
import io.github.seektop.ui.resources.assets_sign_in_random_value
import org.jetbrains.compose.resources.stringResource

/** Shared choice UI. The desktop startup caller saves the first choice; manual callers may use it once. */
@Composable
fun AttendanceModeDialog(
    onPick: (AttendanceMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(SeekTopIcons.EventAvailable, contentDescription = null) },
        title = { Text(stringResource(Res.string.assets_sign_in_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val scheme = MaterialTheme.colorScheme
                    listOf(
                        Triple(SeekTopIcons.Casino, AttendanceMode.RANDOM, scheme.primary),
                        Triple(SeekTopIcons.PushPin, AttendanceMode.FIXED_FIVE, scheme.secondaryContainer),
                    ).forEach { (icon, mode, container) ->
                        val random = mode == AttendanceMode.RANDOM
                        TonalTile(
                            onClick = { onPick(mode) },
                            containerColor = container,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                            modifier = Modifier.weight(1f).heightIn(min = 96.dp),
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                            Text(
                                stringResource(
                                    if (random) Res.string.assets_sign_in_random_label else Res.string.assets_sign_in_fixed_label,
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                stringResource(
                                    if (random) Res.string.assets_sign_in_random_value else Res.string.assets_sign_in_fixed_value,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(Res.string.assets_sign_in_choice_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
