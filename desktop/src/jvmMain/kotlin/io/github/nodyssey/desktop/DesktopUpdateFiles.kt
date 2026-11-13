package io.github.nodyssey.desktop

import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.compareVersionNames
import io.github.plaza.core.update.isNewerVersionName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

internal const val DESKTOP_RELEASES_API = "https://api.github.com/repos/ccwan-mamoon/seektop/releases?per_page=100"
internal const val DESKTOP_REPOSITORY = "https://github.com/ccwan-mamoon/seektop"
private val versionPattern = Regex("desktop-v(\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?)")
private val hashPattern = Regex("[a-f0-9]{64}")

internal fun desktopReleaseList(body: String, previews: Boolean): List<AppRelease> = Json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
    val item = element.jsonObject
    if (item["draft"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
    val tag = item["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
    val version = versionPattern.matchEntire(tag)?.groupValues?.get(1) ?: return@mapNotNull null
    val preview = item["prerelease"]?.jsonPrimitive?.booleanOrNull == true || '-' in version
    if (preview && !previews) return@mapNotNull null
    val name = "SeekTop-$version-windows-x64.zip"
    val asset = item["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == name } ?: return@mapNotNull null
    val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
    if (url != "$DESKTOP_REPOSITORY/releases/download/$tag/$name") return@mapNotNull null
    val digest = asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:") ?: return@mapNotNull null
    if (!hashPattern.matches(digest)) return@mapNotNull null
    val size = asset["size"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
    if (size !in 1..2_000_000_000L) return@mapNotNull null
    AppRelease(version, tag, item["body"]?.jsonPrimitive?.contentOrNull.orEmpty(), url, name, size, "$DESKTOP_REPOSITORY/releases/tag/$tag", preview, digest)
}.sortedWith { a, b -> compareVersionNames(b.versionName, a.versionName) }

internal fun newestDesktopRelease(body: String, current: String, previews: Boolean): AppRelease? =
    desktopReleaseList(body, previews).firstOrNull { isNewerVersionName(it.versionName, current) }

internal fun trustedDesktopAsset(release: AppRelease): Boolean =
    versionPattern.matches(release.tag) && release.tag == "desktop-v${release.versionName}" &&
        release.assetName == "SeekTop-${release.versionName}-windows-x64.zip" &&
        release.downloadUrl == "$DESKTOP_REPOSITORY/releases/download/${release.tag}/${release.assetName}" && hashPattern.matches(release.sha256)

internal fun trustedUpdateConnection(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) && uri.host in setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com", "github-releases.githubusercontent.com")
}.getOrDefault(false)

internal fun sha256(file: File): String = file.inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(128 * 1024)
    while (true) {
        val size = input.read(buffer)
        if (size < 0) break
        digest.update(buffer, 0, size)
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

/** ZIP extraction never follows links or accepts Windows aliases, ADS paths, or duplicate names. */
internal suspend fun extractDesktopUpdate(archive: File, destination: File, version: String) {
    check(destination.isDirectory && destination.listFiles().orEmpty().isEmpty())
    val root = destination.toPath().toAbsolutePath().normalize()
    val names = HashSet<String>()
    var expanded = 0L
    ZipFile(archive).use { zip ->
        val entries = zip.entries().toList()
        require(entries.size <= 100_000)
        entries.forEach { entry ->
            currentCoroutineContext().ensureActive()
            val name = entry.name
            require(name.startsWith("SeekTop/") && !name.contains('\\') && !name.contains(':'))
            val relative = name.removePrefix("SeekTop/").trimEnd('/')
            if (relative.isEmpty()) return@forEach
            val parts = relative.split('/')
            require(parts.none { it.isEmpty() || it == "." || it == ".." || it.endsWith(' ') || it.endsWith('.') || it.substringBefore('.').uppercase() in setOf("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9") })
            require(names.add(relative.lowercase()))
            val target = root.resolve(relative).normalize()
            require(target.startsWith(root))
            if (entry.isDirectory) {
                java.nio.file.Files.createDirectories(target)
                return@forEach
            }
            require(entry.size in 0..1_000_000_000L)
            expanded += entry.size
            require(expanded <= 4_000_000_000L)
            java.nio.file.Files.createDirectories(target.parent)
            zip.getInputStream(entry).use { input ->
                java.nio.file.Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= entry.size)
                        output.write(buffer, 0, count)
                    }
                    require(written == entry.size)
                }
            }
        }
    }
    require(File(destination, "SeekTop.exe").inputStream().use { it.read() == 'M'.code && it.read() == 'Z'.code })
    require(File(destination, "runtime/release").isFile)
    require(File(destination, "app/resources/SeekTopUpdater.exe").isFile)
    require(File(destination, "app/SeekTop.cfg").readText().lineSequence().any { it.trim() == "java-options=-Djpackage.app-version=$version" })
}
