package io.github.seektop.ui.plugins

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.seektop.plugins.ToolField
import io.github.seektop.plugins.ToolManifest
import io.github.seektop.plugins.ToolPluginManager
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_back
import io.github.seektop.ui.resources.connection_settings
import io.github.seektop.ui.resources.plugin_start
import io.github.seektop.ui.resources.plugin_stop
import io.github.seektop.ui.resources.tool_chart
import io.github.seektop.ui.resources.tool_details
import io.github.seektop.ui.resources.tool_done
import io.github.seektop.ui.resources.tool_down
import io.github.seektop.ui.resources.tool_ready
import io.github.seektop.ui.resources.tool_settings
import io.github.seektop.ui.resources.tool_up
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Native presentation of the three official tools, following Bettbox's information hierarchy.
 * Flutter widgets cannot be embedded in Compose; controls remain Material 3 components.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkToolPage(manifest: ToolManifest, enabled: Boolean, manager: ToolPluginManager, modifier: Modifier, onBack: () -> Unit) {
    if (manifest.id.endsWith(".speed")) {
        ReferenceSpeedPage(manifest, enabled, manager, modifier, onBack)
        return
    }
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var parameters by rememberSaveable(manifest.id, manifest.version) { mutableStateOf(manifest.fields.associate { it.id to it.default }) }
    var settings by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val result = state.event.takeIf { state.resultPluginId == manifest.id }
    val running = state.runningId == manifest.id
    val speed = manifest.id.endsWith(".speed")
    val connectivity = manifest.id.endsWith(".connectivity")
    var samples by remember(manifest.id) { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    LaunchedEffect(result) {
        if (result == null) {
            samples = emptyList()
        } else if (running && !connectivity) {
            fun rate(label: String) = result.metrics.find { it.label.en == label }?.value?.substringBefore(' ')?.toFloatOrNull() ?: 0f
            val live = rate("Live rate")
            val point = if (speed) {
                if (result.phase.en.startsWith("Upload")) 0f to live else live to 0f
            } else {
                rate("Download") to rate("Upload")
            }
            samples = (samples + point).takeLast(160)
        }
    }
    val field: @Composable (ToolField) -> Unit = { item ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.options.isEmpty()) {
                OutlinedTextField(parameters[item.id].orEmpty(), { if (it.length <= 2048) parameters = parameters + (item.id to it) }, label = { Text(item.label.localized()) }, singleLine = true, enabled = state.runningId == null, modifier = Modifier.fillMaxWidth())
            } else {
                Text(item.label.localized(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.options.forEach { option ->
                        FilterChip(selected = parameters[item.id] == option.value, onClick = { parameters = parameters + (item.id to option.value) }, enabled = state.runningId == null, label = { Text(option.label.localized()) })
                    }
                }
            }
        }
    }
    LazyColumn(modifier.fillMaxSize().then(if (connectivity) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
                Column(Modifier.weight(1f)) {
                    Text(manifest.name.localized(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (!connectivity || result != null) Text(result?.phase?.localized() ?: stringResource(Res.string.tool_ready), color = if (result?.error == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (connectivity) IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, stringResource(Res.string.connection_settings)) }
                if (running) {
                    OutlinedButton(onClick = { scope.launch { manager.stop() } }) { Text(stringResource(Res.string.plugin_stop)) }
                } else if (connectivity) {
                    IconButton(onClick = { scope.launch { manager.run(manifest.id, parameters) } }, enabled = enabled && state.runningId == null && !state.busy) { Icon(Icons.Default.Refresh, stringResource(Res.string.plugin_start)) }
                } else {
                    Button(onClick = { scope.launch { manager.run(manifest.id, parameters) } }, enabled = enabled && state.runningId == null && !state.busy) { Text(stringResource(Res.string.plugin_start)) }
                }
            }
        }
        if (!speed && !connectivity) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val visible = manifest.fields.filter { item ->
                            if (connectivity) {
                                item.id != "url" || parameters["targets"] == "custom"
                            } else {
                                parameters["mode"] != "server" || item.id !in setOf("protocol", "direction", "parallel", "bandwidth")
                            }
                        }
                        visible.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                pair.forEach { item -> Box(Modifier.weight(1f)) { field(item) } }
                                if (pair.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        if (!connectivity && !result?.metrics.isNullOrEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    result!!.metrics.chunked(3).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            group.forEach { metric ->
                                OutlinedCard(Modifier.weight(1f)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(metric.label.localized(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(metric.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!connectivity && samples.size > 1) {
            item { ThroughputPlot(samples) }
        }
        if (connectivity) {
            item { ReferenceConnectivity(result, state.runningId != null, onRetry = { target -> scope.launch { manager.run(manifest.id, parameters + mapOf("targets" to "custom", "url" to target)) } }) }
        }
        item {
            TextButton(onClick = { details = !details }) { Text(stringResource(Res.string.tool_details)) }
            if (details) Text(manifest.notice.localized(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (settings) {
        AlertDialog(onDismissRequest = { settings = false }, title = { Text(stringResource(if (connectivity) Res.string.connection_settings else Res.string.tool_settings)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { manifest.fields.forEach { field(it) } }
        }, confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(Res.string.tool_done)) } })
    }
}

@Composable
private fun ThroughputPlot(samples: List<Pair<Float, Float>>) {
    val download = MaterialTheme.colorScheme.primary
    val upload = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ceiling = samples.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1f)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(Res.string.tool_chart), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("↓ " + stringResource(Res.string.tool_down), color = download, style = MaterialTheme.typography.labelMedium)
                Text("↑ " + stringResource(Res.string.tool_up), color = upload, style = MaterialTheme.typography.labelMedium)
            }
            Text("0 – $ceiling Mbps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                repeat(4) { n ->
                    val y = size.height * n / 3
                    drawLine(grid, Offset(0f, y), Offset(size.width, y))
                }
                for (up in listOf(false, true)) {
                    val path = Path()
                    samples.forEachIndexed { index, sample ->
                        val x = size.width * index / (samples.size - 1)
                        val y = size.height * (1f - (if (up) sample.second else sample.first) / ceiling)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, if (up) upload else download, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
