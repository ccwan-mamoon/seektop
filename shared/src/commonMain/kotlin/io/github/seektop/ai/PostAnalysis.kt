package io.github.seektop.ai

import io.github.seektop.foundation.core.richtext.InlineNode
import io.github.seektop.foundation.core.richtext.RichNode
import io.github.seektop.model.PostContent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

const val POST_ANALYSIS_PLUGIN = "io.github.seektop.toolbox.ai-analysis"

@Serializable
data class AiProvider(val id: String, val name: String, val baseUrl: String, val model: String, val vision: Boolean = false, val keySet: Boolean = false)

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
data class AiPost(val id: Long, val title: String, val boardKey: String, val boardTitle: String, val text: String, val images: List<String>, val truncated: Boolean = false)

@Serializable
data class AiTurn(val role: String, val content: String)

@Serializable
data class AiAnalysisRequest(val post: AiPost, val prompt: String, val images: List<String>, val history: List<AiTurn> = emptyList(), val question: String = "", val language: String = "zh-CN")

data class AiAnalysisUpdate(val text: String, val done: Boolean = false)

interface PostAnalysisService {
    fun today(): String
    suspend fun available(): Boolean
    suspend fun settings(): AiSettings

    /** Blank key preserves an existing credential only when its endpoint is unchanged. */
    suspend fun save(settings: AiSettings, newKeys: Map<String, String>)
    suspend fun test(providerId: String)
    suspend fun templates(): AiTemplatePack
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
fun analysisPost(id: Long, title: String, boardKey: String?, body: PostContent): AiPost {
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
    return AiPost(id, title.take(512), boardKey.orEmpty().ifBlank { body.categoryTitle.orEmpty() }, body.categoryTitle.orEmpty(), text.take(60_000), images.toList(), text.length > 60_000)
}

/** Built-in defaults keep one-click analysis independent of optional plugin installation. */
val DefaultAiTemplates = AiTemplatePack(
    api = 1,
    systemPrompt = "Respond with readable Markdown only. Distinguish stated facts, calculations, assumptions and missing information. Never invent prices or claim to browse. For valuation, show the formula and units, identify the original/renewal price basis, billing period and date convention. Do not mix currencies. Ask for missing inputs rather than guessing. Do not output machine JSON or refer to a client calculator.",
    defaultPrompt = "概括帖子主题、关键信息、风险和待核实问题。仅根据标题、正文和提供的图片分析。",
    boardPrompts = mapOf("trade" to "分析交易帖的产品、配置、价格、续费、到期日和过户费用。若信息充分，列出剩余价值与溢价的计算过程；明确计价基准、剩余天数、周期和币种。缺失数据明确指出，不编造市场价。用 Markdown 给出结论和风险。"),
)
