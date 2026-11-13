package io.github.nodyssey

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDetailNavigationTest {
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
