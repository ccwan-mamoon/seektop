package io.github.seektop.ai

import io.github.seektop.foundation.core.richtext.InlineNode
import io.github.seektop.foundation.core.richtext.RichNode
import io.github.seektop.model.PostContent
import io.github.seektop.model.PostDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadAnalysisCollectorTest {
    private val seed = AiPost(7, "title", "trade", "Trading", "cached main post", emptyList())
    private fun content(id: Long, text: String, blocked: Boolean = false) = PostContent(id, "#$id", "author$id", id, null, id == 0L, emptyList(), null, null, "Trading", listOf(RichNode.Paragraph(listOf(InlineNode.Text(text)))), isBlocked = blocked, signatureNodes = listOf(RichNode.Paragraph(listOf(InlineNode.Text("private-signature")))))
    private fun page(number: Int, total: Int = 3, comments: List<PostContent> = listOf(content(number.toLong(), "reply-$number"))) = PostDetail(7, "fresh title", if (number == 1) content(0, "fresh main post") else null, comments, number, total, number < total)

    @Test fun `all floor snapshot reads every page without leaking signatures or blocked content`() = runTest {
        val calls = mutableListOf<Int>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = collectThreadAnalysis(seed, AiAnalysisScope.ALL_FLOORS, true, { _, n ->
            calls += n
            page(n, comments = listOf(content(n.toLong(), "reply-$n"), content((n + 10).toLong(), "blocked-$n", true)))
        }, { "session-a" }, { a, b -> progress += a to b }, {})
        assertEquals(listOf(1, 2, 3), calls)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(3, result.replyCount)
        assertEquals(3, result.skippedReplies)
        assertEquals(AiAnalysisScope.ALL_FLOORS, result.scope)
        for (n in 1..3) assertTrue("reply-$n" in result.text)
        assertFalse("blocked-" in result.text)
        assertFalse("private-signature" in result.text)
        assertFalse("session-a" in Json.encodeToString(result))
    }

    @Test fun `session change or pagination drift cannot return a falsely complete snapshot`() = runTest {
        var session = "a"
        val failure = assertFailsWith<IllegalStateException> { collectThreadAnalysis(seed, AiAnalysisScope.ALL_FLOORS, true, { _, n -> page(n).also { if (n == 2) session = "b" } }, { session }, { _, _ -> }, {}) }
        assertEquals("AI_SESSION_CHANGED", failure.message)
        val changed = assertFailsWith<IllegalStateException> { collectThreadAnalysis(seed, AiAnalysisScope.ALL_FLOORS, true, { _, n -> page(n, if (n == 1) 3 else 4) }, { "a" }, { _, _ -> }, {}) }
        assertEquals("AI_THREAD_CHANGED", changed.message)
        val duplicate = assertFailsWith<IllegalStateException> { collectThreadAnalysis(seed, AiAnalysisScope.ALL_FLOORS, true, { _, n -> page(n, comments = listOf(content(9, "same reply"))) }, { "a" }, { _, _ -> }, {}) }
        assertEquals("AI_THREAD_CHANGED", duplicate.message)
    }

    @Test fun `large threads are rejected without quietly sending only a prefix`() = runTest {
        val calls = mutableListOf<Int>()
        val failure = assertFailsWith<IllegalStateException> {
            collectThreadAnalysis(seed, AiAnalysisScope.ALL_FLOORS, true, { _, n ->
                calls += n
                page(n, 101)
            }, { "a" }, { _, _ -> }, {})
        }
        assertEquals("AI_THREAD_TOO_LARGE", failure.message)
        assertEquals(listOf(1), calls)
        val long = assertFailsWith<IllegalStateException> { collectThreadAnalysis(seed, AiAnalysisScope.ALL_FLOORS, true, { _, n -> page(n, comments = listOf(content(n.toLong(), "x".repeat(60001)))) }, { "a" }, { _, _ -> }, {}) }
        assertEquals("AI_THREAD_TOO_LARGE", long.message)
    }

    @Test fun `opening post auto analysis avoids a new forum fetch and explicit refresh reads page one`() = runTest {
        var calls = 0
        val load: suspend (Long, Int) -> PostDetail = { _, _ ->
            calls++
            page(1)
        }
        assertEquals("cached main post", collectThreadAnalysis(seed, AiAnalysisScope.ORIGINAL_POST, false, load, { "a" }, { _, _ -> }, {}).text)
        assertEquals(0, calls)
        assertEquals("fresh main post", collectThreadAnalysis(seed, AiAnalysisScope.ORIGINAL_POST, true, load, { "a" }, { _, _ -> }, {}).text)
        assertEquals(1, calls)
    }

    @Test fun `cancellation is propagated rather than disguised as an incomplete thread`() = runTest {
        assertFailsWith<CancellationException> {
            collectThreadAnalysis(seed, AiAnalysisScope.ALL_FLOORS, true, { _, n ->
                if (n == 2) throw CancellationException()
                page(n)
            }, { "a" }, { _, _ -> }, {})
        }
    }
}
