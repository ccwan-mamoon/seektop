package io.github.nodyssey

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.nodyssey.ui.navigation.TopLevelDestination
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNavigationFrameTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `sidebar and toolbar remain available while nested content is shown`() = runComposeUiTest {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        var selected = TopLevelDestination.HOME
        var created = 0
        var wentBack = 0
        var openedBoard = 0
        var slug: String? = null
        setContent {
            MaterialTheme {
                DesktopAwareNavigationFrame(
                    desktop = true,
                    current = TopLevelDestination.HOME,
                    unreadCount = 2,
                    onSelect = { selected = it },
                    canGoBack = true,
                    onBack = { wentBack++ },
                    onSearch = {},
                    onCreatePost = { created++ },
                    onSettings = {},
                    onBookmarks = {},
                    onHistory = {},
                    onRefresh = {},
                    onOpenBoard = { openedBoard++ },
                    mobileNavigation = { error("Desktop must not use mobile navigation") },
                ) {
                    PublishDesktopBoards(io.github.nodyssey.data.CategoryRepository.initialBoards, slug) { slug = it }
                    Text("Nested reader")
                }
            }
        }
        onNodeWithText("Nested reader").assertIsDisplayed()
        onNodeWithContentDescription("通知").performClick()
        assertEquals(TopLevelDestination.NOTIFICATIONS, selected)
        // Compact board navigation must open Home as well as publish the slug:
        // merely changing the board while a nested page is shown is invisible.
        onNodeWithContentDescription("技术").performClick()
        assertEquals(1, openedBoard)
        assertEquals("tech", slug)
        onNodeWithText("发帖").performClick()
        assertEquals(1, created)
        onNodeWithContentDescription("返回").performClick()
        assertEquals(1, wentBack)
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.N)
            keyUp(Key.CtrlLeft)
        }
        assertEquals(2, created)
        onNodeWithContentDescription("设置").assertIsDisplayed()
    }
}
