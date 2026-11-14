package io.github.seektop.desktop

import io.github.seektop.foundation.core.update.ApkInstaller
import io.github.seektop.foundation.core.update.AppRelease
import io.github.seektop.foundation.core.update.isNewerVersionName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal interface DesktopUpdateApplier : ApkInstaller {
    fun register(archive: File, release: AppRelease)
}

internal class DesktopUpdateInstaller(
    private val cache: File,
    private val resources: File?,
    private val currentVersion: String,
    private val target: File? = resources?.parentFile?.parentFile,
) : DesktopUpdateApplier {
    @Volatile var requestExit: () -> Unit = {}
    private val prepared = ConcurrentHashMap<String, AppRelease>()
    private val mutex = Mutex()
    private var committed = false

    override fun register(archive: File, release: AppRelease) {
        require(archive.canonicalFile.toPath().startsWith(cache.canonicalFile.toPath()))
        require(trustedDesktopAsset(release) && isNewerVersionName(release.versionName, currentVersion))
        prepared[archive.canonicalPath] = release
    }

    override fun canInstallPackages(): Boolean = target?.let {
        File(it, "SeekTop.exe").isFile && File(it, "app/SeekTop.cfg").isFile && File(it, "runtime/release").isFile && resources?.let { dir -> File(dir, "SeekTopUpdater.exe").isFile } == true
    } ?: false

    override suspend fun install(apkPath: String): Boolean = mutex.withLock {
        if (committed) return@withLock true
        withContext(Dispatchers.IO) {
            try {
                check(canInstallPackages())
                val archive = File(apkPath).canonicalFile
                val release = checkNotNull(prepared[archive.path])
                require(archive.length() == release.sizeBytes && sha256(archive) == release.sha256)
                val installed = checkNotNull(target).canonicalFile
                val parent = checkNotNull(installed.parentFile)
                val token = UUID.randomUUID().toString().replace("-", "")
                val stage = File(parent, ".${installed.name}.update-$token").canonicalFile
                val backup = File(parent, ".${installed.name}.backup-$token").canonicalFile
                require(stage.parentFile == parent && backup.parentFile == parent && !Files.isSymbolicLink(installed.toPath()))
                check(stage.mkdir())
                extractDesktopUpdate(archive, stage, release.versionName)
                val transaction = File(cache, token).apply { check(mkdirs()) }
                val helper = File(transaction, "SeekTopUpdater.exe")
                File(checkNotNull(resources), "SeekTopUpdater.exe").copyTo(helper)
                val process = ProcessHandle.current()
                val pids = mutableListOf(process.pid())
                process.parent().ifPresent { candidate ->
                    if (candidate.info().command().orElse("").equals(File(installed, "SeekTop.exe").absolutePath, ignoreCase = true)) pids += candidate.pid()
                }
                // Chromium children can outlive their parent briefly and still hold runtime DLLs.
                process.descendants().use { descendants ->
                    descendants.forEach { candidate ->
                        if (candidate.info().command().orElse("").equals(File(installed, "app/resources/browser-runtime/SeekTopBrowser.exe").absolutePath, ignoreCase = true)) pids += candidate.pid()
                    }
                }
                val plan = File(transaction, "plan.json")
                plan.writeText(
                    buildJsonObject {
                        put("target", installed.path)
                        put("stage", stage.path)
                        put("backup", backup.path)
                        put("token", token)
                        put("pids", JsonArray(pids.map(::JsonPrimitive)))
                        put("restart", true)
                        put("interactive", true)
                    }.toString(),
                )
                currentCoroutineContext().ensureActive()
                // After the helper owns the transaction, cancellation must not strand it waiting for us.
                withContext(NonCancellable) {
                    val helperProcess = ProcessBuilder(helper.path, plan.path).directory(transaction).start()
                    val result = File(transaction, "result.json")
                    var ready = false
                    repeat(50) {
                        if (result.isFile) {
                            val status = runCatching { Json.parseToJsonElement(result.readText()).jsonObject["status"]?.jsonPrimitive?.content }.getOrNull()
                            if (status == "ready") ready = true
                        }
                        if (!ready && helperProcess.isAlive) delay(100)
                    }
                    check(ready) { "Updater did not confirm a valid transaction" }
                    committed = true
                    javax.swing.SwingUtilities.invokeLater { requestExit() }
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                cache.mkdirs()
                File(cache, "last-error.log").writeText(error.stackTraceToString())
                false
            }
        }
    }
}
