package io.github.nodyssey.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateDownload
import io.github.plaza.core.update.UpdateFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal fun updateFixture(version: String = "0.4.1", digest: String = "a".repeat(64), size: Long = 10, preview: Boolean = false): String = buildJsonObject {
    put("tag_name", "desktop-v$version")
    put("draft", false)
    put("prerelease", preview)
    put("body", "Fixture release")
    put(
        "assets",
        JsonArray(
            listOf(
                buildJsonObject {
                    put("name", "SeekTop-$version-windows-x64.zip")
                    put("size", size)
                    put("digest", "sha256:$digest")
                    put("browser_download_url", "$DESKTOP_REPOSITORY/releases/download/desktop-v$version/SeekTop-$version-windows-x64.zip")
                },
            ),
        ),
    )
}.toString()

class DesktopUpdateTest {
    @Test fun `every launch bypasses cache and a cancelled check cannot publish its late result`() = runBlocking {
        fixture { settings, scope, wait, root ->
            val entered = CompletableDeferred<Unit>()
            val transport = object : DesktopUpdateTransport {
                override suspend fun releases(): String {
                    entered.complete(Unit)
                    kotlinx.coroutines.awaitCancellation()
                }
                override suspend fun download(release: AppRelease, output: File, progress: (Long) -> Unit) = Unit
            }
            settings.setUpdateCheckOnLaunch(false)
            settings.setUpdateCheckRecord(io.github.plaza.core.update.UpdateCheckRecord(System.currentTimeMillis(), null, false))
            val repository = DesktopUpdateRepository(settings, "1.0.0", root, scope, FakeApplier(), transport)
            repository.checkOnLaunch()
            withTimeout(5000) { entered.await() }
            assertIs<UpdateCheck.Checking>(repository.state.value.check)
            repository.cancelCheck()
            wait()
            assertIs<UpdateCheck.Idle>(repository.state.value.check)
            assertEquals(null, repository.launchReminder.value)
        }
    }

