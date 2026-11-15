package io.github.seektop.ui.plugins

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.seektop.plugins.ToolManifest
import io.github.seektop.plugins.ToolPluginManager
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_back
import io.github.seektop.ui.resources.action_cancel
import io.github.seektop.ui.resources.plugin_start
import io.github.seektop.ui.resources.plugin_stop
import io.github.seektop.ui.resources.speed_direct
import io.github.seektop.ui.resources.speed_engine_note
import io.github.seektop.ui.resources.speed_jitter
import io.github.seektop.ui.resources.speed_latency
import io.github.seektop.ui.resources.speed_network
import io.github.seektop.ui.resources.speed_route
import io.github.seektop.ui.resources.speed_server
import io.github.seektop.ui.resources.speed_system_route
import io.github.seektop.ui.resources.tool_done
import io.github.seektop.ui.resources.tool_down
import io.github.seektop.ui.resources.tool_ready
import io.github.seektop.ui.resources.tool_settings
import io.github.seektop.ui.resources.tool_up
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.cos
import kotlin.math.sin

/** Layout follows the supplied Bettbox reference; measurements remain owned by the installed engine. */
@Composable
internal fun ReferenceSpeedPage(manifest: ToolManifest, enabled: Boolean, manager: ToolPluginManager, modifier: Modifier, onBack: () -> Unit) {
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var parameters by rememberSaveable(manifest.id, manifest.version) { mutableStateOf(manifest.fields.associate { it.id to it.default }) }
    var draft by remember { mutableStateOf(parameters) }
    var settings by remember { mutableStateOf(false) }
    val result = state.event.takeIf { state.resultPluginId == manifest.id }
    val running = state.runningId == manifest.id
    val down = stringResource(Res.string.tool_down)
    val up = stringResource(Res.string.tool_up)
    val latency = stringResource(Res.string.speed_latency)
    val jitter = stringResource(Res.string.speed_jitter)
    fun metric(name: String) = result?.metrics?.find { it.label.en == name }?.value
    val live = metric("Live rate")
    val value = live?.substringBefore(' ')?.toFloatOrNull()
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
            Text(manifest.name.localized(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                draft = parameters
                settings = true
            }, enabled = !running) { Icon(Icons.Default.Settings, stringResource(Res.string.tool_settings)) }
        }
        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NetworkLine("↗", stringResource(Res.string.speed_network), stringResource(Res.string.speed_direct))
                NetworkLine("⑂", stringResource(Res.string.speed_route), stringResource(Res.string.speed_system_route))
                NetworkLine("▤", stringResource(Res.string.speed_server), "Cloudflare · speed.cloudflare.com")
            }
        }
        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReferenceGauge(value, result?.phase?.localized() ?: stringResource(Res.string.tool_ready))
                    Button(onClick = { scope.launch { if (running) manager.stop() else manager.run(manifest.id, parameters) } }, enabled = running || (enabled && state.runningId == null && !state.busy), modifier = Modifier.width(220.dp)) {
                        Text((if (running) "■  " else "▶  ") + stringResource(if (running) Res.string.plugin_stop else Res.string.plugin_start))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        listOf(latency, down, up).forEachIndexed { index, label ->
                            val selected = running && when (index) {
                                0 -> result?.phase?.en?.startsWith("Download") != true && result?.phase?.en?.startsWith("Upload") != true
                                1 -> result?.phase?.en?.startsWith("Download") == true
                                else -> result?.phase?.en?.startsWith("Upload") == true
                            }
                            Text("● $label" + if (index < 2) " ── " else "", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpeedTile("↓", down, metric("Download test") ?: live.takeIf { result?.phase?.en?.startsWith("Download") == true } ?: "— Mbps", Modifier.weight(1f))
                        SpeedTile("↑", up, metric("Upload test") ?: live.takeIf { result?.phase?.en?.startsWith("Upload") == true } ?: "— Mbps", Modifier.weight(1f), alternate = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpeedTile("◴", latency, metric("Mean HTTP latency") ?: "—", Modifier.weight(1f))
                        SpeedTile("∿", jitter, metric("HTTP jitter") ?: "—", Modifier.weight(1f))
                    }
                }
            }
        }
        Text(stringResource(Res.string.speed_engine_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
    if (settings) {
        AlertDialog(onDismissRequest = { settings = false }, title = { Text(stringResource(Res.string.tool_settings)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(Res.string.speed_server), style = MaterialTheme.typography.titleSmall)
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) { Text("Cloudflare · speed.cloudflare.com", Modifier.fillMaxWidth().padding(18.dp)) }
                manifest.fields.forEach { field ->
                    Column {
                        Text(field.label.localized(), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val options = if (field.options.isNotEmpty()) field.options.map { it.value to it.label.localized() } else listOf("3", "5", "10").map { it to "${it}s" }
                            options.forEach { (value, label) -> FilterChip(selected = draft[field.id] == value, onClick = { draft = draft + (field.id to value) }, label = { Text(label) }) }
                        }
                    }
                }
                Text(stringResource(Res.string.speed_engine_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = {
            TextButton(onClick = {
                parameters = draft
                settings = false
            }) { Text(stringResource(Res.string.tool_done)) }
        }, dismissButton = { TextButton(onClick = { settings = false }) { Text(stringResource(Res.string.action_cancel)) } })
    }
}

@Composable
private fun NetworkLine(icon: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SpeedTile(icon: String, label: String, value: String, modifier: Modifier, alternate: Boolean = false) {
    val accent = if (alternate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = accent.copy(alpha = 0.12f)) { Text(icon, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = accent, style = MaterialTheme.typography.titleLarge) }
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ReferenceGauge(value: Float?, phase: String) {
    val ticks = listOf(0f, 5f, 10f, 50f, 100f, 250f, 500f, 750f, 1000f)
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val bounded = (value ?: 0f).coerceIn(0f, 1000f)
    val bucket = (ticks.indexOfLast { it <= bounded }).coerceAtMost(7)
    val fraction = (bucket + (bounded - ticks[bucket]) / (ticks[bucket + 1] - ticks[bucket])) / 8f
    Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        // Material's circular progress has no open 270-degree scale/ticks API.
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            drawArc(track, 135f, 270f, false, size = Size(size.width, size.height), style = Stroke(20.dp.toPx(), cap = StrokeCap.Butt))
            drawArc(accent, 135f, 270f * fraction, false, size = Size(size.width, size.height), style = Stroke(20.dp.toPx(), cap = StrokeCap.Butt))
        }
        ticks.forEachIndexed { index, tick ->
            val angle = kotlin.math.PI * (135 + 270.0 * index / 8) / 180
            Box(Modifier.offset((105 * cos(angle)).dp, (105 * sin(angle)).dp).size(42.dp, 22.dp), contentAlignment = Alignment.Center) {
                Text(if (index == 8) "1G+" else tick.toInt().toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 70.dp)) {
            Text(phase, style = MaterialTheme.typography.bodyMedium)
            Text(value?.toString() ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("↔ Mbps", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
