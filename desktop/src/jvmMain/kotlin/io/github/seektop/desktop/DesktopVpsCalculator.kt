package io.github.seektop.desktop

import io.github.seektop.vps.VPS_CURRENCIES
import io.github.seektop.vps.VpsCalculatorService
import io.github.seektop.vps.VpsFeePayer
import io.github.seektop.vps.VpsInput
import io.github.seektop.vps.VpsResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

internal class DesktopVpsCalculator(private val rates: DesktopExchangeRates) : VpsCalculatorService {
    override fun today() = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
    override suspend fun exchangeRates() = rates.configuration()
    override fun calculate(input: VpsInput) = calculateVps(input)
}

/** Decimal calculations stay in the Windows shell: Kotlin common has no BigDecimal API. */
internal fun calculateVps(input: VpsInput): VpsResult {
    fun amount(text: String, code: String, positive: Boolean = false): BigDecimal {
        require(Regex("[0-9]{1,9}(?:\\.[0-9]{1,2})?").matches(text.trim())) { code }
        return text.trim().toBigDecimal().also { require(if (positive) it > BigDecimal.ZERO else it >= BigDecimal.ZERO) { code } }
    }
    fun rate(currency: String, text: String): BigDecimal {
        require(currency in VPS_CURRENCIES) { "VPS_RATE" }
        if (currency == "CNY") return BigDecimal.ONE
        require(text.length in 1..64) { "VPS_RATE" }
        val value = text.toBigDecimalOrNull()
        require(value != null && value in BigDecimal("0.000000001")..BigDecimal("1000000000")) { "VPS_RATE" }
        return value
    }
    fun date(text: String): LocalDateTime = try {
        val clean = text.trim().replace(' ', 'T')
        (if (clean.length == 10) LocalDate.parse(clean).atStartOfDay() else LocalDateTime.parse(clean)).also { require(it.year in 2000..2200 && it.second == 0 && it.nano == 0) }
    } catch (_: Exception) {
        throw IllegalArgumentException("VPS_DATE")
    }
    fun BigDecimal.money() = setScale(2, RoundingMode.HALF_UP).toPlainString()
    fun divide(a: BigDecimal, b: BigDecimal) = a.divide(b, 16, RoundingMode.HALF_UP)
    fun split(total: BigDecimal, payer: VpsFeePayer): Pair<BigDecimal, BigDecimal> {
        val rounded = total.setScale(2, RoundingMode.HALF_UP)
        val buyer = when (payer) {
            VpsFeePayer.BUYER -> rounded
            VpsFeePayer.SELLER -> BigDecimal.ZERO
            VpsFeePayer.SPLIT -> rounded.divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
        }
        return buyer to rounded.subtract(buyer)
    }
    val price = amount(input.renewal, "VPS_AMOUNT", true)
    val period = input.periodDays.toIntOrNull()
    require(period != null && period in 1..3660) { "VPS_PERIOD" }
    val seconds = Duration.between(date(input.tradeDate), date(input.expiryDate)).seconds.coerceAtLeast(0)
    require(seconds <= 36600L * 86400) { "VPS_DATE" }
    val days = divide(seconds.toBigDecimal(), BigDecimal(86400))
    val renewalCny = price.multiply(rate(input.currency, input.toCny))
    val original = divide(price.multiply(days), period.toBigDecimal())
    val remaining = divide(renewalCny.multiply(days), period.toBigDecimal()).setScale(2, RoundingMode.HALF_UP)
    val basic = VpsResult(days.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(), original.money(), remaining.money(), divide(renewalCny, period.toBigDecimal()).money())
    if (!input.transaction) return basic
    val payment = amount(input.paymentCny, "VPS_PAYMENT")
    val push = amount(input.pushFee, "VPS_FEES")
    val (buyerPush, sellerPush) = split(if (push.signum() == 0) BigDecimal.ZERO else push.multiply(rate(input.pushCurrency, input.pushToCny)), input.pushPayer)
    val percent = if (input.middleman) amount(input.middlemanPercent, "VPS_FEES") else BigDecimal.ZERO
    require(percent <= BigDecimal(100)) { "VPS_FEES" }
    val (buyerMiddleman, sellerMiddleman) = split(payment.multiply(percent).movePointLeft(2), input.middlemanPayer)
    val total = payment + buyerPush + buyerMiddleman
    val net = payment - sellerPush - sellerMiddleman
    val premium = total - remaining
    return basic.copy(
        buyerTotal = total.money(), sellerNet = net.money(), sellerPremium = (net - remaining).money(), buyerPremium = premium.money(),
        premiumPercent = if (remaining.signum() == 0) null else divide(premium * BigDecimal(100), remaining).money(),
        buyerDaily = if (days.signum() == 0) null else divide(total, days).money(),
        buyerPush = buyerPush.money(), sellerPush = sellerPush.money(), buyerMiddleman = buyerMiddleman.money(), sellerMiddleman = sellerMiddleman.money(),
    )
}
