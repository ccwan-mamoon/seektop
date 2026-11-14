package io.github.seektop.ui.login

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seektop.data.session.SessionRepository
import io.github.seektop.foundation.core.net.UserAgent
import io.github.seektop.ui.resources.action_close
import io.github.seektop.ui.resources.action_retry
import io.github.seektop.ui.resources.desktop_browser_failed
import io.github.seektop.ui.resources.desktop_browser_loading

/** Installed by the desktop shell; the login browser shares the shell's transport session. */
val LocalDesktopBrowser = staticCompositionLocalOf<(suspend (String) -> Unit)?> { null }

/** The desktop shell supplies a session-sharing browser; preview hosts may use the system browser. */
@Composable
actual fun WebViewRoute(
    url: String,
    title: String,
    goal: WebViewGoal,
    session: SessionRepository,
    userAgent: UserAgent,
    onOpenExternal: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    isBound: (suspend () -> Boolean)?,
) {
    val open = LocalDesktopBrowser.current
    var attempt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var failure by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    LaunchedEffect(url, attempt) {
        failure = null
        try {
            if (open != null) {
                open(url)
                session.sync()
            } else {
                onOpenExternal(url)
            }
            onClose()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message?.take(160) ?: "browser-load"
        }
    }
    androidx.compose.foundation.layout.Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        if (failure == null) androidx.compose.material3.CircularProgressIndicator()
        androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(if (failure == null) io.github.seektop.ui.resources.Res.string.desktop_browser_loading else io.github.seektop.ui.resources.Res.string.desktop_browser_failed))
        failure?.let { message ->
            androidx.compose.material3.Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            androidx.compose.material3.TextButton(onClick = { attempt++ }) { androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.action_retry)) }
        }
        androidx.compose.material3.TextButton(onClick = onClose) { androidx.compose.material3.Text(org.jetbrains.compose.resources.stringResource(io.github.seektop.ui.resources.Res.string.action_close)) }
    }
}
