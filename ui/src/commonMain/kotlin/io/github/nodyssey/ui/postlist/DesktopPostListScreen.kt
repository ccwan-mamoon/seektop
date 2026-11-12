package io.github.nodyssey.ui.postlist

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.data.FeedPost
import io.github.nodyssey.model.FeedSort
import io.github.nodyssey.ui.common.DesktopScrollbars
import io.github.nodyssey.ui.common.EmptyFeedState
import io.github.nodyssey.ui.common.SiteErrorState
import io.github.nodyssey.ui.common.shortMessage
import io.github.nodyssey.ui.common.webViewUrl
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.action_refresh
import io.github.nodyssey.ui.resources.desktop_open_browser
import io.github.nodyssey.ui.resources.sort_by_post_time
import io.github.nodyssey.ui.resources.sort_by_reply_time
import io.github.nodyssey.ui.resources.tab_home
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.UserAvatar
import org.jetbrains.compose.resources.stringResource

/** Mouse-oriented feed: no swipe pager, pull refresh, collapsing header or floating controls. */
@Composable
internal fun DesktopPostListScreen(
    state: PostListUiState,
    posts: LazyPagingItems<FeedPost>,
    listState: LazyListState,
    onPostClick: (FeedPost) -> Unit,
    onBoardClick: (String?) -> Unit,
    onSortChange: (FeedSort) -> Unit,
    onSignIn: () -> Unit,
    onVerify: (String) -> Unit,
    onGoToPage: (Int) -> Unit,
    refreshRequests: Int,
    modifier: Modifier = Modifier,
) {
    io.github.nodyssey.DesktopRefreshTarget(priority = 1, onRefresh = posts::refresh)
    var answered by rememberSaveable { mutableIntStateOf(refreshRequests) }
    LaunchedEffect(refreshRequests) {
        if (answered != refreshRequests) {
            answered = refreshRequests
            posts.refresh()
        }
    }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val refresh = posts.loadState.refresh
    val error = (refresh as? LoadState.Error)?.error?.toSiteError()
    val page = if (listState.firstVisibleItemIndex < posts.itemCount) posts.peek(listState.firstVisibleItemIndex)?.page ?: state.startPage else state.startPage
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        DesktopBoardStrip(
            boards = (state.boards + state.parkedBoards).distinctBy { it.slug },
            selectedSlug = state.categorySlug,
            onSelect = onBoardClick,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.selectedBoardTitle ?: stringResource(Res.string.tab_home), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = { onSortChange(if (state.sort == FeedSort.LAST_REPLY) FeedSort.POST_TIME else FeedSort.LAST_REPLY) }) {
                Text(stringResource(if (state.sort == FeedSort.LAST_REPLY) Res.string.sort_by_reply_time else Res.string.sort_by_post_time))
            }
            TextButton(onClick = posts::refresh, enabled = refresh !is LoadState.Loading) { Text(stringResource(Res.string.action_refresh)) }
        }
        HorizontalDivider()
        if (refresh is LoadState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null && posts.itemCount > 0) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error.shortMessage(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onVerify(error.webViewUrl(NodeSeekSite.BASE_URL)) }) { Text(stringResource(Res.string.desktop_open_browser)) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                error != null && posts.itemCount == 0 -> SiteErrorState(error = error, onRetry = posts::refresh, onVerify = onVerify, onSignIn = onSignIn, onOpenBrowser = { onVerify(error.webViewUrl(NodeSeekSite.BASE_URL)) })

                refresh is LoadState.NotLoading && posts.itemCount == 0 -> EmptyFeedState()

                else -> DesktopScrollbars(listState) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(posts.itemCount, key = posts.itemKey { it.summary.postId }) { index ->
                            val post = posts[index]
                            if (post == null) {
                                Spacer(Modifier.fillMaxWidth().height(74.dp))
                            } else {
                                val summary = post.summary
                                val interaction = remember(summary.postId) { MutableInteractionSource() }
                                val hovered by interaction.collectIsHoveredAsState()
                                io.github.nodyssey.ui.common.DesktopPostContextMenu(NodeSeekSite.BASE_URL + NodeSeekSite.postPath(summary.postId)) {
                                    Row(
                                        Modifier.fillMaxWidth().background(
                                            when {
                                                selectedId == summary.postId -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                                hovered -> MaterialTheme.colorScheme.surfaceContainerLow
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                        ).hoverable(interaction).clickable(interactionSource = interaction, indication = LocalIndication.current) {
                                            selectedId = summary.postId
                                            onPostClick(post)
                                        }.padding(horizontal = 16.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        UserAvatar(url = summary.avatarUrl, name = summary.authorName, size = 30.dp)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(summary.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = if (post.isRead) FontWeight.Normal else FontWeight.SemiBold, color = if (post.isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(summary.authorName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                                summary.categoryTitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                                Icon(PlazaIcons.Reply, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(summary.commentCount?.toString() ?: "—", style = MaterialTheme.typography.labelSmall)
                                                summary.lastActiveText?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                        when (val append = posts.loadState.append) {
                            is LoadState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            is LoadState.Error -> item { TextButton(onClick = posts::retry) { Text(append.error.toSiteError().shortMessage()) } }
                            else -> Unit
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onGoToPage((page - 1).coerceAtLeast(1)) }, enabled = page > 1) { Text("‹") }
            Text("$page / ${state.totalPages}", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { onGoToPage((page + 1).coerceAtMost(state.totalPages)) }, enabled = page < state.totalPages) { Text("›") }
        }
    }
}
