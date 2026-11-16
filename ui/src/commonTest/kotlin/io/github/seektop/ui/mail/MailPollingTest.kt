package io.github.seektop.ui.mail

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MailPollingTest {
    @Test
    fun `default minute and changing interval replace the previous timer`() = runTest {
        var count = 0
        val poll = MailPolling(backgroundScope, { true }, { count++ })
        poll.start()
        advanceTimeBy(59999)
        runCurrent()
        assertEquals(0, count)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, count)
        poll.start(30)
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(2, count)
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(3, count)
    }

    @Test
    fun `disconnected ticks are skipped and slow fetches never overlap`() = runTest {
        var allowed = false
        var count = 0
        val poll = MailPolling(backgroundScope, { allowed }, {
            count++
            delay(90000)
        })
        poll.start(30)
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(0, count)
        allowed = true
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(1, count)
        advanceTimeBy(90000)
        runCurrent()
        assertEquals(1, count)
        advanceTimeBy(30000)
        runCurrent()
        assertEquals(2, count)
    }
}
