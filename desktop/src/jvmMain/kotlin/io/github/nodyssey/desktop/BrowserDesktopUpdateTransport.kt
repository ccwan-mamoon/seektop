package io.github.nodyssey.desktop

import io.github.nodyssey.data.proxy.ProxySettings
import io.github.plaza.core.update.AppRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Chromium owns proxy selection/authentication; the updater never receives forum cookies. */
internal class BrowserDesktopUpdateTransport(private val browser: DesktopBrowser, private val proxy: ProxySettings) : DesktopUpdateTransport {
    override suspend fun releases(): String = withContext(Dispatchers.IO) {
        browser.applyProxy(proxy.config.first())
        browser.call("update-releases").getValue("body").jsonPrimitive.content
    }

    override suspend fun download(release: AppRelease, output: File, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        require(trustedDesktopAsset(release))
        browser.applyProxy(proxy.config.first())
        val id = browser.call(
            "update-start",
            buildJsonObject {
                put("url", release.downloadUrl)
                put("digest", release.sha256)
                put("size", release.sizeBytes)
            },
        ).getValue("id").jsonPrimitive.content
        val query = buildJsonObject { put("id", id) }
        try {
            while (true) {
                val state = browser.call("update-status", query)
                val error = state.getValue("error").jsonPrimitive.content
                if (error.isNotBlank()) throw java.io.IOException("Update transfer failed")
                progress(state.getValue("bytes").jsonPrimitive.long)
                if (state.getValue("done").jsonPrimitive.content == "true") {
                    val cached = File(state.getValue("file").jsonPrimitive.content)
                    Files.copy(cached.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    // The repository checks length and SHA-256 before handing off to the installer.
                    cached.delete()
                    break
                }
                delay(250)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { browser.call("update-cancel", query) }
        }
    }
}

/** A resumed prefix is the baseline, never counted as newly downloaded throughput. */
internal class DesktopDownloadMeter {
    private var baseline: Long? = null
    private var at = 0L
    private var rate = 0L
    fun sample(bytes: Long, now: Long = System.nanoTime()): Long {
        val previous = baseline
        if (previous == null || bytes < previous) {
            baseline = bytes
            at = now
            rate = 0
        } else if (now - at >= 500_000_000L) {
            rate = ((bytes - previous).toDouble() * 1_000_000_000 / (now - at)).toLong()
            baseline = bytes
            at = now
        }
        return rate
    }
}
