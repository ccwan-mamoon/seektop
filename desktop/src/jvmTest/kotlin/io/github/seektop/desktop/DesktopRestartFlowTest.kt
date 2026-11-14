package io.github.seektop.desktop

import androidx.lifecycle.ViewModelStore
import io.github.seektop.data.update.AppUpdateRepository
import io.github.seektop.foundation.core.AppVersion
import io.github.seektop.foundation.core.crash.NoCrashReports
import io.github.seektop.foundation.core.update.ApkInstaller
import io.github.seektop.foundation.core.update.AppRelease
import io.github.seektop.foundation.core.update.AppUpdateState
import io.github.seektop.foundation.core.update.InstallOutcome
import io.github.seektop.foundation.core.update.ReleaseNote
import io.github.seektop.foundation.core.update.UpdateDownload
import io.github.seektop.ui.settings.AboutAppViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopRestartFlowTest {
    @Test fun `restart cannot be submitted twice while preparing and can retry after failure`() = runBlocking {
        withTimeout(5000) {
            withContext(Dispatchers.Swing) {
                val failed = CompletableDeferred<Unit>()
                val updates = object : AppUpdateRepository {
                    override val state = MutableStateFlow(AppUpdateState(download = UpdateDownload.Ready("verified.zip", "1.0.0")))
                    override val launchReminder = MutableStateFlow<AppRelease?>(null)
                    override fun check(force: Boolean) = Unit
                    override fun checkOnLaunch() = Unit
                    override fun acceptLaunchReminder() = Unit
                    override fun postponeLaunchReminder() = Unit
                    override fun download() = error("Restart must not start another download")
                    override fun cancelDownload() = Unit
                    override suspend fun releaseNotes(force: Boolean): List<ReleaseNote> = emptyList()
                    override fun onInstallOutcome(outcome: InstallOutcome) {
                        if (outcome is InstallOutcome.Failed) failed.complete(Unit)
                    }
                }
                var calls = 0
                var answer = CompletableDeferred<Boolean>()
                val installer = object : ApkInstaller {
                    override fun canInstallPackages() = true
                    override suspend fun install(apkPath: String): Boolean {
                        assertEquals("verified.zip", apkPath)
                        calls++
                        return answer.await()
                    }
                }
                val viewModel = AboutAppViewModel(updates, installer, AppVersion("0.9.9", 6), NoCrashReports)
                val store = ViewModelStore().apply { put("about", viewModel) }
                try {
                    viewModel.install()
                    viewModel.install()
                    assertEquals(1, calls)
                    answer.complete(false)
                    failed.await()
                    answer = CompletableDeferred()
                    viewModel.install()
                    assertEquals(2, calls)
                    answer.complete(true)
                } finally {
                    store.clear()
                }
                Unit
            }
        }
    }
}
