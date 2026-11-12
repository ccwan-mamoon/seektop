package io.github.nodyssey.desktop

import io.github.nodyssey.data.composer.ImagePreparer
import io.github.nodyssey.data.composer.originalUpload
import io.github.nodyssey.data.composer.withExtension
import io.github.nodyssey.data.imagehost.ImageHostUpload
import io.github.nodyssey.data.offline.OfflineFileStore
import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.imageio.ImageIO

class DesktopOfflineFiles(private val directory: File) : OfflineFileStore {
    init {
        directory.mkdirs()
    }
    override fun nameOf(url: String): String = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun file(name: String): File {
        require(name.matches(Regex("[a-f0-9]{64}")))
        return File(directory, name)
    }
    override fun hasStored(url: String) = file(nameOf(url)).isFile
    override fun write(name: String, bytes: ByteArray): Long {
        val target = file(name)
        val temp = File(directory, "$name.tmp")
        temp.writeBytes(bytes)
        java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return bytes.size.toLong()
    }
    override fun sweep(keep: Set<String>) {
        directory.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }
    override fun clear() = sweep(emptySet())
    override fun freeBytes(): Long = directory.usableSpace
    override fun hasRoomFor(bytes: Long) = freeBytes() - bytes >= OfflineFileStore.SPACE_FLOOR_BYTES
}

class DesktopImagePreparer(private val dispatchers: AppDispatchers) : ImagePreparer {
    override suspend fun original(source: String, displayName: String): ImageHostUpload = withContext(dispatchers.io) {
        val bytes = (if (source.startsWith("file:")) File(URI(source)) else File(source)).readBytes()
        originalUpload(bytes, displayName) ?: prepare(source, displayName)
    }
    override suspend fun prepare(source: String, displayName: String): ImageHostUpload = withContext(dispatchers.io) {
        val file = if (source.startsWith("file:")) File(URI(source)) else File(source)
        val bytes = file.readBytes()
        val original = originalUpload(bytes, displayName)
        if (original != null && original.mimeType in setOf("image/gif", "image/webp")) return@withContext original
        val image = requireNotNull(ImageIO.read(file)) { "Unsupported image format" }
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply {
            color = java.awt.Color.WHITE
            fillRect(0, 0, image.width, image.height)
            drawImage(image, 0, 0, null)
            dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(rgb, "jpg", out)
        ImageHostUpload(out.toByteArray(), displayName.withExtension("jpg"), "image/jpeg")
    }
}
