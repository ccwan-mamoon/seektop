package io.github.seektop.data.mail

/** Fixed public OpenAPI operations; no caller-supplied host or arbitrary API routes. */
object SeekLiEndpoints {
    private val operations = listOf(
        "GET" to Regex("^/mailboxes$"),
        "POST" to Regex("^/mailboxes$"),
        "GET" to Regex("^/mailboxes/[0-9]+$"),
        "DELETE" to Regex("^/mailboxes/[0-9]+$"),
        "GET" to Regex("^/mailbox-release-holds$"),
        "POST" to Regex("^/mailbox-release-holds/[0-9]+/restore$"),
        "POST" to Regex("^/mailboxes/check$"),
        "PUT" to Regex("^/mailboxes/[0-9]+/settings$"),
        "GET" to Regex("^/mailboxes/[0-9]+/mails$"),
        "GET" to Regex("^/mails$"),
        "GET" to Regex("^/mails/unread$"),
        "GET" to Regex("^/mails/[0-9]+$"),
        "DELETE" to Regex("^/mails/[0-9]+$"),
        "GET" to Regex("^/mails/[0-9]+/raw$"),
        "GET" to Regex("^/mails/counts$"),
        "PUT" to Regex("^/mails/[0-9]+/read$"),
        "PUT" to Regex("^/mails/[0-9]+/flag$"),
        "PUT" to Regex("^/mails/[0-9]+/folder$"),
        "POST" to Regex("^/mails/[0-9]+/tags$"),
        "DELETE" to Regex("^/mails/[0-9]+/tags/[0-9]+$"),
        "POST" to Regex("^/send$"),
        "GET" to Regex("^/mails/[0-9]+/attachments$"),
        "GET" to Regex("^/mails/[0-9]+/attachments/[0-9]+$"),
        "GET" to Regex("^/folders$"),
        "POST" to Regex("^/folders$"),
        "PUT" to Regex("^/folders/[0-9]+$"),
        "DELETE" to Regex("^/folders/[0-9]+$"),
        "GET" to Regex("^/tags$"),
        "POST" to Regex("^/tags$"),
        "PUT" to Regex("^/tags/[0-9]+$"),
        "DELETE" to Regex("^/tags/[0-9]+$"),
        "GET" to Regex("^/contacts$"),
        "POST" to Regex("^/contacts$"),
        "PUT" to Regex("^/contacts/[0-9]+$"),
        "DELETE" to Regex("^/contacts/[0-9]+$"),
        "GET" to Regex("^/filters$"),
        "POST" to Regex("^/filters$"),
        "PUT" to Regex("^/filters/[0-9]+$"),
        "DELETE" to Regex("^/filters/[0-9]+$"),
        "GET" to Regex("^/signatures$"),
        "POST" to Regex("^/signatures$"),
        "PUT" to Regex("^/signatures/[0-9]+$"),
        "DELETE" to Regex("^/signatures/[0-9]+$"),
        "GET" to Regex("^/storage$"),
        "GET" to Regex("^/quotas$"),
    )
    fun permits(method: String, path: String): Boolean = path.startsWith("/") && !path.startsWith("//") && operations.any { (verb, route) -> verb == method && route.matches(path.substringBefore('?')) }
}

fun mailQuery(value: String): String = value.encodeToByteArray().joinToString("") { byte ->
    val n = byte.toInt() and 255
    if (n in 65..90 || n in 97..122 || n in 48..57 || n in listOf(45, 46, 95, 126)) n.toChar().toString() else "%" + n.toString(16).uppercase().padStart(2, '0')
}
