package io.github.seektop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopRefreshTargetsTest {
    @Test
    fun `a visible thread receives refresh before its feed and disposing it restores feed refresh`() {
        val targets = DesktopRefreshTargets()
        val feed = Any()
        val thread = Any()
        var refreshed = ""
        targets.register(feed, 1) { refreshed = "feed" }
        targets.register(thread, 2) { refreshed = "thread" }
        assertTrue(targets.refresh())
        assertEquals("thread", refreshed)
        targets.unregister(thread)
        assertTrue(targets.refresh())
        assertEquals("feed", refreshed)
        targets.unregister(feed)
        assertFalse(targets.refresh())
    }
}
