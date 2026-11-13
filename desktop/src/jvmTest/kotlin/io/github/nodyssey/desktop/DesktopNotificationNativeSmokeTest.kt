package io.github.nodyssey.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.SystemTray
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopNotificationNativeSmokeTest {
    @Test fun `native tray registers delivers a local smoke notification and releases its icon`() = runBlocking {
        if (System.getenv("SEEKTOP_NOTIFY_SMOKE") != "1") return@runBlocking
        withContext(Dispatchers.Swing) {
            assertTrue(SystemTray.isSupported())
            val tray = SystemTray.getSystemTray()
            val before = tray.trayIcons.toSet()
            val notifier = DesktopNotifier(BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)) {}
            try {
                assertTrue(notifier.isAvailable)
                assertEquals(before.size + 1, tray.trayIcons.size)
                notifier.deliver("SeekTop · 通知测试", "这是本地通知功能测试，不包含真实消息或账号数据。")
            } finally {
                notifier.close()
            }
            assertEquals(before, tray.trayIcons.toSet())
        }
    }
}
