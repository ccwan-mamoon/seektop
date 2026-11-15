package io.github.seektop.desktop

import io.github.seektop.plugins.ToolCatalog
import io.github.seektop.plugins.ToolManifest
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipFile

internal class ToolPackageVerifier(publicKey: ByteArray) {
    private val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKey))
    private val json = Json { ignoreUnknownKeys = false }

    fun verify(payload: ByteArray, signature: ByteArray) {
        require(
            signature.size == 64 && Signature.getInstance("Ed25519").run {
                initVerify(key)
                update(payload)
                verify(signature)
            },
        ) { "Invalid plugin publisher signature" }
    }

    fun catalog(envelope: ByteArray): ToolCatalog {
        val value = json.parseToJsonElement(envelope.decodeToString()).let { it as kotlinx.serialization.json.JsonObject }
        val decoder = Base64.getDecoder()
        val payload = decoder.decode((value.getValue("payload") as kotlinx.serialization.json.JsonPrimitive).content)
        verify(payload, decoder.decode((value.getValue("signature") as kotlinx.serialization.json.JsonPrimitive).content))
        return json.decodeFromString<ToolCatalog>(payload.decodeToString()).also { catalog ->
            require(catalog.revision > 0 && catalog.plugins.size <= 30)
            require(catalog.plugins.map { it.manifest.id }.distinct().size == catalog.plugins.size)
            catalog.plugins.forEach {
                validate(it.manifest)
                require(it.size in 1..MAX_PACKAGE && hash.matches(it.sha256))
                require(it.url.startsWith("https://github.com/ccwan-mamoon/seektop/releases/download/"))
            }
        }
    }

    fun unpack(archive: File, destination: File): ToolManifest {
        require(archive.length() in 1..MAX_PACKAGE)
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            require(entries.size in 4..16 && entries.map { it.name }.distinct().size == entries.size)
            require(entries.map { it.name.lowercase() }.distinct().size == entries.size)
            require(entries.none { it.isDirectory || !fileName.matches(it.name) || it.size !in 0..MAX_PACKAGE })
            fun read(name: String, limit: Int): ByteArray = zip.getInputStream(requireNotNull(zip.getEntry(name))).use {
                it.readNBytes(limit + 1).also { bytes -> require(bytes.size <= limit) }
            }
            val bytes = read("manifest.json", 256 * 1024)
            val signature = read("manifest.sig", 64)
            verify(bytes, signature)
            val manifest = json.decodeFromString<ToolManifest>(bytes.decodeToString())
            validate(manifest)
            require(entries.map { it.name }.toSet() == manifest.files.keys + setOf("manifest.json", "manifest.sig"))
            var total = 0L
            destination.mkdirs()
            manifest.files.forEach { (name, expected) ->
                val content = read(name, MAX_PACKAGE.toInt())
                total += content.size
                require(total <= MAX_PACKAGE && sha256(content) == expected) { "Plugin file checksum mismatch: $name" }
                File(destination, name).writeBytes(content)
            }
            File(destination, "manifest.json").writeBytes(bytes)
            File(destination, "manifest.sig").writeBytes(signature)
            return manifest
        }
    }

    fun installed(directory: File, verifyFiles: Boolean = true): ToolManifest {
        require(!java.nio.file.Files.isSymbolicLink(directory.toPath()))
        val manifestFile = File(directory, "manifest.json")
        require(manifestFile.length() in 1..256 * 1024)
        val bytes = manifestFile.readBytes()
        verify(bytes, File(directory, "manifest.sig").readBytes())
        return json.decodeFromString<ToolManifest>(bytes.decodeToString()).also { manifest ->
            validate(manifest)
            if (verifyFiles) {
                manifest.files.forEach { (name, digest) ->
                    val file = File(directory, name)
                    require(!java.nio.file.Files.isSymbolicLink(file.toPath()) && file.length() in 0..MAX_PACKAGE)
                    require(sha256(file.readBytes()) == digest) { "Installed plugin was changed; reinstall it" }
                }
            }
        }
    }

    private fun validate(manifest: ToolManifest) {
        require(Regex("io\\.github\\.seektop\\.toolbox\\.[a-z][a-z0-9-]{0,40}").matches(manifest.id))
        require(Regex("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})").matches(manifest.version))
        require(manifest.api == 1 && manifest.runtime in setOf("native-tool-v1", "post-analysis-v1") && manifest.timeoutSeconds in 5..120)
        require(manifest.fields.size <= 12 && manifest.fields.map { it.id }.distinct().size == manifest.fields.size)
        manifest.fields.forEach { field ->
            require(Regex("[a-z][a-z0-9-]{0,40}").matches(field.id) && field.default.length <= 2048)
            require(field.options.size <= 30 && field.options.map { it.value }.distinct().size == field.options.size)
            require(field.options.isEmpty() || field.options.any { it.value == field.default })
        }
        require(manifest.files.size in 2..12)
        if (manifest.runtime == "post-analysis-v1") {
            require(manifest.id == io.github.seektop.ai.POST_ANALYSIS_PLUGIN && manifest.files.containsKey("analysis.json"))
            require(manifest.files.keys.none { it.endsWith(".exe", true) || it.endsWith(".dll", true) || it.endsWith(".js", true) })
        } else {
            require(manifest.files.containsKey("worker.exe"))
        }
        require(manifest.files.keys.map { it.lowercase() }.distinct().size == manifest.files.size)
        require(manifest.files.keys.none { !fileName.matches(it) || it in setOf("manifest.json", "manifest.sig") })
        require(manifest.files.values.all { hash.matches(it) })
    }

    companion object {
        const val MAX_PACKAGE = 32L * 1024 * 1024
        private val fileName = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,80}")
        private val hash = Regex("[a-f0-9]{64}")
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
