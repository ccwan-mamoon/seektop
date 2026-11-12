package io.github.nodyssey.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.nodyssey.core.NodeSeekSite
import io.github.nodyssey.core.net.DynamicSignTransport
import io.github.nodyssey.core.net.NodeSeekJsonClient
import io.github.nodyssey.data.AppCacheStore
import io.github.nodyssey.data.AssetsRepository
import io.github.nodyssey.data.AwardRepository
import io.github.nodyssey.data.CategoryRepository
import io.github.nodyssey.data.CollectedPostMetaStore
import io.github.nodyssey.data.CommunityRepository
import io.github.nodyssey.data.CreditRepository
import io.github.nodyssey.data.FollowRepository
import io.github.nodyssey.data.MessageRepository
import io.github.nodyssey.data.NetworkAssetsRepository
import io.github.nodyssey.data.NetworkAwardRepository
import io.github.nodyssey.data.NetworkCommunityRepository
import io.github.nodyssey.data.NetworkCreditRepository
import io.github.nodyssey.data.NetworkFollowRepository
import io.github.nodyssey.data.NetworkMessageRepository
import io.github.nodyssey.data.NetworkPostDataSource
import io.github.nodyssey.data.NetworkProfileRepository
import io.github.nodyssey.data.NetworkRulingRepository
import io.github.nodyssey.data.NetworkSearchRepository
import io.github.nodyssey.data.NetworkStardustRepository
import io.github.nodyssey.data.NetworkTermsRepository
import io.github.nodyssey.data.NetworkUserSpaceRepository
import io.github.nodyssey.data.NetworkVoteRepository
import io.github.nodyssey.data.NotificationRepository
import io.github.nodyssey.data.OfflineFirstPostRepository
import io.github.nodyssey.data.OfflineLibrary
import io.github.nodyssey.data.OfflineThreadReader
import io.github.nodyssey.data.PostCollectionWriter
import io.github.nodyssey.data.PostReactionWriter
import io.github.nodyssey.data.PostRepository
import io.github.nodyssey.data.ProfileRepository
import io.github.nodyssey.data.ReadingPositionStore
import io.github.nodyssey.data.RoomCollectedPostMetaStore
import io.github.nodyssey.data.RoomReadingPositionStore
import io.github.nodyssey.data.RulingRepository
import io.github.nodyssey.data.SearchRepository
import io.github.nodyssey.data.StardustRepository
import io.github.nodyssey.data.TermsRepository
import io.github.nodyssey.data.TitleKeywordStore
import io.github.nodyssey.data.TrackedThreadStore
import io.github.nodyssey.data.UserNoteStore
import io.github.nodyssey.data.UserSpaceRepository
import io.github.nodyssey.data.VoteRepository
import io.github.nodyssey.data.account.AccountSettingsRepository
import io.github.nodyssey.data.account.NetworkAccountSettingsRepository
import io.github.nodyssey.data.composer.CommentComposerRepository
import io.github.nodyssey.data.composer.DefaultCommentComposerRepository
import io.github.nodyssey.data.composer.DefaultPostComposerRepository
import io.github.nodyssey.data.composer.DefaultPostEditor
import io.github.nodyssey.data.composer.ImageHostUploader
import io.github.nodyssey.data.composer.ImagePreparer
import io.github.nodyssey.data.composer.ImageUploader
import io.github.nodyssey.data.composer.PostComposerRepository
import io.github.nodyssey.data.composer.PostEditor
import io.github.nodyssey.data.diagnostics.NetworkDiagnostics
import io.github.nodyssey.data.dns.DohSupport
import io.github.nodyssey.data.imagehost.DataStoreImageHostSettings
import io.github.nodyssey.data.imagehost.DefaultImageHostRepository
import io.github.nodyssey.data.imagehost.ImageHostRepository
import io.github.nodyssey.data.local.createNodeSeekDatabase
import io.github.nodyssey.data.offline.OfflineDownloads
import io.github.nodyssey.data.offline.OfflineImageSource
import io.github.nodyssey.data.offline.OfflineSettingsStore
import io.github.nodyssey.data.offline.OfflineWorkScheduler
import io.github.nodyssey.data.offline.RoomOfflineLibrary
import io.github.nodyssey.data.proxy.DataStoreProxySettings
import io.github.nodyssey.data.proxy.ProxyConnectionFailure
import io.github.nodyssey.data.proxy.ProxyConnectionTester
import io.github.nodyssey.data.proxy.ProxySettings
import io.github.nodyssey.data.security.PlaintextSecretCipher
import io.github.nodyssey.data.session.AccountSignOut
import io.github.nodyssey.data.session.DefaultAccountSignOut
import io.github.nodyssey.data.session.NodeSeekSignInRepository
import io.github.nodyssey.data.session.SessionRepository
import io.github.nodyssey.data.session.SignInRepository
import io.github.nodyssey.data.settings.ReportCompareStore
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.sticker.GitHubStickerSource
import io.github.nodyssey.data.sticker.StickerLibrary
import io.github.nodyssey.data.update.AppUpdateRepository
import io.github.nodyssey.di.AppContainer
import io.github.plaza.core.AppClock
import io.github.plaza.core.AppDispatchers
import io.github.plaza.core.AppVersion
import io.github.plaza.core.crash.CrashReportStore
import io.github.plaza.core.crash.NoCrashReports
import io.github.plaza.core.net.CookieScrubbingTransport
import io.github.plaza.core.net.HttpRequest
import io.github.plaza.core.net.RetryingTransport
import io.github.plaza.core.net.SessionCookies
import io.github.plaza.core.net.SiteHtmlClient
import io.github.plaza.core.net.UserAgent
import io.github.plaza.core.runCatchingExceptCancellation
import io.github.plaza.core.update.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import java.io.File

