package io.github.nodyssey.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import io.github.nodyssey.LocalDesktopExperience
import io.github.nodyssey.NodysseyRoot
import io.github.nodyssey.ui.login.LocalDesktopBrowser
import io.github.nodyssey.ui.login.LocalDesktopVerification
import io.github.nodyssey.ui.navigation.TopLevelDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    try {
        application {
            Window(onCloseRequest = ::exitApplication, title = "SeekTop · 寻桌 — 本地验收版", state = rememberWindowState(width = 1280.dp, height = 860.dp)) {
                window.minimumSize = java.awt.Dimension(1100, 700)
                CompositionLocalProvider(LocalDesktopBrowser provides browser::login, LocalDesktopVerification provides browser::verify, LocalDesktopExperience provides true) {
                    NodysseyRoot(container, initial, TopLevelDestination.HOME, null, {})
                }
            }
        }
    } finally {
        container.close()
    }
}
