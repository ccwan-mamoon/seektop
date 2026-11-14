package io.github.seektop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.seektop.ui.common.DesktopPageJumpDialog
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPageInteractionTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `invalid page cannot submit and enter sends the corrected page`() = runDesktopComposeUiTest(width = 800, height = 600) {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        var open by mutableStateOf(true)
        var requested = 0
        setContent {
            MaterialTheme {
                if (open) {
                    DesktopPageJumpDialog(2, 7, { open = false }, {
                        requested = it
                        open = false
                    })
                }
            }
        }
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("99")
        onNodeWithText("确定").assertIsNotEnabled()
        assertEquals(0, requested)
        onNode(hasSetTextAction()).performTextReplacement("5")
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals(5, requested)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `typing a destination replaces the initially selected page number`() = runDesktopComposeUiTest(width = 800, height = 600) {
        var open by mutableStateOf(true)
        var requested = 0
        setContent {
            MaterialTheme {
                if (open) {
                    DesktopPageJumpDialog(2, 7, { open = false }, {
                        requested = it
                        open = false
                    })
                }
            }
        }
        waitForIdle()
        onNode(hasSetTextAction()).performTextInput("5")
        onNode(hasSetTextAction()).performImeAction()
        waitForIdle()
        assertEquals(5, requested)
    }
}
