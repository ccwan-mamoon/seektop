package io.github.nodyssey.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.nodyssey.ui.resources.about_update_checking
import io.github.nodyssey.ui.resources.action_cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
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
            Window(onCloseRequest = ::exitApplication, title = "SeekTop · 寻桌 ${container.appVersion.name}", state = rememberWindowState(width = 1280.dp, height = 860.dp)) {
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
                val notificationSettings by container.settingsRepository.settings.collectAsState(initial)
                var toastAvailable by remember { mutableStateOf(false) }
                LaunchedEffect(browser) {
                    var configured = false
                    while (true) {
                        try {
                            val status = if (configured) browser.notificationStatus() else browser.configureNotifications()
                            toastAvailable = status["available"]?.jsonPrimitive?.content == "true"
                            configured = toastAvailable
                            if (status["clicked"]?.jsonPrimitive?.content == "true") {
                                window.isVisible = true
                                window.extendedState = java.awt.Frame.NORMAL
                                window.toFront()
                                window.requestFocus()
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            configured = false
                            toastAvailable = false
                        }
                        kotlinx.coroutines.delay(if (configured) 1000 else 10000)
                    }
                }
                LaunchedEffect(notifier) {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        monitorDesktopNotifications(container) { title, body ->
                            if (container.settingsRepository.settings.first().desktopWindowsToast) browser.toast(title, body) else notifier.deliver(title, body)
                        }
                    }
                }
                window.minimumSize = java.awt.Dimension(1100, 700)
                CompositionLocalProvider(LocalDesktopBrowser provides browser::login, LocalDesktopVerification provides browser::verify, LocalDesktopExperience provides true, LocalDesktopNotificationsAvailable provides if (notificationSettings.desktopWindowsToast) toastAvailable else notifier.isAvailable, LocalDesktopResizePointerIcon provides PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR))) {
                    val updateState by container.appUpdateRepository.state.collectAsState()
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                        NodysseyRoot(container, initial, TopLevelDestination.HOME, null, {})
                        if (updateState.check == io.github.plaza.core.update.UpdateCheck.Checking) {
                            androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp), shadowElevation = 4.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                                androidx.compose.foundation.layout.Row(modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.nodyssey.ui.resources.Res.string.about_update_checking))
                                    androidx.compose.material3.TextButton(onClick = { (container.appUpdateRepository as DesktopUpdateRepository).cancelCheck() }) { androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.nodyssey.ui.resources.Res.string.action_cancel)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    } finally {
        container.close()
    }
}
