package io.github.seektop.ui.mail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seektop.data.mail.SeekLiAccount
import io.github.seektop.data.mail.mailQuery
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.mail_admin_address_invalid
import io.github.seektop.ui.resources.mail_admin_apply
import io.github.seektop.ui.resources.mail_admin_back
import io.github.seektop.ui.resources.mail_admin_change_notice
import io.github.seektop.ui.resources.mail_admin_confirm
import io.github.seektop.ui.resources.mail_admin_create
import io.github.seektop.ui.resources.mail_admin_delete
import io.github.seektop.ui.resources.mail_admin_details
import io.github.seektop.ui.resources.mail_admin_disable
import io.github.seektop.ui.resources.mail_admin_edit
import io.github.seektop.ui.resources.mail_admin_empty
import io.github.seektop.ui.resources.mail_admin_enable
import io.github.seektop.ui.resources.mail_admin_invalid
import io.github.seektop.ui.resources.mail_admin_mailbox_notice
import io.github.seektop.ui.resources.mail_admin_more
import io.github.seektop.ui.resources.mail_admin_open
import io.github.seektop.ui.resources.mail_admin_operation_error
import io.github.seektop.ui.resources.mail_admin_release
import io.github.seektop.ui.resources.mail_admin_restore
import io.github.seektop.ui.resources.mail_admin_save
import io.github.seektop.ui.resources.mail_admin_search
import io.github.seektop.ui.resources.mail_admin_structured_help
import io.github.seektop.ui.resources.mail_admin_title
import io.github.seektop.ui.resources.mail_admin_upload
import io.github.seektop.ui.resources.mail_admin_upload_notice
import io.github.seektop.ui.resources.mail_auth_error
import io.github.seektop.ui.resources.mail_cancel
import io.github.seektop.ui.resources.mail_close
import io.github.seektop.ui.resources.mail_permission_error
import io.github.seektop.ui.resources.mail_refresh
import io.github.seektop.ui.resources.mail_settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource

private data class AdminEdit(val resource: String, val path: String, val method: String, val row: JsonObject = JsonObject(emptyMap()))
private data class AdminChange(val path: String, val method: String, val body: JsonObject? = null)

