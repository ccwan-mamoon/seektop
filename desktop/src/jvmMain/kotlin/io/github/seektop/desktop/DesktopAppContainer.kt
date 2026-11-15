package io.github.seektop.desktop

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.seektop.core.NodeSeekSite
import io.github.seektop.core.net.DynamicSignTransport
import io.github.seektop.core.net.NodeSeekJsonClient
import io.github.seektop.data.AppCacheStore
import io.github.seektop.data.AssetsRepository
import io.github.seektop.data.AwardRepository
import io.github.seektop.data.CategoryRepository
import io.github.seektop.data.CollectedPostMetaStore
import io.github.seektop.data.CommunityRepository
import io.github.seektop.data.CreditRepository
import io.github.seektop.data.FollowRepository
import io.github.seektop.data.MessageRepository
import io.github.seektop.data.NetworkAssetsRepository
import io.github.seektop.data.NetworkAwardRepository
import io.github.seektop.data.NetworkCommunityRepository
import io.github.seektop.data.NetworkCreditRepository
import io.github.seektop.data.NetworkFollowRepository
import io.github.seektop.data.NetworkMessageRepository
import io.github.seektop.data.NetworkPostDataSource
import io.github.seektop.data.NetworkProfileRepository
import io.github.seektop.data.NetworkRulingRepository
import io.github.seektop.data.NetworkSearchRepository
import io.github.seektop.data.NetworkStardustRepository
import io.github.seektop.data.NetworkTermsRepository
import io.github.seektop.data.NetworkUserSpaceRepository
import io.github.seektop.data.NetworkVoteRepository
import io.github.seektop.data.NotificationRepository
import io.github.seektop.data.OfflineFirstPostRepository
import io.github.seektop.data.OfflineLibrary
import io.github.seektop.data.OfflineThreadReader
import io.github.seektop.data.PostCollectionWriter
import io.github.seektop.data.PostReactionWriter
import io.github.seektop.data.PostRepository
import io.github.seektop.data.ProfileRepository
import io.github.seektop.data.ReadingPositionStore
import io.github.seektop.data.RoomCollectedPostMetaStore
import io.github.seektop.data.RoomReadingPositionStore
import io.github.seektop.data.RulingRepository
import io.github.seektop.data.SearchRepository
import io.github.seektop.data.StardustRepository
import io.github.seektop.data.TermsRepository
import io.github.seektop.data.TitleKeywordStore
import io.github.seektop.data.TrackedThreadStore
import io.github.seektop.data.UserNoteStore
import io.github.seektop.data.UserSpaceRepository
import io.github.seektop.data.VoteRepository
import io.github.seektop.data.account.AccountSettingsRepository
import io.github.seektop.data.account.NetworkAccountSettingsRepository
import io.github.seektop.data.composer.CommentComposerRepository
import io.github.seektop.data.composer.DefaultCommentComposerRepository
import io.github.seektop.data.composer.DefaultPostComposerRepository
import io.github.seektop.data.composer.DefaultPostEditor
import io.github.seektop.data.composer.ImageHostUploader
import io.github.seektop.data.composer.ImagePreparer
import io.github.seektop.data.composer.ImageUploader
import io.github.seektop.data.composer.PostComposerRepository
import io.github.seektop.data.composer.PostEditor
import io.github.seektop.data.diagnostics.NetworkDiagnostics
import io.github.seektop.data.dns.DohSupport
import io.github.seektop.data.imagehost.DataStoreImageHostSettings
import io.github.seektop.data.imagehost.DefaultImageHostRepository
import io.github.seektop.data.imagehost.ImageHostRepository
import io.github.seektop.data.local.createNodeSeekDatabase
import io.github.seektop.data.offline.OfflineDownloads
import io.github.seektop.data.offline.OfflineImageSource
import io.github.seektop.data.offline.OfflineSettingsStore
import io.github.seektop.data.offline.OfflineWorkScheduler
import io.github.seektop.data.offline.RoomOfflineLibrary
import io.github.seektop.data.proxy.DataStoreProxySettings
import io.github.seektop.data.proxy.ProxyConnectionFailure
import io.github.seektop.data.proxy.ProxyConnectionTester
import io.github.seektop.data.proxy.ProxySettings
import io.github.seektop.data.security.PlaintextSecretCipher
import io.github.seektop.data.session.AccountSignOut
import io.github.seektop.data.session.DefaultAccountSignOut
import io.github.seektop.data.session.NodeSeekSignInRepository
import io.github.seektop.data.session.SessionRepository
import io.github.seektop.data.session.SignInRepository
import io.github.seektop.data.settings.ReportCompareStore
import io.github.seektop.data.settings.SettingsRepository
import io.github.seektop.data.sticker.GitHubStickerSource
import io.github.seektop.data.sticker.StickerLibrary
import io.github.seektop.data.update.AppUpdateRepository
import io.github.seektop.di.AppContainer
import io.github.seektop.foundation.core.AppClock
import io.github.seektop.foundation.core.AppDispatchers
import io.github.seektop.foundation.core.AppVersion
import io.github.seektop.foundation.core.crash.CrashReportStore
import io.github.seektop.foundation.core.crash.NoCrashReports
import io.github.seektop.foundation.core.net.CookieScrubbingTransport
import io.github.seektop.foundation.core.net.HttpRequest
import io.github.seektop.foundation.core.net.RetryingTransport
import io.github.seektop.foundation.core.net.SessionCookies
import io.github.seektop.foundation.core.net.SiteHtmlClient
import io.github.seektop.foundation.core.net.UserAgent
import io.github.seektop.foundation.core.runCatchingExceptCancellation
import io.github.seektop.foundation.core.update.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import java.io.File

