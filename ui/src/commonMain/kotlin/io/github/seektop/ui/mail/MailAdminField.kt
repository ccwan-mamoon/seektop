package io.github.seektop.ui.mail

data class MailAdminField(val name: String, val type: String, val required: Boolean = false)
fun mailAdminFields(resource: String): List<MailAdminField> = when (resource) {
    "mailboxes" -> listOf(MailAdminField("address", "string", true))
    "contacts" -> listOf(MailAdminField("email", "string", true), MailAdminField("name", "string", false))
    "folders" -> listOf(MailAdminField("name", "string", true), MailAdminField("color", "string", false), MailAdminField("icon", "string", false))
    "tags" -> listOf(MailAdminField("name", "string", true), MailAdminField("color", "string", false))
    "filters" -> listOf(MailAdminField("name", "string", true), MailAdminField("conditions", "object", true), MailAdminField("actions", "object", true), MailAdminField("priority", "integer", false), MailAdminField("isActive", "boolean", false))
    "signatures" -> listOf(MailAdminField("name", "string", true), MailAdminField("contentText", "string", true), MailAdminField("contentHtml", "string", false), MailAdminField("mailboxId", "integer", false), MailAdminField("isDefault", "boolean", false))
    "mailbox-settings" -> listOf(MailAdminField("alias", "string"), MailAdminField("doNotDisturb", "boolean"), MailAdminField("forwardTo", "string"), MailAdminField("forwardKeepCopy", "boolean"), MailAdminField("forwardDeleteDays", "integer"), MailAdminField("clientAccessUser", "boolean"))
    else -> emptyList()
}

internal fun isSeekLiMailboxAddress(value: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(value)
