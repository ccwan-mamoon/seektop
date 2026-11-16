package io.github.seektop.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seektop.data.mail.SeekLiAccount
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.mail_admin_create
import io.github.seektop.ui.resources.mail_admin_operation_error
import io.github.seektop.ui.resources.mailbox_available
import io.github.seektop.ui.resources.mailbox_checking
import io.github.seektop.ui.resources.mailbox_create_title
import io.github.seektop.ui.resources.mailbox_prefix
import io.github.seektop.ui.resources.mailbox_prefix_invalid
import io.github.seektop.ui.resources.mailbox_unavailable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MailboxCreation(account: SeekLiAccount, busy: Boolean, onCreate: (JsonObject) -> Unit) {
    val model = viewModel(key = "seekli-mailbox-creation") { MailboxCreationViewModel(account) }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(busy) { if (!busy) model.edit() }
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.mailbox_create_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.prefix,
                    onValueChange = { model.edit(prefix = it) },
                    placeholder = { Text(stringResource(Res.string.mailbox_prefix)) },
                    singleLine = true,
                    isError = (state.prefix.isNotEmpty() && !state.valid) || state.available == false,
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                )
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !busy) { Text(state.domain + " ▾") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("nodeseek.org", "seek.li").forEach { domain ->
                            DropdownMenuItem(text = { Text(domain) }, onClick = {
                                expanded = false
                                model.edit(domain = domain)
                            })
                        }
                    }
                }
                Button(onClick = { if (state.canCreate) onCreate(buildJsonObject { put("address", state.address) }) }, enabled = !busy && state.canCreate) { Text(stringResource(Res.string.mail_admin_create)) }
            }
            val message = when {
                state.prefix.isEmpty() -> ""
                !state.valid -> stringResource(Res.string.mailbox_prefix_invalid)
                state.checking -> stringResource(Res.string.mailbox_checking)
                state.available == false -> stringResource(Res.string.mailbox_unavailable)
                state.available == true -> stringResource(Res.string.mailbox_available)
                else -> stringResource(Res.string.mail_admin_operation_error) + " " + state.error
            }
            if (message.isNotEmpty()) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.available == false || !state.valid || (!state.checking && state.available == null)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
