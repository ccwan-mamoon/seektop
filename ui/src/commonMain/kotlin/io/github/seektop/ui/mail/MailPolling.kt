package io.github.seektop.ui.mail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class MailPolling(private val scope: CoroutineScope, private val allowed: () -> Boolean, private val fetch: suspend () -> Unit) {
    private var job: Job? = null
    fun start(seconds: Int = 60) {
        require(seconds in listOf(30, 60, 120, 300, 600, 1800, 3600))
        job?.cancel()
        job = scope.launch {
            while (true) {
                delay(seconds * 1000L)
                if (allowed()) fetch()
            }
        }
    }
}
