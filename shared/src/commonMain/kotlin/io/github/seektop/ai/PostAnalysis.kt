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
