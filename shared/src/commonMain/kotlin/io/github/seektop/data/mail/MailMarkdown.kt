package io.github.seektop.data.mail

import io.github.seektop.foundation.core.richtext.InlineNode
import io.github.seektop.foundation.core.richtext.InlineStyle
import io.github.seektop.foundation.core.richtext.RichNode
import io.github.seektop.foundation.core.richtext.parseMarkdown

/** The shared preview parser has no HTML exporter; encode its tree with escaped text and safe URLs. */
fun mailMarkdownHtml(markdown: String): String = mailBlocksHtml(parseMarkdown(markdown))
private fun escaped(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
private fun safeUrl(value: String): String? = value.takeIf { it.startsWith("https://") || it.startsWith("http://") || it.startsWith("mailto:") }?.let(::escaped)
private fun styled(text: String, style: InlineStyle): String {
    var value = escaped(text)
    if (style.code) value = "<code>$value</code>"
    if (style.bold) value = "<strong>$value</strong>"
    if (style.italic) value = "<em>$value</em>"
    if (style.strikethrough) value = "<del>$value</del>"
    return value
}
private fun mailInlinesHtml(nodes: List<InlineNode>): String = nodes.joinToString("") { node ->
    when (node) {
        is InlineNode.Text -> styled(node.text, node.style)
        is InlineNode.Link -> safeUrl(node.url)?.let { "<a href=\"$it\">${styled(node.text, node.style)}</a>" } ?: styled(node.text, node.style)
        is InlineNode.Image -> safeUrl(node.url)?.let { "<img src=\"$it\" alt=\"${escaped(node.alt.orEmpty())}\">" }.orEmpty()
        is InlineNode.Sticker -> escaped(node.alt.orEmpty())
        is InlineNode.QuoteRef -> escaped(node.name + " " + node.floor)
        InlineNode.LineBreak -> "<br>"
    }
}
private fun mailBlocksHtml(nodes: List<RichNode>): String = nodes.joinToString("\n") { node ->
    when (node) {
        is RichNode.Paragraph -> "<p>${mailInlinesHtml(node.inlines)}</p>"

        is RichNode.Heading -> "<h${node.level}>${mailInlinesHtml(node.inlines)}</h${node.level}>"

        is RichNode.CodeBlock -> "<pre><code>${escaped(node.code)}</code></pre>"

        is RichNode.Quote -> "<blockquote>${mailBlocksHtml(node.children)}</blockquote>"

        is RichNode.ListBlock -> {
            val tag = if (node.ordered) "ol" else "ul"
            "<$tag>" + node.items.joinToString("") { "<li>${mailBlocksHtml(it)}</li>" } + "</$tag>"
        }

        is RichNode.BlockImage -> safeUrl(node.url)?.let { "<img src=\"$it\" alt=\"${escaped(node.alt.orEmpty())}\">" }.orEmpty()

        is RichNode.Table -> "<table>" + node.content.joinToString("") { row -> "<tr>" + row.joinToString("") { "<td>${mailInlinesHtml(it)}</td>" } + "</tr>" } + "</table>"

        is RichNode.Fold -> "<p>${escaped(node.title)}</p>${mailBlocksHtml(node.children)}"

        is RichNode.Tabs -> node.tabs.joinToString("\n") { "<p>${escaped(it.title)}</p>${mailBlocksHtml(it.children)}" }

        RichNode.Divider -> "<hr>"

        else -> ""
    }
}
