package io.github.seektop.desktop

import io.github.seektop.plugins.ToolField
import io.github.seektop.plugins.ToolManifest
import io.github.seektop.plugins.ToolText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ToolPackageTest {
    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val verifier = ToolPackageVerifier(keys.public.encoded)
    private val text = ToolText("测试", "Test")
    private val worker = byteArrayOf(77, 90, 1, 2, 3)
    private fun manifest(version: String = "1.0.0") = ToolManifest(
        "io.github.seektop.toolbox.test",
        version,
        1,
        text,
        text,
        text,
        listOf(ToolField("host", text, "127.0.0.1")),
        mapOf("worker.exe" to ToolPackageVerifier.sha256(worker), "LICENSE.txt" to ToolPackageVerifier.sha256("license".toByteArray())),
    )

    private fun archive(root: File, manifest: ToolManifest = manifest(), body: ByteArray = worker, extra: String? = null, corruptSignature: Boolean = false): File {
        val bytes = Json.encodeToString(manifest).toByteArray()
        val signature = Signature.getInstance("Ed25519").run {
            initSign(keys.private)
            update(bytes)
            sign()
        }
        if (corruptSignature) signature[0] = (signature[0].toInt() xor 1).toByte()
        return File(root, "package-${System.nanoTime()}.stplugin").apply {
            ZipOutputStream(outputStream()).use { zip ->
                val entries = mapOf("manifest.json" to bytes, "manifest.sig" to signature, "worker.exe" to body, "LICENSE.txt" to "license".toByteArray()) + (extra?.let { mapOf(it to worker) } ?: emptyMap())
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }
    }

    @Test fun `altered engine publisher signature and archive traversal are rejected before activation`() {
        val root = Files.createTempDirectory("tool-security").toFile()
        try {
            assertFailsWith<IllegalArgumentException> { verifier.unpack(archive(root, body = byteArrayOf(0)), File(root, "changed")) }
            assertFailsWith<Exception> { verifier.unpack(archive(root, corruptSignature = true), File(root, "signature")) }
            assertFailsWith<IllegalArgumentException> { verifier.unpack(archive(root, extra = "../escaped.exe"), File(root, "traversal")) }
            assertEquals(false, File(root, "escaped.exe").exists())
            val target = File(root, "valid")
            verifier.unpack(archive(root), target)
            File(target, "worker.exe").writeBytes(byteArrayOf(0))
            assertFailsWith<IllegalArgumentException> { verifier.installed(target) }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `bad update and downgrade keep active version while disabled state survives restart and repair`() = runBlocking {
        val root = Files.createTempDirectory("tool-lifecycle").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = File(root, "store")
        var manager = DesktopToolPlugins(store, scope, verifier)
        try {
            manager.state.first { !it.busy }
            manager.importVerifiedPackage(archive(root))
            assertNull(manager.state.value.error)
            assertEquals("1.0.0", manager.state.value.installed.single().manifest.version)
            manager.importVerifiedPackage(archive(root, manifest("1.1.0"), body = byteArrayOf(0)))
            assertEquals("1.0.0", manager.state.value.installed.single().manifest.version)
            assertNotNull(manager.state.value.error)
            manager.importVerifiedPackage(archive(root, manifest("1.1.0")))
            manager.setEnabled(manifest().id, false)
            manager.close()
            manager = DesktopToolPlugins(store, scope, verifier)
            manager.state.first { !it.busy }
            assertEquals(false, manager.state.value.installed.single().enabled)
            manager.importVerifiedPackage(archive(root))
            assertEquals("1.1.0", manager.state.value.installed.single().manifest.version)
            assertNotNull(manager.state.value.error)
            File(store, "${manifest().id}/1.1.0/worker.exe").writeBytes(byteArrayOf(0))
            manager.importVerifiedPackage(archive(root, manifest("1.1.0")))
            assertNull(manager.state.value.error)
            verifier.installed(File(store, "${manifest().id}/1.1.0"))
            assertEquals(false, manager.state.value.installed.single().enabled)
            manager.uninstall(manifest().id)
            assertEquals(emptyList(), manager.state.value.installed)
        } finally {
            manager.close()
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test fun `unreadable installed index is not overwritten by a later management operation`() = runBlocking {
        val root = Files.createTempDirectory("tool-index").toFile()
        val index = File(root, "installed.json").apply { writeText("interrupted external edit") }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = DesktopToolPlugins(root, scope, verifier)
        try {
            manager.state.first { !it.busy }
            manager.setEnabled(manifest().id, false)
            assertNotNull(manager.state.value.error)
            assertEquals("interrupted external edit", index.readText())
        } finally {
            manager.close()
            scope.cancel()
            root.deleteRecursively()
        }
    }
}
