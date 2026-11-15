package io.github.seektop.desktop

import io.github.seektop.vps.VpsFeePayer
import io.github.seektop.vps.VpsInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopVpsCalculatorTest {
    private val input = VpsInput(renewal = "100", periodDays = "100", tradeDate = "2026-01-01", expiryDate = "2026-02-20", transaction = true, paymentCny = "80", pushFee = "10", middleman = true)

    @Test fun `buyer fees increase total while seller fees reduce net without double counting`() {
        val buyer = calculateVps(input)
        assertEquals("50.00", buyer.remainingCny)
        assertEquals("94.00", buyer.buyerTotal)
        assertEquals("30.00", buyer.sellerPremium)
        assertEquals("44.00", buyer.buyerPremium)
        assertEquals("88.00", buyer.premiumPercent)
        val seller = calculateVps(input.copy(pushPayer = VpsFeePayer.SELLER, middlemanPayer = VpsFeePayer.SELLER))
        assertEquals("80.00", seller.buyerTotal)
        assertEquals("66.00", seller.sellerNet)
        assertEquals("16.00", seller.sellerPremium)
        assertEquals("30.00", seller.buyerPremium)
        val split = calculateVps(input.copy(pushPayer = VpsFeePayer.SPLIT, middlemanPayer = VpsFeePayer.SPLIT))
        assertEquals("87.00", split.buyerTotal)
        assertEquals("73.00", split.sellerNet)
        assertEquals("23.00", split.sellerPremium)
        assertEquals("37.00", split.buyerPremium)
    }

    @Test fun `foreign renewal and fee use independent rates and manual override`() {
        val result = calculateVps(input.copy(renewal = "10", currency = "USD", toCny = "7.2", pushFee = "2", pushCurrency = "EUR", pushToCny = "8", middleman = false))
        assertEquals("5.00", result.remainingOriginal)
        assertEquals("36.00", result.remainingCny)
        assertEquals("96.00", result.buyerTotal)
        assertEquals("60.00", result.buyerPremium)
        assertEquals("166.67", result.premiumPercent)
    }

    @Test fun `fractional days leap day expiry and discounts use consistent boundaries`() {
        val precise = calculateVps(input.copy(renewal = "100", periodDays = "1", tradeDate = "2028-02-28 12:00", expiryDate = "2028-02-29 00:00", transaction = false))
        assertEquals("0.5", precise.remainingDays)
        assertEquals("50.00", precise.remainingCny)
        val expired = calculateVps(input.copy(expiryDate = "2025-12-31"))
        assertEquals("0.00", expired.remainingCny)
        assertEquals(null, expired.premiumPercent)
        assertEquals(null, expired.buyerDaily)
        val discount = calculateVps(input.copy(paymentCny = "30", pushFee = "0", middleman = false))
        assertEquals("-20.00", discount.buyerPremium)
        assertEquals("-40.00", discount.premiumPercent)
    }

    @Test fun `half cents round once and split fees conserve the charged amount`() {
        val split = calculateVps(input.copy(pushFee = "0.01", pushPayer = VpsFeePayer.SPLIT, middleman = false))
        assertEquals("0.01", split.buyerPush)
        assertEquals("0.00", split.sellerPush)
        val halfCent = calculateVps(input.copy(renewal = "0.03", periodDays = "2", tradeDate = "2026-01-01", expiryDate = "2026-01-02", transaction = false))
        assertEquals("0.02", halfCent.remainingCny)
    }

    @Test fun `invalid amounts dates periods and missing FX never silently become zero or one`() {
        for (bad in listOf(input.copy(renewal = "NaN"), input.copy(toCny = "", currency = "USD"), input.copy(periodDays = "0"), input.copy(expiryDate = "2026-02-30"), input.copy(pushFee = "1", pushCurrency = "USD", pushToCny = ""), input.copy(paymentCny = "-1"))) {
            assertFailsWith<IllegalArgumentException> { calculateVps(bad) }
        }
        // A missing fee-currency rate is irrelevant when the fee is zero.
        assertEquals("84.00", calculateVps(input.copy(pushFee = "0", pushCurrency = "USD", pushToCny = "")).buyerTotal)
    }
}
