package io.github.seektop.desktop

import io.github.seektop.links.ExternalLinkRouting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class DesktopLinkPromptState(val visible: Boolean = false, val busy: Boolean = false, val error: String? = null)

/** Persist a decision once; inspecting an existing installation never changes browser associations. */
internal class DesktopLinkPrompt(private val service: ExternalLinkRouting, private val markSeen: suspend () -> Unit) {
    private val mutable = MutableStateFlow(DesktopLinkPromptState())
    val state = mutable.asStateFlow()
    private var inspected = false
    suspend fun inspect(seen: Boolean) {
        if (seen || inspected) return
        inspected = true
        try {
            if (service.status().prepared) markSeen() else mutable.update { it.copy(visible = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(visible = true, error = e.message) }
        }
    }
    suspend fun choose(enable: Boolean) {
        if (state.value.busy || !state.value.visible) return
        mutable.update { it.copy(busy = true, error = null) }
        try {
            if (enable) {
                service.prepare()
                service.openSystemSettings()
            }
            markSeen()
            mutable.update { it.copy(visible = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutable.update { it.copy(error = e.message) }
        } finally {
            mutable.update { it.copy(busy = false) }
        }
    }
}
