package io.github.seektop.ui.mail

import io.github.seektop.data.mail.DefaultSeekLiRepository
import io.github.seektop.data.mail.SeekLiAccount
import io.github.seektop.data.mail.SeekLiRepository
import io.github.seektop.foundation.core.net.HttpRequest
import io.github.seektop.foundation.core.net.HttpResponse
import io.github.seektop.foundation.core.net.HttpTransport
import io.github.seektop.foundation.core.net.UploadProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MailSendFailureTest {
    @Test
    fun `send rejection reaches the dialog without retrying or marking the draft sent`() = runTest {
        var sends = 0
        val repository = DefaultSeekLiRepository(object : HttpTransport {
            override suspend fun execute(request: HttpRequest, onUploadProgress: UploadProgress?): HttpResponse {
                sends++
                assertEquals("https://seek.li/openapi/v1/send", request.url)
                return HttpResponse(400, request.url, emptyMap(), """{"error":{"code":"SEND_REJECTED","message":"fixture reason"}}""")
            }
        }) { "sk_live_test" }
        val account = object : SeekLiAccount, SeekLiRepository by repository {
            override suspend fun connected() = false
            override suspend fun connect(key: String) = Unit
            override suspend fun disconnect() = Unit
        }
        val model = MailViewModel(account, backgroundScope)
        runCurrent()
        model.send(1, "recipient@example.com", "subject", "body")
        runCurrent()
        assertEquals(1, sends)
        assertEquals(400, model.state.value.error)
        assertEquals("SEND_REJECTED\nfixture reason", model.state.value.errorDescription)
        assertEquals(true, model.state.value.sendError)
        assertFalse(model.state.value.sending)
        assertFalse(model.state.value.sent)
        model.clearError()
        assertNull(model.state.value.error)
        assertEquals("", model.state.value.errorDescription)
    }
}
