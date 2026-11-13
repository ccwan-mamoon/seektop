package io.github.nodyssey.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import io.github.nodyssey.LocalDesktopExperience
import io.github.nodyssey.LocalDesktopNotificationsAvailable
import io.github.nodyssey.LocalDesktopResizePointerIcon
import io.github.nodyssey.NodysseyRoot
import io.github.nodyssey.ui.login.LocalDesktopBrowser
import io.github.nodyssey.ui.login.LocalDesktopVerification
import io.github.nodyssey.ui.navigation.TopLevelDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

fun main() {
    try {
        launchDesktop()
    } catch (failure: Throwable) {
        val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "SeekTop").apply { mkdirs() }
        val log = File(directory, "startup-error.log")
        log.writeText(failure.stackTraceToString())
        javax.swing.JOptionPane.showMessageDialog(null, "启动失败：${failure.message}\n日志：${log.absolutePath}", "SeekTop · 寻桌", javax.swing.JOptionPane.ERROR_MESSAGE)
    }
}

private fun launchDesktop() {
    val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "SeekTop").apply { mkdirs() }
    val resources = System.getProperty("compose.application.resources.dir")
    val bundle = File(System.getProperty("nodyssey.browser.bundle") ?: resources?.let { "$it/browser-runtime" } ?: "desktop/resources/windows/browser-runtime")
    val browser = DesktopBrowser(File(bundle, "SeekTopBrowser.exe"), File(bundle, "resources/app"), File(directory, "browser"))
    val container = DesktopAppContainer(browser, directory)
    SingletonImageLoader.setSafe { context -> desktopImageLoader(context, browser) }
    val initial = runBlocking { container.settingsRepository.settings.first() }
    // Window icons use AWT: Compose vector painters require a graphics context that
    // does not exist yet when the application creates its first Window.
    val windowIcon = javax.imageio.ImageIO.read(checkNotNull(object {}.javaClass.getResource("/seektop.png")))
    try {
        application {
            Window(onCloseRequest = ::exitApplication, title = "SeekTop · 寻桌 — 本地验收版", state = rememberWindowState(width = 1280.dp, height = 860.dp)) {
                SideEffect {
                    window.iconImage = windowIcon
                    container.desktopUpdateInstaller.requestExit = { exitApplication() }
                }
                LaunchedEffect(Unit) {
                    container.appUpdateRepository.checkOnLaunch()
                    androidx.compose.runtime.withFrameNanos { }
                    System.getenv("SEEKTOP_UPDATE_ACK")?.let { path ->
                        val ack = File(path).canonicalFile
                        val cache = File(directory, "updates").canonicalFile
                        if (ack.name == "launch.ok" && ack.toPath().startsWith(cache.toPath())) ack.writeText("SeekTop started")
                    }
                }
                val notifier = remember {
                    DesktopNotifier(windowIcon) {
                        window.isVisible = true
                        window.extendedState = java.awt.Frame.NORMAL
                        window.toFront()
                        window.requestFocus()
                    }
                }
                DisposableEffect(notifier) { onDispose { notifier.close() } }
                LaunchedEffect(notifier) { withContext(kotlinx.coroutines.Dispatchers.IO) { monitorDesktopNotifications(container, notifier::deliver) } }
                window.minimumSize = java.awt.Dimension(1100, 700)
                CompositionLocalProvider(LocalDesktopBrowser provides browser::login, LocalDesktopVerification provides browser::verify, LocalDesktopExperience provides true, LocalDesktopNotificationsAvailable provides notifier.isAvailable, LocalDesktopResizePointerIcon provides PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR))) {
                    NodysseyRoot(container, initial, TopLevelDestination.HOME, null, {})
                }
            }
        }
    } finally {
        container.close()
    }
}
