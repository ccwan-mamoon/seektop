package io.github.seektop.plugins

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginContractTest {
    private fun imageHost() = PluginManifest(
        id = PlannedPlugins.NODE_IMAGE,
        name = "NodeImage",
        version = "0.1.0",
        apiVersion = SEEKTOP_PLUGIN_API_VERSION,
        runtime = PluginRuntimeKind.JAVASCRIPT,
        entryPoint = "dist/main.js",
        permissions = setOf(PluginPermission.HTTPS_REQUEST, PluginPermission.SECRET_REFERENCE, PluginPermission.FILE_SELECTION),
        networkHosts = setOf("api.nodeimage.com"),
        imageHosts = listOf(PluginImageHostContribution("upload", "NodeImage")),
    )

    @Test fun `image provider and independent mail page have separate extension identities`() {
        val image = imageHost()
        val mail = image.copy(
            id = PlannedPlugins.SEEK_LI,
            name = "Seek.li",
            networkHosts = setOf("seek.li"),
            imageHosts = emptyList(),
            pages = listOf(PluginPageContribution("inbox", "Seek.li")),
        )
        for (manifest in listOf(image, mail)) {
            assertEquals(emptySet(), PluginManifestValidator.validate(manifest))
            assertEquals(manifest, Json.decodeFromString<PluginManifest>(Json.encodeToString(manifest)))
        }
    }

    @Test fun `path traversal executable paths and unsupported protocols are refused`() {
        for (path in listOf("../main.js", "C:/main.js", "/main.js", "dist\\main.js", "https://example.com/main.js", "main.exe")) {
            assertTrue(PluginManifestProblem.INVALID_ENTRY_POINT in PluginManifestValidator.validate(imageHost().copy(entryPoint = path)), path)
        }
        assertTrue(PluginManifestProblem.UNSUPPORTED_API in PluginManifestValidator.validate(imageHost().copy(apiVersion = 2)))
        assertTrue(PluginManifestProblem.INVALID_ID in PluginManifestValidator.validate(imageHost().copy(id = "../../SeekTop")))
    }

    @Test fun `network scope must declare exact hosts and permission together`() {
        for (host in listOf("*.nodeimage.com", "https://api.nodeimage.com", "127.0.0.1", "localhost", "api.nodeimage.com:443", "api.nodeimage.com/path")) {
            assertTrue(PluginManifestProblem.INVALID_NETWORK_HOST in PluginManifestValidator.validate(imageHost().copy(networkHosts = setOf(host))), host)
        }
        assertTrue(PluginManifestProblem.MISSING_NETWORK_HOST in PluginManifestValidator.validate(imageHost().copy(networkHosts = emptySet())))
        assertTrue(PluginManifestProblem.MISSING_NETWORK_PERMISSION in PluginManifestValidator.validate(imageHost().copy(permissions = emptySet())))
    }

    @Test fun `duplicate contribution identities cannot ambiguously resolve routes`() {
        val manifest = imageHost().copy(pages = listOf(PluginPageContribution("upload", "Images")))
        assertTrue(PluginManifestProblem.DUPLICATE_CONTRIBUTION in PluginManifestValidator.validate(manifest))
    }

    @Test fun `reserved service identities do not become installed or executable plugins`() {
        assertEquals(PluginAvailability.RESERVED, ReservedPluginRegistry.availability)
        assertTrue(ReservedPluginRegistry.installed.value.isEmpty())
        assertEquals(null, ReservedPluginRegistry.find(PlannedPlugins.NODE_IMAGE))
        assertEquals(null, ReservedPluginRegistry.find(PlannedPlugins.SEEK_LI))
        val route = PluginRoute(PlannedPlugins.SEEK_LI, "inbox", mapOf("message" to "123"))
        assertEquals(route, Json.decodeFromString<PluginRoute>(Json.encodeToString(route)))
    }
}
