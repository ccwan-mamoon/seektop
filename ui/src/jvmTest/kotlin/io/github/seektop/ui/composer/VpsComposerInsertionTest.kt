package io.github.seektop.ui.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import io.github.seektop.ai.AiExchangeRates
import io.github.seektop.foundation.core.richtext.RichNode
import io.github.seektop.foundation.core.richtext.parseMarkdown
import io.github.seektop.vps.VpsCalculatorService
import io.github.seektop.vps.VpsInput
import io.github.seektop.vps.VpsResult
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpsComposerInsertionTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `inserting calculation replaces selection and preserves surrounding draft as parseable markdown`() = runDesktopComposeUiTest(width = 1280, height = 1000) {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        val body = TextFieldState("prefix SELECT suffix", TextRange(7, 13))
        val service = object : VpsCalculatorService {
            override fun today() = "2026-11-15"
            override suspend fun exchangeRates() = AiExchangeRates(emptyList())
            override fun calculate(input: VpsInput): VpsResult {
                require(input.renewal.isNotBlank() && input.expiryDate.isNotBlank())
                if (input.transaction) require(input.paymentCny.isNotBlank())
                return VpsResult("182", "49.86", "49.86", "0.27", buyerTotal = "80.00", sellerPremium = "30.14", buyerPremium = "30.14", premiumPercent = "60.45", buyerDaily = "0.44")
            }
        }
        var open by mutableStateOf(false)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalVpsCalculator provides service) {
                    VpsCalculatorEntry { open = true }
                    if (open) VpsCalculatorDialog(bodyState = body, onDismiss = { open = false })
                }
            }
        }
        onNodeWithText("剩余价值计算器").performClick()
        onNodeWithText("续费金额").performTextReplacement("100")
        onNodeWithText("到期日期").performTextReplacement("2027-05-16")
        onNodeWithText("交易溢价").performClick()
        onNodeWithText("实付卖家 (CNY)").performTextReplacement("80")
        onNodeWithText("插入正文 · Markdown").performClick()
        waitForIdle()
        val text = body.text.toString()
        assertTrue(text.startsWith("prefix "))
        assertTrue(text.endsWith(" suffix"))
        assertFalse("SELECT" in text)
        assertTrue(parseMarkdown(text).any { it is RichNode.Table })
        assertFalse(open)
        assertEquals(text.length - " suffix".length, body.selection.end)
        assertTrue(body.selection.collapsed)
    }
}
