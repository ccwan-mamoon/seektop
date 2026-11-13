package io.github.nodyssey

import androidx.compose.foundation.text.input.TextFieldState
import io.github.nodyssey.data.emptyLoadedPagingData
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSearchSessionTest {
    @Test fun `signed out search waits for sign in then resumes exactly once`() {
        val session = DesktopSearchSession(TextFieldState("server"), flowOf(emptyLoadedPagingData()))
        var requests = 0
        session.submitRequest(false) { requests++ }
        assertEquals(0, requests)
        assertTrue(session.awaitingSignIn)
        session.resumeAfterSignIn(false) { requests++ }
        assertEquals(0, requests)
        session.resumeAfterSignIn(true) { requests++ }
        session.resumeAfterSignIn(true) { requests++ }
        assertEquals(1, requests)
        assertFalse(session.awaitingSignIn)
        assertEquals("server", session.submitted)
    }

    @Test fun `clearing search cancels deferred requests and leaves the board list active`() {
        val session = DesktopSearchSession(TextFieldState("server"), flowOf(emptyLoadedPagingData()))
        var requests = 0
        session.submitRequest(false) { requests++ }
        session.query.edit { replace(0, length, "") }
        session.submitRequest(false) { requests++ }
        session.resumeAfterSignIn(true) { requests++ }
        assertEquals(0, requests)
        assertFalse(session.active)
        assertFalse(session.awaitingSignIn)
    }

    @Test fun `switching boards cancels a search waiting for sign in`() {
        val session = DesktopSearchSession(TextFieldState("server"), flowOf(emptyLoadedPagingData()))
        var requests = 0
        session.submitRequest(false) { requests++ }
        session.exitSearch()
        session.resumeAfterSignIn(true) { requests++ }
        assertEquals(0, requests)
        assertFalse(session.active)
        assertFalse(session.awaitingSignIn)
        assertEquals("", session.query.text.toString())
    }

    @Test fun `signing out hides the active search and requires a new signed in request`() {
        val session = DesktopSearchSession(TextFieldState("server"), flowOf(emptyLoadedPagingData()))
        var requests = 0
        session.submitRequest(true) { requests++ }
        session.resumeAfterSignIn(false) { requests++ }
        assertEquals(1, requests)
        assertTrue(session.awaitingSignIn)
        session.resumeAfterSignIn(true) { requests++ }
        assertEquals(2, requests)
        assertFalse(session.awaitingSignIn)
    }
}
