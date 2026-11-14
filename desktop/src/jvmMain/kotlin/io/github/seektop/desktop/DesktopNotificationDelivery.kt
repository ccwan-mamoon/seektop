package io.github.seektop.desktop

import io.github.seektop.data.MessageConversation
import io.github.seektop.data.NotificationDestination
import kotlinx.coroutines.CancellationException

internal class DesktopMessageArrivals {
    private var previous: Map<Long, MessageConversation>? = null
    fun accept(rows: List<MessageConversation>): List<MessageConversation> {
        val before = previous
        previous = rows.associateBy { it.uid }
        if (before == null) return emptyList()
        return rows.filter { row ->
            val old = before[row.uid]
            row.unreadCount > 0 && !row.isSnippetMine && (old == null || row.unreadCount > old.unreadCount || (row.updatedAtMillis ?: 0) > (old.updatedAtMillis ?: 0))
        }
    }
}

internal class DesktopInteractionArrivals {
    private var seen: Set<String>? = null
    fun accept(ids: List<String>): Set<String> {
        val old = seen
        seen = ((old ?: emptySet()) + ids).toList().takeLast(5000).toSet()
        return if (old == null) emptySet() else ids.toSet() - old
    }
}

internal data class DesktopPendingNotification(val title: String, val body: String, val destination: NotificationDestination)

/** Delivery acknowledges one item at a time, so a later failure cannot replay successful toasts. */
internal class DesktopNotificationOutbox {
    private val pending = linkedMapOf<String, DesktopPendingNotification>()
    val hasPending: Boolean get() = pending.isNotEmpty()
    fun enqueue(id: String, notification: DesktopPendingNotification) {
        pending.putIfAbsent(id, notification)
    }
    fun clear() = pending.clear()
    suspend fun flush(deliver: suspend (String, String, NotificationDestination) -> Unit) {
        for ((id, notification) in pending.toMap()) {
            try {
                deliver(notification.title, notification.body, notification.destination)
                pending.remove(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return
            }
        }
    }
}
