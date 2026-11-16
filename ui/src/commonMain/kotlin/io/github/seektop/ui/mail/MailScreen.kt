package io.github.seektop.ui.mail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seektop.data.mail.SeekLiAccount
import io.github.seektop.foundation.core.richtext.parseMarkdown
import io.github.seektop.foundation.designsys.component.EditorTextField
import io.github.seektop.foundation.designsys.editor.EditorAction
import io.github.seektop.foundation.designsys.editor.applyMarkdown
import io.github.seektop.foundation.designsys.richtext.RichContent
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.mail_add_tag
import io.github.seektop.ui.resources.mail_admin_delete
import io.github.seektop.ui.resources.mail_admin_empty
import io.github.seektop.ui.resources.mail_admin_section_folders
import io.github.seektop.ui.resources.mail_admin_section_tags
import io.github.seektop.ui.resources.mail_all_boxes
import io.github.seektop.ui.resources.mail_attachments
import io.github.seektop.ui.resources.mail_auth_error
import io.github.seektop.ui.resources.mail_bcc
import io.github.seektop.ui.resources.mail_body
import io.github.seektop.ui.resources.mail_cancel
import io.github.seektop.ui.resources.mail_cc
import io.github.seektop.ui.resources.mail_close
import io.github.seektop.ui.resources.mail_compose
import io.github.seektop.ui.resources.mail_confirm_send
import io.github.seektop.ui.resources.mail_connect
import io.github.seektop.ui.resources.mail_copies
import io.github.seektop.ui.resources.mail_delete_confirm
import io.github.seektop.ui.resources.mail_developer_link
import io.github.seektop.ui.resources.mail_disconnect
import io.github.seektop.ui.resources.mail_disconnect_confirm
import io.github.seektop.ui.resources.mail_edit_markdown
import io.github.seektop.ui.resources.mail_empty
import io.github.seektop.ui.resources.mail_flag
import io.github.seektop.ui.resources.mail_from
import io.github.seektop.ui.resources.mail_inbox
import io.github.seektop.ui.resources.mail_key_help
import io.github.seektop.ui.resources.mail_load_error
import io.github.seektop.ui.resources.mail_manage_boxes
import io.github.seektop.ui.resources.mail_manage_messages
import io.github.seektop.ui.resources.mail_mark_read
import io.github.seektop.ui.resources.mail_mark_unread
import io.github.seektop.ui.resources.mail_move
import io.github.seektop.ui.resources.mail_network_error
import io.github.seektop.ui.resources.mail_no_subject
import io.github.seektop.ui.resources.mail_permission_error
import io.github.seektop.ui.resources.mail_poll_interval
import io.github.seektop.ui.resources.mail_poll_minutes
import io.github.seektop.ui.resources.mail_poll_seconds
import io.github.seektop.ui.resources.mail_preview
import io.github.seektop.ui.resources.mail_rate_error
import io.github.seektop.ui.resources.mail_raw
import io.github.seektop.ui.resources.mail_remove_tag
import io.github.seektop.ui.resources.mail_search
import io.github.seektop.ui.resources.mail_search_boxes
import io.github.seektop.ui.resources.mail_search_messages
import io.github.seektop.ui.resources.mail_select
import io.github.seektop.ui.resources.mail_send
import io.github.seektop.ui.resources.mail_sent
import io.github.seektop.ui.resources.mail_settings
import io.github.seektop.ui.resources.mail_spam
import io.github.seektop.ui.resources.mail_subject
import io.github.seektop.ui.resources.mail_title
import io.github.seektop.ui.resources.mail_to
import io.github.seektop.ui.resources.mail_trash
import io.github.seektop.ui.resources.mail_unflag
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource

