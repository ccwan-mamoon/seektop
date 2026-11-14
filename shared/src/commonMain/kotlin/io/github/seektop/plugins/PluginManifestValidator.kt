package io.github.seektop.plugins

enum class PluginManifestProblem {
    INVALID_ID,
    INVALID_NAME,
    INVALID_VERSION,
    UNSUPPORTED_API,
    INVALID_ENTRY_POINT,
    INVALID_NETWORK_HOST,
    MISSING_NETWORK_PERMISSION,
    MISSING_NETWORK_HOST,
    INVALID_CONTRIBUTION,
    DUPLICATE_CONTRIBUTION,
}

/** Metadata validation only. Installation, signature checks and runtime permission enforcement are separate. */
object PluginManifestValidator {
    private val pluginId = Regex("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+")
    private val contributionId = Regex("[a-z][a-z0-9-]{0,63}")
    private val version = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?")
    private val host = Regex("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
    private val entry = Regex("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*\\.js")

    fun validate(manifest: PluginManifest): Set<PluginManifestProblem> = buildSet {
        if (manifest.id.length > 128 || !pluginId.matches(manifest.id)) add(PluginManifestProblem.INVALID_ID)
        if (manifest.name.isBlank() || manifest.name.length > 128) add(PluginManifestProblem.INVALID_NAME)
        if (manifest.version.length > 64 || !version.matches(manifest.version)) add(PluginManifestProblem.INVALID_VERSION)
        if (manifest.apiVersion != SEEKTOP_PLUGIN_API_VERSION) add(PluginManifestProblem.UNSUPPORTED_API)
        if (manifest.entryPoint.length > 256 || !entry.matches(manifest.entryPoint)) add(PluginManifestProblem.INVALID_ENTRY_POINT)
        if (manifest.networkHosts.any { it.length > 253 || !host.matches(it) }) add(PluginManifestProblem.INVALID_NETWORK_HOST)
        val network = PluginPermission.HTTPS_REQUEST in manifest.permissions
        if (manifest.networkHosts.isNotEmpty() && !network) add(PluginManifestProblem.MISSING_NETWORK_PERMISSION)
        if (network && manifest.networkHosts.isEmpty()) add(PluginManifestProblem.MISSING_NETWORK_HOST)
        val contributions = manifest.pages.map { it.id to it.title } + manifest.imageHosts.map { it.id to it.title }
        if (contributions.any { !contributionId.matches(it.first) || it.second.isBlank() || it.second.length > 128 }) add(PluginManifestProblem.INVALID_CONTRIBUTION)
        if (contributions.map { it.first }.distinct().size != contributions.size) add(PluginManifestProblem.DUPLICATE_CONTRIBUTION)
    }
}
