package io.github.seektop.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopExternalLinksTest {
    @Test fun `native external routes reject lookalike origins credentials ports and non-native pages`() {
        for (url in listOf("https://www.nodeseek.com/post-123-2#29", "https://nodeseek.com/space/99", "http://nodeseek.com:80/post-1-1", "https://nodeseek.com:443/post-1-1")) assertTrue(supportedDesktopLink(url), url)
        for (url in listOf("https://nodeseek.com.evil.test/post-1-1", "https://evil.test/?url=https://nodeseek.com/post-1-1", "https://u:p@nodeseek.com/post-1-1", "https://nodeseek.com:444/post-1-1", "https://nodeseek.com/account", "file:///post-1-1", "https://nodeseek.com/%70ost-1-1", "https://nodeseek.com/post-0-1")) assertFalse(supportedDesktopLink(url), url)
    }
}
