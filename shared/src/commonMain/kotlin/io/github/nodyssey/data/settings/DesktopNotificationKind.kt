package io.github.nodyssey.data.settings

/** Independent desktop schedules; Android keeps its WorkManager interval unchanged. */
enum class DesktopNotificationKind(val storageName: String, val defaultSeconds: Int, val choices: List<Int>) {
    INTERACTIONS("interactions", 30, listOf(30, 60, 120, 300)),
    MESSAGES("messages", 30, listOf(30, 60, 120, 300)),
    KEYWORDS("keywords", 300, listOf(60, 120, 300, 600, 900)),
    TRACKED("tracked", 300, listOf(60, 120, 300, 600, 900)),
    ;

    fun normalize(seconds: Int): Int = seconds.takeIf { it in choices } ?: defaultSeconds
    fun seconds(settings: UserSettings): Int = normalize(settings.desktopNotificationSeconds[this] ?: defaultSeconds)
}
