package io.github.seektop

import io.github.seektop.ui.common.parseDesktopPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPreferencesTest {
    @Test fun `page jump rejects invalid and overflowing pages instead of silently changing destination`() {
        assertEquals(77, parseDesktopPage(" 77 ", 100))
        for (value in listOf("0", "-1", "101", "1.5", "abc", "999999999999999")) assertNull(parseDesktopPage(value, 100))
        assertEquals(1, parseDesktopPage("1", 0))
    }
}
