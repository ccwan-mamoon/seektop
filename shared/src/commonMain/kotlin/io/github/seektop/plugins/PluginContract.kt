package io.github.seektop.plugins

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Metadata protocol reservation, not a promise that executable plugins are supported yet. */
const val SEEKTOP_PLUGIN_API_VERSION = 1

@Serializable
enum class PluginPermission {
    HTTPS_REQUEST,
    SECRET_REFERENCE,
    FILE_SELECTION,
    BACKGROUND_TASK,
    NOTIFICATION,
}

@Serializable
enum class PluginRuntimeKind {
    @SerialName("javascript")
    JAVASCRIPT,
}

@Serializable
data class PluginPageContribution(val id: String, val title: String)

@Serializable
data class PluginImageHostContribution(val id: String, val title: String)

/** No forum cookies, JVM classes, filesystem paths or loopback bridge tokens belong in this contract. */
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int,
    val runtime: PluginRuntimeKind,
    val entryPoint: String,
    val permissions: Set<PluginPermission> = emptySet(),
    val networkHosts: Set<String> = emptySet(),
    val pages: List<PluginPageContribution> = emptyList(),
    val imageHosts: List<PluginImageHostContribution> = emptyList(),
)

/** Route ownership must survive persisted history and native notification activation. */
@Serializable
data class PluginRoute(val pluginId: String, val pageId: String, val parameters: Map<String, String> = emptyMap())

/** A future broker issues an opaque, revocable reference to a user-selected file. */
@Serializable
data class PluginFileReference(val handle: String, val fileName: String, val mimeType: String, val sizeBytes: Long)

@Serializable
data class PluginImageUploadResult(val imageId: String, val url: String, val fileName: String)
