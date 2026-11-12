package io.github.nodyssey

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_back
import io.github.nodyssey.ui.resources.action_create_post
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.bookmarks_title
import io.github.nodyssey.ui.resources.desktop_boards
import io.github.nodyssey.ui.resources.desktop_boards_collapse
import io.github.nodyssey.ui.resources.desktop_boards_expand
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
    onOpenBoard: () -> Unit = { onSelect(TopLevelDestination.HOME) },
    mobileNavigation: @Composable (@Composable () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!desktop) {
        mobileNavigation(content)
        return
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val boardNavigation = remember { DesktopBoardNavigation() }
    var boardsExpanded by rememberSaveable { mutableStateOf(true) }
    val boardListState = rememberLazyListState()
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
        Surface(color = Color(0xFF121E2E), contentColor = Color.White) {
            Column(Modifier.width(212.dp).fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF66DFC1)) {
                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Text("S", color = Color(0xFF122D30), fontWeight = FontWeight.Bold) }
                    }
                    Column {
                        Text("SeekTop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("NodeSeek Desktop", style = MaterialTheme.typography.labelSmall, color = Color(0xFF91A2B8))
                    }
                }
                Text(stringResource(Res.string.desktop_workspace), style = MaterialTheme.typography.labelMedium, color = Color(0xFF91A2B8), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
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
                        shape = RoundedCornerShape(10.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF233E47),
                            unselectedContainerColor = Color.Transparent,
                            selectedIconColor = Color(0xFF84E7CF),
                            selectedTextColor = Color(0xFFB6F4E5),
                            unselectedIconColor = Color(0xFFA2B3C9),
                            unselectedTextColor = Color(0xFFD2DCE9),
                            selectedBadgeColor = Color(0xFF84E7CF),
                            unselectedBadgeColor = Color(0xFFD2DCE9),
                        ),
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), color = Color.White.copy(alpha = 0.08f))
                TextButton(onClick = onBookmarks, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD2DCE9))) {
                    Icon(PlazaIcons.Bookmark, null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.bookmarks_title))
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onHistory, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD2DCE9))) {
                    Icon(PlazaIcons.History, null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.history_title))
                    Spacer(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.desktop_boards), color = Color(0xFF91A2B8), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        IconButton(onClick = { boardsExpanded = !boardsExpanded }) {
                            Icon(if (boardsExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(if (boardsExpanded) Res.string.desktop_boards_collapse else Res.string.desktop_boards_expand), tint = Color(0xFF91A2B8))
                        }
                    }
                    if (boardsExpanded) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            io.github.nodyssey.ui.common.DesktopScrollbars(boardListState) {
                                LazyColumn(state = boardListState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    items(boardNavigation.boards, key = { it.slug ?: "front" }) { board ->
                                        NavigationDrawerItem(
                                            selected = current == TopLevelDestination.HOME && boardNavigation.selectedSlug == board.slug,
                                            onClick = {
                                                onOpenBoard()
                                                boardNavigation.select(board.slug)
                                            },
                                            label = { Text(board.title, fontSize = 13.sp, maxLines = 1) },
                                            modifier = Modifier.height(38.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0xFF233E47), unselectedContainerColor = Color.Transparent, selectedTextColor = Color(0xFFB6F4E5), unselectedTextColor = Color(0xFFD2DCE9)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD2DCE9))) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.settings_title))
                    Spacer(Modifier.weight(1f))
                }
                Text("SEE MORE. SEEK MORE.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF72859D), modifier = Modifier.padding(12.dp))
            }
        }
        Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, enabled = canGoBack, colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
                OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.home_search_hint))
                    Spacer(Modifier.weight(1f))
                    Text("Ctrl K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                TextButton(onClick = refreshCurrent) {
                    Icon(Icons.Default.Refresh, null)
                    Text(stringResource(Res.string.action_refresh))
                    Text("  F5", style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onCreatePost, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(42.dp)) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_create_post))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))) {
                    CompositionLocalProvider(LocalDesktopRefreshTargets provides refreshTargets, LocalDesktopBoardNavigation provides boardNavigation) { content() }
                }
            }
        }
    }
}
