package io.github.seektop.desktop

import io.github.seektop.data.MessageConversation
import io.github.seektop.data.NotificationDestination
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNotificationDeliveryTest {
    private fun conversation(uid: Long, count: Int, time: Long) = MessageConversation(uid, "member", null, emptyList(), false, time, null, count, false)

    @Test fun `new message does not renotify unchanged unread conversations`() {
        val tracker = DesktopMessageArrivals()
        val old = conversation(1, 3, 100)
        val other = conversation(2, 1, 100)
        assertTrue(tracker.accept(listOf(old, other)).isEmpty())
        assertEquals(listOf(2L), tracker.accept(listOf(old, other.copy(unreadCount = 2, updatedAtMillis = 200))).map { it.uid })
        assertTrue(tracker.accept(listOf(old, other.copy(unreadCount = 2, updatedAtMillis = 200))).isEmpty())
    }

    @Test fun `delivery failure retries remaining items without replaying acknowledged ones`() = runBlocking {
        val outbox = DesktopNotificationOutbox()
        for (id in listOf("first", "second")) outbox.enqueue(id, DesktopPendingNotification(id, "body", NotificationDestination()))
        val delivered = mutableListOf<String>()
        outbox.flush { title, _, _ -> if (title == "second") error("Windows failure") else delivered.add(title) }
        assertEquals(listOf("first"), delivered)
        assertTrue(outbox.hasPending)
        outbox.flush { title, _, _ -> delivered.add(title) }
        assertEquals(listOf("first", "second"), delivered)
        assertFalse(outbox.hasPending)
    }
}
