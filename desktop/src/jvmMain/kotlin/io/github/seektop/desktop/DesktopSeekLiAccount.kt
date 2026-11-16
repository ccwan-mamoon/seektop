package io.github.seektop.desktop

import io.github.seektop.data.mail.DefaultSeekLiRepository
import io.github.seektop.data.mail.SeekLiAccount
import io.github.seektop.data.mail.SeekLiRepository
import io.github.seektop.foundation.core.net.HttpBody
import io.github.seektop.foundation.core.net.HttpRequest
import io.github.seektop.foundation.core.net.HttpResponse
import io.github.seektop.foundation.core.net.HttpTransport
import io.github.seektop.foundation.core.net.UploadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun mailRepository(browser: DesktopBrowser): SeekLiRepository = DefaultSeekLiRepository(
    object : HttpTransport {
        override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse = withContext(Dispatchers.IO) {
            val reply = browser.call(
                "mail-request",
                buildJsonObject {
                    put("url", request.url)
                    put("method", request.method)
                    (request.body as? HttpBody.Text)?.let { put("body", it.content) }
                },
            )
            HttpResponse(reply.getValue("code").jsonPrimitive.int, request.url, emptyMap(), reply.getValue("body").jsonPrimitive.content)
        }
    },
    // The broker owns the real key and adds it only to fixed-origin requests.
    { "sk_live_broker" },
)

class DesktopSeekLiAccount(private val browser: DesktopBrowser) :
    SeekLiAccount,
    SeekLiRepository by mailRepository(browser) {
    override suspend fun uploadAttachment(): kotlinx.serialization.json.JsonObject? = withContext(Dispatchers.IO) {
        var source: java.io.File? = null
        javax.swing.SwingUtilities.invokeAndWait {
            val picker = javax.swing.JFileChooser()
            if (picker.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) source = picker.selectedFile
        }
        val file = source ?: return@withContext null
        require(file.isFile && file.length() <= 24L * 1024 * 1024)
        val reply = browser.call(
            "mail-request",
            buildJsonObject {
                put("url", "https://seek.li/openapi/v1/upload/attachment")
                put("method", "POST")
                put(
                    "upload",
                    buildJsonObject {
                        put("name", file.name)
                        put("mime", java.nio.file.Files.probeContentType(file.toPath()) ?: "application/octet-stream")
                        put("bytes", java.util.Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(file.toPath())))
                    },
                )
            },
        )
        val code = reply.getValue("code").jsonPrimitive.int
        if (code !in 200..299) throw io.github.seektop.data.mail.SeekLiException(code)
        kotlinx.serialization.json.Json.parseToJsonElement(reply.getValue("body").jsonPrimitive.content).let { it as kotlinx.serialization.json.JsonObject }
    }
    override suspend fun download(path: String, fileName: String): Unit = withContext(Dispatchers.IO) {
        require(io.github.seektop.data.mail.SeekLiEndpoints.permits("GET", path))
        require(Regex("^/mails/[0-9]+/(raw|attachments/[0-9]+)$").matches(path))
        var target: java.io.File? = null
        javax.swing.SwingUtilities.invokeAndWait {
            val picker = javax.swing.JFileChooser().apply { selectedFile = java.io.File(fileName.substringAfterLast('/').substringAfterLast('\\')) }
            if (picker.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) target = picker.selectedFile
        }
        val destination = target ?: return@withContext
        if (destination.exists()) {
            var overwrite = false
            javax.swing.SwingUtilities.invokeAndWait { overwrite = javax.swing.JOptionPane.showConfirmDialog(null, destination.name, "SeekTop", javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION }
            if (!overwrite) return@withContext
        }
        val reply = browser.call(
            "mail-request",
            buildJsonObject {
                put("url", "https://seek.li/openapi/v1$path")
                put("binary", true)
            },
        )
        val code = reply.getValue("code").jsonPrimitive.int
        if (code !in 200..299) throw io.github.seektop.data.mail.SeekLiException(code)
        val bytes = java.util.Base64.getDecoder().decode(reply.getValue("body").jsonPrimitive.content)
        java.nio.file.Files.write(destination.toPath(), bytes)
        Unit
    }
    private val mailPreferences = java.util.prefs.Preferences.userRoot().node("io/github/seektop/mail")
    override suspend fun pollSeconds(): Int = withContext(Dispatchers.IO) {
        mailPreferences.getInt("pollSeconds", 60).takeIf { it in listOf(30, 60, 120, 300, 600, 1800, 3600) } ?: 60
    }
    override suspend fun setPollSeconds(seconds: Int): Unit = withContext(Dispatchers.IO) {
        require(seconds in listOf(30, 60, 120, 300, 600, 1800, 3600))
        mailPreferences.putInt("pollSeconds", seconds)
        mailPreferences.flush()
    }
    override suspend fun connected(): Boolean = withContext(Dispatchers.IO) { browser.call("mail-settings").getValue("connected").jsonPrimitive.boolean }
    override suspend fun connect(key: String): Unit = withContext(Dispatchers.IO) {
        browser.call("mail-save", buildJsonObject { put("key", key.trim()) })
        Unit
    }
    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        browser.call("mail-clear")
        Unit
    }
}
