package io.github.seektop.ui.mail

import androidx.compose.runtime.Composable
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.mail_days_ago
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun mailDateLabel(value: String): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    val days = (Clock.System.now() - instant).inWholeDays.coerceAtLeast(0)
    if (days < 7) return stringResource(Res.string.mail_days_ago, days.toInt())
    return instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString().replace('-', '/')
}
