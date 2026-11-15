package io.github.seektop.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seektop.ai.AiExchangeRates
import io.github.seektop.ai.AiSettings
import io.github.seektop.ai.PostAnalysisService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsState(val settings: AiSettings? = null, val busy: Boolean = false, val error: String? = null, val saved: Boolean = false, val tested: Boolean = false, val keyRevision: Int = 0, val rates: AiExchangeRates? = null, val rateDrafts: Map<String, String> = emptyMap(), val ratesBusy: Boolean = false, val ratesSaved: Boolean = false, val ratesError: String? = null)

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
                try {
                    val rates = service.exchangeRates()
                    mutable.update { it.copy(rates = rates) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    mutable.update { it.copy(ratesError = e.message) }
                }
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
    fun rate(code: String, value: String) {
        if (!state.value.ratesBusy && value.length <= 40 && code != "CNY") mutable.update { it.copy(rateDrafts = it.rateDrafts + (code to value), ratesSaved = false, ratesError = null) }
    }
    fun saveRates(reset: Boolean = false) {
        val state = state.value
        if (state.ratesBusy) return
        val rates = state.rates ?: return
        val overrides = if (reset) {
            emptyMap()
        } else {
            buildMap {
                rates.rates.filterNot { it.code == "CNY" }.forEach { row ->
                    val value = state.rateDrafts[row.code]
                    if (value != null) {
                        if (value.isNotBlank()) put(row.code, value.trim())
                    } else if (row.manual) {
                        put(row.code, row.toCny)
                    }
                }
            }
        }
        updateRates(saved = true) { service.saveExchangeRates(overrides) }
    }
    fun refreshRates() {
        if (state.value.ratesBusy || state.value.rateDrafts.isNotEmpty()) return
        updateRates(saved = false) { service.refreshExchangeRates() }
    }
    private fun updateRates(saved: Boolean, operation: suspend () -> AiExchangeRates) {
        mutable.update { it.copy(ratesBusy = true, ratesSaved = false, ratesError = null) }
        viewModelScope.launch {
            try {
                val result = operation()
                mutable.update { it.copy(rates = result, rateDrafts = emptyMap(), ratesSaved = saved, ratesError = if (!saved && result.refreshFailed) "FX_REFRESH_FAILED" else null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                mutable.update { it.copy(ratesError = e.message) }
            } finally {
                mutable.update { it.copy(ratesBusy = false) }
            }
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
