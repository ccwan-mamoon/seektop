package io.github.seektop.ui.mail

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MailboxAddressTest {
    @Test
    fun `mailbox creation rejects prefix only and malformed addresses`() {
        assertFalse(isSeekLiMailboxAddress("vpstaxi"))
        assertFalse(isSeekLiMailboxAddress("a@@seek.li"))
        assertFalse(isSeekLiMailboxAddress("a@seek.li\n"))
        assertTrue(isSeekLiMailboxAddress("vpstaxi@seek.li"))
    }
}
