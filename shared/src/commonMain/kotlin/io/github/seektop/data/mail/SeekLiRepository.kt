package io.github.seektop.data.mail

import io.github.seektop.foundation.core.net.HttpBody
import io.github.seektop.foundation.core.net.HttpRequest
import io.github.seektop.foundation.core.net.HttpTransport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
data class SeekLiMailbox(val id: Long, val address: String, val sendDisabled: Boolean = false)

@Serializable
data class SeekLiMail(
    val id: Long,
    val mailboxId: Long = 0,
    val fromAddress: String = "",
    val fromName: String = "",
    val toAddress: String = "",
    val subject: String = "",
    val bodyText: String = "",
    val bodyHtml: String = "",
    val isRead: Boolean = false,
    val isFlagged: Boolean = false,
    val hasAttachments: Boolean = false,
    val receivedAt: String = "",
) {
    val plainText: String get() = bodyText.ifBlank { com.fleeksoft.ksoup.Ksoup.parse(bodyHtml).text() }
}

@Serializable
data class SeekLiPagination(val total: Int = 0, val limit: Int = 20, val offset: Int = 0)

@Serializable
data class SeekLiMailPage(val data: List<SeekLiMail> = emptyList(), val pagination: SeekLiPagination = SeekLiPagination())

/** Only an explicit API error description is exposed to the local UI, never the response body or logs. */
class SeekLiException(val status: Int, val apiCode: String? = null, val apiMessage: String? = null) : Exception("Seek.li request failed ($status)")

internal fun seekLiFailure(status: Int, body: String, key: String): SeekLiException {
    val error = runCatching { Json.parseToJsonElement(body).jsonObject["error"] }.getOrNull()
    fun safe(value: String?): String? = value?.replace(key, "[redacted]")
        ?.replace(Regex("""sk_live_[^\s"<>]+"""), "[redacted]")
        ?.filter { !it.isISOControl() || it == '\n' }?.take(500)?.takeIf { it.isNotBlank() }
    val fields = error as? JsonObject
    return SeekLiException(
        status,
        safe((fields?.get("code") as? JsonPrimitive)?.contentOrNull),
        safe((fields?.get("message") as? JsonPrimitive)?.contentOrNull ?: (error as? JsonPrimitive)?.contentOrNull),
    )
}

interface SeekLiAccount : SeekLiRepository {
    suspend fun pollSeconds(): Int = 60
    suspend fun setPollSeconds(seconds: Int) {
        require(seconds in listOf(30, 60, 120, 300, 600, 1800, 3600))
    }
    suspend fun uploadAttachment(): JsonObject? = throw UnsupportedOperationException()
    suspend fun download(path: String, fileName: String): Unit = throw UnsupportedOperationException()
    suspend fun connected(): Boolean
    suspend fun connect(key: String)
    suspend fun disconnect()
}

interface SeekLiRepository {
    suspend fun manage(path: String, method: String = "GET", body: JsonObject? = null): JsonObject = throw UnsupportedOperationException()
    suspend fun queryMails(mailboxId: Long?, folder: String, offset: Int, search: String, folderId: Long? = null, tagId: Long? = null): SeekLiMailPage = mails(mailboxId, folder, offset)
    suspend fun mailboxes(): List<SeekLiMailbox>
    suspend fun mails(mailboxId: Long? = null, folder: String = "inbox", offset: Int = 0): SeekLiMailPage
    suspend fun mail(id: Long): SeekLiMail
    suspend fun markRead(id: Long, read: Boolean)
    suspend fun sendWithCopies(mailboxId: Long, to: List<String>, cc: List<String>, bcc: List<String>, subject: String, body: String) {
        require(cc.isEmpty() && bcc.isEmpty())
        send(mailboxId, to, subject, body)
    }
    suspend fun send(mailboxId: Long, to: List<String>, subject: String, body: String)
}

