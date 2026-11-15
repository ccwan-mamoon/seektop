package io.github.seektop.ai

import io.github.seektop.foundation.core.richtext.InlineNode
import io.github.seektop.foundation.core.richtext.RichNode
import io.github.seektop.foundation.core.richtext.parseMarkdown

/** AI results are text, never a source of automatic image or avatar requests. */
fun analysisMarkdown(markdown: String): List<RichNode> {
    fun inline(nodes: List<InlineNode>): List<InlineNode> = nodes.map {
        when (it) {
            is InlineNode.Image -> InlineNode.Text(it.alt.orEmpty())
            is InlineNode.Sticker -> InlineNode.Text("")
            else -> it
        }
    }
    fun clean(nodes: List<RichNode>): List<RichNode> = nodes.mapNotNull {
        when (it) {
            is RichNode.Paragraph -> it.copy(inlines = inline(it.inlines))
            is RichNode.Heading -> it.copy(inlines = inline(it.inlines))
            is RichNode.BlockImage -> RichNode.Paragraph(listOf(InlineNode.Text(it.alt.orEmpty())))
            is RichNode.Quote -> it.copy(children = clean(it.children))
            is RichNode.Fold -> it.copy(children = clean(it.children))
            is RichNode.Tabs -> it.copy(tabs = it.tabs.map { tab -> tab.copy(children = clean(tab.children)) })
            is RichNode.ListBlock -> it.copy(items = it.items.map { item -> clean(item) })
            is RichNode.Table -> RichNode.Table(cells = it.content.map { row -> row.map { cell -> inline(cell) } })
            is RichNode.CodeBlock, RichNode.Divider -> it
            else -> null
        }
    }
    return clean(parseMarkdown(markdown))
}
