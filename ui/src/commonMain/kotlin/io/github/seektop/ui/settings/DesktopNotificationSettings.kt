package io.github.seektop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seektop.LocalDesktopNotificationsAvailable
import io.github.seektop.data.settings.DesktopNotificationKind
import io.github.seektop.data.settings.UserSettings
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.desktop_notify_categories
import io.github.seektop.ui.resources.desktop_notify_description
import io.github.seektop.ui.resources.desktop_notify_enable
import io.github.seektop.ui.resources.desktop_notify_minutes
import io.github.seektop.ui.resources.desktop_notify_public_hint
import io.github.seektop.ui.resources.desktop_notify_seconds
import io.github.seektop.ui.resources.desktop_notify_subscriptions
import io.github.seektop.ui.resources.desktop_notify_unavailable
import io.github.seektop.ui.resources.desktop_notify_windows_toast
import io.github.seektop.ui.resources.desktop_notify_windows_toast_hint
import io.github.seektop.ui.resources.keywords_alert_title
import io.github.seektop.ui.resources.notifications_interactions
import io.github.seektop.ui.resources.notifications_messages
import io.github.seektop.ui.resources.notify_keyword_alerts
import io.github.seektop.ui.resources.notify_quiet_hours
import io.github.seektop.ui.resources.notify_quiet_hours_hint
import io.github.seektop.ui.resources.notify_settings_title
import io.github.seektop.ui.resources.notify_tracked_threads
import io.github.seektop.ui.resources.tracked_threads_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DesktopNotificationSettings(
    settings: UserSettings,
    onEnabled: (Boolean) -> Unit,
    onQuiet: (Boolean) -> Unit,
    onInteractions: (Boolean) -> Unit,
    onMessages: (Boolean) -> Unit,
    onKeywords: (Boolean) -> Unit,
    onTracked: (Boolean) -> Unit,
    openKeywords: () -> Unit,
    openTracked: () -> Unit,
    onInterval: (DesktopNotificationKind, Int) -> Unit,
    modifier: Modifier = Modifier,
    onWindowsToast: (Boolean) -> Unit = {},
) {
    val enabled = settings.notificationsEnabled
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.widthIn(max = 920.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.notify_settings_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.desktop_notify_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!LocalDesktopNotificationsAvailable.current) Text(stringResource(Res.string.desktop_notify_unavailable), color = MaterialTheme.colorScheme.error)
            DesktopNotificationSwitch(stringResource(Res.string.desktop_notify_enable), enabled, true, onEnabled)
            DesktopNotificationSwitch(stringResource(Res.string.desktop_notify_windows_toast), settings.desktopWindowsToast, true, onWindowsToast, stringResource(Res.string.desktop_notify_windows_toast_hint))
            HorizontalDivider()
            Text(stringResource(Res.string.desktop_notify_categories), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            DesktopNotificationSwitch(stringResource(Res.string.notifications_interactions), settings.notifyInteractions, enabled, onInteractions, intervalKind = DesktopNotificationKind.INTERACTIONS, intervalSeconds = DesktopNotificationKind.INTERACTIONS.seconds(settings), onInterval = onInterval)
            DesktopNotificationSwitch(stringResource(Res.string.notifications_messages), settings.notifyMessages, enabled, onMessages, intervalKind = DesktopNotificationKind.MESSAGES, intervalSeconds = DesktopNotificationKind.MESSAGES.seconds(settings), onInterval = onInterval)
            DesktopNotificationSwitch(stringResource(Res.string.notify_quiet_hours), settings.notificationQuietHours, enabled, onQuiet, stringResource(Res.string.notify_quiet_hours_hint))
            HorizontalDivider()
            Text(stringResource(Res.string.desktop_notify_subscriptions), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            DesktopNotificationSwitch(stringResource(Res.string.notify_keyword_alerts), settings.notifyKeywordAlerts, enabled, onKeywords, intervalKind = DesktopNotificationKind.KEYWORDS, intervalSeconds = DesktopNotificationKind.KEYWORDS.seconds(settings), onInterval = onInterval)
            OutlinedButton(onClick = openKeywords) { Text(stringResource(Res.string.keywords_alert_title)) }
            DesktopNotificationSwitch(stringResource(Res.string.notify_tracked_threads), settings.notifyTrackedThreads, enabled, onTracked, intervalKind = DesktopNotificationKind.TRACKED, intervalSeconds = DesktopNotificationKind.TRACKED.seconds(settings), onInterval = onInterval)
            OutlinedButton(onClick = openTracked) { Text(stringResource(Res.string.tracked_threads_title)) }
            Text(stringResource(Res.string.desktop_notify_public_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DesktopNotificationSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, description: String? = null, intervalKind: DesktopNotificationKind? = null, intervalSeconds: Int = 30, onInterval: (DesktopNotificationKind, Int) -> Unit = { _, _ -> }) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (intervalKind != null) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = enabled && checked) { Text(intervalLabel(intervalSeconds)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    intervalKind.choices.forEach { seconds ->
                        DropdownMenuItem(text = { Text(intervalLabel(seconds)) }, onClick = {
                            onInterval(intervalKind, seconds)
                            expanded = false
                        })
                    }
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun intervalLabel(seconds: Int): String =
    if (seconds < 60) stringResource(Res.string.desktop_notify_seconds, seconds) else stringResource(Res.string.desktop_notify_minutes, seconds / 60)
