package io.github.seektop.ui.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seektop.ai.AiExchangeRates
import io.github.seektop.vps.VpsCalculatorService
import io.github.seektop.vps.VpsInput
import io.github.seektop.vps.VpsResult
import io.github.seektop.vps.vpsRateUnits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

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
                        toCny = input.toCny.ifBlank { roundedRate(input.currency, rates.rates.find { it.code == input.currency }?.toCny.orEmpty()) },
                        pushToCny = input.pushToCny.ifBlank { roundedRate(input.pushCurrency, rates.rates.find { it.code == input.pushCurrency }?.toCny.orEmpty()) },
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
        val rate = if (code == "CNY") "1" else roundedRate(code, state.value.rates?.rates?.find { it.code == code }?.toCny.orEmpty())
        edit { if (push) it.copy(pushCurrency = code, pushToCny = rate) else it.copy(currency = code, toCny = rate) }
    }
    private fun roundedRate(code: String, value: String): String = service.rateFromDisplay(service.displayRate(value, vpsRateUnits(code)), vpsRateUnits(code))
}

internal fun vpsCalendarMillis(value: String): Long? = try {
    LocalDateTime.parse(value.take(10) + "T00:00").also { require(it.year in 2000..2200) }.toInstant(TimeZone.UTC).toEpochMilliseconds()
} catch (_: IllegalArgumentException) {
    null
}

internal fun vpsCalendarDate(millis: Long, previous: String, precise: Boolean): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date.toString()
    val time = previous.trim().replace('T', ' ').substringAfter(' ', "00:00").take(5)
    return if (precise) "$date ${time.takeIf { Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(it) } ?: "00:00"}" else date
}

internal fun vpsModelMarkdown(value: String): String = value.trim().take(120).map { if (it in "\\`*_[]<>") "\\$it" else it.toString() }.joinToString("")

internal fun vpsMarkdown(title: String, rows: List<Pair<String, String>>, notes: List<String>, columns: Pair<String, String>): String {
    fun cell(value: String) = value.replace("|", "\\|").replace('\n', ' ').replace('\r', ' ')
    return buildString {
        append("### 🧮 ").append(title).append("\n\n")
        append("| ").append(cell(columns.first)).append(" | ").append(cell(columns.second)).append(" |\n| :--- | ---: |\n")
        rows.forEach { (label, value) -> append("| ").append(cell(label)).append(" | ").append(cell(value)).append(" |\n") }
        if (notes.isNotEmpty()) append("\n> ").append(notes.joinToString(" · ") { cell(it) }).append('\n')
    }
}
