package io.github.seektop.desktop

import io.github.seektop.data.settings.DesktopNotificationKind

/** Deadlines use a monotonic clock. A changed category restarts only its own countdown. */
internal class DesktopNotificationSchedule {
    private data class Slot(val seconds: Int, var deadline: Long, var failures: Int = 0)
    private val slots = mutableMapOf<DesktopNotificationKind, Slot>()

    fun update(intervals: Map<DesktopNotificationKind, Int>, now: Long) {
        slots.keys.retainAll(intervals.keys)
        intervals.forEach { (kind, seconds) ->
            if (slots[kind]?.seconds != seconds) slots[kind] = Slot(seconds, now + seconds * 1000L)
        }
    }

    fun due(now: Long): Set<DesktopNotificationKind> = slots.filterValues { it.deadline <= now }.keys
    fun waitMillis(now: Long): Long = slots.values.minOfOrNull { (it.deadline - now).coerceAtLeast(1L) } ?: 60_000L

    fun completed(kind: DesktopNotificationKind, now: Long, success: Boolean) {
        val slot = slots[kind] ?: return
        slot.failures = if (success) 0 else (slot.failures + 1).coerceAtMost(4)
        val interval = slot.seconds * 1000L
        slot.deadline = now + if (success) interval else maxOf(interval, desktopNotificationDelay(slot.failures))
    }
}
