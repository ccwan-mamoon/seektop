package io.github.seektop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavKey
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopSessionGateTest {
    @Test
    fun `private page requests wait for sign in and stop again after sign out`() = runComposeUiTest {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        var destination by mutableStateOf<NavKey>(MyTopicsKey)
        var signedIn by mutableStateOf(false)
        var screenStarts = 0
        var signInRequests = 0
        setContent {
            MaterialTheme {
                DesktopSessionGate(destination, signedIn, { signInRequests++ }) {
                    LaunchedEffect(destination) { screenStarts++ }
                }
            }
        }
        for (page in listOf(MyTopicsKey, MyCommentsKey, BookmarksKey, FollowKey(), FollowKey(true), AccountSettingsKey, PostComposerKey())) {
            runOnIdle { destination = page }
            waitForIdle()
            assertEquals(0, screenStarts, "A guest must not start the private page: $page")
        }
        onNodeWithText("登录").performClick()
        assertEquals(1, signInRequests)
        runOnIdle { signedIn = true }
        waitForIdle()
        assertEquals(1, screenStarts)
        runOnIdle {
            signedIn = false
            destination = MyTopicsKey
        }
        waitForIdle()
        assertEquals(1, screenStarts)
        runOnIdle { signedIn = true }
        waitForIdle()
        assertEquals(2, screenStarts)
    }

    @Test
    fun `guests can still open local history settings and public profiles`() = runComposeUiTest {
        var destination by mutableStateOf<NavKey>(ReadHistoryKey)
        var screenStarts = 0
        setContent {
            MaterialTheme {
                DesktopSessionGate(destination, false, { error("Guest pages must not request sign in") }) {
                    LaunchedEffect(destination) { screenStarts++ }
                }
            }
        }
        waitForIdle()
        assertEquals(1, screenStarts)
        for (page in listOf(SettingsKey, AiSettingsKey, ThemeSettingsKey, UserSpaceKey(42), SignInKey, NotificationsKey)) {
            runOnIdle { destination = page }
            waitForIdle()
        }
        assertEquals(7, screenStarts)
    }
}
