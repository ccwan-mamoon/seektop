package io.github.seektop.desktop

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.SingletonImageLoader
import io.github.seektop.LaunchRequest
import io.github.seektop.LocalDesktopExperience
import io.github.seektop.LocalDesktopNotificationsAvailable
import io.github.seektop.LocalDesktopResizePointerIcon
import io.github.seektop.SeekTopRoot
import io.github.seektop.data.NotificationDestination
import io.github.seektop.ui.login.LocalDesktopBrowser
import io.github.seektop.ui.login.LocalDesktopVerification
import io.github.seektop.ui.navigation.TopLevelDestination
import io.github.seektop.ui.resources.about_update_checking
import io.github.seektop.ui.resources.action_cancel
import io.github.seektop.ui.resources.desktop_attendance_ask
import io.github.seektop.ui.resources.desktop_attendance_checking
import io.github.seektop.ui.resources.desktop_attendance_done
import io.github.seektop.ui.resources.desktop_attendance_failed
import io.github.seektop.ui.resources.desktop_attendance_login
import io.github.seektop.ui.resources.desktop_attendance_signed
import io.github.seektop.ui.resources.desktop_close_exit
import io.github.seektop.ui.resources.desktop_close_hint
import io.github.seektop.ui.resources.desktop_close_title
import io.github.seektop.ui.resources.desktop_close_tray
import io.github.seektop.ui.resources.desktop_tray_open
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

fun main(args: Array<String>) {
    if (args.size == 2 && args[0] == "--check-runtime") {
        checkDesktopRuntime(File(args[1]))
        return
    }
    try {
        val encodedTarget = args.indexOf("--notification-target").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        val target = encodedTarget?.let {
            runCatching {
                val activation = Json.parseToJsonElement(String(java.util.Base64.getUrlDecoder().decode(it), Charsets.UTF_8)).jsonObject
                Json.decodeFromJsonElement(NotificationDestination.serializer(), activation.getValue("destination"))
            }.getOrNull()
        }
        launchDesktop(target?.let(LaunchRequest::OpenNotification) ?: if (args.contains("--open-notifications")) LaunchRequest.OpenTab(TopLevelDestination.NOTIFICATIONS) else null)
    } catch (failure: Throwable) {
        val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "SeekTop").apply { mkdirs() }
        val log = File(directory, "startup-error.log")
        log.writeText(failure.stackTraceToString())
        javax.swing.JOptionPane.showMessageDialog(null, "启动失败：${failure.message}\n日志：${log.absolutePath}", "SeekTop · 寻桌", javax.swing.JOptionPane.ERROR_MESSAGE)
    }
}

