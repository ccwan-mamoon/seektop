package io.github.seektop.ui.composer

import io.github.seektop.ai.AiExchangeRate
import io.github.seektop.ai.AiExchangeRates
import io.github.seektop.vps.VpsCalculatorService
import io.github.seektop.vps.VpsInput
import io.github.seektop.vps.VpsResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class VpsCalculatorViewModelTest {
    private class Service : VpsCalculatorService {
        val ready = CompletableDeferred<AiExchangeRates>()
        override fun today() = "2026-11-15"
        override suspend fun exchangeRates() = ready.await()
        override fun calculate(input: VpsInput): VpsResult = throw IllegalArgumentException("VPS_AMOUNT")
    }

    @Test fun `late cache load fills an empty currency rate but never overwrites a typed override`() = runBlocking<Unit> {
        withTimeout(10000) {
            val service = Service()
            val empty = withContext(Dispatchers.Main) { VpsCalculatorViewModel(service).also { it.currency("USD") } }
            val typed = withContext(Dispatchers.Main) {
                VpsCalculatorViewModel(service).also { vm ->
                    vm.currency("USD")
                    vm.edit { it.copy(toCny = "6.5") }
                }
            }
            service.ready.complete(AiExchangeRates(listOf(AiExchangeRate("USD", "7.2", "2026-11-15", true))))
            empty.state.first { it.input.toCny == "7.2" }
            typed.state.first { it.rates != null }
            withContext(Dispatchers.Main) {
                assertEquals("6.5", typed.state.value.input.toCny)
                assertEquals("7.2", empty.state.value.input.toCny)
            }
        }
    }
}
