package io.github.seektop.data.mail

import io.github.seektop.foundation.core.net.HttpBody
import io.github.seektop.foundation.core.net.HttpResponse
import io.github.seektop.foundation.core.net.RecordingTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeekLiManagementTest {
    @Test
    fun `only documented routes and methods may reach the broker`() {
        assertTrue(SeekLiEndpoints.permits("DELETE", "/mailboxes/12"))
        assertTrue(SeekLiEndpoints.permits("POST", "/mailbox-release-holds/12/restore"))
        assertTrue(SeekLiEndpoints.permits("GET", "/mails/12/attachments/0"))
        assertTrue(SeekLiEndpoints.permits("POST", "/mailboxes/check?address=a%40seek.li"))
        assertFalse(SeekLiEndpoints.permits("GET", "https://evil.example/mails"))
        assertFalse(SeekLiEndpoints.permits("DELETE", "/send"))
        assertFalse(SeekLiEndpoints.permits("PUT", "/contacts/12/../../mailboxes"))
        assertFalse(SeekLiEndpoints.permits("POST", "/admin/config"))
    }

    @Test
    fun `management uses explicit method and preserves boolean false`() = runTest {
        val http = RecordingTransport(HttpResponse(200, "https://seek.li", emptyMap(), "{}"))
        val repository = DefaultSeekLiRepository(http) { "sk_live_test" }
        repository.manage("/filters/12", "PUT", buildJsonObject { put("isActive", false) })
        assertEquals("PUT", http.last.method)
        assertEquals("{\"isActive\":false}", (http.last.body as HttpBody.Text).content)
        assertFailsWith<IllegalArgumentException> { repository.manage("/admin/config", "DELETE") }
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `search encoding cannot inject another query parameter`() {
        assertEquals("a%26folder%3Dtrash", mailQuery("a&folder=trash"))
        assertEquals("%E9%82%AE%E4%BB%B6", mailQuery("\u90ae\u4ef6"))
    }

    @Test
    fun `mailbox rejection keeps explicit API reason without logging credentials or raw response`() = runTest {
        val http = RecordingTransport(
            HttpResponse(
                400,
                "https://seek.li",
                emptyMap(),
                """{"error":{"code":"MAILBOX_LOCKED","message":"Unlock mailbox first sk_live_secret"},"private":"mail body"}""",
            ),
        )
        val repository = DefaultSeekLiRepository(http) { "sk_live_secret" }
        val error = assertFailsWith<SeekLiException> { repository.manage("/mailboxes/12", "DELETE") }
        assertEquals(400, error.status)
        assertEquals("MAILBOX_LOCKED", error.apiCode)
        assertEquals("Unlock mailbox first [redacted]", error.apiMessage)
        assertFalse(error.toString().contains("Unlock"))
        assertFalse(error.toString().contains("secret"))
        assertEquals("DELETE", http.last.method)
    }

    @Test
    fun `HTML errors are never displayed as server messages`() {
        val error = seekLiFailure(400, "<html>private proxy diagnostics</html>", "sk_live_test")
        assertEquals(null, error.apiMessage)
        assertEquals(null, error.apiCode)
    }
}
