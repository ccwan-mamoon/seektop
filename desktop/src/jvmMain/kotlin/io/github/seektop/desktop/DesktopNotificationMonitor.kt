package io.github.seektop.desktop

import io.github.seektop.data.NotificationCounts
import io.github.seektop.data.settings.DesktopNotificationKind
import io.github.seektop.data.settings.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** A fresh account/launch seeds its baseline; quiet hours consume arrivals without a later burst. */
internal class DesktopUnreadTracker {
    private var previous: Pair<Int, Int>? = null

    /** Reading in this app lowers the baseline, but another screen fetching new counts must not raise it. */
    fun noteRead(counts: NotificationCounts) {
        previous = previous?.let { minOf(it.first, counts.interactions) to minOf(it.second, counts.messages) }
    }

    val isSeeded: Boolean get() = previous != null

    fun accept(counts: NotificationCounts, settings: UserSettings, quiet: Boolean, interactionsDue: Boolean = true, messagesDue: Boolean = true): Pair<Int, Int> {
        val old = previous
        previous = (if (interactionsDue || old == null) counts.interactions else old.first) to (if (messagesDue || old == null) counts.messages else old.second)
        if (old == null || quiet) return 0 to 0
        return (if (settings.notifyInteractions && interactionsDue) (counts.interactions - old.first).coerceAtLeast(0) else 0) to
            (if (settings.notifyMessages && messagesDue) (counts.messages - old.second).coerceAtLeast(0) else 0)
    }
}

internal fun desktopQuietHours(settings: UserSettings, minute: Int): Boolean =
    settings.notificationQuietHours && (minute >= 23 * 60 || minute < 7 * 60)

internal fun desktopNotificationDelay(failures: Int): Long = (30_000L * (1L shl failures.coerceIn(0, 4))).coerceAtMost(300_000L)

private data class NotificationMonitorKey(
    val enabled: Boolean,
    val quiet: Boolean,
    val intervals: Map<DesktopNotificationKind, Int>,
    val account: Int?,
)

