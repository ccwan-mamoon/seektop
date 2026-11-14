package io.github.seektop.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.seektop.data.settings.Reminder
import io.github.seektop.data.settings.ReminderInbox
import io.github.seektop.data.settings.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderInboxTest {
    @Test fun `reminders survive process store restart and reading one leaves others unread`() = runBlocking {
        val directory = Files.createTempDirectory("seektop-reminder-test").toFile()
        val file = directory.resolve("reminders.preferences_pb")
        var job = SupervisorJob()
        fun open() = ReminderInbox(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
        try {
            val first = open()
            first.add(
                listOf(
                    Reminder("keyword:7", ReminderKind.KEYWORD, 7, "keyword thread", "deal", 1),
                    Reminder("tracked:8:2", ReminderKind.TRACKED, 8, "tracked thread", "3", 2, floor = "29"),
                ),
            )
            first.markRead("keyword:7")
            job.cancelAndJoin()
            job = SupervisorJob()
            val restarted = open()
            val rows = restarted.reminders.first()
            assertEquals(listOf(8L, 7L), rows.map { it.postId })
            assertTrue(rows[0].isUnread)
            assertEquals("29", rows[0].floor)
            assertEquals(null, rows[1].floor)
            assertFalse(rows[1].isUnread)
            restarted.add(listOf(rows[1].copy(isUnread = true)))
            assertFalse(restarted.reminders.first().last().isUnread, "Polling must not resurrect an existing read reminder")
            restarted.markRead()
            assertTrue(restarted.reminders.first().none { it.isUnread })
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
