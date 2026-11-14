package io.github.seektop.desktop

import io.github.seektop.plugins.InstalledTool
import io.github.seektop.plugins.ToolEvent
import io.github.seektop.plugins.ToolListing
import io.github.seektop.plugins.ToolManifest
import io.github.seektop.plugins.ToolPluginManager
import io.github.seektop.plugins.ToolPluginState
import io.github.seektop.plugins.ToolText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Only publisher-signed first-party native tools; this is not a sandbox for arbitrary extensions. */
internal class DesktopToolPlugins(
    private val directory: File,
    private val scope: CoroutineScope,
    private val verifier: ToolPackageVerifier = ToolPackageVerifier(Base64.getDecoder().decode("MCowBQYDK2VwAyEAuyibqYg1Se4cLBLICnh1m+6FFrm2hVZjUGEqyiY3YxQ=")),
    private val remoteDownload: suspend (String, Long) -> ByteArray = { _, _ -> error("Plugin marketplace transport unavailable") },
) : ToolPluginManager,
    AutoCloseable {
    private val mutableState = MutableStateFlow(ToolPluginState(busy = true))
    override val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private val json = Json

    @Volatile private var process: Process? = null
    private var runJob: Job? = null
    private var revision = 0L
    private var storageReadable = true

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    directory.mkdirs()
                    val stored = File(directory, "installed.json").takeIf { it.exists() }?.readText()?.let { parseStored(it) } ?: StoredTools()
                    revision = stored.revision
                    val loaded = stored.entries.map { entry ->
                        val manifest = verifier.installed(location(entry.id, entry.version), verifyFiles = false)
                        require(manifest.id == entry.id && manifest.version == entry.version)
                        InstalledTool(manifest, entry.enabled)
                    }
                    mutableState.update { it.copy(installed = loaded) }
                } catch (failure: Exception) {
                    storageReadable = false
                    mutableState.update { it.copy(error = failure.message ?: "Plugin storage could not be read") }
                } finally {
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }

    override suspend fun refresh() = operation {
        val catalog = verifier.catalog(download(CATALOG, 1024 * 1024L))
        require(catalog.revision >= revision) { "Plugin catalog is older than the last verified catalog" }
        revision = catalog.revision
        save(state.value.installed)
        mutableState.update { it.copy(catalog = catalog.plugins) }
    }

    override suspend fun install(listing: ToolListing) = operation {
        // Do not accept arbitrary URLs or descriptors passed by a stale/forged UI command.
        require(listing in state.value.catalog)
        val bytes = download(listing.url, ToolPackageVerifier.MAX_PACKAGE, listing.size)
        require(bytes.size.toLong() == listing.size && ToolPackageVerifier.sha256(bytes) == listing.sha256) { "Plugin download checksum mismatch" }
        installBytes(bytes, listing.manifest)
    }

    override suspend fun importPackage() {
        val file = withContext(Dispatchers.Main) {
            javax.swing.JFileChooser().apply {
                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("SeekTop plugin (*.stplugin)", "stplugin")
            }.let { chooser -> if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null }
        } ?: return
        operation {
            require(file.length() in 1..ToolPackageVerifier.MAX_PACKAGE)
            installBytes(file.readBytes(), null)
        }
    }

    internal suspend fun importVerifiedPackage(file: File) = operation {
        require(file.length() in 1..ToolPackageVerifier.MAX_PACKAGE)
        installBytes(file.readBytes(), null)
    }

    private suspend fun installBytes(bytes: ByteArray, expected: ToolManifest?) {
        val temp = File(directory, ".install-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val archive = File(temp, "package.zip").apply { writeBytes(bytes) }
            val unpacked = File(temp, "files")
            val manifest = verifier.unpack(archive, unpacked)
            require(expected == null || expected == manifest) { "Plugin manifest differs from catalog" }
            require(state.value.runningId != manifest.id) { "Stop this tool before updating" }
            val old = state.value.installed.find { it.manifest.id == manifest.id }
            require(old == null || compareVersions(manifest.version, old.manifest.version) >= 0) { "Plugin downgrade is not allowed" }
            val target = location(manifest.id, manifest.version)
            // Verify an existing version instead of replacing files that another instance may use.
            if (target.exists()) {
                val valid = runCatching { verifier.installed(target) }.getOrNull()
                if (valid != null) {
                    require(valid == manifest) { "A different plugin build already uses this version" }
                } else {
                    val damaged = File(target.parentFile, ".damaged-${UUID.randomUUID()}")
                    Files.move(target.toPath(), damaged.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    try {
                        Files.move(unpacked.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    } catch (failure: Exception) {
                        Files.move(damaged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        throw failure
                    }
                }
            } else {
                target.parentFile.mkdirs()
                Files.move(unpacked.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            }
            val installed = state.value.installed.filterNot { it.manifest.id == manifest.id } + InstalledTool(manifest, old?.enabled ?: true)
            save(installed)
            mutableState.update { it.copy(installed = installed) }
        } finally {
            temp.deleteRecursively()
        }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = operation {
        require(state.value.runningId != id) { "Stop this tool first" }
        val installed = state.value.installed.map { if (it.manifest.id == id) it.copy(enabled = enabled) else it }
        save(installed)
        mutableState.update { it.copy(installed = installed) }
    }

    override suspend fun uninstall(id: String) = operation {
        require(state.value.runningId != id) { "Stop this tool first" }
        val installed = state.value.installed.filterNot { it.manifest.id == id }
        save(installed)
        mutableState.update { it.copy(installed = installed) }
        val folder = location(id, "0.0.0").parentFile.toPath().toAbsolutePath().normalize()
        require(folder.startsWith(directory.toPath().toAbsolutePath().normalize()) && folder != directory.toPath().toAbsolutePath().normalize())
        if (Files.exists(folder)) {
            // Files.walk does not follow symbolic links; deletion is confined to this plugin's tree.
            Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    override suspend fun run(id: String, parameters: Map<String, String>) = operation {
        require(runJob?.isActive != true) { "Another tool is running" }
        val tool = requireNotNull(state.value.installed.find { it.manifest.id == id && it.enabled })
        require(parameters.keys == tool.manifest.fields.map { it.id }.toSet())
        require(parameters.values.all { it.length <= 2048 })
        tool.manifest.fields.forEach { field ->
            require(field.options.isEmpty() || field.options.any { it.value == parameters[field.id] })
        }
        mutableState.update { it.copy(runningId = id, resultPluginId = id, event = null, error = null) }
        runJob = scope.launch(Dispatchers.IO) {
            try {
                withTimeout(tool.manifest.timeoutSeconds * 1000L) {
                    val folder = location(id, tool.manifest.version)
                    require(verifier.installed(folder) == tool.manifest)
                    val worker = ProcessBuilder(File(folder, "worker.exe").absolutePath).directory(folder).apply {
                        // Neither forum session nor browser bridge credentials are passed to tools.
                        val retained = environment().filterKeys { it.uppercase() in setOf("SYSTEMROOT", "WINDIR", "TEMP", "TMP", "LOCALAPPDATA", "USERPROFILE", "SYSTEMDRIVE") }
                        environment().clear()
                        environment().putAll(retained)
                        redirectError(ProcessBuilder.Redirect.DISCARD)
                    }.start()
                    process = worker
                    worker.outputStream.write((json.encodeToString(parameters) + "\n").toByteArray())
                    worker.outputStream.flush()
                    var final = false
                    var total = 0
                    // Process pipe reads are not reliably interruptible on Windows. Read only
                    // available bytes and suspend between polls so Stop always reaches cleanup.
                    val pending = java.io.ByteArrayOutputStream()
                    val input = worker.inputStream
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val available = input.available()
                        if (available == 0) {
                            if (!worker.isAlive) break
                            kotlinx.coroutines.delay(30)
                            continue
                        }
                        val count = input.read(buffer, 0, minOf(buffer.size, available))
                        if (count < 0) break
                        total += count
                        require(total <= 2 * 1024 * 1024) { "Tool output limit exceeded" }
                        for (index in 0 until count) {
                            if (buffer[index] == 10.toByte()) {
                                val event = json.decodeFromString<ToolEvent>(pending.toByteArray().decodeToString())
                                pending.reset()
                                require(event.rows.size <= 100 && event.metrics.size <= 20)
                                final = event.done
                                mutableState.update { it.copy(event = event) }
                            } else {
                                require(pending.size() < 64 * 1024) { "Tool event is too large" }
                                pending.write(buffer[index].toInt())
                            }
                        }
                    }
                    require(pending.size() == 0) { "Incomplete tool event" }
                    require(runInterruptible { worker.waitFor() } == 0 && final) { "Tool exited without a complete result" }
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(event = ToolEvent(ToolText("已停止", "Stopped", "已停止"), done = true)) }
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = failure.message ?: "Tool failed") }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    process?.let { worker ->
                        runCatching {
                            worker.outputStream.write("cancel\n".toByteArray())
                            worker.outputStream.flush()
                        }
                        if (!worker.waitFor(800, TimeUnit.MILLISECONDS)) worker.destroyForcibly().waitFor(2, TimeUnit.SECONDS)
                    }
                    process = null
                    mutableState.update { it.copy(runningId = null) }
                }
            }
        }
        runJob?.invokeOnCompletion { mutableState.update { if (it.runningId == id) it.copy(runningId = null) else it } }
    }

    override suspend fun stop() {
        runJob?.cancelAndJoin()
    }

    private suspend fun operation(block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableState.update { it.copy(busy = true, progress = null, error = null) }
            try {
                require(storageReadable) { "Plugin index could not be read. Restore plugins/installed.json before making changes." }
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = failure.message ?: "Plugin operation failed") }
            } finally {
                mutableState.update { it.copy(busy = false, progress = null) }
            }
        }
    }

    private suspend fun download(address: String, limit: Long, expected: Long? = null): ByteArray = withTimeout(75_000) {
        remoteDownload(address, limit).also { bytes ->
            require(bytes.size.toLong() <= limit)
            if (expected != null) mutableState.update { it.copy(progress = 1f) }
        }
    }

    private fun location(id: String, version: String): File {
        require(Regex("io\\.github\\.seektop\\.toolbox\\.[a-z][a-z0-9-]{0,40}").matches(id))
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(version))
        return File(directory, "$id/$version")
    }

    private fun parseStored(value: String): StoredTools {
        val root = json.parseToJsonElement(value).jsonObject
        return StoredTools(
            root.getValue("revision").jsonPrimitive.long,
            root.getValue("entries").jsonArray.map {
                val entry = it.jsonObject
                StoredTool(entry.getValue("id").jsonPrimitive.content, entry.getValue("version").jsonPrimitive.content, entry.getValue("enabled").jsonPrimitive.boolean)
            },
        )
    }

    private fun save(installed: List<InstalledTool>) {
        directory.mkdirs()
        val temporary = File(directory, "installed-${UUID.randomUUID()}.tmp")
        temporary.writeText(
            buildJsonObject {
                put("revision", revision)
                put(
                    "entries",
                    buildJsonArray {
                        installed.forEach { tool ->
                            add(
                                buildJsonObject {
                                    put("id", tool.manifest.id)
                                    put("version", tool.manifest.version)
                                    put("enabled", tool.enabled)
                                },
                            )
                        }
                    },
                )
            }.toString(),
        )
        Files.move(temporary.toPath(), File(directory, "installed.json").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun close() {
        runJob?.cancel()
        process?.destroyForcibly()
    }

    private data class StoredTool(val id: String, val version: String, val enabled: Boolean)

    private data class StoredTools(val revision: Long = 0, val entries: List<StoredTool> = emptyList())

    companion object {
        private const val CATALOG = "https://github.com/ccwan-mamoon/seektop/releases/download/plugin-market/catalog.json"
        internal fun compareVersions(left: String, right: String): Int {
            val a = left.split('.').map(String::toInt)
            val b = right.split('.').map(String::toInt)
            return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first.compareTo(it.second) } ?: 0
        }
    }
}
