package io.github.nodyssey.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_close
import io.github.nodyssey.ui.resources.desktop_verify_loading
import io.github.nodyssey.ui.resources.desktop_verify_passed
import io.github.nodyssey.ui.resources.desktop_verify_retry
import io.github.nodyssey.ui.resources.desktop_verify_start
import io.github.nodyssey.ui.resources.desktop_verify_timeout
import io.github.nodyssey.ui.resources.desktop_verify_title
import io.github.nodyssey.ui.resources.desktop_verify_unavailable
import io.github.nodyssey.ui.resources.desktop_verify_working
import io.github.plaza.core.net.UserAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The shell supplies the same Chromium session used by native sign-in requests. */
val LocalDesktopVerification = staticCompositionLocalOf<(suspend (Boolean, java.awt.Component) -> String?)?> { null }

@Composable
actual fun TurnstileWidget(
    sitekey: String,
    darkTheme: Boolean,
    userAgent: UserAgent,
    resetSignal: Int,
    onToken: (String) -> Unit,
    onExpired: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier,
) {
    val timeoutMessage = stringResource(Res.string.desktop_verify_timeout)
    val loadingLabel = stringResource(Res.string.desktop_verify_loading)
    val host = remember {
        java.awt.Panel(java.awt.BorderLayout()).apply {
            background = java.awt.Color.WHITE
            add(java.awt.Label(loadingLabel, java.awt.Label.CENTER), java.awt.BorderLayout.CENTER)
        }
    }
    val verify = LocalDesktopVerification.current
    val currentToken by rememberUpdatedState(onToken)
    val currentExpired by rememberUpdatedState(onExpired)
    var attempt by remember(resetSignal, sitekey, darkTheme) { mutableIntStateOf(0) }
    var busy by remember(resetSignal, sitekey, darkTheme) { mutableStateOf(false) }
    var passed by remember(resetSignal, sitekey, darkTheme) { mutableStateOf(false) }
    var failureMessage by remember(resetSignal, sitekey, darkTheme) { mutableStateOf<String?>(null) }
    var failed by remember(resetSignal, sitekey, darkTheme) { mutableStateOf(false) }
    DisposableEffect(sitekey, darkTheme, resetSignal) { onDispose { if (passed) currentExpired() } }
    LaunchedEffect(verify) { if (verify == null) onUnavailable() }
    LaunchedEffect(resetSignal, attempt, sitekey, darkTheme) {
        if (attempt == 0 || verify == null) return@LaunchedEffect
        busy = true
        try {
            val token = verify(darkTheme, host)
            busy = false
            if (token.isNullOrBlank()) {
                failed = true
                currentExpired()
            } else {
                passed = true
                currentToken(token)
                // Close the widget window after success; expire locally before a token can go stale.
                delay(240_000)
                passed = false
                currentExpired()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureMessage = if (failure.message == "verification-timeout" || failure.message == "widget-not-ready") timeoutMessage else failure.message?.take(160)
            failed = true
            currentExpired()
        } finally {
            busy = false
        }
    }
    if (busy) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
            attempt = 0
            currentExpired()
        }) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Column(Modifier.width(420.dp).height(280.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.desktop_verify_title), modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = {
                            attempt = 0
                            currentExpired()
                        }) { Text(stringResource(Res.string.action_close)) }
                    }
                    androidx.compose.ui.awt.SwingPanel(factory = { javax.swing.JPanel(java.awt.BorderLayout()).apply { add(host, java.awt.BorderLayout.CENTER) } }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                passed = false
                failureMessage = null
                failed = false
                currentExpired()
                attempt++
            }, enabled = verify != null && !busy && !passed) {
                Text(
                    stringResource(
                        if (passed) {
                            Res.string.desktop_verify_passed
                        } else if (failed) {
                            Res.string.desktop_verify_retry
                        } else {
                            Res.string.desktop_verify_start
                        },
                    ),
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp))
                TextButton(onClick = {
                    attempt = 0
                    currentExpired()
                }) { Text(stringResource(Res.string.action_close)) }
            }
        }
        if (verify == null || busy || failed) {
            Text(
                failureMessage ?: stringResource(
                    if (verify == null) {
                        Res.string.desktop_verify_unavailable
                    } else if (busy) {
                        Res.string.desktop_verify_working
                    } else if (failed) {
                        Res.string.desktop_verify_retry
                    } else if (passed) {
                        Res.string.desktop_verify_passed
                    } else {
                        Res.string.desktop_verify_start
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
