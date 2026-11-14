package io.github.seektop.ui.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import io.github.seektop.plugins.ToolListing
import io.github.seektop.plugins.ToolManifest
import io.github.seektop.plugins.ToolPluginManager
import io.github.seektop.plugins.ToolText
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_cancel
import io.github.seektop.ui.resources.plugin_center
import io.github.seektop.ui.resources.plugin_disable
import io.github.seektop.ui.resources.plugin_empty
import io.github.seektop.ui.resources.plugin_enable
import io.github.seektop.ui.resources.plugin_import
import io.github.seektop.ui.resources.plugin_install
import io.github.seektop.ui.resources.plugin_installed
import io.github.seektop.ui.resources.plugin_market
import io.github.seektop.ui.resources.plugin_native_consent
import io.github.seektop.ui.resources.plugin_native_title
import io.github.seektop.ui.resources.plugin_open
import io.github.seektop.ui.resources.plugin_refresh
import io.github.seektop.ui.resources.plugin_start
import io.github.seektop.ui.resources.plugin_stop
import io.github.seektop.ui.resources.plugin_uninstall
import io.github.seektop.ui.resources.plugin_uninstall_confirm
import io.github.seektop.ui.resources.plugin_update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private fun ToolText.localized(): String = when {
    Locale.current.language == "en" -> en
    Locale.current.region in setOf("TW", "HK", "MO") -> tw
    else -> zh
}

@Composable
fun ToolPluginCenter(manager: ToolPluginManager, initialPluginId: String? = null) {
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var market by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf(initialPluginId) }
    var consent by remember { mutableStateOf<ToolListing?>(null) }
    var importing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ToolManifest?>(null) }
    val selected = state.installed.find { it.manifest.id == selectedId }
    LaunchedEffect(market) { if (market && state.catalog.isEmpty()) manager.refresh() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.plugin_center), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                market = false
                selectedId = null
            }) { Text(stringResource(Res.string.plugin_installed)) }
            TextButton(onClick = {
                market = true
                selectedId = null
            }) { Text(stringResource(Res.string.plugin_market)) }
            OutlinedButton(onClick = { importing = true }, enabled = !state.busy && state.runningId == null) { Text(stringResource(Res.string.plugin_import)) }
            OutlinedButton(onClick = { scope.launch { manager.refresh() } }, enabled = !state.busy) { Text(stringResource(Res.string.plugin_refresh)) }
        }
        if (state.busy) {
            val progress = state.progress
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.runningId?.let { id ->
            val tool = state.installed.find { it.manifest.id == id }
            TextButton(onClick = {
                market = false
                selectedId = id
            }) {
                Text((tool?.manifest?.name?.localized() ?: id) + " · " + (state.event?.phase?.localized().orEmpty()))
            }
        }
        when {
            selected != null && !market -> {
                ToolPage(selected.manifest, selected.enabled, manager, Modifier.weight(1f))
            }

            market -> {
                if (state.catalog.isEmpty() && !state.busy) Text(stringResource(Res.string.plugin_empty))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.catalog, key = { it.manifest.id }) { listing ->
                        val installed = state.installed.find { it.manifest.id == listing.manifest.id }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(listing.manifest.name.localized() + " · " + listing.manifest.version, style = MaterialTheme.typography.titleMedium)
                                Text(listing.manifest.description.localized())
                                Text("${listing.size / 1024 / 1024} MiB", style = MaterialTheme.typography.labelMedium)
                                Button(onClick = { consent = listing }, enabled = !state.busy && state.runningId != listing.manifest.id && installed?.manifest?.version != listing.manifest.version) {
                                    Text(stringResource(if (installed == null) Res.string.plugin_install else Res.string.plugin_update))
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                if (state.installed.isEmpty()) Text(stringResource(Res.string.plugin_empty))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.installed, key = { it.manifest.id }) { tool ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tool.manifest.name.localized() + " · " + tool.manifest.version, style = MaterialTheme.typography.titleMedium)
                                Text(tool.manifest.description.localized())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { selectedId = tool.manifest.id }, enabled = tool.enabled) { Text(stringResource(Res.string.plugin_open)) }
                                    OutlinedButton(onClick = { scope.launch { manager.setEnabled(tool.manifest.id, !tool.enabled) } }, enabled = !state.busy && state.runningId != tool.manifest.id) { Text(stringResource(if (tool.enabled) Res.string.plugin_disable else Res.string.plugin_enable)) }
                                    TextButton(onClick = { removing = tool.manifest }, enabled = !state.busy && state.runningId != tool.manifest.id) { Text(stringResource(Res.string.plugin_uninstall)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (consent != null || importing) {
        AlertDialog(
            onDismissRequest = {
                consent = null
                importing = false
            },
            title = { Text(stringResource(Res.string.plugin_native_title)) },
            text = { Text(stringResource(Res.string.plugin_native_consent)) },
            confirmButton = {
                TextButton(onClick = {
                    val listing = consent
                    consent = null
                    importing = false
                    scope.launch { if (listing == null) manager.importPackage() else manager.install(listing) }
                }) { Text(stringResource(Res.string.plugin_install)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    consent = null
                    importing = false
                }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }
    removing?.let { tool ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.plugin_uninstall)) },
            text = { Text(stringResource(Res.string.plugin_uninstall_confirm, tool.name.localized())) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    selectedId = null
                    scope.launch { manager.uninstall(tool.id) }
                }) { Text(stringResource(Res.string.plugin_uninstall)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ToolPage(manifest: ToolManifest, enabled: Boolean, manager: ToolPluginManager, modifier: Modifier = Modifier) {
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var parameters by rememberSaveable(manifest.id, manifest.version) { mutableStateOf(manifest.fields.associate { it.id to it.default }) }
    val running = state.runningId != null
    // Results belong to the page that started them, including after the worker exits.
    val ownResult = state.event.takeIf { state.resultPluginId == manifest.id }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(manifest.name.localized(), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(manifest.fields, key = { it.id }) { field ->
                    if (field.options.isEmpty()) {
                        OutlinedTextField(value = parameters[field.id].orEmpty(), onValueChange = { if (it.length <= 2048) parameters = parameters + (field.id to it) }, label = { Text(field.label.localized()) }, enabled = !running, singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        Column {
                            Text(field.label.localized(), style = MaterialTheme.typography.labelLarge)
                            OutlinedButton(onClick = { expanded = true }, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text(field.options.find { it.value == parameters[field.id] }?.label?.localized().orEmpty()) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                field.options.forEach { option ->
                                    DropdownMenuItem(text = { Text(option.label.localized()) }, onClick = {
                                        parameters = parameters + (field.id to option.value)
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { scope.launch { manager.run(manifest.id, parameters) } }, enabled = enabled && !running && !state.busy) { Text(stringResource(Res.string.plugin_start)) }
                OutlinedButton(onClick = { scope.launch { manager.stop() } }, enabled = state.runningId == manifest.id) { Text(stringResource(Res.string.plugin_stop)) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(manifest.notice.localized(), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ownResult?.let { result ->
                item { Text(result.phase.localized(), color = if (result.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
                items(result.metrics) { metric ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(metric.label.localized(), modifier = Modifier.weight(1f))
                        Text(metric.value, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(result.rows) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(row.target, style = MaterialTheme.typography.titleSmall)
                            Text(row.status.localized())
                            Text(row.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
