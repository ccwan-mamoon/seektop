package io.github.nodyssey.desktop

import io.github.nodyssey.data.NotificationCounts
import io.github.nodyssey.data.settings.UserSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNotificationMonitorTest {
    private val settings = UserSettings(notifyInteractions = true, notifyMessages = true)

    @Test fun `first launch seeds existing unread and subsequent arrivals notify only once`() {
        val tracker = DesktopUnreadTracker()
        assertEquals(0 to 0, tracker.accept(NotificationCounts(replies = 4, mentions = 4, overlap = 4, messages = 3), settings, false))
        val new = NotificationCounts(replies = 5, mentions = 5, overlap = 5, messages = 5)
        assertEquals(1 to 2, tracker.accept(new, settings, false))
        assertEquals(0 to 0, tracker.accept(new, settings, false))
        assertEquals(0 to 0, tracker.accept(NotificationCounts(), settings, false))
        assertEquals(1 to 0, tracker.accept(NotificationCounts(replies = 1), settings, false))
    }

    @Test fun `quiet hours consume arrivals without a morning burst and disabled categories stay silent`() {
        val tracker = DesktopUnreadTracker()
        tracker.accept(NotificationCounts(), settings, false)
        val night = NotificationCounts(replies = 7, messages = 2)
        assertEquals(0 to 0, tracker.accept(night, settings, true))
        assertEquals(0 to 0, tracker.accept(night, settings, false))
        assertEquals(0 to 1, tracker.accept(night.copy(replies = 8, messages = 3), settings.copy(notifyInteractions = false), false))
        assertEquals(0 to 0, tracker.accept(night.copy(replies = 8, messages = 3), settings, false))
        assertEquals(0 to 0, DesktopUnreadTracker().accept(night, settings, false))
    }

    @Test fun `reading between polls lowers baseline but refreshing another screen cannot consume arrivals`() {
        val tracker = DesktopUnreadTracker()
        tracker.accept(NotificationCounts(replies = 5, messages = 4), settings, false)
        tracker.noteRead(NotificationCounts(replies = 1, messages = 0))
        assertEquals(1 to 1, tracker.accept(NotificationCounts(replies = 2, messages = 1), settings, false))
        tracker.noteRead(NotificationCounts(replies = 3, messages = 2))
        assertEquals(1 to 1, tracker.accept(NotificationCounts(replies = 3, messages = 2), settings, false))
    }

    @Test fun `quiet boundaries and retry delays prevent overnight alerts and request bursts`() {
        assertTrue(desktopQuietHours(settings, 23 * 60))
        assertTrue(desktopQuietHours(settings, 7 * 60 - 1))
        assertFalse(desktopQuietHours(settings, 7 * 60))
        assertFalse(desktopQuietHours(settings.copy(notificationQuietHours = false), 0))
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L, 300_000L, 300_000L), (0..5).map(::desktopNotificationDelay))
    }
}
