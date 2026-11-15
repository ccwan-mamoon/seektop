package io.github.seektop.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seektop.ai.AiSettings
import io.github.seektop.ai.PostAnalysisService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsState(val settings: AiSettings? = null, val busy: Boolean = false, val error: String? = null, val saved: Boolean = false, val tested: Boolean = false, val keyRevision: Int = 0)

class AiSettingsViewModel(private val service: PostAnalysisService) : ViewModel() {
    private val mutable = MutableStateFlow(AiSettingsState())
    val state = mutable.asStateFlow()
    private val keys = mutableMapOf<String, String>()
    init {
        load()
    }
    private fun load() {
        viewModelScope.launch {
            try {
                mutable.update { it.copy(settings = service.settings()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = e.message) }
            }
        }
    }
    fun edit(settings: AiSettings) {
        if (!state.value.busy) mutable.update { it.copy(settings = settings, saved = false, tested = false) }
    }
    fun key(id: String, value: String) {
        if (!state.value.busy) {
            keys[id] = value
            mutable.update { it.copy(saved = false, tested = false) }
        }
    }
    fun save(test: Boolean) {
        if (state.value.busy) return
        val settings = state.value.settings ?: return
        mutable.update { it.copy(busy = true, error = null, saved = false, tested = false) }
        viewModelScope.launch {
            try {
                service.save(settings, keys.toMap())
                keys.clear()
                mutable.update { it.copy(settings = service.settings(), saved = true, keyRevision = it.keyRevision + 1) }
                if (test) {
                    service.test(settings.selectedProvider)
                    mutable.update { it.copy(tested = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = e.message) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}
