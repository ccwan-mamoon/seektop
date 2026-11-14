package io.github.seektop

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.paging.PagingData
import io.github.seektop.data.FeedPost
import kotlinx.coroutines.flow.Flow

internal class DesktopSearchSession(val query: TextFieldState, val results: Flow<PagingData<FeedPost>>, val viewModel: io.github.seektop.ui.search.SearchViewModel? = null) {
    var focusRequests by mutableStateOf(0)
    var submitted by mutableStateOf("")
    var generation by mutableStateOf(0)
    var active by mutableStateOf(false)
    var awaitingSignIn by mutableStateOf(false)
        private set
    var submit: () -> Unit = {}
    var onUserClick: (Long) -> Unit = {}

    fun submitRequest(signedIn: Boolean, search: () -> Unit) {
        submitted = query.text.toString().trim()
        generation++
        active = submitted.isNotBlank()
        awaitingSignIn = active && !signedIn
        if (active && !awaitingSignIn) search()
    }

    fun exitSearch() {
        active = false
        awaitingSignIn = false
        submitted = ""
        query.edit { replace(0, length, "") }
    }

    fun resumeAfterSignIn(signedIn: Boolean, search: () -> Unit) {
        if (!signedIn && active) awaitingSignIn = true
        if (signedIn && active && awaitingSignIn) {
            awaitingSignIn = false
            generation++
            search()
        }
    }
}
internal val LocalDesktopSearch = staticCompositionLocalOf<DesktopSearchSession?> { null }