class DesktopAppContainer(val browser: DesktopBrowser, val directory: File) : AppContainer {
    override val dispatchers = AppDispatchers()
    override val clock: AppClock = AppClock.System
    override val userAgent = UserAgent(browser.userAgent, true)
    private val appScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val database by lazy { createNodeSeekDatabase(directory) }
    private val sessionCookies by lazy { SessionCookies(NodeSeekSite.CONFIG, browser) }
    private val transport by lazy { RetryingTransport(DynamicSignTransport(CookieScrubbingTransport(browser.transport(true), sessionCookies), userAgent.value)) }
    private val htmlClient by lazy { SiteHtmlClient(transport, dispatchers, NodeSeekSite.CONFIG) }
    private val jsonClient by lazy { NodeSeekJsonClient(transport, dispatchers) }
    private val remotePosts by lazy { NetworkPostDataSource(htmlClient, dispatchers, clock) }
    private val secretCipher = PlaintextSecretCipher()
    private fun preferences(name: String) = PreferenceDataStoreFactory.createWithPath(produceFile = { File(directory, "$name.preferences_pb").absolutePath.toPath() })
    private fun secretStore(name: String) = preferences(name)
    override val proxySettings: ProxySettings by lazy { DataStoreProxySettings(secretStore("proxy"), secretCipher) }
    init {
        appScope.launch { proxySettings.config.collect { browser.applyProxy(it) } }
    }
    override val proxyConnectionTester = object : ProxyConnectionTester {
        override suspend fun test(): Result<Unit> = runCatchingExceptCancellation {
            val r = transport.execute(HttpRequest(NodeSeekSite.BASE_URL))
            check(r.isSuccessful) { "HTTP ${r.code}" }
        }
        override fun classify(failure: Throwable) = ProxyConnectionFailure(ProxyConnectionFailure.Kind.OTHER, failure.javaClass.simpleName)
    }
    override val doh: DohSupport? = null
    override val networkDiagnostics: NetworkDiagnostics? = null
    private val scheduler = object : OfflineWorkScheduler {
        private var job: Job? = null
        override fun startDrain(wifiOnly: Boolean, restart: Boolean) {
            if (job?.isActive == true && !restart) return
            job?.cancel()
            job = appScope.launch { (offlineLibrary as OfflineDownloads).drainQueue() }
        }
        override fun ensureMaintenance() {
            appScope.launch { (offlineLibrary as OfflineDownloads).sweepExpired() }
        }
    }
    override val postRepository: PostRepository by lazy {
        OfflineFirstPostRepository(
            database,
            remotePosts,
            clock,
            PostReactionWriter(jsonClient),
            settingsRepository.showBlockedContent,
            PostCollectionWriter(jsonClient),
            settingsRepository.settings.map { it.readHistoryLimit }.distinctUntilChanged(),
            offlineLibrary as? OfflineThreadReader,
            collectedPostMetaStore,
        )
    }