class DesktopAppContainer(val browser: DesktopBrowser, val directory: File) : AppContainer {
    override val dispatchers = AppDispatchers()
    override val clock: AppClock = AppClock.System
    override val userAgent = UserAgent(browser.userAgent, true)
    private val appScope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val desktopToolPlugins = DesktopToolPlugins(File(directory, "plugins"), appScope, remoteDownload = { url, limit ->
        browser.applyProxy(proxySettings.config.first())
        browser.downloadPlugin(url, limit)
    })
    override val toolPlugins: io.github.seektop.plugins.ToolPluginManager = desktopToolPlugins
    override val externalLinkRouting: io.github.seektop.links.ExternalLinkRouting = DesktopExternalLinks(File(System.getProperty("compose.application.resources.dir") ?: "desktop/resources/windows", "SeekTopLinkRouter.exe"))
    override val postAnalysis: io.github.seektop.ai.PostAnalysisService = DesktopPostAnalysis(browser) { browser.applyProxy(proxySettings.config.first()) }
    private val databaseDelegate = lazy { createNodeSeekDatabase(directory) }
    private val database by databaseDelegate
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

    internal val backgroundAlerts by lazy {
        io.github.seektop.data.BackgroundAlertChecker(
            loadList = { sort, page -> remotePosts.loadList(categorySlug = null, page = page, sort = sort).posts },
            keywords = titleKeywordStore,
            tracked = trackedThreadStore,
            settings = settingsRepository,
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

    override val appVersion: AppVersion by lazy { AppVersion(System.getProperty("jpackage.app-version") ?: "development", 6, "SeekTop · 寻桌") }

    internal val desktopUpdateInstaller by lazy {
        DesktopUpdateInstaller(File(directory, "updates"), System.getProperty("compose.application.resources.dir")?.let(::File), appVersion.name)
    }
    override val appUpdateRepository: AppUpdateRepository by lazy {
        DesktopUpdateRepository(settingsRepository, appVersion.name, File(directory, "updates"), appScope, desktopUpdateInstaller, BrowserDesktopUpdateTransport(browser, proxySettings))
    }

    override val apkInstaller: ApkInstaller get() = desktopUpdateInstaller

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
        desktopToolPlugins.close()
        appScope.cancel()
        try {
            if (databaseDelegate.isInitialized()) database.close()
        } finally {
            browser.close()
        }
    }
}
