package io.github.seektop.ai

import io.github.seektop.foundation.core.richtext.InlineNode
import io.github.seektop.foundation.core.richtext.RichNode
import io.github.seektop.model.PostContent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostAnalysisTest {
    private val post = AiPost(7, "Title", "trade", "交易", "Body", emptyList())

    @Test fun `board rules override common prompt and plugin defaults remain fallback`() {
        val pack = AiTemplatePack(1, "system", "plugin default", mapOf("trade" to "plugin trade"))
        assertEquals("plugin trade", AiSettings().promptFor(post, pack))
        assertEquals("custom default", AiSettings(defaultPrompt = "custom default").promptFor(post, pack))
        val settings = AiSettings(defaultPrompt = "custom default", boardPrompts = mapOf("trade" to "custom trade", "交易" to "title rule"))
        assertEquals("custom trade", settings.promptFor(post, pack))
        assertEquals("title rule", settings.promptFor(post.copy(boardKey = "unknown"), pack))
        assertEquals("plugin default", AiSettings().promptFor(post.copy(boardKey = "other"), pack))
    }

    @Test fun `opening post includes nested text and actual images but excludes avatar signature and stickers`() {
        val content = PostContent(
            null, "0", "author", 1, "https://secret/avatar", true, emptyList(), null, null, "交易",
            listOf(
                RichNode.Quote(listOf(RichNode.Paragraph(listOf(InlineNode.Text("quoted evidence"))))),
                RichNode.ListBlock(false, listOf(listOf(RichNode.BlockImage("https://public/image", "image")))),
                RichNode.Paragraph(listOf(InlineNode.Sticker("sticker", "https://secret/sticker"))),
            ),
            signatureNodes = listOf(RichNode.Paragraph(listOf(InlineNode.Text("private signature")))),
        )
        val extracted = analysisPost(1, "title", "trade", content)
        assertEquals(listOf("https://public/image"), extracted.images)
        assertTrue("quoted evidence" in extracted.text)
        assertTrue("private signature" !in extracted.text)
        assertTrue("secret" !in extracted.text)
        val long = analysisPost(1, "title", "trade", content.copy(nodes = listOf(RichNode.CodeBlock("x".repeat(60001), null))))
        assertEquals(60000, long.text.length)
        assertTrue(long.truncated)
    }

    @Test fun `AI markdown cannot load remote images in quotes lists or tables`() {
        val nodes = analysisMarkdown("![secret](https://leak.invalid/top)\n\n> ![nested](https://leak.invalid/nested)\n\n- ![list](https://leak.invalid/list)\n\n| Header |\n| --- |\n| ![cell](https://leak.invalid/table) |")
        // Serialized output traverses every nested node, not just the top-level list.
        val encoded = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(RichNode.serializer()), nodes)
        assertTrue("leak.invalid" !in encoded)
        assertTrue("nested" in encoded)
    }

    @Test fun `valuation rounds money locally and computes signed premium using actual calendar days`() {
        val value = TradeCalculator.calculate(TradeValuationInput("100.00", "65", "5", 100, "2027-01-04", "CNY"), LocalDate(2026, 11, 15))
        assertEquals(50, value.remainingDays)
        assertEquals(5000, value.remainingMinor)
        assertEquals(7000, value.totalMinor)
        assertEquals(2000, value.premiumMinor)
        assertEquals("40.00", value.premiumPercent)
        val discounted = TradeCalculator.calculate(TradeValuationInput("100", "40", "0", 100, "2027-01-04"), LocalDate(2026, 11, 15))
        assertEquals("-20.00", discounted.premiumPercent)
        assertEquals("-10.00", TradeCalculator.money(discounted.premiumMinor))
        val leap = TradeCalculator.calculate(TradeValuationInput("0.01", "0", "0", 2, "2028-03-01"), LocalDate(2028, 2, 29))
        assertEquals(1, leap.remainingDays)
        assertEquals(1, leap.remainingMinor)
    }

    @Test fun `expired value has no percentage and invalid amounts or inconsistent dates are rejected`() {
        val expired = TradeCalculator.calculate(TradeValuationInput("100", "20", "0", 30, "2026-11-15"), LocalDate(2026, 11, 15))
        assertEquals(0, expired.remainingMinor)
        assertNull(expired.premiumPercent)
        for (price in listOf("-1", "1.001", "1e2", "NaN", "", "1,000")) assertFailsWith<IllegalArgumentException> { TradeCalculator.minor(price) }
        assertFailsWith<IllegalArgumentException> { TradeCalculator.calculate(TradeValuationInput("100", "20", "0", 1, "2026-11-17"), LocalDate(2026, 11, 15)) }
        assertFailsWith<IllegalArgumentException> { TradeCalculator.calculate(TradeValuationInput("100", "20", "0", 0, "2026-11-16"), LocalDate(2026, 11, 15)) }
    }
}
