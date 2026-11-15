package io.github.seektop.ai

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

@Serializable
data class TradeValuationInput(val basisPrice: String, val askingPrice: String, val fee: String = "0", val periodDays: Int, val expiryDate: String, val currency: String = "")

data class TradeValuation(val remainingDays: Int, val remainingMinor: Long, val totalMinor: Long, val premiumMinor: Long, val premiumPercent: String?)

/** Fixed-point cents. No floating-point money, currency conversion or inferred billing period. */
object TradeCalculator {
    fun minor(value: String): Long {
        require(Regex("(?:0|[1-9][0-9]{0,8})(?:\\.[0-9]{1,2})?").matches(value)) { "Invalid price (up to two decimals)" }
        val parts = value.split('.')
        return parts[0].toLong() * 100 + parts.getOrElse(1) { "0" }.padEnd(2, '0').toLong()
    }
    fun money(minor: Long): String {
        val n = if (minor < 0) -minor else minor
        return (if (minor < 0) "-" else "") + "${n / 100}.${(n % 100).toString().padStart(2, '0')}"
    }
    fun calculate(input: TradeValuationInput, today: LocalDate): TradeValuation {
        require(input.periodDays in 1..3660) { "Invalid billing period" }
        val days = today.daysUntil(LocalDate.parse(input.expiryDate)).coerceAtLeast(0)
        require(days <= input.periodDays) { "Expiry exceeds billing period; verify the dates" }
        val remaining = (minor(input.basisPrice) * days + input.periodDays / 2) / input.periodDays
        val total = minor(input.askingPrice) + minor(input.fee)
        val premium = total - remaining
        val percent = if (remaining == 0L) {
            null
        } else {
            val sign = if (premium < 0) -1 else 1
            val n = (if (premium < 0) -premium else premium) * 10_000
            money(sign * ((n + remaining / 2) / remaining))
        }
        return TradeValuation(days, remaining, total, premium, percent)
    }
}
