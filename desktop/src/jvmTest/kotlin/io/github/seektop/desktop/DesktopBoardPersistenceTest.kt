package io.github.seektop.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.seektop.data.settings.DesktopNotificationKind
import io.github.seektop.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopBoardPersistenceTest {
    @Test fun `removing all favorites persists across a restart without restoring defaults`() = runBlocking {
        val root = File("build").toPath().toAbsolutePath().normalize()
        val directory = Files.createTempDirectory(root, "board-preferences-").toFile()
        val file = File(directory, "settings.preferences_pb").absolutePath.toPath()
        var job = SupervisorJob()
        fun repository() = SettingsRepository(PreferenceDataStoreFactory.createWithPath(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
        try {
            val first = repository()
            // Existing 0.4 settings have no Toast preference. They must migrate
            // to native delivery, without overwriting an explicit opt-out later.
            assertEquals(true, first.settings.first().desktopWindowsToast)
            first.setDesktopWindowsToast(false)
            first.setDesktopNotificationSeconds(DesktopNotificationKind.INTERACTIONS, 120)
            first.setDesktopNotificationSeconds(DesktopNotificationKind.MESSAGES, 60)
            first.setDesktopNotificationSeconds(DesktopNotificationKind.KEYWORDS, 900)
            first.setDesktopNotificationSeconds(DesktopNotificationKind.TRACKED, 7) // Invalid input falls back to its own default.
            first.setDesktopBoardSlugs(listOf("tech", "daily", "tech"))
            assertEquals(listOf("tech", "daily"), first.settings.first().desktopBoardSlugs)
            first.setDesktopBoardSlugs(emptyList())
            job.cancelAndJoin()
            job = SupervisorJob()
            val restored = repository().settings.first()
            assertEquals(false, restored.desktopWindowsToast)
            assertEquals(emptyList(), restored.desktopBoardSlugs)
            assertEquals(120, DesktopNotificationKind.INTERACTIONS.seconds(restored))
            assertEquals(60, DesktopNotificationKind.MESSAGES.seconds(restored))
            assertEquals(900, DesktopNotificationKind.KEYWORDS.seconds(restored))
            assertEquals(300, DesktopNotificationKind.TRACKED.seconds(restored))
        } finally {
            job.cancelAndJoin()
            check(directory.toPath().normalize().startsWith(root))
            directory.deleteRecursively()
        }
    }
}
