package io.github.seektop.desktop

import io.github.seektop.data.settings.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class DesktopRuntime(val browser: DesktopBrowser, val container: DesktopAppContainer, val settings: UserSettings) : AutoCloseable {
    override fun close() = container.close()
}

/** Retain ownership across the dispatcher handoff: cancellation can discard withContext's result. */
internal suspend fun <T : AutoCloseable> loadDesktopResource(open: () -> T, publish: (T) -> Unit) {
    var pending: T? = null
    try {
        withContext(Dispatchers.IO) { pending = open() }
        publish(checkNotNull(pending))
        pending = null
    } finally {
        pending?.let { resource ->
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { resource.close() }
        }
    }
}

internal fun writeStartupFailure(directory: File, failure: Throwable) {
    runCatching { File(directory, "startup-error.log").writeText(failure.stackTraceToString()) }
}
