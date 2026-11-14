package io.github.seektop.ui.postdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.seektop.DesktopRefreshTargets
import io.github.seektop.LocalDesktopExperience
import io.github.seektop.LocalDesktopRefreshTargets
import io.github.seektop.foundation.core.richtext.InlineNode
import io.github.seektop.foundation.core.richtext.RichNode
import io.github.seektop.model.PostContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopDetailRefreshTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `desktop refresh from later comments requests the opening post and clears the scroll anchor`() = runComposeUiTest {
        val targets = DesktopRefreshTargets()
        val comments = (0..79).map { index -> PostContent(index.toLong(), (401 + index).toString(), "reader-$index", null, null, false, emptyList(), null, null, null, emptyList()) }
        var state by mutableStateOf(PostDetailUiState(postId = 7, title = "Fixture thread", comments = comments, commentPages = List(80) { 9 }, firstLoadedPage = 9, lastLoadedPage = 9, totalPages = 12))
        var requestedPage = 0
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDesktopExperience provides true, LocalDesktopRefreshTargets provides targets) {
                    PostDetailScreen(state, "https://www.nodeseek.com/post-7-9", {}, {}, {}, {}, {}, onRefreshPage = { page ->
                        requestedPage = page
                        state = state.copy(firstLoadedPage = page, lastLoadedPage = page, commentPages = List(80) { page }, body = comments[0].copy(floor = "0", nodes = (1..40).map { RichNode.Paragraph(listOf(InlineNode.Text("Paragraph $it: this is selectable content for mouse drag regression. ".repeat(3)))) }))
                    }, onVerify = {})
                }
            }
        }
        onNode(hasScrollAction()).performScrollToIndex(40)
        waitForIdle()
        assertTrue(onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        runOnIdle { assertTrue(targets.refresh()) }
        waitForIdle()
        assertEquals(1, requestedPage)
        assertEquals(0f, onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value())
        onNode(hasScrollAction()).performMouseInput {
            moveTo(center)
            press()
            repeat(5) { moveBy(Offset(0f, -40f), delayMillis = 20) }
            release()
        }
        waitForIdle()
        assertTrue(onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f, "Dragging rich text must scroll the desktop reader")
    }
}
