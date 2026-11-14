package io.github.seektop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.seektop.ui.navigation.TopLevelDestination
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_back
import io.github.seektop.ui.resources.action_create_post
import io.github.seektop.ui.resources.desktop_boards
import io.github.seektop.ui.resources.desktop_boards_collapse
import io.github.seektop.ui.resources.desktop_boards_expand
import io.github.seektop.ui.resources.desktop_manage_boards
import io.github.seektop.ui.resources.desktop_menu_collapse
import io.github.seektop.ui.resources.desktop_menu_expand
import io.github.seektop.ui.resources.home_search_hint
import io.github.seektop.ui.resources.plugin_center
import io.github.seektop.ui.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/** Supplied by the desktop shell; platform affordances do not change persisted mobile preferences. */
val LocalDesktopExperience = staticCompositionLocalOf { false }

val LocalDesktopNotificationsAvailable = staticCompositionLocalOf { true }

/** Optional native shortcut: empty string opens the center, otherwise selects an installed tool. */
val LocalDesktopPluginLaunch = staticCompositionLocalOf<String?> { null }

/** The shell supplies its platform resize cursor without exposing AWT to common UI. */
val LocalDesktopResizePointerIcon = staticCompositionLocalOf { androidx.compose.ui.input.pointer.PointerIcon.Default }

