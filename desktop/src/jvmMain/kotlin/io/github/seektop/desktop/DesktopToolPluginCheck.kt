package io.github.seektop.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files

/** Isolated packaged-runtime check: authentic publisher signatures, native IPC and cancellation. */
internal fun checkToolPlugins(packages: File, fromMarket: Boolean = false) = runBlocking {
    val temp = Files.createTempDirectory("seektop-plugin-check").toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val browser = if (fromMarket) {
        val bundle = File(System.getProperty("compose.application.resources.dir"), "browser-runtime")
        DesktopBrowser(File(bundle, "SeekTopBrowser.exe"), File(bundle, "resources/app"), File(temp, "browser"))
    } else {
        null
    }
    val manager = DesktopToolPlugins(temp, scope, remoteDownload = { url, limit -> checkNotNull(browser).downloadPlugin(url, limit) })
    try {
        withTimeout(15_000) { manager.state.first { !it.busy } }
        if (fromMarket) {
            manager.refresh()
            check(manager.state.value.error == null) { manager.state.value.error.orEmpty() }
        }
        for (kind in listOf("speed", "connectivity", "iperf")) {
            if (fromMarket) {
                manager.install(manager.state.value.catalog.single { it.manifest.id.endsWith(".$kind") })
            } else {
                manager.importVerifiedPackage(File(packages, "SeekTop-$kind-1.0.0-windows-x64.stplugin"))
            }
            check(manager.state.value.error == null) { manager.state.value.error.orEmpty() }
        }
        check(manager.state.value.installed.size == 3)
        val tool = manager.state.value.installed.single { it.manifest.id.endsWith(".iperf") }
        val port = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
        val parameters = tool.manifest.fields.associate { it.id to it.default } + mapOf("mode" to "server", "port" to port.toString(), "duration" to "30")
        manager.run(tool.manifest.id, parameters)
        withTimeout(10_000) { manager.state.first { it.event?.phase?.en?.startsWith("Listening") == true || it.error != null } }
        check(manager.state.value.error == null) { manager.state.value.error.orEmpty() }
        withTimeout(4_000) { manager.stop() }
        check(manager.state.value.runningId == null)
        // Binding again proves the cancelled worker released its listening socket.
        ServerSocket(port, 1, java.net.InetAddress.getLoopbackAddress()).close()
        val speed = manager.state.value.installed.single { it.manifest.id.endsWith(".speed") }
        manager.run(speed.manifest.id, speed.manifest.fields.associate { it.id to it.default } + ("duration" to "-1"))
        withTimeout(8_000) { manager.state.first { it.runningId == null && it.event?.done == true } }
        check(manager.state.value.event?.error == true)
        File(packages, if (fromMarket) "market-smoke.ok" else "host-smoke.ok").writeText("PASS: signed install, packaged Ed25519, native NDJSON, cancellation, port release, worker failure\n")
    } catch (failure: Exception) {
        File(packages, "host-smoke-error.txt").writeText(failure.stackTraceToString())
        throw failure
    } finally {
        manager.close()
        browser?.close()
        scope.cancel()
        temp.deleteRecursively()
    }
}
