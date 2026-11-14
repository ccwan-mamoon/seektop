package io.github.seektop.ui.notifications

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.seektop.LocalDesktopExperience
import io.github.seektop.data.NotificationTab
import io.github.seektop.data.settings.Reminder
import io.github.seektop.data.settings.ReminderKind
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopNotificationTabsTest {
    @Test fun `desktop shows interactions messages reminders and opens a local reminder while signed out`() = runComposeUiTest {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        val reminder = Reminder("tracked:8:2", ReminderKind.TRACKED, 8, "Tracked thread 8", "3", 2)
        var state by mutableStateOf(NotificationsUiState(reminders = listOf(reminder)))
        var opened: Reminder? = null
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDesktopExperience provides true) {
                    NotificationsScreen(
                        state = state,
                        onSignIn = {}, onVerify = {}, onTabChange = { state = state.copy(selectedTab = it) },
                        onRetry = {}, onMarkAllRead = {}, onNotificationClick = {}, onConversationClick = {},
                        onNewConversation = {}, onNewConversationQueryChange = {}, onNewConversationSearch = {},
                        onNewConversationDismiss = {}, onRecipientClick = {}, showOneHandTip = false,
                        onDismissOneHandTip = {}, onOpenSettings = {}, onReminderClick = { opened = it },
                    )
                }
            }
        }
        onNodeWithText("互动").assertIsDisplayed()
        onNodeWithText("私信").assertIsDisplayed()
        onNodeWithText("提醒").performClick()
        assertEquals(NotificationTab.REMINDERS, state.selectedTab)
        onNodeWithText("Tracked thread 8").performClick()
        assertEquals(reminder, opened)
        onNodeWithText("提醒关键词").assertIsDisplayed()
        onNodeWithText("私信").performClick()
        onNodeWithText("登录").assertIsDisplayed()
    }
}