@Composable
fun MailScreen(account: SeekLiAccount) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.red + colors.surface.green + colors.surface.blue < 1.5f
    MaterialTheme(
        colorScheme = colors.copy(
            primary = if (dark) Color(0xFFA8C7FA) else Color(0xFF245BC2),
            onPrimary = if (dark) Color(0xFF102F60) else Color.White,
            primaryContainer = if (dark) Color(0xFF233D63) else Color(0xFFE7EFFF),
            onPrimaryContainer = if (dark) Color(0xFFD7E6FF) else Color(0xFF17365D),
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = colors.surface, contentColor = colors.onSurface) { MailScreenContent(account) }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    if (count > 0) {
        Badge(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) {
            Text(if (count > 999) "999+" else count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MailScreenContent(account: SeekLiAccount) {
    val uriHandler = LocalUriHandler.current
    val model = viewModel(key = "seekli-mail") { MailViewModel(account) }
    LaunchedEffect(model) { model.refresh() }
    val state by model.state.collectAsStateWithLifecycle()
    var managementSection by remember { mutableStateOf("mailboxes") }
    var deletingMail by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var managing by remember { mutableStateOf(false) }
    if (managing) {
        MailAdminScreen(account, initialResource = managementSection, onBack = {
            managing = false
            model.refresh()
        }, onOpenMail = { id ->
            managing = false
            model.open(io.github.seektop.data.mail.SeekLiMail(id))
        })
        return
    }
    var configuring by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    if (!state.connected || configuring) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.mail_title), style = MaterialTheme.typography.headlineSmall)
            var choosingInterval by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { choosingInterval = true }) {
                    Text(stringResource(Res.string.mail_poll_interval) + ": " + stringResource(if (state.pollSeconds < 60) Res.string.mail_poll_seconds else Res.string.mail_poll_minutes, if (state.pollSeconds < 60) state.pollSeconds else state.pollSeconds / 60))
                }
                DropdownMenu(expanded = choosingInterval, onDismissRequest = { choosingInterval = false }) {
                    listOf(30, 60, 120, 300, 600, 1800, 3600).forEach { seconds ->
                        DropdownMenuItem(text = { Text(stringResource(if (seconds < 60) Res.string.mail_poll_seconds else Res.string.mail_poll_minutes, if (seconds < 60) seconds else seconds / 60)) }, onClick = {
                            model.setPollSeconds(seconds)
                            choosingInterval = false
                        })
                    }
                }
            }
            Text(stringResource(Res.string.mail_key_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { uriHandler.openUri("https://seek.li/manage/support?tab=developer") }) { Text(stringResource(Res.string.mail_developer_link)) }
            OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Button(onClick = {
                model.connect(key)
                key = ""
                configuring = false
            }, enabled = key.isNotBlank() && !state.busy) { Text(stringResource(Res.string.mail_connect)) }
            if (state.connected) {
                TextButton(onClick = { configuring = false }) { Text(stringResource(Res.string.mail_cancel)) }
                TextButton(onClick = { disconnecting = true }) { Text(stringResource(Res.string.mail_disconnect)) }
            }
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(232.dp).fillMaxHeight().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.mail_title), style = MaterialTheme.typography.titleMedium)
                var choosingMailbox by remember { mutableStateOf(false) }
                var mailboxSearch by remember { mutableStateOf("") }
                Box {
                    OutlinedButton(onClick = { choosingMailbox = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(state.mailboxes.firstOrNull { it.id == state.mailbox }?.address ?: stringResource(Res.string.mail_all_boxes), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        UnreadBadge(if (state.mailbox == null) state.unread.values.sum() else state.unread[state.mailbox] ?: 0)
                        Text("⌄", Modifier.padding(start = 8.dp))
                    }
                    DropdownMenu(expanded = choosingMailbox, onDismissRequest = { choosingMailbox = false }, modifier = Modifier.width(280.dp).heightIn(max = 420.dp)) {
                        val menuScroll = rememberScrollState()
                        io.github.seektop.ui.common.DesktopScrollbars(menuScroll, Modifier.width(280.dp).height(320.dp)) {
                            Column(Modifier.verticalScroll(menuScroll).padding(end = 12.dp)) {
                                OutlinedTextField(value = mailboxSearch, onValueChange = { mailboxSearch = it }, placeholder = { Text(stringResource(Res.string.mail_search_boxes)) }, singleLine = true, modifier = Modifier.padding(8.dp))
                                DropdownMenuItem(text = { Text(stringResource(Res.string.mail_all_boxes)) }, trailingIcon = { UnreadBadge(state.unread.values.sum()) }, onClick = {
                                    model.select(null, state.folder)
                                    choosingMailbox = false
                                })
                                state.mailboxes.filter { it.address.contains(mailboxSearch, ignoreCase = true) }.forEach { box ->
                                    DropdownMenuItem(text = { Text(box.address, maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingIcon = { UnreadBadge(state.unread[box.id] ?: 0) }, onClick = {
                                        model.select(box.id, state.folder)
                                        choosingMailbox = false
                                    })
                                }
                            }
                        }
                    }
                }
                Button(onClick = {
                    model.resetSent()
                    composing = true
                }, enabled = state.mailboxes.any { !it.sendDisabled }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(Res.string.mail_compose)) }
                HorizontalDivider()
                listOf("inbox" to Res.string.mail_inbox, "sent" to Res.string.mail_sent, "trash" to Res.string.mail_trash, "spam" to Res.string.mail_spam).forEach { (folder, label) ->
                    TextButton(onClick = { model.select(state.mailbox, folder) }) { Text(stringResource(label), fontWeight = if (state.folder == folder) FontWeight.Bold else FontWeight.Normal) }
                }
                HorizontalDivider()
                Text(stringResource(Res.string.mail_admin_section_folders), style = MaterialTheme.typography.labelMedium)
                state.folders.forEach { folder ->
                    TextButton(onClick = { model.select(state.mailbox, "inbox", folderId = folder.getValue("id").jsonPrimitive.long) }) { Text(folder.getValue("name").jsonPrimitive.content, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Text(stringResource(Res.string.mail_admin_section_tags), style = MaterialTheme.typography.labelMedium)
                state.tags.forEach { tag ->
                    TextButton(onClick = { model.select(state.mailbox, "inbox", tagId = tag.getValue("id").jsonPrimitive.long) }) { Text(tag.getValue("name").jsonPrimitive.content, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                HorizontalDivider()
                TextButton(onClick = {
                    managementSection = "mailboxes"
                    managing = true
                }) { Text(stringResource(Res.string.mail_manage_boxes)) }
                TextButton(onClick = {
                    managementSection = "filters"
                    managing = true
                }) { Text(stringResource(Res.string.mail_manage_messages)) }
                TextButton(onClick = { configuring = true }) { Text(stringResource(Res.string.mail_settings)) }
            }
            VerticalDivider()
            Column(Modifier.width(360.dp).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text(stringResource(Res.string.mail_search_messages)) }, modifier = Modifier.weight(1f))
                    TextButton(onClick = { model.search(query) }, enabled = !state.busy) { Text(stringResource(Res.string.mail_search)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { model.select(state.mailbox, state.folder, (state.offset - 20).coerceAtLeast(0), state.folderId, state.tagId) }, enabled = state.offset > 0 && !state.busy) { Text("‹") }
                    TextButton(onClick = { model.select(state.mailbox, state.folder, state.offset + 20, state.folderId, state.tagId) }, enabled = state.offset + state.mails.size < state.total && !state.busy) { Text("›") }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    io.github.seektop.ui.common.DesktopScrollbars(listState) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(state.mails, key = { it.id }) { mail ->
                                TextButton(onClick = { model.open(mail) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = if (state.detail?.id == mail.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) {
                                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                                        Text(mail.fromName.ifBlank { mail.fromAddress }, maxLines = 1, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(mail.subject.ifBlank { stringResource(Res.string.mail_no_subject) }, maxLines = 2, fontWeight = if (mail.isRead) FontWeight.Normal else FontWeight.Bold)
                                        Text(mailDateLabel(mail.receivedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider()
                            }
                            if (state.mails.isEmpty() && !state.busy) item { Text(stringResource(Res.string.mail_empty), Modifier.padding(16.dp)) }
                        }
                    }
                }
            }
            VerticalDivider()
            Column(Modifier.weight(1f).fillMaxHeight().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val mail = state.detail
                if (mail == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.mail_select), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    Text(mail.subject.ifBlank { stringResource(Res.string.mail_no_subject) }, style = MaterialTheme.typography.titleLarge)
                    Text(mail.fromAddress)
                    Text(mail.toAddress)
                    Text(mailDateLabel(mail.receivedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = model::markRead) { Text(stringResource(if (mail.isRead) Res.string.mail_mark_unread else Res.string.mail_mark_read)) }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { model.operate("PUT", "/flag", buildJsonObject { put("flagged", !mail.isFlagged) }) }) { Text(stringResource(if (mail.isFlagged) Res.string.mail_unflag else Res.string.mail_flag)) }
                        TextButton(onClick = { model.choices("folder") }) { Text(stringResource(Res.string.mail_move)) }
                        TextButton(onClick = { model.choices("tag") }) { Text(stringResource(Res.string.mail_add_tag)) }
                        TextButton(onClick = { model.choices("untag") }) { Text(stringResource(Res.string.mail_remove_tag)) }
                        TextButton(onClick = { deletingMail = true }) { Text(stringResource(Res.string.mail_admin_delete)) }
                        TextButton(onClick = { model.download() }) { Text(stringResource(Res.string.mail_raw)) }
                        if (mail.hasAttachments) TextButton(onClick = { model.choices("attachments") }) { Text(stringResource(Res.string.mail_attachments)) }
                    }
                    HorizontalDivider()
                    SelectionContainer(modifier = Modifier.weight(1f)) { Text(mail.plainText, modifier = Modifier.verticalScroll(rememberScrollState())) }
                }
            }
        }
    }
    if (deletingMail) {
        AlertDialog(onDismissRequest = { deletingMail = false }, text = { Text(stringResource(Res.string.mail_delete_confirm)) }, confirmButton = {
            TextButton(onClick = {
                deletingMail = false
                model.operate("DELETE", "")
            }) { Text(stringResource(Res.string.mail_admin_delete)) }
        }, dismissButton = { TextButton(onClick = { deletingMail = false }) { Text(stringResource(Res.string.mail_cancel)) } })
    }
    state.menu?.let { menu ->
        AlertDialog(onDismissRequest = model::closeMenu, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (menu == "folder") TextButton(onClick = { model.operate("PUT", "/folder", buildJsonObject { put("folderId", JsonNull) }) }) { Text(stringResource(Res.string.mail_inbox)) }
                state.choices.forEach { item ->
                    TextButton(onClick = {
                        val id = item["id"]?.jsonPrimitive?.longOrNull
                        when (menu) {
                            "folder" -> model.operate("PUT", "/folder", buildJsonObject { put("folderId", checkNotNull(id)) })
                            "tag" -> model.operate("POST", "/tags", buildJsonObject { put("tagId", checkNotNull(id)) })
                            "untag" -> model.operate("DELETE", "/tags/${checkNotNull(id)}")
                            else -> item["index"]?.jsonPrimitive?.intOrNull?.takeIf { it >= 0 }?.let { model.download(it, item["filename"]?.jsonPrimitive?.content) }
                        }
                    }) { Text((item["name"] ?: item["filename"] ?: item["id"])?.jsonPrimitive?.content.orEmpty()) }
                }
                if (state.choices.isEmpty()) Text(stringResource(Res.string.mail_admin_empty))
            }
        }, confirmButton = { TextButton(onClick = model::closeMenu) { Text(stringResource(Res.string.mail_close)) } })
    }
    if (disconnecting) {
        AlertDialog(onDismissRequest = { disconnecting = false }, text = { Text(stringResource(Res.string.mail_disconnect_confirm)) }, confirmButton = {
            TextButton(onClick = {
                disconnecting = false
                configuring = false
                model.disconnect()
            }) { Text(stringResource(Res.string.mail_disconnect)) }
        }, dismissButton = { TextButton(onClick = { disconnecting = false }) { Text(stringResource(Res.string.mail_cancel)) } })
    }
    if (composing) {
        var sender by remember { mutableStateOf(state.mailboxes.firstOrNull { !it.sendDisabled }?.id) }
        var to by remember { mutableStateOf("") }
        var cc by remember { mutableStateOf("") }
        var bcc by remember { mutableStateOf("") }
        var copies by remember { mutableStateOf(false) }
        var subject by remember { mutableStateOf("") }
        val bodyState = rememberTextFieldState()
        var preview by remember { mutableStateOf(false) }
        var confirm by remember { mutableStateOf(false) }
        LaunchedEffect(state.sent) { if (state.sent) composing = false }
        Dialog(onDismissRequest = { if (!state.sending) composing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.widthIn(max = 780.dp).fillMaxWidth(0.9f).fillMaxHeight(0.9f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.mail_compose), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { composing = false }, enabled = !state.sending) { Text("×") }
                    }
                    HorizontalDivider()
                    var choosingSender by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.mail_from), modifier = Modifier.width(72.dp))
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { choosingSender = true }, enabled = !state.sending, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Text(state.mailboxes.firstOrNull { it.id == sender }?.address.orEmpty(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("⌄")
                            }
                            DropdownMenu(expanded = choosingSender, onDismissRequest = { choosingSender = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                                val menuScroll = rememberScrollState()
                                io.github.seektop.ui.common.DesktopScrollbars(menuScroll, Modifier.width(280.dp).height(320.dp)) {
                                    Column(Modifier.verticalScroll(menuScroll).padding(end = 12.dp)) {
                                        state.mailboxes.filter { !it.sendDisabled }.forEach { box ->
                                            DropdownMenuItem(text = { Text(box.address) }, onClick = {
                                                sender = box.id
                                                choosingSender = false
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.mail_to), modifier = Modifier.width(72.dp))
                        OutlinedTextField(value = to, onValueChange = { to = it }, enabled = !state.sending, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    TextButton(onClick = { copies = !copies }) { Text(stringResource(Res.string.mail_copies)) }
                    if (copies) {
                        OutlinedTextField(value = cc, onValueChange = { cc = it }, label = { Text(stringResource(Res.string.mail_cc)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.sending)
                        OutlinedTextField(value = bcc, onValueChange = { bcc = it }, label = { Text(stringResource(Res.string.mail_bcc)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.sending)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.mail_subject), modifier = Modifier.width(72.dp))
                        OutlinedTextField(value = subject, onValueChange = { subject = it }, enabled = !state.sending, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        listOf(EditorAction.BOLD to "B", EditorAction.ITALIC to "I", EditorAction.STRIKETHROUGH to "S", EditorAction.HEADING to "H", EditorAction.LIST to "•", EditorAction.QUOTE to ">", EditorAction.CODE to "<>", EditorAction.LINK to "↗").forEach { (action, label) ->
                            TextButton(onClick = { bodyState.edit { applyMarkdown(action) } }, enabled = !state.sending && !preview, modifier = Modifier.width(44.dp)) { Text(label) }
                        }
                        TextButton(onClick = { preview = !preview }) { Text(stringResource(if (preview) Res.string.mail_edit_markdown else Res.string.mail_preview)) }
                    }
                    Surface(Modifier.fillMaxWidth().weight(1f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(12.dp)) {
                        if (preview) {
                            val nodes = parseMarkdown(bodyState.text.toString())
                            RichContent(nodes = nodes, onLinkClick = {}, onImageClick = {}, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()))
                        } else {
                            EditorTextField(state = bodyState, hint = stringResource(Res.string.mail_body), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxSize().padding(12.dp))
                        }
                    }
                    if (state.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { confirm = true }, enabled = !state.sending && sender != null && to.isNotBlank(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(Res.string.mail_send)) }
                        TextButton(onClick = { composing = false }, enabled = !state.sending) { Text(stringResource(Res.string.mail_cancel)) }
                    }
                }
            }
        }
        if (confirm) {
            AlertDialog(onDismissRequest = { confirm = false }, text = { Text(stringResource(Res.string.mail_confirm_send) + "\n" + to + "\n" + cc + "\n" + bcc) }, confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    model.send(checkNotNull(sender), to, subject, bodyState.text.toString(), cc, bcc)
                }) { Text(stringResource(Res.string.mail_send)) }
            }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(Res.string.mail_cancel)) } })
        }
    }
    state.error?.let { code ->
        AlertDialog(onDismissRequest = model::clearError, confirmButton = { TextButton(onClick = model::clearError) { Text(stringResource(Res.string.mail_close)) } }, text = {
            Text(
                stringResource(
                    when (code) {
                        401 -> Res.string.mail_auth_error
                        403 -> Res.string.mail_permission_error
                        429 -> Res.string.mail_rate_error
                        else -> if (state.sendError) Res.string.mail_network_error else Res.string.mail_load_error
                    },
                ) + " ($code)",
            )
        })
    }
}
