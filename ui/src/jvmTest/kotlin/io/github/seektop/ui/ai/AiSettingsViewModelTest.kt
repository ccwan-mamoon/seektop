package io.github.seektop.ui.ai

import io.github.seektop.ai.AiAnalysisRequest
import io.github.seektop.ai.AiAnalysisUpdate
import io.github.seektop.ai.AiExchangeRate
import io.github.seektop.ai.AiExchangeRates
import io.github.seektop.ai.AiSettings
import io.github.seektop.ai.DefaultAiTemplates
import io.github.seektop.ai.PostAnalysisService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AiSettingsViewModelTest {
    private class Service : PostAnalysisService {
        var overrides = mapOf("EUR" to "8")
        var fail = false
        var refreshes = 0
        override fun today() = "2026-11-15"
        override suspend fun available() = true
        override suspend fun settings() = AiSettings()
        override suspend fun save(settings: AiSettings, newKeys: Map<String, String>) = Unit
        override suspend fun test(providerId: String) = Unit
        override suspend fun templates() = DefaultAiTemplates
        override fun analyze(request: AiAnalysisRequest) = emptyFlow<AiAnalysisUpdate>()
        override suspend fun exchangeRates() = AiExchangeRates(
            listOf("USD", "EUR", "CNY").map { code ->
                AiExchangeRate(code, overrides[code] ?: if (code == "CNY") "1" else "6", "2026-11-15", code in overrides)
            },
        )
        override suspend fun saveExchangeRates(overrides: Map<String, String>): AiExchangeRates {
            check(!fail) { "disk unavailable" }
            this.overrides = overrides
            return exchangeRates()
        }
        override suspend fun refreshExchangeRates(): AiExchangeRates {
            refreshes++
            return exchangeRates()
        }
    }

    @Test fun `editing one rate preserves existing overrides and clearing only that field restores automatic`() = runBlocking<Unit> {
        withTimeout(10000) {
            val service = Service()
            val vm = withContext(Dispatchers.Main) { AiSettingsViewModel(service) }
            vm.state.first { it.rates != null }
            withContext(Dispatchers.Main) {
                vm.rate("USD", "7.2")
                vm.refreshRates()
                vm.saveRates()
            }
            vm.state.first { it.ratesSaved && !it.ratesBusy }
            assertEquals(0, service.refreshes)
            assertEquals(mapOf("USD" to "7.2", "EUR" to "8"), service.overrides)
            withContext(Dispatchers.Main) {
                vm.rate("USD", "")
                vm.saveRates()
            }
            vm.state.first { it.ratesSaved && !it.ratesBusy }
            assertEquals(mapOf("EUR" to "8"), service.overrides)
            withContext(Dispatchers.Main) { vm.saveRates(reset = true) }
            vm.state.first { it.ratesSaved && !it.ratesBusy }
            assertEquals(emptyMap(), service.overrides)
        }
    }

    @Test fun `failed save preserves draft and does not claim success`() = runBlocking<Unit> {
        withTimeout(10000) {
            val service = Service().apply { fail = true }
            val vm = withContext(Dispatchers.Main) { AiSettingsViewModel(service) }
            vm.state.first { it.rates != null }
            withContext(Dispatchers.Main) {
                vm.rate("USD", "7.2")
                vm.saveRates()
            }
            val failed = vm.state.first { it.ratesError != null && !it.ratesBusy }
            assertEquals("7.2", failed.rateDrafts["USD"])
            assertFalse(failed.ratesSaved)
            assertEquals(mapOf("EUR" to "8"), service.overrides)
        }
    }
}
