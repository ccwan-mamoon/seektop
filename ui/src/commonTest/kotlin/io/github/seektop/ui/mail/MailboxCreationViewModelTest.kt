package io.github.seektop.ui.mail

import io.github.seektop.data.mail.SeekLiMail
import io.github.seektop.data.mail.SeekLiMailPage
import io.github.seektop.data.mail.SeekLiMailbox
import io.github.seektop.data.mail.SeekLiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MailboxCreationViewModelTest {
    private fun repository(check: suspend (String) -> JsonObject): SeekLiRepository = object : SeekLiRepository {
        override suspend fun manage(path: String, method: String, body: JsonObject?): JsonObject {
            assertEquals("POST", method)
            return check(path)
        }
        override suspend fun mailboxes(): List<SeekLiMailbox> = error("unused")
        override suspend fun mails(mailboxId: Long?, folder: String, offset: Int): SeekLiMailPage = error("unused")
        override suspend fun mail(id: Long): SeekLiMail = error("unused")
        override suspend fun markRead(id: Long, read: Boolean): Unit = error("unused")
        override suspend fun send(mailboxId: Long, to: List<String>, subject: String, body: String): Unit = error("unused")
    }
    private fun reply(available: Boolean) = Json.parseToJsonElement("""{"data":{"available":$available}}""").jsonObject

    @Test
    fun `typing is debounced and changing suffix invalidates previous availability`() = runTest {
        val calls = mutableListOf<String>()
        val model = MailboxCreationViewModel(
            repository {
                calls += it
                reply(!it.contains("seek.li"))
            },
            backgroundScope,
        )
        model.edit(prefix = "vps")
        advanceTimeBy(300)
        model.edit(prefix = "vpstaxi")
        advanceTimeBy(499)
        runCurrent()
        assertTrue(calls.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("/mailboxes/check?address=vpstaxi%40nodeseek.org"), calls)
        assertTrue(model.state.value.canCreate)
        model.edit(domain = "seek.li")
        assertFalse(model.state.value.canCreate)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(false, model.state.value.available)
        assertFalse(model.state.value.canCreate)
        model.edit(prefix = "invalid@prefix")
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(2, calls.size)
    }

    @Test
    fun `late check for previous address cannot enable creation for new address`() = runTest {
        val model = MailboxCreationViewModel(
            repository { path ->
                if (path.contains("old%40")) withContext(NonCancellable) { delay(2000) }
                reply(path.contains("old%40"))
            },
            backgroundScope,
        )
        model.edit(prefix = "old")
        advanceTimeBy(500)
        runCurrent()
        model.edit(prefix = "new")
        advanceTimeBy(500)
        runCurrent()
        assertEquals(false, model.state.value.available)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals("new@nodeseek.org", model.state.value.checkedAddress)
        assertFalse(model.state.value.canCreate)
    }
}