internal suspend fun monitorDesktopNotifications(container: DesktopAppContainer, deliver: suspend (String, String) -> Unit) {
    var tracker = DesktopUnreadTracker()
    var account: Int? = null
    var activeSiteKinds = emptySet<DesktopNotificationKind>()
    val schedule = DesktopNotificationSchedule()
    fun now() = System.nanoTime() / 1_000_000L
    combine(container.settingsRepository.settings, container.sessionRepository.state) { settings, session ->
        val intervals = buildMap {
            if (settings.notifyInteractions && session.isSignedIn) put(DesktopNotificationKind.INTERACTIONS, DesktopNotificationKind.INTERACTIONS.seconds(settings))
            if (settings.notifyMessages && session.isSignedIn) put(DesktopNotificationKind.MESSAGES, DesktopNotificationKind.MESSAGES.seconds(settings))
            if (settings.notifyKeywordAlerts) put(DesktopNotificationKind.KEYWORDS, DesktopNotificationKind.KEYWORDS.seconds(settings))
            if (settings.notifyTrackedThreads) put(DesktopNotificationKind.TRACKED, DesktopNotificationKind.TRACKED.seconds(settings))
        }
        NotificationMonitorKey(settings.notificationsEnabled, settings.notificationQuietHours, intervals, session.fingerprint.takeIf { session.isSignedIn })
    }.distinctUntilChanged().collectLatest { key ->
        if (account != key.account || !key.enabled) {
            account = key.account
            tracker = DesktopUnreadTracker()
            activeSiteKinds = emptySet()
            schedule.update(emptyMap(), now())
        }
        schedule.update(if (key.enabled) key.intervals else emptyMap(), now())
        if (!key.enabled || key.intervals.isEmpty()) return@collectLatest
        val settings = UserSettings(notificationQuietHours = key.quiet)
        fun quiet(): Boolean = LocalTime.now().let { desktopQuietHours(settings, it.hour * 60 + it.minute) }
        suspend fun check(block: suspend () -> Unit): Boolean = try {
            block()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        suspend fun counts(): NotificationCounts {
            val repository = container.notificationRepository
            val before = repository.counts.value
            tracker.noteRead(before)
            val fetched = repository.refreshCounts()
            // Reply + mention may name the same comment; use the existing repository merger.
            if ((fetched.replies > before.replies && fetched.mentions > before.mentions) || fetched.overlap > minOf(fetched.replies, fetched.mentions)) repository.interactions()
            return repository.counts.value.let { it.copy(overlap = it.overlap.coerceIn(0, minOf(it.replies, it.mentions))) }
        }
        // Seed newly enabled categories, retaining the baseline when only an interval changes.
        val siteEnabled = key.intervals.keys.filterTo(mutableSetOf()) { it == DesktopNotificationKind.INTERACTIONS || it == DesktopNotificationKind.MESSAGES }
        val newlyEnabled = siteEnabled - activeSiteKinds
        activeSiteKinds = siteEnabled
        if (key.account != null && (!tracker.isSeeded || newlyEnabled.isNotEmpty()) && siteEnabled.isNotEmpty()) {
            check { tracker.accept(counts(), settings, true, !tracker.isSeeded || DesktopNotificationKind.INTERACTIONS in newlyEnabled, !tracker.isSeeded || DesktopNotificationKind.MESSAGES in newlyEnabled) }
        }
        while (currentCoroutineContext().isActive) {
            delay(schedule.waitMillis(now()))
            val due = schedule.due(now())
            val siteKinds = due.filter { it == DesktopNotificationKind.INTERACTIONS || it == DesktopNotificationKind.MESSAGES }
            if (siteKinds.isNotEmpty()) {
                val success = check {
                    val (interactions, messages) = tracker.accept(counts(), settings, quiet(), DesktopNotificationKind.INTERACTIONS in due, DesktopNotificationKind.MESSAGES in due)
                    if (interactions > 0) deliver("SeekTop · 新互动", "有 $interactions 条新回复或提及，点击打开 SeekTop 查看。")
                    if (messages > 0) deliver("SeekTop · 新私信", "有 $messages 条新私信，点击打开 SeekTop 查看。")
                }
                siteKinds.forEach { schedule.completed(it, now(), success) }
            }
            if (DesktopNotificationKind.KEYWORDS in due) {
                val success = check {
                    val hits = container.backgroundAlerts.checkKeywords()
                    if (!quiet() && hits.isNotEmpty()) deliver("SeekTop · 关键词提醒", "${hits.size} 个新主题匹配关键词：${hits.first().title}")
                }
                schedule.completed(DesktopNotificationKind.KEYWORDS, now(), success)
            }
            if (DesktopNotificationKind.TRACKED in due) {
                val success = check {
                    val updates = container.backgroundAlerts.checkTrackedThreads()
                    if (!quiet() && updates.isNotEmpty()) deliver("SeekTop · 追踪的新回复", "${updates.size} 个追踪主题有新回复：${updates.first().title}")
                }
                schedule.completed(DesktopNotificationKind.TRACKED, now(), success)
            }
        }
    }
}

/** Windows owns balloon delivery and honors its system notification / Do Not Disturb settings. */
internal class DesktopNotifier(image: java.awt.Image, onOpen: () -> Unit) : AutoCloseable {
    private val tray: java.awt.TrayIcon? = if (java.awt.SystemTray.isSupported()) {
        java.awt.TrayIcon(image, "SeekTop · 寻桌").apply {
            isImageAutoSize = true
            addActionListener { onOpen() }
        }.let { icon ->
            try {
                java.awt.SystemTray.getSystemTray().add(icon)
                icon
            } catch (_: java.awt.AWTException) {
                null
            }
        }
    } else {
        null
    }

    val isAvailable: Boolean get() = tray != null

    suspend fun deliver(title: String, body: String) = withContext(Dispatchers.Swing) {
        tray?.displayMessage(title, body, java.awt.TrayIcon.MessageType.INFO)
    }

    override fun close() {
        tray?.let { java.awt.SystemTray.getSystemTray().remove(it) }
    }
}
