package io.github.nodyssey

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_create_post
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.bookmarks_title
import io.github.nodyssey.ui.resources.desktop_workspace
import io.github.nodyssey.ui.resources.history_title
import io.github.nodyssey.ui.resources.home_search_hint
import io.github.nodyssey.ui.resources.settings_title
import io.github.plaza.designsys.component.PlazaIcons
import org.jetbrains.compose.resources.stringResource

/** Supplied by the desktop shell; platform affordances do not change persisted mobile preferences. */
val LocalDesktopExperience = staticCompositionLocalOf { false }

@Composable
internal fun DesktopAwareNavigationFrame(
    desktop: Boolean,
    current: TopLevelDestination,
    unreadCount: Int,
    onSelect: (TopLevelDestination) -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onCreatePost: () -> Unit,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    mobileNavigation: @Composable (@Composable () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!desktop) {
        mobileNavigation(content)
        return
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val refreshTargets = remember { DesktopRefreshTargets() }
    val refreshCurrent = { if (!refreshTargets.refresh()) onRefresh() }
    Row(
        modifier.fillMaxSize().focusRequester(focusRequester).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                event.isCtrlPressed && event.key == Key.K -> {
                    onSearch()
                    true
                }

                event.isCtrlPressed && event.key == Key.N -> {
                    onCreatePost()
                    true
                }

                event.isCtrlPressed && event.key == Key.Comma -> {
                    onSettings()
                    true
                }

                event.isAltPressed && event.key == Key.DirectionLeft -> {
                    onBack()
                    true
                }

                event.key == Key.F5 -> {
                    refreshCurrent()
                    true
                }

                else -> false
            }
        }.focusable(),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.width(196.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SeekTop", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(12.dp))
                Text(stringResource(Res.string.desktop_workspace), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                TopLevelDestination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(destination.label)) },
                        selected = current == destination,
                        onClick = { onSelect(destination) },
                        icon = { Icon(if (current == destination) destination.selectedIcon else destination.icon, null) },
                        badge = if (destination == TopLevelDestination.NOTIFICATIONS && unreadCount > 0) {
                            { Text(unreadCount.toString()) }
                        } else {
                            null
                        },
                        modifier = Modifier.height(44.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                TextButton(onClick = onBookmarks, modifier = Modifier.fillMaxWidth()) {
                    Icon(PlazaIcons.Bookmark, null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.bookmarks_title))
                }
                TextButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                    Icon(PlazaIcons.History, null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.history_title))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.settings_title))
                }
                Text("NodeSeek · Desktop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
        }
        VerticalDivider()
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, enabled = canGoBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
                OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.home_search_hint))
                    Spacer(Modifier.weight(1f))
                    Text("Ctrl K", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = refreshCurrent) {
                    Icon(Icons.Default.Refresh, null)
                    Text(stringResource(Res.string.action_refresh))
                    Text("  F5", style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onCreatePost) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_create_post))
                }
            }
            HorizontalDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) { CompositionLocalProvider(LocalDesktopRefreshTargets provides refreshTargets) { content() } }
        }
    }
}
