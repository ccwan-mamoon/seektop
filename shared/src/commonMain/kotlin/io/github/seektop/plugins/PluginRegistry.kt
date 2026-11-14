package io.github.seektop.plugins

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PluginAvailability { RESERVED }

data class InstalledPlugin(val manifest: PluginManifest, val enabled: Boolean)

/** The app container exposes metadata, never an installation or execution shortcut. */
interface PluginRegistry {
    val availability: PluginAvailability
    val installed: StateFlow<List<InstalledPlugin>>
    fun find(id: String): InstalledPlugin? = installed.value.firstOrNull { it.manifest.id == id }
}

/** All shells stay inert until a real installer, isolated runtime and permission broker are available. */
object ReservedPluginRegistry : PluginRegistry {
    override val availability = PluginAvailability.RESERVED
    override val installed: StateFlow<List<InstalledPlugin>> = MutableStateFlow(emptyList<InstalledPlugin>()).asStateFlow()
}

/** Planned identities only; these entries are neither installed plugins nor marketplace listings. */
object PlannedPlugins {
    const val SEEK_LI = "io.github.seektop.seekli"
    const val NODE_IMAGE = "io.github.seektop.nodeimage"
}
