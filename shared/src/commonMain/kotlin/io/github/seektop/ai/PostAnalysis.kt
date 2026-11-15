package io.github.seektop.ai

import io.github.seektop.foundation.core.richtext.InlineNode
import io.github.seektop.foundation.core.richtext.RichNode
import io.github.seektop.model.PostContent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

const val POST_ANALYSIS_PLUGIN = "io.github.seektop.toolbox.ai-analysis"

@Serializable
data class AiProvider(val id: String, val name: String, val baseUrl: String, val model: String, val vision: Boolean = false, val keySet: Boolean = false, val thinking: Boolean = false)

@Serializable
data class AiSettings(
    val providers: List<AiProvider> = listOf(AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-flash", true)),
    val selectedProvider: String = "deepseek",
    val includeImages: Boolean = false,
    val defaultPrompt: String = "",
    val boardPrompts: Map<String, String> = emptyMap(),
)

@Serializable
data class AiTemplatePack(val api: Int, val systemPrompt: String, val defaultPrompt: String, val boardPrompts: Map<String, String>)

@Serializable
// ORIGINAL_POST is retained as a wire identifier; it includes the opening author’s later replies.
enum class AiAnalysisScope {
    ORIGINAL_POST,
    ALL_FLOORS,
}

@Serializable
data class AiPost(val id: Long, val title: String, val boardKey: String, val boardTitle: String, val text: String, val images: List<String>, val truncated: Boolean = false, val scope: AiAnalysisScope = AiAnalysisScope.ORIGINAL_POST, val sourcePages: Int = 1, val replyCount: Int = 0, val skippedReplies: Int = 0, @kotlinx.serialization.Transient val originSession: String? = null)

@Serializable
data class AiTurn(val role: String, val content: String)

@Serializable
data class AiAnalysisRequest(val post: AiPost, val prompt: String, val images: List<String>, val history: List<AiTurn> = emptyList(), val question: String = "", val language: String = "zh-CN", val supplements: List<String> = emptyList())

data class AiAnalysisUpdate(val text: String, val done: Boolean = false, val phase: String = "answering")

interface PostAnalysisService {
    fun today(): String
    suspend fun available(): Boolean
    suspend fun settings(): AiSettings

    /** Blank key preserves an existing credential only when its endpoint is unchanged. */
    suspend fun save(settings: AiSettings, newKeys: Map<String, String>)
    suspend fun test(providerId: String)
    suspend fun templates(): AiTemplatePack
    suspend fun collectPost(post: AiPost, scope: AiAnalysisScope, refresh: Boolean, onProgress: (Int, Int) -> Unit): AiPost {
        check(scope == AiAnalysisScope.ORIGINAL_POST) { "AI_ALL_FLOORS_UNAVAILABLE" }
        return post
    }
    fun analyze(request: AiAnalysisRequest): Flow<AiAnalysisUpdate>
}

fun AiSettings.promptFor(post: AiPost, templates: AiTemplatePack): String =
    boardPrompts[post.boardKey]?.takeIf(String::isNotBlank)
        ?: boardPrompts[post.boardTitle]?.takeIf(String::isNotBlank)
        ?: defaultPrompt.takeIf(String::isNotBlank)
        ?: templates.boardPrompts[post.boardKey]
        ?: templates.boardPrompts[post.boardTitle]
        ?: templates.defaultPrompt

/** Opening post only. Never include replies, signatures, avatar URLs or sticker images. */
fun analysisPost(id: Long, title: String, boardKey: String?, body: PostContent, maxChars: Int = 60000): AiPost {
    require(maxChars in 1..120000)
    val images = linkedSetOf<String>()
    fun inline(nodes: List<InlineNode>): String = nodes.joinToString("") {
        when (it) {
            is InlineNode.Text -> it.text

            is InlineNode.Link -> it.text

            is InlineNode.Image -> {
                images.add(it.url)
                it.alt.orEmpty()
            }

            is InlineNode.QuoteRef -> "${it.name} #${it.floor}"

            InlineNode.LineBreak -> "\n"

            is InlineNode.Sticker -> ""
        }
    }
    fun blocks(nodes: List<RichNode>): String = nodes.joinToString("\n") {
        when (it) {
            is RichNode.Paragraph -> inline(it.inlines)

            is RichNode.Heading -> inline(it.inlines)

            is RichNode.BlockImage -> {
                images.add(it.url)
                it.alt.orEmpty()
            }

            is RichNode.CodeBlock -> it.code

            is RichNode.Quote -> blocks(it.children)

            is RichNode.Fold -> it.title + "\n" + blocks(it.children)

            is RichNode.Tabs -> it.tabs.joinToString("\n") { tab -> tab.title + "\n" + blocks(tab.children) }

            is RichNode.ListBlock -> it.items.joinToString("\n") { item -> blocks(item) }

            is RichNode.Table -> it.content.joinToString("\n") { row -> row.joinToString(" | ") { cell -> inline(cell) } }

            else -> ""
        }
    }
    val text = blocks(body.nodes)
    return AiPost(id, title.take(512), boardKey.orEmpty().ifBlank { body.categoryTitle.orEmpty() }, body.categoryTitle.orEmpty(), text.take(maxChars), images.toList(), text.length > maxChars)
}

/** Built-in defaults keep one-click analysis independent of optional plugin installation. */
val DefaultAiTemplates = AiTemplatePack(
    api = 1,
    systemPrompt = "Respond with readable Markdown only. Distinguish stated facts, calculations, assumptions and missing information. Never invent prices or claim to browse. For valuation, show the formula and units, identify the original/renewal price basis, billing period and date convention. Convert currencies using client-provided reference rates or explicit user-supplied rates before combining amounts; never invent a rate. Calculate remaining value in the original currency even if conversion is unavailable. Ask for missing inputs rather than guessing. Keep answers concise: lead with the conclusion, avoid repeating source text or prior answers, and show only essential evidence. Follow-up answers should focus on what changed. Do not output machine JSON or refer to a client calculator.",
    defaultPrompt = "仅依据提供的标题、正文、回复及图片，用简短 Markdown 分析。先用一句话给结论，再列最多 3 条关键信息或风险；确有缺项时只问最关键的 1–2 项。通常控制在 150 字以内，不复述原文，不写背景科普、客套话或重复总结。楼主后续说明与主帖冲突时指出变更；其他用户的说法须标为未核实。追问只回答新增问题和修正后的结论。",
    boardPrompts = mapOf("trade" to "简短分析交易帖，通常 200 字以内，用 Markdown 最多 4 条：结论、估值、风险、待补充（无则省略）。优先采用楼主最新明确说明及用户补充，冲突处简要指出。提取售价、原价或续费价、计费周期、到期日及过户费；信息齐全时只列一行公式和结果：剩余价值 = 周期价格 × 剩余天数 ÷ 周期天数；溢价 = 售价 + 买方另付过户费 − 剩余价值，并给出溢价率（分母为剩余价值，大于 0 才计算）。注明计价基准、日期、币种，不混用币种，不重复计费。期限或基准不明时不猜算，只问缺失的 1–2 项；不编造行情。只指出影响交易的关键风险。追问只更新受补充信息影响的计算与结论，不重复整份报告。"),
)
