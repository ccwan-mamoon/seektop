package io.github.seektop.ui.plugins

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.seektop.plugins.ToolEvent
import io.github.seektop.plugins.ToolResultRow
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.connection_all
import io.github.seektop.ui.resources.connection_checked
import io.github.seektop.ui.resources.connection_other
import io.github.seektop.ui.resources.connection_restricted
import io.github.seektop.ui.resources.connection_retry
import io.github.seektop.ui.resources.connection_stream
import io.github.seektop.ui.resources.plugin_brand_claude
import io.github.seektop.ui.resources.plugin_brand_cloudflare
import io.github.seektop.ui.resources.plugin_brand_github
import io.github.seektop.ui.resources.plugin_brand_netflix
import io.github.seektop.ui.resources.plugin_brand_openai
import io.github.seektop.ui.resources.plugin_brand_youtube
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Supported targets only: the current engine does not implement Bettbox's 55 service checks. */
@Composable
internal fun ReferenceConnectivity(event: ToolEvent?, running: Boolean, onRetry: (String) -> Unit) {
    val targets = listOf("https://chatgpt.com/" to "OpenAI", "https://claude.ai/" to "Claude", "https://www.youtube.com/" to "YouTube", "https://www.netflix.com/" to "Netflix", "https://www.nodeseek.com/" to "NodeSeek", "https://github.com/" to "GitHub", "https://www.google.com/generate_204" to "Google", "https://www.cloudflare.com/cdn-cgi/trace" to "Cloudflare")
    var results by remember { mutableStateOf<Map<String, ToolResultRow>>(emptyMap()) }
    var category by remember { mutableStateOf(0) }
    LaunchedEffect(event) { if (event != null) results = results + event.rows.associateBy { it.target } }
    val platforms = targets + results.keys.filter { target -> targets.none { it.first == target } }.map { it to it.removePrefix("https://").substringBefore('/') }
    val checked = results.size
    val restricted = results.values.count { it.status.en in setOf("Access restricted", "Region restricted") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(checked.toString() to stringResource(Res.string.connection_checked), restricted.toString() to stringResource(Res.string.connection_restricted), (platforms.size - checked).coerceAtLeast(0).toString() to stringResource(Res.string.connection_other)).forEachIndexed { index, pair ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pair.first, style = MaterialTheme.typography.titleLarge, color = if (index == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(pair.second, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(stringResource(Res.string.connection_all), "AI", stringResource(Res.string.connection_stream)).forEachIndexed { index, label ->
                FilterChip(selected = category == index, onClick = { category = index }, label = { Text(label) })
            }
        }
        platforms.filter { (_, name) -> category == 0 || (category == 1 && name in setOf("OpenAI", "Claude")) || (category == 2 && name in setOf("YouTube", "Netflix")) }.forEach { (url, name) ->
            val result = results[url]
            val color = when (result?.status?.en) {
                "Reachable" -> MaterialTheme.colorScheme.primary
                "Access restricted", "Region restricted", "Connection failed" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            val logo = when (name) {
                                "OpenAI" -> Res.drawable.plugin_brand_openai
                                "Claude" -> Res.drawable.plugin_brand_claude
                                "YouTube" -> Res.drawable.plugin_brand_youtube
                                "Netflix" -> Res.drawable.plugin_brand_netflix
                                "GitHub" -> Res.drawable.plugin_brand_github
                                "Cloudflare" -> Res.drawable.plugin_brand_cloudflare
                                else -> null
                            }
                            if (logo != null) Image(painterResource(logo), name, Modifier.size(28.dp)) else Text(name.take(1), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        result?.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.08f)) { Text("● " + (result?.status?.localized() ?: "—"), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = color) }
                    IconButton(onClick = { onRetry(url) }, enabled = !running) { Icon(Icons.Default.Refresh, stringResource(Res.string.connection_retry)) }
                }
            }
        }
    }
}
