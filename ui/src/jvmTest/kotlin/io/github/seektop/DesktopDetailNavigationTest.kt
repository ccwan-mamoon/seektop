package io.github.seektop

import androidx.navigation3.runtime.NavKey
import io.github.seektop.data.NotificationDestination
import io.github.seektop.data.NotificationTab
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDetailNavigationTest {
    @Test fun `native notifications open their own post floor conversation or reminder thread`() {
        val stack = mutableListOf<NavKey>(NotificationsKey, SettingsKey)
        stack.openDesktopNotification(NotificationDestination(postId = 7, floor = "#29", account = 42), 42)
        assertEquals(listOf<NavKey>(NotificationsKey, PostDetailKey(7, "#29")), stack)
        stack.openDesktopNotification(NotificationDestination(NotificationTab.MESSAGES, uid = 88, userName = "member", account = 42), 42)
        assertEquals(listOf<NavKey>(NotificationsKey, MessageThreadKey(88, "member")), stack)
        stack.openDesktopNotification(NotificationDestination(NotificationTab.REMINDERS, postId = 901, reminderId = "keyword:901"), 123)
        assertEquals(listOf<NavKey>(NotificationsKey, PostDetailKey(901)), stack)
        stack.openDesktopNotification(NotificationDestination(postId = 7, account = 42), 123)
        assertEquals(listOf<NavKey>(NotificationsKey), stack, "Another account's private toast must not open its content")
    }

    @Test fun `rail selection leaves settings and nested screens on every tab`() {
        for (root in listOf(PostListKey, NotificationsKey, ProfileKey)) {
            val stack = mutableListOf<NavKey>(root, SettingsKey, NotificationSettingsKey)
            stack.returnToDesktopRoot()
            assertEquals(listOf<NavKey>(root), stack)
            stack.returnToDesktopRoot()
            assertEquals(listOf<NavKey>(root), stack)
        }
    }

    @Test fun `clicking an open post closes it and a different post replaces without growing history`() {
        val stack = mutableListOf<NavKey>(PostListKey)
        stack.toggleDesktopDetail(PostDetailKey(7))
        assertEquals(listOf<NavKey>(PostListKey, PostDetailKey(7)), stack)
        stack.toggleDesktopDetail(PostDetailKey(8))
        assertEquals(listOf<NavKey>(PostListKey, PostDetailKey(8)), stack)
        stack.toggleDesktopDetail(PostDetailKey(8, page = 3))
        assertEquals(listOf<NavKey>(PostListKey), stack)
    }

    @Test fun `close reader collapses linked posts to the original list`() {
        val stack = mutableListOf<NavKey>(ProfileKey, BookmarksKey, PostDetailKey(7), PostDetailKey(8))
        stack.closeDesktopDetails()
        assertEquals(listOf<NavKey>(ProfileKey, BookmarksKey), stack)
        stack.toggleDesktopDetail(PostDetailKey(9))
        stack.add(PostDetailKey(10))
        stack.toggleDesktopDetail(PostDetailKey(10))
        assertEquals(listOf<NavKey>(ProfileKey, BookmarksKey), stack)
    }
}