/** Official https://seek.li/api/openapi.json contract. Supply a cookie-free transport with redirects disabled. */
class DefaultSeekLiRepository(
    private val http: HttpTransport,
    private val apiKey: suspend () -> String,
) : SeekLiRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun manage(path: String, method: String, body: JsonObject?): JsonObject {
        require(SeekLiEndpoints.permits(method, path))
        return request(path, method, body)
    }

    override suspend fun mailboxes(): List<SeekLiMailbox> = json.decodeFromJsonElement(request("/mailboxes").getValue("data"))

    override suspend fun mails(mailboxId: Long?, folder: String, offset: Int): SeekLiMailPage = queryMails(mailboxId, folder, offset, "")

    override suspend fun queryMails(mailboxId: Long?, folder: String, offset: Int, search: String, folderId: Long?, tagId: Long?): SeekLiMailPage {
        require(folder in setOf("inbox", "sent", "trash", "spam"))
        require(offset >= 0)
        require(mailboxId == null || mailboxId > 0)
        require(folderId == null || folderId > 0)
        require(tagId == null || tagId > 0)
        val query = "?folder=$folder&limit=20&offset=$offset" + (mailboxId?.let { "&mailbox_id=$it" } ?: "") +
            search.takeIf { it.isNotBlank() }?.let { "&search=${mailQuery(it)}" }.orEmpty() +
            folderId?.let { "&folder_id=$it" }.orEmpty() + tagId?.let { "&tag_id=$it" }.orEmpty()
        return json.decodeFromJsonElement(request("/mails$query"))
    }

    override suspend fun mail(id: Long): SeekLiMail {
        require(id > 0)
        val data = request("/mails/$id").getValue("data").jsonObject
        val mail = json.decodeFromJsonElement<SeekLiMail>(data["mail"] ?: data)
        if (mail.id != id) throw SeekLiException(502)
        return mail
    }

    override suspend fun markRead(id: Long, read: Boolean) {
        require(id > 0)
        request("/mails/$id/read", "PUT", buildJsonObject { put("read", read) })
    }

    override suspend fun send(mailboxId: Long, to: List<String>, subject: String, body: String) = sendWithCopies(mailboxId, to, emptyList(), emptyList(), subject, body)

    override suspend fun sendWithCopies(mailboxId: Long, to: List<String>, cc: List<String>, bcc: List<String>, subject: String, body: String) {
        require(mailboxId > 0)
        require(to.isNotEmpty() && (to + cc + bcc).all { it.isNotBlank() && '@' in it && '\r' !in it && '\n' !in it })
        request(
            "/send",
            "POST",
            buildJsonObject {
                put("mailboxId", mailboxId)
                // The published OpenAPI lists arrays, but the official compose + request wrapper
                // sends comma-separated strings unchanged. Arrays are rejected with BAD_REQUEST.
                put("to", to.joinToString(",") { it.trim() })
                if (cc.isNotEmpty()) put("cc", cc.joinToString(",") { it.trim() })
                if (bcc.isNotEmpty()) put("bcc", bcc.joinToString(",") { it.trim() })
                put("subject", subject)
                put("bodyText", body)
                put("bodyHtml", mailMarkdownHtml(body))
            },
        )
    }

    private suspend fun request(path: String, method: String = "GET", body: JsonObject? = null): JsonObject {
        val key = apiKey().trim()
        require(key.startsWith("sk_live_") && key.none { it.isWhitespace() }) { "Invalid Seek.li API key" }
        val response = http.execute(
            HttpRequest(
                url = "https://seek.li/openapi/v1$path",
                method = method,
                headers = mapOf("Authorization" to "Bearer $key", "Accept" to "application/json"),
                body = body?.let { HttpBody.Text(it.toString()) },
            ),
        )
        if (!response.isSuccessful) throw seekLiFailure(response.code, response.body, key)
        if (response.body.isBlank()) return buildJsonObject {}
        return try {
            json.parseToJsonElement(response.body).jsonObject.also { if ("error" in it) throw seekLiFailure(502, response.body, key) }
        } catch (_: IllegalArgumentException) {
            throw SeekLiException(502)
        }
    }
}
