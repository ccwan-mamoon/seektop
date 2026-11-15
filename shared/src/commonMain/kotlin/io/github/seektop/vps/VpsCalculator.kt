package io.github.seektop.vps

import io.github.seektop.ai.AiExchangeRates

val VPS_CURRENCIES = listOf("USD", "AUD", "CAD", "CHF", "CNY", "EUR", "GBP", "HKD", "INR", "JPY", "KRW", "NZD", "RUB", "SGD", "TWD")
enum class VpsFeePayer { BUYER, SELLER, SPLIT }

data class VpsInput(
    val renewal: String = "",
    val currency: String = "CNY",
    val toCny: String = "1",
    val periodDays: String = "365",
    val tradeDate: String = "",
    val expiryDate: String = "",
    val transaction: Boolean = false,
    val paymentCny: String = "",
    val pushFee: String = "0",
    val pushCurrency: String = "CNY",
    val pushToCny: String = "1",
    val pushPayer: VpsFeePayer = VpsFeePayer.BUYER,
    val middleman: Boolean = false,
    val middlemanPercent: String = "5",
    val middlemanPayer: VpsFeePayer = VpsFeePayer.BUYER,
)

data class VpsResult(
    val remainingDays: String,
    val remainingOriginal: String,
    val remainingCny: String,
    val officialDaily: String,
    val buyerTotal: String? = null,
    val sellerNet: String? = null,
    val sellerPremium: String? = null,
    val buyerPremium: String? = null,
    val premiumPercent: String? = null,
    val buyerDaily: String? = null,
    val buyerPush: String = "0.00",
    val sellerPush: String = "0.00",
    val buyerMiddleman: String = "0.00",
    val sellerMiddleman: String = "0.00",
)

interface VpsCalculatorService {
    fun today(): String
    suspend fun exchangeRates(): AiExchangeRates
    fun calculate(input: VpsInput): VpsResult
}
