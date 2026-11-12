package io.github.nodyssey.ui.postlist

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.nodyssey.data.Board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopBoardStripTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `vertical wheel and primary mouse drag reveal overflowing boards without selecting them`() = runComposeUiTest {
        val scrollState = ScrollState(0)
        var selections = 0
        setContent {
            MaterialTheme {
                Box(Modifier.width(420.dp)) {
                    DesktopBoardStrip((0..29).map { Board("b$it", "Board $it", null) }, "b0", { selections++ }, modifier = Modifier.testTag("boards"), scrollState = scrollState)
                }
            }
        }
        waitForIdle()
        onNodeWithTag("boards").performMouseInput {
            moveTo(center)
            scroll(3f)
        }
        waitForIdle()
        val afterWheel = scrollState.value
        assertTrue(afterWheel > 0, "A vertical mouse wheel must move the horizontal board strip")
        onNodeWithTag("boards").performMouseInput {
            moveTo(center)
            press()
            repeat(5) { moveBy(Offset(-20f, 0f), delayMillis = 20) }
            release()
        }
        waitForIdle()
        assertTrue(scrollState.value > afterWheel, "Dragging left must reveal boards to the right")
        assertEquals(0, selections, "Scrolling must not select a board accidentally")
        onNodeWithTag("boards").performMouseInput {
            moveTo(center)
            scroll(-100f)
        }
        waitForIdle()
        assertEquals(0, scrollState.value, "Scrolling backwards clamps at the start")
    }
}
