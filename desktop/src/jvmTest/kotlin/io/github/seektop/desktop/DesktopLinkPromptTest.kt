package io.github.seektop.desktop

import io.github.seektop.links.ExternalLinkRouting
import io.github.seektop.links.LinkRoutingStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLinkPromptTest {
    private class Links : ExternalLinkRouting {
        var prepared = false
        var fail = false
        val calls = mutableListOf<String>()
        override suspend fun status() = LinkRoutingStatus(prepared = prepared).also { calls += "status" }
        override suspend fun prepare(): LinkRoutingStatus {
            calls += "prepare"
            check(!fail) { "LINK_BROWSER_UNAVAILABLE" }
            prepared = true
            return status()
        }
        override suspend fun disable() = LinkRoutingStatus()
        override suspend fun openSystemSettings() {
            calls += "settings"
        }
    }

    @Test fun `first startup asks before preparing and declining is remembered without registry mutation`() = runBlocking {
        val links = Links()
        var seen = false
        val prompt = DesktopLinkPrompt(links) { seen = true }
        prompt.inspect(false)
        assertTrue(prompt.state.value.visible)
        assertEquals(listOf("status"), links.calls)
        prompt.choose(false)
        assertTrue(seen)
        assertFalse(prompt.state.value.visible)
        val next = DesktopLinkPrompt(links) { error("must not mark again") }
        next.inspect(seen)
        assertFalse(next.state.value.visible)
        assertEquals(listOf("status"), links.calls)
    }

    @Test fun `existing configuration skips the question and preparation failure still permits declining`() = runBlocking {
        var seen = false
        val existing = Links().apply { prepared = true }
        val prompt = DesktopLinkPrompt(existing) { seen = true }
        prompt.inspect(false)
        assertTrue(seen)
        assertFalse(prompt.state.value.visible)
        assertEquals(listOf("status"), existing.calls)
        val failed = Links().apply { fail = true }
        seen = false
        val retry = DesktopLinkPrompt(failed) { seen = true }
        retry.inspect(false)
        retry.choose(true)
        assertFalse(seen)
        assertTrue(retry.state.value.visible)
        assertEquals("LINK_BROWSER_UNAVAILABLE", retry.state.value.error)
        assertFalse("settings" in failed.calls)
        retry.choose(false)
        assertTrue(seen)
    }

    @Test fun `acceptance prepares before opening Windows settings and marks only once`() = runBlocking {
        val links = Links()
        var records = 0
        val prompt = DesktopLinkPrompt(links) { records++ }
        prompt.inspect(false)
        prompt.choose(true)
        prompt.choose(true)
        assertEquals(listOf("status", "prepare", "status", "settings"), links.calls)
        assertEquals(1, records)
    }
}
