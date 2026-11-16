package io.github.seektop.data.mail

import io.github.seektop.foundation.core.net.HttpBody
import io.github.seektop.foundation.core.net.HttpResponse
import io.github.seektop.foundation.core.net.RecordingTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SeekLiRepositoryTest {
    private fun response(body: String, code: Int = 200) = HttpResponse(code, "https://seek.li/openapi/v1/mails", emptyMap(), body)

    @Test
    fun `detail accepts nested mail and omitted mailbox id`() = runTest {
        val http = RecordingTransport(response("{\"data\":{\"mail\":{\"id\":42,\"bodyText\":\"hello\"}}}"))
        assertEquals("hello", DefaultSeekLiRepository(http) { "sk_live_test" }.mail(42).plainText)
    }

    @Test
    fun `markdown becomes escaped formatted HTML`() {
        val html = mailMarkdownHtml("**bold**\n\n<script>alert(1)</script>")
        kotlin.test.assertTrue(html.contains("<strong>bold</strong>"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun `html-only body becomes inert native text`() {
        val mail = SeekLiMail(1, 1, bodyHtml = "<p>Hello <b>reader</b></p><script>fetch(\"https://example.invalid\")</script><img src=\"https://example.invalid/pixel\">")
        assertEquals("Hello reader", mail.plainText)
    }

    @Test
    fun `empty mailbox accepts nullable or omitted list and pagination`() = runTest {
        for (payload in listOf("{\"data\":[]}", "{\"data\":null,\"pagination\":null}", "{\"pagination\":{\"total\":0}}")) {
            val http = RecordingTransport(response(payload))
            val page = DefaultSeekLiRepository(http) { "sk_live_test" }.mails(3)
            assertEquals(emptyList(), page.data)
            assertEquals(0, page.pagination.total)
        }
    }

    @Test
    fun `successful status with error payload is not an empty mailbox`() = runTest {
        val http = RecordingTransport(response("{\"error\":{\"message\":\"failed\"}}"))
        assertFailsWith<SeekLiException> { DefaultSeekLiRepository(http) { "sk_live_test" }.mails(3) }
    }

    @Test
    fun `empty list does not hide permission failures`() = runTest {
        val http = RecordingTransport(response("{\"data\":[]}", 403))
        val error = assertFailsWith<SeekLiException> { DefaultSeekLiRepository(http) { "sk_live_test" }.mails(3) }
        assertEquals(403, error.status)
    }

    @Test
    fun `pagination uses mailbox filter and preserves unread state`() = runTest {
        val http = RecordingTransport(response("""{"data":[{"id":42,"mailboxId":3,"isRead":false,"subject":"test"}],"pagination":{"total":43,"limit":20,"offset":20}}"""))
        val page = DefaultSeekLiRepository(http) { "sk_live_test" }.mails(3, offset = 20)
        assertEquals("https://seek.li/openapi/v1/mails?folder=inbox&limit=20&offset=20&mailbox_id=3", http.last.url)
        assertEquals(43, page.pagination.total)
        assertFalse(page.data.single().isRead)
        assertEquals("Bearer sk_live_test", http.last.headers["Authorization"])
    }

    @Test
    fun `read mutation uses documented boolean field and method`() = runTest {
        val http = RecordingTransport(response("{}"))
        DefaultSeekLiRepository(http) { "sk_live_test" }.markRead(42, true)
        assertEquals("PUT", http.last.method)
        val body = Json.parseToJsonElement((http.last.body as HttpBody.Text).content).jsonObject
        assertEquals("true", body.getValue("read").jsonPrimitive.content)
    }

    @Test
    fun `send encodes recipients and body as JSON without a retry`() = runTest {
        val http = RecordingTransport(response("{}"))
        DefaultSeekLiRepository(http) { "sk_live_test" }.send(3, listOf("a@example.com", "b@example.com"), "A \"quote\"", "line 1\nline 2")
        assertEquals("https://seek.li/openapi/v1/send", http.last.url)
        assertEquals("POST", http.last.method)
        val body = Json.parseToJsonElement((http.last.body as HttpBody.Text).content).jsonObject
        assertEquals("a@example.com,b@example.com", body.getValue("to").jsonPrimitive.content)
        assertEquals("line 1\nline 2", body.getValue("bodyText").jsonPrimitive.content)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `authentication errors never expose response body`() = runTest {
        val http = RecordingTransport(response("private diagnostic", 401))
        val error = assertFailsWith<SeekLiException> { DefaultSeekLiRepository(http) { "sk_live_test" }.mailboxes() }
        assertEquals(401, error.status)
        assertFalse(error.message.orEmpty().contains("private diagnostic"))
    }
}
