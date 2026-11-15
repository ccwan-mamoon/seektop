package io.github.seektop.ui.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seektop.ai.AiExchangeRates
import io.github.seektop.vps.VpsCalculatorService
import io.github.seektop.vps.VpsInput
import io.github.seektop.vps.VpsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VpsCalculatorState(val input: VpsInput, val result: VpsResult? = null, val rates: AiExchangeRates? = null, val error: String? = null)

class VpsCalculatorViewModel(private val service: VpsCalculatorService) : ViewModel() {
    private val mutable = MutableStateFlow(VpsCalculatorState(VpsInput(tradeDate = service.today())))
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val rates = service.exchangeRates()
                mutable.update { it.copy(rates = rates) }
                edit { input ->
                    input.copy(
                        toCny = input.toCny.ifBlank { rates.rates.find { it.code == input.currency }?.toCny.orEmpty() },
                        pushToCny = input.pushToCny.ifBlank { rates.rates.find { it.code == input.pushCurrency }?.toCny.orEmpty() },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { /* Manual exchange rate entry remains available offline. */ }
        }
    }
    fun edit(change: (VpsInput) -> VpsInput) {
        val input = change(state.value.input)
        val result = try {
            service.calculate(input)
        } catch (e: IllegalArgumentException) {
            mutable.update { it.copy(input = input, result = null, error = if (input.renewal.isBlank() || input.expiryDate.isBlank()) null else e.message) }
            return
        }
        mutable.update { it.copy(input = input, result = result, error = null) }
    }
    fun currency(code: String, push: Boolean = false) {
        val rate = if (code == "CNY") "1" else state.value.rates?.rates?.find { it.code == code }?.toCny.orEmpty()
        edit { if (push) it.copy(pushCurrency = code, pushToCny = rate) else it.copy(currency = code, toCny = rate) }
    }
}

internal fun vpsMarkdown(title: String, rows: List<Pair<String, String>>, notes: List<String>, columns: Pair<String, String>): String {
    fun cell(value: String) = value.replace("|", "\\|").replace('\n', ' ').replace('\r', ' ')
    return buildString {
        append("### 🧮 ").append(title).append("\n\n")
        append("| ").append(cell(columns.first)).append(" | ").append(cell(columns.second)).append(" |\n| :--- | ---: |\n")
        rows.forEach { (label, value) -> append("| ").append(cell(label)).append(" | ").append(cell(value)).append(" |\n") }
        if (notes.isNotEmpty()) append("\n> ").append(notes.joinToString(" · ") { cell(it) }).append('\n')
    }
}
