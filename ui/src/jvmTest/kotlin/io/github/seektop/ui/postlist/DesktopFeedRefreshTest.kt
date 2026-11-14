package io.github.seektop.ui.postlist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.seektop.DesktopBoardNavigation
import io.github.seektop.LocalDesktopBoardNavigation
import io.github.seektop.data.FeedPost
import io.github.seektop.model.FeedSort
import io.github.seektop.model.PostSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopFeedRefreshTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `board switching and home reselect perform real paging loads from page one`() = runComposeUiTest {
        val navigation = DesktopBoardNavigation()
        val loads = ConcurrentLinkedQueue<Pair<String?, Int>>()
        val listState = LazyListState()
        var state by mutableStateOf(PostListUiState(categorySlug = "tech", startPages = mapOf("tech" to 5), totalPages = 12))
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDesktopBoardNavigation provides navigation) {
                    val pagingScope = rememberCoroutineScope()
                    val feeds = remember { mutableMapOf<Pair<String?, Int>, Flow<PagingData<FeedPost>>>() }
                    val slug = state.categorySlug
                    val page = state.startPage
                    val paging = remember(slug, page) {
                        feeds.getOrPut(slug to page) {
                            Pager(PagingConfig(pageSize = 20), initialKey = page) {
                                object : PagingSource<Int, FeedPost>() {
                                    override fun getRefreshKey(state: PagingState<Int, FeedPost>): Int? = null
                                    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FeedPost> {
                                        val loadedPage = params.key ?: 1
                                        loads.add(slug to loadedPage)
                                        val post = FeedPost(PostSummary(1, "Board $slug page $loadedPage", "reader", null, null, "board", slug, 1, 1, null, null), false, 0, loadedPage)
                                        return LoadResult.Page(listOf(post), null, null)
                                    }
                                }
                            }.flow.cachedIn(pagingScope)
                        }
                    }
                    DesktopPostListScreen(state, paging.collectAsLazyPagingItems(), listState, {}, { selected ->
                        state = state.copy(categorySlug = selected, startPages = state.startPages + (selected to 1))
                    }, {}, {}, {}, { target -> state = state.copy(startPages = state.startPages + (state.categorySlug to target)) }, 0)
                }
            }
        }
        waitUntil { loads.contains("tech" to 5) }
        runOnIdle { navigation.select("tech") }
        waitUntil { loads.contains("tech" to 1) && navigation.pendingSelection == null }
        runOnIdle { navigation.select("daily") }
        waitUntil { loads.contains("daily" to 1) && navigation.pendingSelection == null }
        val techBefore = loads.count { it == ("tech" to 1) }
        runOnIdle { navigation.select("tech") }
        waitUntil { loads.count { it == ("tech" to 1) } > techBefore && navigation.pendingSelection == null }
        runOnIdle { navigation.select(null) }
        waitUntil { loads.contains(null to 1) && navigation.pendingSelection == null }
        waitForIdle()
        val before = loads.count { it == (null to 1) }
        runOnIdle { navigation.select(null) }
        waitUntil { loads.count { it == (null to 1) } > before && navigation.pendingSelection == null }
        assertEquals(null, state.categorySlug)
        assertEquals(1, state.startPage)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `refresh from a paged away feed requests page one and resets scroll while preserving sort`() = runComposeUiTest {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        val listState = LazyListState()
        var state by mutableStateOf(PostListUiState(sort = FeedSort.POST_TIME, startPages = mapOf(null to 5), totalPages = 12))
        var requestedPage = 0
        val paging = flowOf(
            PagingData.from(
                (0..99).map { index ->
                    FeedPost(PostSummary(index.toLong(), "Topic $index", "reader", null, null, "board", null, 1, 1, null, null), false, 0, 5)
                },
                sourceLoadStates = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true)),
            ),
        )
        setContent {
            MaterialTheme {
                DesktopPostListScreen(state, paging.collectAsLazyPagingItems(), listState, {}, {}, {}, {}, {}, { page ->
                    requestedPage = page
                    state = state.copy(startPages = mapOf(null to page))
                }, 0)
            }
        }
        waitUntil { listState.layoutInfo.totalItemsCount >= 100 }
        runOnIdle { listState.requestScrollToItem(30) }
        waitForIdle()
        assertTrue(listState.firstVisibleItemIndex >= 30)
        onNodeWithContentDescription("刷新").performClick()
        waitForIdle()
        assertEquals(1, requestedPage)
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(FeedSort.POST_TIME, state.sort)
        runOnIdle { listState.requestScrollToItem(30) }
        waitForIdle()
        runOnIdle { state = state.copy(categorySlug = "tech") }
        waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex, "Changing board must not restore its old scroll position")
        onNode(hasScrollAction()).performMouseInput {
            moveTo(center)
            press()
            repeat(5) { moveBy(Offset(0f, -40f), delayMillis = 20) }
            release()
        }
        waitForIdle()
        assertTrue(listState.firstVisibleItemIndex > 0, "Mouse dragging must scroll the desktop feed")
    }
}
