package io.github.nodyssey.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.github.nodyssey.data.session.SessionRepository
import io.github.plaza.core.net.UserAgent

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
    LaunchedEffect(url) {
        if (open != null) {
            open(url)
            session.sync()
        } else {
            onOpenExternal(url)
        }
        onClose()
    }
}