@OptIn(ExperimentalMaterial3Api::class)
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
    searchSession: DesktopSearchSession? = null,
    toolPlugins: io.github.seektop.plugins.ToolPluginManager? = null,
    workspaceKey: Any? = null,
    pinnedBoards: List<String> = listOf("daily", "tech"),
    onPinnedBoardsChange: (List<String>) -> Unit = {},
    onOpenBoard: () -> Unit = { onSelect(TopLevelDestination.HOME) },
    mobileNavigation: @Composable (@Composable () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    if (!desktop) {
        mobileNavigation(content)
        return
    }
    val initialPlugin = LocalDesktopPluginLaunch.current
    var pluginsVisible by rememberSaveable { mutableStateOf(initialPlugin != null) }
    val pluginStateHolder = rememberSaveableStateHolder()
    var previousWorkspace by remember { mutableStateOf(workspaceKey) }
    LaunchedEffect(workspaceKey) {
        if (previousWorkspace != workspaceKey) pluginsVisible = false
        previousWorkspace = workspaceKey
    }
    val goBack = { if (pluginsVisible) pluginsVisible = false else onBack() }
    val search = {
        pluginsVisible = false
        onSearch()
    }
    val createPost = {
        pluginsVisible = false
        onCreatePost()
    }
    val settings = {
        pluginsVisible = false
        onSettings()
    }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchSession?.focusRequests) { if ((searchSession?.focusRequests ?: 0) > 0) searchFocus.requestFocus() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val boardNavigation = remember { DesktopBoardNavigation() }
    val selectDestination: (TopLevelDestination) -> Unit = { destination ->
        pluginsVisible = false
        if (destination == TopLevelDestination.HOME) boardNavigation.select(null)
        onSelect(destination)
    }
    var managingBoards by remember { mutableStateOf(false) }
    var menuCollapsed by rememberSaveable { mutableStateOf(true) }
    var boardsExpanded by rememberSaveable { mutableStateOf(true) }
    val boardListState = rememberLazyListState()
    val refreshTargets = remember { DesktopRefreshTargets() }
    val refreshCurrent = { if (!refreshTargets.refresh()) onRefresh() }
    Row(
        modifier.fillMaxSize().focusRequester(focusRequester).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                event.isCtrlPressed && event.key == Key.K -> {
                    if (pluginsVisible) {
                        search()
                    } else if (searchSession != null) {
                        searchFocus.requestFocus()
                    } else {
                        search()
                    }
                    true
                }

                event.isCtrlPressed && event.key == Key.N -> {
                    createPost()
                    true
                }

                event.isCtrlPressed && event.key == Key.Comma -> {
                    settings()
                    true
                }

                event.isAltPressed && event.key == Key.DirectionLeft -> {
                    goBack()
                    true
                }

                else -> false
            }
        }.focusable(),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.width(if (menuCollapsed) 72.dp else 216.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    androidx.compose.foundation.Image(seekTopIconPainter(), "SeekTop", Modifier.size(34.dp).clickable(role = Role.Button) { selectDestination(TopLevelDestination.HOME) })
                    if (!menuCollapsed) {
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("SeekTop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("NodeSeek Desktop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                DesktopMenuIcon(stringResource(if (menuCollapsed) Res.string.desktop_menu_expand else Res.string.desktop_menu_collapse), false, { menuCollapsed = !menuCollapsed }) {
                    Icon(if (menuCollapsed) Icons.Default.Menu else Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                }
                TopLevelDestination.entries.forEach { destination ->
                    if (menuCollapsed) {
                        DesktopMenuIcon(stringResource(destination.label), current == destination && !pluginsVisible, { selectDestination(destination) }) {
                            androidx.compose.material3.BadgedBox(badge = {
                                if (destination == TopLevelDestination.NOTIFICATIONS && unreadCount > 0) androidx.compose.material3.Badge { Text(unreadCount.toString()) }
                            }) { Icon(if (current == destination && !pluginsVisible) destination.selectedIcon else destination.icon, null) }
                        }
                    } else {
                        NavigationDrawerItem(
                            label = { Text(stringResource(destination.label)) },
                            selected = current == destination && !pluginsVisible,
                            onClick = { selectDestination(destination) },
                            icon = { Icon(if (current == destination && !pluginsVisible) destination.selectedIcon else destination.icon, null) },
                            badge = if (destination == TopLevelDestination.NOTIFICATIONS && unreadCount > 0) {
                                { Text(unreadCount.toString()) }
                            } else {
                                null
                            },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedContainerColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                selectedBadgeColor = MaterialTheme.colorScheme.primary,
                                unselectedBadgeColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
                if (toolPlugins != null) {
                    if (menuCollapsed) {
                        DesktopMenuIcon(stringResource(Res.string.plugin_center), pluginsVisible, { pluginsVisible = true }) { Icon(Icons.Default.Build, null) }
                    } else {
                        NavigationDrawerItem(selected = pluginsVisible, onClick = { pluginsVisible = true }, label = { Text(stringResource(Res.string.plugin_center)) }, icon = { Icon(Icons.Default.Build, null) }, modifier = Modifier.height(42.dp), shape = RoundedCornerShape(6.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                run {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        if (menuCollapsed) {
                            DesktopMenuIcon(stringResource(Res.string.desktop_manage_boards), false, { managingBoards = true }) { Icon(Icons.Default.Add, null) }
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.desktop_boards), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                IconButton(onClick = { managingBoards = true }) { Icon(Icons.Default.Add, stringResource(Res.string.desktop_manage_boards), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                IconButton(onClick = { boardsExpanded = !boardsExpanded }) {
                                    Icon(if (boardsExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(if (boardsExpanded) Res.string.desktop_boards_collapse else Res.string.desktop_boards_expand), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (menuCollapsed || boardsExpanded) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                io.github.seektop.ui.common.DesktopScrollbars(boardListState, reserveScrollbarSpace = !menuCollapsed) {
                                    LazyColumn(modifier = Modifier.fillMaxWidth(), state = boardListState, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(boardNavigation.boards.filter { it.slug == null || it.slug in pinnedBoards }, key = { it.slug ?: "front" }) { board ->
                                            if (menuCollapsed) {
                                                DesktopMenuIcon(board.title, current == TopLevelDestination.HOME && !pluginsVisible && boardNavigation.selectedSlug == board.slug, {
                                                    pluginsVisible = false
                                                    onOpenBoard()
                                                    boardNavigation.select(board.slug)
                                                }) { Text(board.title.take(2), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                                            } else {
                                                NavigationDrawerItem(
                                                    selected = current == TopLevelDestination.HOME && !pluginsVisible && boardNavigation.selectedSlug == board.slug,
                                                    onClick = {
                                                        pluginsVisible = false
                                                        onOpenBoard()
                                                        boardNavigation.select(board.slug)
                                                    },
                                                    label = { Text(board.title, fontSize = 13.sp, maxLines = 1) },
                                                    modifier = Modifier.height(38.dp),
                                                    shape = RoundedCornerShape(4.dp),
                                                    colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, unselectedContainerColor = Color.Transparent, selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer, unselectedTextColor = MaterialTheme.colorScheme.onSurface),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (menuCollapsed) {
                    DesktopMenuIcon(stringResource(Res.string.settings_title), false, settings) { Icon(Icons.Default.Settings, null) }
                } else {
                    TextButton(onClick = settings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(Res.string.settings_title))
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = goBack, enabled = canGoBack || pluginsVisible, colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
                if (pluginsVisible) {
                    Text(stringResource(Res.string.plugin_center), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                } else if (searchSession != null) {
                    DesktopSearchBox(searchSession, searchFocus, Modifier.weight(1f))
                } else {
                    OutlinedButton(onClick = search, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.home_search_hint))
                        Spacer(Modifier.weight(1f))
                        Text("Ctrl K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
                Button(onClick = createPost, shape = RoundedCornerShape(6.dp), modifier = Modifier.height(36.dp)) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_create_post))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))) {
                    CompositionLocalProvider(LocalDesktopRefreshTargets provides refreshTargets, LocalDesktopBoardNavigation provides boardNavigation, LocalDesktopSearch provides searchSession) {
                        if (pluginsVisible && toolPlugins != null) {
                            pluginStateHolder.SaveableStateProvider("plugins") { io.github.seektop.ui.plugins.ToolPluginCenter(toolPlugins, initialPlugin?.takeIf { it.isNotEmpty() }) }
                        } else {
                            content()
                        }
                    }
                }
            }
        }
    }
    if (managingBoards) {
        DesktopBoardPicker(boardNavigation.boards, pinnedBoards, onDismiss = { managingBoards = false }, onApply = { slugs ->
            onPinnedBoardsChange(slugs)
            managingBoards = false
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopMenuIcon(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(6.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent, contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
            Box(Modifier.semantics { contentDescription = label }, contentAlignment = Alignment.Center) { icon() }
        }
    }
}