private fun launchDesktop(initialNotification: LaunchRequest?) {
    val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "SeekTop").apply { mkdirs() }
    val resources = System.getProperty("compose.application.resources.dir")
    val bundle = File(System.getProperty("seektop.browser.bundle") ?: resources?.let { "$it/browser-runtime" } ?: "desktop/resources/windows/browser-runtime")
    // Window icons use AWT: Compose vector painters require a graphics context that
    // does not exist yet when the application creates its first Window.
    val windowIcon = javax.imageio.ImageIO.read(checkNotNull(object {}.javaClass.getResource("/seektop.png")))
    application {
        var runtime by remember { mutableStateOf<DesktopRuntime?>(null) }
        var startupFailed by remember { mutableStateOf(false) }
        var attempt by remember { mutableStateOf(0) }
        var visible by remember { mutableStateOf(true) }
        var closeRequested by remember { mutableStateOf(false) }
        DisposableEffect(Unit) { onDispose { runtime?.close() } }
        Window(onCloseRequest = { if (runtime == null) exitApplication() else closeRequested = true }, visible = visible, title = "SeekTop · 寻桌 ${System.getProperty("jpackage.app-version") ?: "development"}", state = rememberWindowState(width = 1280.dp, height = 860.dp)) {
            SideEffect {
                window.iconImage = windowIcon
                window.minimumSize = java.awt.Dimension(1100, 700)
            }
            LaunchedEffect(attempt) {
                startupFailed = false
                try {
                    loadDesktopResource(open = {
                        val browser = DesktopBrowser(File(bundle, "SeekTopBrowser.exe"), File(bundle, "resources/app"), File(directory, "browser"))
                        var container: DesktopAppContainer? = null
                        try {
                            val opened = DesktopAppContainer(browser, directory)
                            container = opened
                            val initial = kotlinx.coroutines.runBlocking {
                                browser.applyProxy(opened.proxySettings.config.first())
                                opened.settingsRepository.settings.first()
                            }
                            // Read cookies on the worker too; the first navigation composition must not block on IPC.
                            opened.sessionRepository.state.value
                            DesktopRuntime(browser, opened, initial)
                        } catch (failure: Throwable) {
                            runCatching { container?.close() ?: browser.close() }
                            throw failure
                        }
                    }) { opened ->
                        SingletonImageLoader.setSafe { context -> desktopImageLoader(context, opened.browser) }
                        runtime = opened
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    writeStartupFailure(directory, failure)
                    startupFailed = true
                }
            }
            val ready = runtime
            if (ready == null) {
                DesktopStartupScreen(startupFailed, onRetry = { attempt++ }, onExit = ::exitApplication)
                return@Window
            }
            val browser = ready.browser
            val container = ready.container
            val initial = ready.settings
            var notificationRequest by remember { mutableStateOf(initialNotification) }
            val showMainWindow = {
                closeRequested = false
                visible = true
                window.isVisible = true
                window.extendedState = window.extendedState and java.awt.Frame.ICONIFIED.inv()
                val wasAlwaysOnTop = window.isAlwaysOnTop
                try {
                    window.isAlwaysOnTop = true
                    window.toFront()
                    window.requestFocus()
                } finally {
                    window.isAlwaysOnTop = wasAlwaysOnTop
                }
            }
            val openNotifications: (NotificationDestination?) -> Unit = { target ->
                notificationRequest = target?.let(LaunchRequest::OpenNotification) ?: LaunchRequest.OpenTab(TopLevelDestination.NOTIFICATIONS)
                showMainWindow()
            }
            SideEffect {
                window.iconImage = windowIcon
                container.desktopUpdateInstaller.requestExit = { exitApplication() }
            }
            val attendance = remember { DesktopAttendanceCheck() }
            val attendanceSession by container.sessionRepository.state.collectAsState()
            val attendanceState by attendance.state.collectAsState()
            val attendancePreferences by container.settingsRepository.settings.collectAsState(initial)
            var attendanceNoticeVisible by remember { mutableStateOf(true) }
            LaunchedEffect(attendanceState) {
                attendanceNoticeVisible = true
                if (attendanceState != DesktopAttendanceState.CHECKING) {
                    kotlinx.coroutines.delay(6000)
                    attendanceNoticeVisible = false
                }
            }

            val attendanceScope = rememberCoroutineScope()
            var attendanceDialogDismissed by remember(attendanceSession.fingerprint) { mutableStateOf(false) }
            suspend fun checkAttendance(retry: Boolean = false) = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val session = container.sessionRepository.state.value
                attendance.check(
                    session.fingerprint.takeIf { session.isSignedIn },
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString(),
                    current = { container.sessionRepository.state.value.let { it.isSignedIn && it.fingerprint == session.fingerprint } },
                    hasSigned = {
                        val profile = container.profileRepository.profile(refresh = true)
                        container.assetsRepository.refreshAttendanceStatus(profile.uid).hasSignedIn
                    },
                    mode = container.settingsRepository.settings.first().desktopAttendanceMode,
                    retry = retry,
                    sign = { mode ->
                        container.assetsRepository.signInForToday(mode)
                    },
                )
            }
            LaunchedEffect(attendanceSession.fingerprint, attendanceSession.isSignedIn, attendancePreferences.desktopAttendanceMode) {
                checkAttendance(retry = attendance.state.value == DesktopAttendanceState.CHOOSE_MODE && attendancePreferences.desktopAttendanceMode != null)
            }
            LaunchedEffect(Unit) {
                checkAttendance()
                container.appUpdateRepository.checkOnLaunch()
            }
            LaunchedEffect(Unit) {
                androidx.compose.runtime.withFrameNanos { }
                System.getenv("SEEKTOP_UPDATE_ACK")?.let { path ->
                    val ack = File(path).canonicalFile
                    val cache = File(directory, "updates").canonicalFile
                    if (ack.name == "launch.ok" && ack.toPath().startsWith(cache.toPath())) ack.writeText("SeekTop started")
                }
            }
            if (attendanceState == DesktopAttendanceState.CHOOSE_MODE && !attendanceDialogDismissed && !closeRequested && visible) {
                io.github.seektop.ui.common.AttendanceModeDialog(
                    onPick = { mode ->
                        attendanceDialogDismissed = true
                        attendanceScope.launch {
                            container.settingsRepository.setDesktopAttendanceMode(mode)
                        }
                    },
                    onDismiss = { attendanceDialogDismissed = true },
                )
            }
            val notifier = remember {
                DesktopNotifier(windowIcon) { showMainWindow() }
            }
            DisposableEffect(notifier) { onDispose { notifier.close() } }
            val openLabel = org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.desktop_tray_open)
            val exitLabel = org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.desktop_close_exit)
            DisposableEffect(notifier, openLabel, exitLabel) {
                notifier.configureMenu(openLabel, exitLabel, showMainWindow, { exitApplication() })
                onDispose { }
            }
            if (closeRequested) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { closeRequested = false },
                    title = { androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.desktop_close_title)) },
                    text = { androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.desktop_close_hint)) },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { exitApplication() }) { androidx.compose.material3.Text(exitLabel) } },
                    dismissButton = {
                        androidx.compose.foundation.layout.Row {
                            androidx.compose.material3.TextButton(enabled = notifier.isAvailable, onClick = {
                                closeRequested = false
                                visible = false
                            }) {
                                androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.desktop_close_tray))
                            }
                            androidx.compose.material3.TextButton(onClick = { closeRequested = false }) {
                                androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.action_cancel))
                            }
                        }
                    },
                )
            }

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
                            val destination = status["activations"]?.jsonArray?.firstOrNull()?.jsonObject?.get("destination")?.let {
                                Json.decodeFromJsonElement(NotificationDestination.serializer(), it)
                            }
                            openNotifications(destination)
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
                    monitorDesktopNotifications(container) { title, body, destination ->
                        if (container.settingsRepository.settings.first().desktopWindowsToast) browser.toast(title, body, destination) else notifier.deliver(title, body)
                    }
                }
            }
            window.minimumSize = java.awt.Dimension(1100, 700)
            CompositionLocalProvider(LocalDesktopBrowser provides browser::login, LocalDesktopVerification provides browser::verify, LocalDesktopExperience provides true, LocalDesktopNotificationsAvailable provides if (notificationSettings.desktopWindowsToast) toastAvailable else notifier.isAvailable, LocalDesktopResizePointerIcon provides PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR))) {
                val updateState by container.appUpdateRepository.state.collectAsState()
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    SeekTopRoot(container, initial, TopLevelDestination.HOME, notificationRequest, { notificationRequest = null })
                    val attendanceLabel = when (attendanceState) {
                        DesktopAttendanceState.IDLE -> null
                        DesktopAttendanceState.CHECKING -> io.github.seektop.ui.resources.Res.string.desktop_attendance_checking
                        DesktopAttendanceState.ALREADY_DONE -> io.github.seektop.ui.resources.Res.string.desktop_attendance_done
                        DesktopAttendanceState.SIGNED -> io.github.seektop.ui.resources.Res.string.desktop_attendance_signed
                        DesktopAttendanceState.NEED_LOGIN -> io.github.seektop.ui.resources.Res.string.desktop_attendance_login
                        DesktopAttendanceState.CHOOSE_MODE -> io.github.seektop.ui.resources.Res.string.desktop_attendance_ask
                        DesktopAttendanceState.FAILED -> io.github.seektop.ui.resources.Res.string.desktop_attendance_failed
                    }
                    if (attendanceLabel != null && attendanceNoticeVisible) {
                        androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(12.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                            androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(attendanceLabel), modifier = androidx.compose.ui.Modifier.padding(10.dp), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (updateState.check == io.github.seektop.foundation.core.update.UpdateCheck.Checking) {
                        androidx.compose.material3.Surface(modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp), shadowElevation = 4.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                            androidx.compose.foundation.layout.Row(modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.about_update_checking))
                                androidx.compose.material3.TextButton(onClick = { (container.appUpdateRepository as DesktopUpdateRepository).cancelCheck() }) { androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.action_cancel)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
