package io.github.nodyssey.desktop

import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.plaza.core.update.AppRelease
import io.github.plaza.core.update.AppUpdateException
import io.github.plaza.core.update.AppUpdateState
import io.github.plaza.core.update.InstallFailure
import io.github.plaza.core.update.InstallOutcome
import io.github.plaza.core.update.ReleaseNote
import io.github.plaza.core.update.UpdateCheck
import io.github.plaza.core.update.UpdateCheckRecord
import io.github.plaza.core.update.UpdateDownload
import io.github.plaza.core.update.UpdateFailure
import io.github.plaza.core.update.isNewerVersionName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

internal interface DesktopUpdateTransport {
    suspend fun releases(): String
    suspend fun download(release: AppRelease, output: File, progress: (Long) -> Unit)
}

internal class GitHubDesktopUpdateTransport : DesktopUpdateTransport {
    private fun connection(url: String): HttpURLConnection {
        var next = url
        repeat(6) {
            require(trustedUpdateConnection(next))
            val connection = URI(next).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "SeekTop-Desktop-Updater")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val status = connection.responseCode
            if (status in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                next = URI(next).resolve(requireNotNull(location)).toString()
            } else {
                if (status != 200) {
                    connection.disconnect()
                    throw AppUpdateException(UpdateFailure.Server(status))
                }
                return connection
            }
        }
        throw AppUpdateException(UpdateFailure.Unreadable)
    }

    override suspend fun releases(): String = withContext(Dispatchers.IO) {
        val connection = connection(DESKTOP_RELEASES_API)
        try {
            connection.inputStream.use { input ->
                val bytes = input.readNBytes(2_000_001)
                require(bytes.size <= 2_000_000)
                bytes.toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(release: AppRelease, output: File, progress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        require(trustedDesktopAsset(release))
        val connection = connection(release.downloadUrl)
        try {
            val declared = connection.contentLengthLong
            if (declared >= 0 && declared != release.sizeBytes) throw AppUpdateException(UpdateFailure.Checksum)
            connection.inputStream.use { input ->
                output.outputStream().use { file ->
                    val buffer = ByteArray(128 * 1024)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        currentCoroutineContext().ensureActive()
                        written += count
                        if (written > release.sizeBytes) throw AppUpdateException(UpdateFailure.Checksum)
                        file.write(buffer, 0, count)
                        progress(written)
                    }
                    if (written != release.sizeBytes) throw AppUpdateException(UpdateFailure.Checksum)
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

internal class DesktopUpdateRepository(
    private val settings: SettingsRepository,
    private val currentVersion: String,
    private val directory: File,
    private val scope: CoroutineScope,
    private val installer: DesktopUpdateApplier,
    private val transport: DesktopUpdateTransport = GitHubDesktopUpdateTransport(),
) : AppUpdateRepository {
    private val mutableState = MutableStateFlow(AppUpdateState())
    override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
    private val reminder = MutableStateFlow<AppRelease?>(null)
    override val launchReminder: StateFlow<AppRelease?> = reminder.asStateFlow()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var lastList: List<AppRelease>? = null
    private var listAt = 0L
    private var listPreview = false

    private suspend fun checkNow(force: Boolean) {
        val preview = settings.devChannelEnabled()
        val cached = settings.updateCheckRecord()
        val cachedRelease = cached.release
        val fresh = System.currentTimeMillis() - cached.checkedAtMillis in 0 until 6 * 60 * 60 * 1000L
        val valid = cachedRelease == null || trustedDesktopAsset(cachedRelease)
        val release = if (!force && fresh && valid && cached.devChannel == preview) {
            cachedRelease
        } else {
            val list = desktopReleaseList(transport.releases(), preview)
            lastList = list
            listAt = System.currentTimeMillis()
            listPreview = preview
            list.firstOrNull { isNewerVersionName(it.versionName, currentVersion) }.also {
                settings.setUpdateCheckRecord(UpdateCheckRecord(System.currentTimeMillis(), it, preview))
            }
        }
        mutableState.update { it.copy(check = if (release != null && isNewerVersionName(release.versionName, currentVersion)) UpdateCheck.Available(release) else UpdateCheck.UpToDate) }
    }

    private fun failure(error: Exception): UpdateFailure = when (error) {
        is AppUpdateException -> error.failure
        is java.nio.file.AccessDeniedException -> UpdateFailure.Storage
        is IllegalArgumentException -> UpdateFailure.Unreadable
        is kotlinx.serialization.SerializationException -> UpdateFailure.Unreadable
        else -> UpdateFailure.Network
    }

    private fun checkInternal(force: Boolean, atLaunch: Boolean) {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        checkJob = scope.launch(Dispatchers.IO) {
            if (atLaunch && !settings.settings.first().updateCheckOnLaunch) return@launch
            mutableState.update { it.copy(check = UpdateCheck.Checking) }
            try {
                checkNow(force)
                val available = state.value.available
                if (atLaunch && available != null && settings.postponedUpdateVersion() != available.versionName) reminder.value = available
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(check = UpdateCheck.Failed(failure(error))) }
            }
        }
    }

    override fun check(force: Boolean) = checkInternal(force, false)
    override fun checkOnLaunch() = checkInternal(false, true)
    override fun acceptLaunchReminder() {
        reminder.value = null
        download()
    }
    override fun postponeLaunchReminder() {
        val release = reminder.value ?: return
        reminder.value = null
        scope.launch { settings.setPostponedUpdateVersion(release.versionName) }
    }

    override fun download() {
        val release = state.value.available ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch(Dispatchers.IO) {
            val transaction = File(directory, UUID.randomUUID().toString()).apply { mkdirs() }
            val archive = File(transaction, "bundle.zip")
            var keepArchive = false
            mutableState.update { it.copy(download = UpdateDownload.Running(0, release.sizeBytes), installFailure = null) }
            try {
                transport.download(release, archive) { count -> mutableState.update { it.copy(download = UpdateDownload.Running(count, release.sizeBytes)) } }
                currentCoroutineContext().ensureActive()
                if (archive.length() != release.sizeBytes || sha256(archive) != release.sha256) throw AppUpdateException(UpdateFailure.Checksum)
                installer.register(archive, release)
                keepArchive = true
                mutableState.update { it.copy(download = UpdateDownload.Ready(archive.absolutePath, release.versionName)) }
                if (!installer.install(archive.absolutePath)) onInstallOutcome(InstallOutcome.Failed(InstallFailure.UNKNOWN))
            } catch (cancelled: CancellationException) {
                keepArchive = false
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(download = UpdateDownload.Failed(failure(error))) }
            } finally {
                if (!keepArchive) {
                    // Only this attempt's uniquely named partial file is removed; no recursive deletion.
                    archive.delete()
                    transaction.delete()
                }
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        mutableState.update { it.copy(download = UpdateDownload.Idle) }
    }

    override suspend fun releaseNotes(force: Boolean): List<ReleaseNote> = withContext(Dispatchers.IO) {
        val preview = settings.devChannelEnabled()
        val list = if (!force && lastList != null && listPreview == preview && System.currentTimeMillis() - listAt < 6 * 60 * 60 * 1000L) {
            checkNotNull(lastList)
        } else {
            desktopReleaseList(transport.releases(), preview).also {
                lastList = it
                listPreview = preview
                listAt = System.currentTimeMillis()
            }
        }
        list.map { ReleaseNote(it.versionName, it.tag, it.notes, "", it.preRelease, it.htmlUrl) }
    }

    override fun onInstallOutcome(outcome: InstallOutcome) {
        mutableState.update { it.copy(installFailure = (outcome as? InstallOutcome.Failed)?.failure) }
    }
}