    override val collectedPostMetaStore: CollectedPostMetaStore by lazy {
        RoomCollectedPostMetaStore(database.collectedPostMetaDao(), clock)
    }

    override val userNoteStore: UserNoteStore by lazy { UserNoteStore(database.userNoteDao(), clock) }
    override val titleKeywordStore: TitleKeywordStore by lazy { TitleKeywordStore(database.titleKeywordDao(), clock) }
    override val trackedThreadStore: TrackedThreadStore by lazy { TrackedThreadStore(database.trackedThreadDao(), clock) }

    override val readingPositionStore: ReadingPositionStore by lazy {
        RoomReadingPositionStore(database.readingPositionDao(), clock)
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(jsonClient, database.boardDao(), clock)
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            dataStore = preferences("settings"),
            devChannelDefault = false,
        )
    }

    override val reportCompareStore: ReportCompareStore get() = settingsRepository.reportCompare

    override val notificationRepository: NotificationRepository by lazy { NotificationRepository(jsonClient) }

    override val messageRepository: MessageRepository by lazy {
        NetworkMessageRepository(jsonClient) {
            runCatchingExceptCancellation { profileRepository.profile().uid }.getOrNull()
        }
    }

    override val profileRepository: ProfileRepository by lazy {
        NetworkProfileRepository(
            htmlSource = htmlClient,
            jsonSource = jsonClient,
            profileDao = database.profileDao(),
            currentSessionFingerprint = { sessionRepository.state.value.fingerprint },
            isSignedIn = { sessionRepository.state.value.isSignedIn },
            clock = clock,
        )
    }

    override val searchRepository: SearchRepository by lazy {
        NetworkSearchRepository(jsonClient, htmlClient, dispatchers)
    }

    override val postComposerRepository: PostComposerRepository by lazy {
        DefaultPostComposerRepository(preferences("post-composer"), transport, dispatchers, clock)
    }

    override val accountSettingsRepository: AccountSettingsRepository by lazy {
        NetworkAccountSettingsRepository(
            jsonApi = jsonClient,
            multipartWriteSource = jsonClient,
            htmlSource = htmlClient,
            profileRepository = profileRepository,
        )
    }

    override val userSpaceRepository: UserSpaceRepository by lazy {
        NetworkUserSpaceRepository(jsonClient, dispatchers)
    }

    override val offlineLibrary: OfflineLibrary by lazy {
        RoomOfflineLibrary(
            database.offlineDao(), remotePosts, DesktopOfflineFiles(File(directory, "offline")),
            object : OfflineImageSource {
                override suspend fun fetch(url: String, maxBytes: Long): ByteArray? = browser.bytes(url, maxBytes)
            },
            OfflineSettingsStore(preferences("offline")), collectedPostMetaStore, scheduler, clock, dispatchers,
        )
    }

    override val assetsRepository: AssetsRepository by lazy {
        NetworkAssetsRepository(profileRepository, creditRepository, jsonClient, dispatchers, clock)
    }

    override val creditRepository: CreditRepository by lazy { NetworkCreditRepository(jsonClient, dispatchers) }

    override val stardustRepository: StardustRepository by lazy { NetworkStardustRepository(jsonClient, dispatchers) }

    override val awardRepository: AwardRepository by lazy { NetworkAwardRepository(htmlClient, dispatchers) }

    override val communityRepository: CommunityRepository by lazy { NetworkCommunityRepository(htmlClient, dispatchers) }

    override val termsRepository: TermsRepository by lazy { NetworkTermsRepository(htmlClient) }

    override val followRepository: FollowRepository by lazy {
        NetworkFollowRepository(jsonClient, dispatchers) { sessionRepository.state.value.isSignedIn }
    }

    override val voteRepository: VoteRepository by lazy { NetworkVoteRepository(jsonClient) }

    override val rulingRepository: RulingRepository by lazy { NetworkRulingRepository(jsonClient, dispatchers) }

    override val commentComposerRepository: CommentComposerRepository by lazy {
        DefaultCommentComposerRepository(
            preferences("comment-composer"),
            transport,
            dispatchers,
            clock,
            postRepository::noteOwnReplyPublished,
        )
    }

    override val postEditor: PostEditor by lazy {
        DefaultPostEditor(
            remote = remotePosts,
            posts = postComposerRepository,
            comments = commentComposerRepository,
            threads = postRepository::extendThread,
        )
    }

    override val imageHostRepository: ImageHostRepository by lazy {
        DefaultImageHostRepository(
            settings = DataStoreImageHostSettings(secretStore("imagehost"), secretCipher),
            http = browser.transport(false),
            dispatchers = dispatchers,
        )
    }

    override val imageUploader: ImageUploader by lazy {
        ImageHostUploader(repository = imageHostRepository, preparer = imagePreparer)
    }

    private val imagePreparer: ImagePreparer by lazy { DesktopImagePreparer(dispatchers) }

    override val stickerLibrary: StickerLibrary by lazy {
        val thirdParty = browser.transport(false)
        StickerLibrary(
            dao = database.stickerDao(),
            github = GitHubStickerSource(thirdParty, dispatchers),
            settingsStore = settingsRepository.stickerSettings,
            imageHost = imageHostRepository,
            preparer = imagePreparer,
            http = thirdParty,
            dispatchers = dispatchers,
            clock = clock,
            scope = appScope,
        )
    }

    override val sessionRepository: SessionRepository by lazy { SessionRepository(sessionCookies) }

    override val accountSignOut: AccountSignOut by lazy {
        DefaultAccountSignOut(
            posts = postRepository,
            profiles = profileRepository,
            offline = offlineLibrary,
            postDrafts = postComposerRepository,
            commentDrafts = commentComposerRepository,
            settings = settingsRepository,
            session = sessionRepository,
        )
    }

    override val signInRepository: SignInRepository by lazy { NodeSeekSignInRepository(jsonClient) }

    override val appVersion: AppVersion by lazy { AppVersion(System.getProperty("jpackage.app-version") ?: "development", 4, "SeekTop · 寻桌") }

    override val appUpdateRepository: AppUpdateRepository get() = NoAppUpdates

    override val apkInstaller: ApkInstaller get() = NoApkInstaller

    // Not wired yet, not can't-be-wired: capture here means `setUnhandledExceptionHook` for the
    // Kotlin side plus something for NSException/signal crashes, and neither exists today. The
    // 关于 screen reads this as "no record" and shows no 导出 row.
    override val crashReportStore: CrashReportStore get() = NoCrashReports

    override val appCacheStore = object : AppCacheStore {
        override suspend fun sizeBytes(): Long = browser.cacheSize()
        override suspend fun clear() = browser.clearCache()
        override suspend fun evictImage(url: String) = browser.clearCache()
    }
    fun close() {
        appScope.cancel()
        database.close()
        browser.close()
    }
}
