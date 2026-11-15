package io.github.seektop.desktop

import io.github.seektop.links.ExternalLinkRouting
import io.github.seektop.links.LinkRoutingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

internal fun supportedDesktopLink(raw: String): Boolean = runCatching {
    val uri = URI(raw)
    raw.length <= 8192 && uri.scheme?.lowercase() in setOf("http", "https") && uri.userInfo == null &&
        uri.host?.lowercase() in setOf("nodeseek.com", "www.nodeseek.com") &&
        (uri.port == -1 || uri.port == if (uri.scheme.equals("https", true)) 443 else 80) &&
        (Regex("/post-[1-9][0-9]{0,17}-[1-9][0-9]{0,8}/?").matches(uri.rawPath) || Regex("/space/[1-9][0-9]{0,17}/?").matches(uri.rawPath))
}.getOrDefault(false)

internal class DesktopExternalLinks(private val helper: File) : ExternalLinkRouting {
    private suspend fun command(vararg args: String): String = withContext(Dispatchers.IO) {
        runInterruptible {
            val process = ProcessBuilder(listOf(helper.absolutePath) + args).redirectErrorStream(true).start()
            try {
                check(process.waitFor(20, TimeUnit.SECONDS)) { "LINK_HELPER_TIMEOUT" }
                val body = process.inputStream.bufferedReader().readText().trim()
                check(process.exitValue() == 0) {
                    runCatching { Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull() ?: "LINK_OPERATION_FAILED"
                }
                body
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }
    override suspend fun status(): LinkRoutingStatus = Json.decodeFromString(command("--status"))
    override suspend fun prepare(): LinkRoutingStatus {
        val app = File(helper.parentFile, "../../SeekTop.exe").canonicalFile
        return Json.decodeFromString(command("--prepare", app.absolutePath))
    }
    override suspend fun disable(): LinkRoutingStatus = Json.decodeFromString(command("--disable"))
    override suspend fun openSystemSettings() {
        command("--settings")
    }
}

/** Native pipe ACL is restricted to this Windows user; the receiver follows the JVM's lifetime. */
internal class DesktopLinkReceiver(helper: File) : AutoCloseable {
    private val queue = Channel<String>(64)
    val links = queue.receiveAsFlow()
    private val process = ProcessBuilder(helper.absolutePath, "--listen", ProcessHandle.current().pid().toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    private val reader = Thread({
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val url = runCatching { Json.parseToJsonElement(line).jsonObject["url"]?.jsonPrimitive?.content }.getOrNull()
                    if (url != null && supportedDesktopLink(url)) kotlinx.coroutines.runBlocking { queue.send(url) }
                }
            }
        } catch (_: Exception) { /* Closing the pipe or queue releases this reader. */ }
    }, "SeekTop native links").apply {
        isDaemon = true
        start()
    }
    override fun close() {
        queue.close()
        process.destroy()
        reader.interrupt()
    }
}
