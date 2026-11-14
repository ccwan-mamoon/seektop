package io.github.seektop.plugins

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Official, signed native tools use a separate protocol from the reserved JavaScript SDK. */
@Serializable
data class ToolText(val zh: String, val en: String, val tw: String = zh)

@Serializable
data class ToolOption(val value: String, val label: ToolText)

@Serializable
data class ToolField(
    val id: String,
    val label: ToolText,
    val default: String = "",
    val options: List<ToolOption> = emptyList(),
)

@Serializable
data class ToolManifest(
    val id: String,
    val version: String,
    val api: Int,
    val name: ToolText,
    val description: ToolText,
    val notice: ToolText,
    val fields: List<ToolField>,
    val files: Map<String, String>,
    val runtime: String = "native-tool-v1",
    val timeoutSeconds: Int = 120,
)

@Serializable
data class ToolListing(val manifest: ToolManifest, val url: String, val sha256: String, val size: Long)

@Serializable
data class ToolCatalog(val revision: Long, val plugins: List<ToolListing>)

@Serializable
data class InstalledTool(val manifest: ToolManifest, val enabled: Boolean = true)

@Serializable
data class ToolMetric(val label: ToolText, val value: String)

@Serializable
data class ToolResultRow(val target: String, val status: ToolText, val detail: String = "")

@Serializable
data class ToolEvent(
    val phase: ToolText,
    val metrics: List<ToolMetric> = emptyList(),
    val rows: List<ToolResultRow> = emptyList(),
    val done: Boolean = false,
    val error: Boolean = false,
)

data class ToolPluginState(
    val catalog: List<ToolListing> = emptyList(),
    val installed: List<InstalledTool> = emptyList(),
    val busy: Boolean = false,
    val progress: Float? = null,
    val runningId: String? = null,
    val resultPluginId: String? = null,
    val event: ToolEvent? = null,
    val error: String? = null,
)

interface ToolPluginManager {
    val state: StateFlow<ToolPluginState>
    suspend fun refresh()
    suspend fun install(listing: ToolListing)
    suspend fun importPackage()
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun uninstall(id: String)
    suspend fun run(id: String, parameters: Map<String, String>)
    suspend fun stop()
}
