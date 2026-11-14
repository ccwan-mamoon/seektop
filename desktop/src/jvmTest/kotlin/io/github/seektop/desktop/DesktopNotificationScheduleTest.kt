package io.github.seektop.desktop

import io.github.seektop.data.NotificationCounts
import io.github.seektop.data.settings.DesktopNotificationKind
import io.github.seektop.data.settings.UserSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopNotificationScheduleTest {
    private val interactions = DesktopNotificationKind.INTERACTIONS
    private val messages = DesktopNotificationKind.MESSAGES
    private val keywords = DesktopNotificationKind.KEYWORDS

    @Test fun `changing one interval restarts only its countdown and disabling removes pending work`() {
        val schedule = DesktopNotificationSchedule()
        schedule.update(mapOf(interactions to 30, messages to 120, keywords to 300), 0)
        assertTrue(schedule.due(29_999).isEmpty())
        assertEquals(setOf(interactions), schedule.due(30_000))
        schedule.completed(interactions, 30_000, true)
        schedule.update(mapOf(interactions to 60, messages to 120, keywords to 300), 40_000)
        assertEquals(setOf(interactions), schedule.due(100_000))
        schedule.completed(interactions, 100_000, true)
        assertEquals(setOf(messages), schedule.due(120_000))
        schedule.update(mapOf(interactions to 60, keywords to 300), 121_000)
        assertTrue(schedule.due(121_000).isEmpty())
        schedule.update(emptyMap(), 121_000)
        assertTrue(schedule.due(Long.MAX_VALUE).isEmpty())
    }

    @Test fun `failure backs off only that category and success restores selected interval`() {
        val schedule = DesktopNotificationSchedule()
        schedule.update(mapOf(interactions to 30, keywords to 60), 0)
        schedule.completed(interactions, 30_000, false)
        assertEquals(setOf(keywords), schedule.due(60_000))
        schedule.completed(keywords, 60_000, true)
        assertTrue(schedule.due(89_999).isEmpty())
        assertEquals(setOf(interactions), schedule.due(90_000))
        schedule.completed(interactions, 90_000, true)
        assertEquals(setOf(interactions, keywords), schedule.due(120_000))
    }

    @Test fun `faster interaction checks do not consume private messages before their own interval`() {
        val tracker = DesktopUnreadTracker()
        val settings = UserSettings()
        tracker.accept(NotificationCounts(), settings, false)
        val early = NotificationCounts(replies = 1, messages = 2)
        assertEquals(1 to 0, tracker.accept(early, settings, false, interactionsDue = true, messagesDue = false))
        tracker.noteRead(early)
        assertEquals(0 to 2, tracker.accept(early, settings, false, interactionsDue = false, messagesDue = true))
        assertEquals(0 to 0, tracker.accept(early, settings, false))
    }
}