@Composable
fun MailAdminScreen(account: SeekLiAccount, initialResource: String = "mailboxes", onBack: () -> Unit, onOpenMail: (Long) -> Unit) {
    val model = viewModel(key = "seekli-management") { MailAdminViewModel(account) }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialResource) { model.load(initialResource) }
    var edit by remember { mutableStateOf<AdminEdit?>(null) }
    var change by remember { mutableStateOf<AdminChange?>(null) }
    var search by remember { mutableStateOf("") }
    val modules = listOf("mailboxes", "contacts", "folders", "tags", "filters", "signatures", "mailbox-release-holds", "mails/unread", "mails/counts", "upload", "storage", "quotas")
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(200.dp).fillMaxHeight().padding(12.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.mail_admin_back)) }
            Text(stringResource(Res.string.mail_admin_title), style = MaterialTheme.typography.titleMedium)
            modules.forEach { resource ->
                NavigationDrawerItem(selected = state.resource == resource, onClick = {
                    search = ""
                    model.load(resource)
                }, label = { Text(mailAdminLabel(resource)) }, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        VerticalDivider()
        Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(mailAdminLabel(state.resource), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.load() }, enabled = !state.busy) { Text(stringResource(Res.string.mail_refresh)) }
                if (state.resource != "mailboxes" && mailAdminFields(state.resource).isNotEmpty()) Button(onClick = { edit = AdminEdit(state.resource, "/${state.resource}", "POST") }, enabled = !state.busy) { Text(stringResource(Res.string.mail_admin_create)) }
            }
            if (state.resource == "upload") {
                Text(stringResource(Res.string.mail_admin_upload_notice))
                Button(onClick = model::upload, enabled = !state.busy) { Text(stringResource(Res.string.mail_admin_upload)) }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text(stringResource(Res.string.mail_admin_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (state.resource == "mailboxes") {
                MailboxCreation(account, state.busy) { body -> change = AdminChange("/mailboxes", "POST", body) }
            }
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                io.github.seektop.ui.common.DesktopScrollbars(listState) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        val rows = state.rows.filter { row -> row.values.any { value -> value.toString().contains(search, ignoreCase = true) } }
                        itemsIndexed(rows) { _, row ->
                            val id = (row["id"] ?: row["mailboxId"])?.jsonPrimitive?.longOrNull
                            val title = listOf("address", "name", "subject", "mailboxAddress", "username").firstNotNullOfOrNull { field -> row[field]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull }.orEmpty()
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (state.resource in listOf("storage", "quotas", "mails/counts")) {
                                        MailAdminValues(row)
                                    } else {
                                        listOf("createdAt", "receivedAt", "fromAddress", "releaseAfter", "status").forEach { name -> row[name]?.takeUnless { it is JsonNull }?.let { Text(mailAdminLabel(name) + ": " + it.jsonPrimitive.content, style = MaterialTheme.typography.bodySmall) } }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        when {
                                            state.resource == "mailboxes" && id != null -> {
                                                TextButton(onClick = { model.inspect("/mailboxes/$id") }) { Text(stringResource(Res.string.mail_admin_details)) }
                                                TextButton(onClick = { edit = AdminEdit("mailbox-settings", "/mailboxes/$id/settings", "PUT", row) }) { Text(stringResource(Res.string.mail_settings)) }
                                                TextButton(onClick = { change = AdminChange("/mailboxes/$id", "DELETE") }) { Text(stringResource(Res.string.mail_admin_release)) }
                                            }

                                            state.resource == "mailbox-release-holds" && id != null -> TextButton(onClick = { change = AdminChange("/mailbox-release-holds/$id/restore", "POST") }) { Text(stringResource(Res.string.mail_admin_restore)) }

                                            state.resource == "mails/unread" && id != null -> TextButton(onClick = { onOpenMail(id) }) { Text(stringResource(Res.string.mail_admin_open)) }

                                            state.resource in listOf("contacts", "folders", "tags", "filters", "signatures") && id != null -> {
                                                val system = row["isSystem"]?.jsonPrimitive?.booleanOrNull == true
                                                TextButton(onClick = { edit = AdminEdit(state.resource, "/${state.resource}/$id", "PUT", row) }, enabled = !system) { Text(stringResource(Res.string.mail_admin_edit)) }
                                                TextButton(onClick = { change = AdminChange("/${state.resource}/$id", "DELETE") }, enabled = !system) { Text(stringResource(Res.string.mail_admin_delete)) }
                                                if (state.resource == "filters") TextButton(onClick = { change = AdminChange("/filters/$id", "PUT", buildJsonObject { put("isActive", row["isActive"]?.jsonPrimitive?.booleanOrNull != true) }) }) { Text(stringResource(if (row["isActive"]?.jsonPrimitive?.booleanOrNull == true) Res.string.mail_admin_disable else Res.string.mail_admin_enable)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (rows.isEmpty() && !state.busy) item { Text(stringResource(Res.string.mail_admin_empty)) }
                    }
                }
            }
            if (state.nextCursor.isNotEmpty()) TextButton(onClick = { model.load(cursor = state.nextCursor) }) { Text(stringResource(Res.string.mail_admin_more)) }
        }
    }
    edit?.let { current ->
        MailAdminEditor(current, onDismiss = { edit = null }, onSave = { body ->
            change = if (current.resource == "check") AdminChange("/mailboxes/check?address=${mailQuery(body.getValue("address").jsonPrimitive.content)}", "POST") else AdminChange(current.path, current.method, body)
            edit = null
        })
    }
    change?.let { pending ->
        AlertDialog(onDismissRequest = { change = null }, title = { Text(stringResource(Res.string.mail_admin_confirm)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(if (pending.path.startsWith("/mailboxes")) Res.string.mail_admin_mailbox_notice else Res.string.mail_admin_change_notice))
                pending.body?.let { MailAdminValues(it) }
            }
        }, confirmButton = {
            TextButton(onClick = {
                change = null
                model.mutate(pending.path, pending.method, pending.body)
            }) { Text(stringResource(Res.string.mail_admin_apply)) }
        }, dismissButton = { TextButton(onClick = { change = null }) { Text(stringResource(Res.string.mail_cancel)) } })
    }
    (state.detail ?: state.result)?.let { value -> AlertDialog(onDismissRequest = model::closeDetail, text = { Column(Modifier.verticalScroll(rememberScrollState())) { MailAdminValues(value) } }, confirmButton = { TextButton(onClick = model::closeDetail) { Text(stringResource(Res.string.mail_close)) } }) }
    state.error?.let { code ->
        AlertDialog(onDismissRequest = model::clearError, text = {
            Text(
                stringResource(
                    if (code == 403) {
                        Res.string.mail_permission_error
                    } else if (code == 401) {
                        Res.string.mail_auth_error
                    } else {
                        Res.string.mail_admin_operation_error
                    },
                ) + " ($code)" + state.errorDescription.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty(),
            )
        }, confirmButton = { TextButton(onClick = model::clearError) { Text(stringResource(Res.string.mail_close)) } })
    }
}

@Composable
private fun MailAdminEditor(edit: AdminEdit, onDismiss: () -> Unit, onSave: (JsonObject) -> Unit) {
    val fields = if (edit.resource == "check") listOf(MailAdminField("address", "string", true)) else mailAdminFields(edit.resource)
    val values = remember(edit) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { field -> put(field.name, edit.row[field.name]?.takeUnless { it is JsonNull }?.let { if (field.type == "object" && it !is JsonPrimitive) it.toString() else it.jsonPrimitive.content }.orEmpty()) }
        }
    }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(mailAdminLabel(edit.resource)) }, text = {
        Column(Modifier.widthIn(min = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEach { field ->
                if (field.type == "boolean") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = values[field.name] == "true", onCheckedChange = { values[field.name] = it.toString() })
                        Text(mailAdminLabel(field.name))
                    }
                } else {
                    OutlinedTextField(value = values[field.name].orEmpty(), onValueChange = { values[field.name] = it }, label = { Text(mailAdminLabel(field.name) + if (field.required) " *" else "") }, minLines = if (field.type == "object" || field.name.startsWith("content")) 3 else 1, modifier = Modifier.fillMaxWidth())
                }
            }
            if (fields.any { it.type == "object" }) Text(stringResource(Res.string.mail_admin_structured_help), style = MaterialTheme.typography.bodySmall)
            if (invalid) Text(stringResource(if (edit.resource in listOf("mailboxes", "check")) Res.string.mail_admin_address_invalid else Res.string.mail_admin_invalid), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        TextButton(onClick = {
            try {
                val body = buildJsonObject {
                    fields.forEach { field ->
                        val value = values[field.name].orEmpty()
                        require(!field.required || value.isNotBlank())
                        if (field.name == "address") require(isSeekLiMailboxAddress(value.trim()))
                        if (value.isNotBlank() || field.type == "boolean" || (edit.method == "PUT" && field.type == "string")) {
                            when (field.type) {
                                "boolean" -> put(field.name, value == "true")
                                "integer" -> put(field.name, value.toLong())
                                "object" -> put(field.name, Json.parseToJsonElement(value).jsonObject)
                                else -> put(field.name, if (field.name == "address") value.trim() else value)
                            }
                        }
                    }
                }
                onSave(body)
            } catch (_: IllegalArgumentException) {
                invalid = true
            }
        }) { Text(stringResource(Res.string.mail_admin_save)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.mail_cancel)) } })
}

@Composable
private fun MailAdminValues(value: JsonObject) {
    value.forEach { (key, item) ->
        if (item is JsonObject) {
            Text(mailAdminLabel(key), style = MaterialTheme.typography.titleSmall)
            MailAdminValues(item)
        } else {
            Text(mailAdminLabel(key) + ": " + if (item is JsonPrimitive) item.content else item.toString(), modifier = Modifier.padding(vertical = 3.dp))
        }
    }
}
