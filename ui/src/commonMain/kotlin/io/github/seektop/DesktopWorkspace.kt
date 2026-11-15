package io.github.seektop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import io.github.seektop.ui.navigation.TopLevelDestination
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.ai_settings
import io.github.seektop.ui.resources.desktop_account_overview
import io.github.seektop.ui.resources.profile_section_content
import io.github.seektop.ui.resources.profile_section_settings
import io.github.seektop.ui.resources.profile_tile_about
import io.github.seektop.ui.resources.profile_tile_account
import io.github.seektop.ui.resources.profile_tile_collections
import io.github.seektop.ui.resources.profile_tile_comments
import io.github.seektop.ui.resources.profile_tile_followers
import io.github.seektop.ui.resources.profile_tile_following
import io.github.seektop.ui.resources.profile_tile_history
import io.github.seektop.ui.resources.profile_tile_notifications
import io.github.seektop.ui.resources.profile_tile_theme
import io.github.seektop.ui.resources.profile_tile_topics
import io.github.seektop.ui.resources.settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class WorkspaceDestination(val key: NavKey, val label: StringResource)

@Composable
internal fun DesktopWorkspace(desktop: Boolean, tab: TopLevelDestination, current: NavKey?, isSignedIn: Boolean, onSignIn: () -> Unit, onSelect: (NavKey) -> Unit, content: @Composable () -> Unit) {
    val personal = tab == TopLevelDestination.PROFILE && current != SignInKey && current !is WebKey
    val settings = current == SettingsKey || current == AiSettingsKey || current == ThemeSettingsKey || current == AccountSettingsKey || current == NotificationSettingsKey || current == AboutAppKey
    if (!desktop) {
        content()
        return
    }
    val guardedContent: @Composable () -> Unit = {
        DesktopSessionGate(current, isSignedIn, onSignIn, content)
    }
    if (!personal && !settings) {
        guardedContent()
        return
    }
    val account = listOf(
        WorkspaceDestination(ProfileKey, Res.string.desktop_account_overview),
        WorkspaceDestination(MyTopicsKey, Res.string.profile_tile_topics),
        WorkspaceDestination(MyCommentsKey, Res.string.profile_tile_comments),
        WorkspaceDestination(BookmarksKey, Res.string.profile_tile_collections),
        WorkspaceDestination(ReadHistoryKey, Res.string.profile_tile_history),
        WorkspaceDestination(FollowKey(), Res.string.profile_tile_following),
        WorkspaceDestination(FollowKey(showFollowers = true), Res.string.profile_tile_followers),
    )
    val preferences = listOf(
        WorkspaceDestination(SettingsKey, Res.string.settings_title),
        WorkspaceDestination(AiSettingsKey, Res.string.ai_settings),
        WorkspaceDestination(AccountSettingsKey, Res.string.profile_tile_account),
        WorkspaceDestination(NotificationSettingsKey, Res.string.profile_tile_notifications),
        WorkspaceDestination(ThemeSettingsKey, Res.string.profile_tile_theme),
        WorkspaceDestination(AboutAppKey, Res.string.profile_tile_about),
    )
    Row(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.width(168.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(10.dp)) {
                if (personal) {
                    Text(stringResource(Res.string.profile_section_content), modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    account.forEach { item -> WorkspaceItem(item, current, onSelect) }
                }
                Text(stringResource(Res.string.profile_section_settings), modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                preferences.forEach { item -> WorkspaceItem(item, current, onSelect) }
            }
        }
        VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.weight(1f).fillMaxHeight()) { guardedContent() }
    }
}

@Composable
private fun WorkspaceItem(item: WorkspaceDestination, current: NavKey?, onSelect: (NavKey) -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(item.label), style = MaterialTheme.typography.bodyMedium) },
        selected = current == item.key,
        onClick = { onSelect(item.key) },
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.height(38.dp),
    )
}