    @Test fun `release selection rejects downgrade source packages foreign URLs and wrong channels`() {
        val list = "[${updateFixture("0.3.0")},${updateFixture("0.4.1")},${updateFixture("0.5.0-dev.1", preview = true)}]"
        assertEquals("0.4.1", newestDesktopRelease(list, "0.4.0", false)?.versionName)
        assertEquals("0.5.0-dev.1", newestDesktopRelease(list, "0.4.0", true)?.versionName)
        assertEquals(null, newestDesktopRelease(list, "1.0.0", true))
        assertEquals(null, newestDesktopRelease("[${updateFixture().replace(DESKTOP_REPOSITORY, "https://example.com/repo")}]", "0.4.0", true))
        assertEquals(null, newestDesktopRelease("[${updateFixture().replace("-windows-x64.zip", "-source.zip")}]", "0.4.0", true))
        assertEquals(null, newestDesktopRelease("[${updateFixture(digest = "broken")}]", "0.4.0", true))
        assertFalse(trustedUpdateConnection("http://github.com/file"))
        assertFalse(trustedUpdateConnection("https://github.com.evil.example/file"))
        assertFalse(trustedUpdateConnection("https://user@github.com/file"))
        assertTrue(trustedUpdateConnection("https://release-assets.githubusercontent.com/file?token=fixture"))
    }

    @Test fun `check caches by channel and verified download waits for explicit restart`() = runBlocking {
        fixture { settings, scope, wait, root ->
            val payload = File(root, "fixture.zip").apply { writeBytes("verified update fixture".toByteArray()) }
            val transport = FakeTransport("[${updateFixture(digest = sha256(payload), size = payload.length())}]", payload.readBytes())
            val applier = FakeApplier()
            val repository = DesktopUpdateRepository(settings, "0.4.0", root, scope, applier, transport)
            repository.check()
            wait()
            assertIs<UpdateCheck.Available>(repository.state.value.check)
            assertEquals("0.4.1", repository.state.value.available?.versionName)
            repository.check()
            wait()
            assertEquals(1, transport.checks)
            repository.download()
            wait()
            assertIs<UpdateDownload.Ready>(repository.state.value.download)
            assertEquals(0, applier.installs)
            assertEquals(sha256(payload), sha256(checkNotNull(applier.archive)))
            transport.body = "[${updateFixture("0.5.0-dev.1", preview = true)}]"
            settings.setUpdateDevChannel(true)
            repository.check()
            wait()
            assertEquals(2, transport.checks)
            assertEquals("0.5.0-dev.1", repository.state.value.available?.versionName)
        }
    }

    @Test fun `corrupt bytes never reach installer and cancelling an active download leaves it idle`() = runBlocking {
        fixture { settings, scope, wait, root ->
            val transport = FakeTransport("[${updateFixture(size = 4)}]", byteArrayOf(1, 2, 3, 4))
            val applier = FakeApplier()
            val repository = DesktopUpdateRepository(settings, "0.4.0", root, scope, applier, transport)
            repository.check()
            wait()
            repository.download()
            wait()
            assertEquals(UpdateFailure.Checksum, (repository.state.value.download as UpdateDownload.Failed).failure)
            assertEquals(0, applier.installs)
            transport.block = true
            repository.download()
            withTimeout(5_000) { transport.started.await() }
            repository.cancelDownload()
            wait()
            assertEquals(UpdateDownload.Idle, repository.state.value.download)
            assertFalse(root.walkTopDown().any { it.name == "bundle.zip" })
            assertEquals(0, applier.installs)
        }
    }

    @Test fun `malformed release response reports failure rather than latest version`() = runBlocking {
        fixture { settings, scope, wait, root ->
            val repository = DesktopUpdateRepository(settings, "0.4.0", root, scope, FakeApplier(), FakeTransport("<html>error</html>", byteArrayOf()))
            repository.check()
            wait()
            assertIs<UpdateCheck.Failed>(repository.state.value.check)
        }
    }

    @Test fun `archive validates application version and rejects traversal or Windows path aliases`() = runBlocking {
        val root = Files.createTempDirectory(File("build").toPath().toAbsolutePath(), "update-zip-").toFile()
        fun zip(name: String, extra: List<Pair<String, ByteArray>> = emptyList(), version: String = "0.4.1"): File {
            val entries = listOf("SeekTop/SeekTop.exe" to byteArrayOf(77, 90), "SeekTop/runtime/release" to byteArrayOf(1), "SeekTop/app/resources/SeekTopUpdater.exe" to byteArrayOf(77, 90), "SeekTop/app/SeekTop.cfg" to "java-options=-Djpackage.app-version=$version".toByteArray()) + extra
            return File(root, name).apply {
                ZipOutputStream(outputStream()).use { output ->
                    entries.forEach { (path, bytes) ->
                        output.putNextEntry(ZipEntry(path))
                        output.write(bytes)
                        output.closeEntry()
                    }
                }
            }
        }
        try {
            extractDesktopUpdate(zip("valid.zip"), File(root, "valid").apply { mkdir() }, "0.4.1")
            assertFailsWith<IllegalArgumentException> { extractDesktopUpdate(zip("old.zip", version = "0.3.0"), File(root, "old").apply { mkdir() }, "0.4.1") }
            listOf("SeekTop/../escape.txt", "SeekTop/app/payload:stream", "SeekTop/app/CON.txt", "SeekTop/app/trailing. ").forEachIndexed { index, path ->
                assertFailsWith<IllegalArgumentException> { extractDesktopUpdate(zip("bad$index.zip", listOf(path to byteArrayOf(1))), File(root, "bad$index").apply { mkdir() }, "0.4.1") }
            }
            assertFailsWith<IllegalArgumentException> { extractDesktopUpdate(zip("alias.zip", listOf("SeekTop/app/A" to byteArrayOf(1), "SeekTop/app/a" to byteArrayOf(2))), File(root, "alias").apply { mkdir() }, "0.4.1") }
            assertFalse(File(root, "escape.txt").exists())
        } finally {
            check(root.toPath().startsWith(File("build").toPath().toAbsolutePath()))
            root.deleteRecursively()
        }
    }

    private suspend fun fixture(block: suspend (SettingsRepository, CoroutineScope, suspend () -> Unit, File) -> Unit) {
        val root = Files.createTempDirectory(File("build").toPath().toAbsolutePath(), "updater-").toFile()
        val storeJob = SupervisorJob()
        val workerJob = SupervisorJob()
        val settings = SettingsRepository(PreferenceDataStoreFactory.createWithPath(scope = CoroutineScope(Dispatchers.IO + storeJob), produceFile = { File(root, "settings.preferences_pb").absolutePath.toPath() }))
        try {
            block(settings, CoroutineScope(Dispatchers.Default + workerJob), { withTimeout(10_000) { workerJob.children.toList().joinAll() } }, root)
        } finally {
            workerJob.cancelAndJoin()
            storeJob.cancelAndJoin()
            check(root.toPath().startsWith(File("build").toPath().toAbsolutePath()))
            root.deleteRecursively()
        }
    }

    private class FakeTransport(var body: String, val payload: ByteArray) : DesktopUpdateTransport {
        var checks = 0
        var block = false
        val started = CompletableDeferred<Unit>()
        override suspend fun releases(): String {
            checks++
            return body
        }
        override suspend fun download(release: AppRelease, output: File, progress: (Long) -> Unit) {
            if (block) {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
            output.writeBytes(payload)
            progress(payload.size.toLong())
        }
    }
    private class FakeApplier : DesktopUpdateApplier {
        var archive: File? = null
        var installs = 0
        override fun register(archive: File, release: AppRelease) {
            this.archive = archive
        }
        override fun canInstallPackages() = true
        override suspend fun install(apkPath: String): Boolean {
            installs++
            return true
        }
    }
}
