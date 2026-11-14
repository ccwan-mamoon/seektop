package io.github.seektop.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.seektop.data.AttendanceMode
import io.github.seektop.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopAttendanceSettingsTest {
    @Test fun `attendance choice survives restart and unknown choices ask rather than silently signing`() = runBlocking {
        val directory = Files.createTempDirectory("seektop-attendance").toFile()
        val file = directory.resolve("settings.preferences_pb")
        var job = SupervisorJob()
        fun open() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            var store = open()
            var settings = SettingsRepository(store)
            assertNull(settings.settings.first().desktopAttendanceMode)
            settings.setDesktopAttendanceMode(AttendanceMode.RANDOM)
            job.cancelAndJoin()
            job = SupervisorJob()
            store = open()
            settings = SettingsRepository(store)
            assertEquals(AttendanceMode.RANDOM, settings.settings.first().desktopAttendanceMode)
            store.edit { it[stringPreferencesKey("desktop_attendance_mode")] = "future-unrecognized-mode" }
            assertNull(settings.settings.first().desktopAttendanceMode)
            settings.setDesktopAttendanceMode(AttendanceMode.FIXED_FIVE)
            settings.setDesktopAttendanceMode(null)
            assertNull(settings.settings.first().desktopAttendanceMode)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
