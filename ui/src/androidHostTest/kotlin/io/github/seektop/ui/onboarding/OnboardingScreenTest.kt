package io.github.seektop.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.seektop.foundation.designsys.theme.SeekTopTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 新手引导 — paging to the last screen, and which screens there are.
 *
 * The screen this is really about is 单手模式: the guide exists because that band of blank keeps being
 * reported as a bug, so a test that lets it silently stop being reachable would let the bug reports
 * come back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `walks through four screens and finishes on the last`() {
        var finished = false
        composeRule.setContent {
            SeekTopTheme {
                OnboardingScreen(onFinish = { finished = true }, appLinksEnabled = false)
            }
        }

        composeRule.onNodeWithText("欢迎使用 SeekTop").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("单手模式").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("首页操作").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("编辑器工具栏").assertIsDisplayed()

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("站内链接").assertIsDisplayed()

        // The button becomes the way out only on the last screen; up to here it has been paging.
        assertFalse(finished)
        composeRule.onNodeWithText("开始使用").performClick()
        assertTrue(finished)
    }

    @Test
    fun `站内链接 screen is dropped where the platform has no such notion`() {
        composeRule.setContent {
            SeekTopTheme {
                OnboardingScreen(onFinish = {}, appLinksEnabled = null)
            }
        }

        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("下一步").performClick()
        composeRule.onNodeWithText("开始使用").assertIsDisplayed()
    }
}
