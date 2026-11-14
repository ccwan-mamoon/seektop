package io.github.seektop.di

import android.content.Context
import coil3.SingletonImageLoader
import io.github.seektop.core.NodeSeekSite
import io.github.seektop.core.SeekTopRelease
import io.github.seektop.core.net.AppDns
import io.github.seektop.core.net.AppProxyAuthenticator
import io.github.seektop.core.net.AppProxySelector
import io.github.seektop.core.net.AppSocksAuthenticator
import io.github.seektop.core.net.DynamicSignTransport
import io.github.seektop.core.net.LiveProxyConfig
import io.github.seektop.core.net.NodeSeekJsonClient
import io.github.seektop.core.net.dnsOverHttpsResolvers
import io.github.seektop.data.AppCacheStore
import io.github.seektop.data.AssetsRepository
import io.github.seektop.data.AwardRepository
import io.github.seektop.data.BackgroundAlertChecker
import io.github.seektop.data.CategoryRepository
import io.github.seektop.data.CoilImageCaches
import io.github.seektop.data.CollectedPostMetaStore
import io.github.seektop.data.CommunityRepository
import io.github.seektop.data.CreditRepository
import io.github.seektop.data.DefaultAppCacheStore
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
import io.github.seektop.data.diagnostics.BrowserIdentities
import io.github.seektop.data.diagnostics.NetworkDiagnostics
import io.github.seektop.data.diagnostics.OkHttpNetworkDiagnostics
import io.github.seektop.data.diagnostics.SessionSummary
import io.github.seektop.data.dns.DataStoreDohSettings
import io.github.seektop.data.dns.DohCapabilities
import io.github.seektop.data.dns.DohSettings
import io.github.seektop.data.dns.DohSupport
import io.github.seektop.data.dns.OkHttpDnsResolutionTester
import io.github.seektop.data.imagehost.DataStoreImageHostSettings
import io.github.seektop.data.imagehost.DefaultImageHostRepository
import io.github.seektop.data.imagehost.ImageHostRepository
import io.github.seektop.data.local.createNodeSeekDatabase
import io.github.seektop.data.offline.AndroidOfflineFileStore
import io.github.seektop.data.offline.OfflineSettingsStore
import io.github.seektop.data.offline.OkHttpOfflineImageSource
import io.github.seektop.data.offline.RoomOfflineLibrary
import io.github.seektop.data.offline.WorkManagerOfflineScheduler
import io.github.seektop.data.proxy.DataStoreProxySettings
import io.github.seektop.data.proxy.NetworkProxyConnectionTester
import io.github.seektop.data.proxy.ProxyClientKind
import io.github.seektop.data.proxy.ProxyConnectionTester
import io.github.seektop.data.proxy.ProxySettings
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
import io.github.seektop.data.update.DefaultAppUpdateRepository
import io.github.seektop.foundation.core.AppClock
import io.github.seektop.foundation.core.AppDispatchers
import io.github.seektop.foundation.core.AppVersion
import io.github.seektop.foundation.core.crash.CrashReportStore
import io.github.seektop.foundation.core.crash.FileCrashReportStore
import io.github.seektop.foundation.core.net.BrowserHeadersInterceptor
import io.github.seektop.foundation.core.net.CookieScrubbingTransport
import io.github.seektop.foundation.core.net.CrossOriginRefererInterceptor
import io.github.seektop.foundation.core.net.OkHttpTransport
import io.github.seektop.foundation.core.net.RetryingTransport
import io.github.seektop.foundation.core.net.SessionCookies
import io.github.seektop.foundation.core.net.SiteHtmlClient
import io.github.seektop.foundation.core.net.UserAgent
import io.github.seektop.foundation.core.net.WebViewCookieJar
import io.github.seektop.foundation.core.net.WebViewCookieStore
import io.github.seektop.foundation.core.net.deviceAcceptLanguage
import io.github.seektop.foundation.core.net.resolveUserAgent
import io.github.seektop.foundation.core.readAppVersion
import io.github.seektop.foundation.core.runCatchingExceptCancellation
import io.github.seektop.foundation.core.update.ApkInstaller
import io.github.seektop.foundation.core.update.UpdateManifestSource
import io.github.seektop.foundation.core.update.isPreReleaseVersionName
import io.github.seektop.platform.AndroidApkInstaller
import io.github.seektop.platform.DefaultImagePreparer
import io.github.seektop.platform.KeystoreSecretCipher
import io.github.seektop.platform.customTabsProvider
import io.github.seektop.platform.defaultBrowser
import io.github.seektop.platform.deviceIdentity
import io.github.seektop.platform.networkSnapshot
import io.github.seektop.platform.webViewProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The members of the graph that name a type `:ui` cannot.
 *
 * The two `OkHttpClient`s are Coil's call factories, handed over in [io.github.seektop.SeekTopApp],
 * and [SessionCookies] is read off the same cookie jar the first is built with. None is reached from
 * a screen — see the note on [AppContainer] — so step D1 left them up here rather than widening the
 * common interface to a type that only exists on two of the four targets `:shared` builds for.
 */
interface AndroidAppContainer : AppContainer {
    /** What the shared cookie store says about this session. See [SessionCookies]. */
    val sessionCookies: SessionCookies
    val okHttpClient: OkHttpClient

    /**
     * What an image from anywhere other than nodeseek.com loads over — the same browser headers as
     * [okHttpClient], minus the cookie jar. See the builder for why the jar is the one thing removed.
     */
    val imageContentClient: OkHttpClient

    /** 提醒关键词 and 追踪新回复's half of the background poll. Android-only: iOS has no poll yet. */
    val backgroundAlerts: BackgroundAlertChecker
}

class DefaultAppContainer(
    context: Context,
    override val dispatchers: AppDispatchers = AppDispatchers(),
    override val clock: AppClock = AppClock.System,
) : AndroidAppContainer {
    private val appContext = context.applicationContext

    /**
     * The one store OkHttp and the sign-in WebView share, and the two things built on it: the
     * `CookieJar` OkHttp is configured with, and the session read model everything else asks.
     */
    private val cookieStore = WebViewCookieStore()

    override val sessionCookies: SessionCookies by lazy { SessionCookies(NodeSeekSite.CONFIG, cookieStore) }

    /**
     * Eager, and the only member of the graph that must be: this is where the WebView provider gets
     * initialised, and that initialisation **waits on the main thread**.
     *
     * It was a `lazy` like everything else, and the combination deadlocked the app at launch — found
     * by the R8 smoke on CI, reproduced at 420dpi, and diagnosed from the ANR trace. The shape: the
     * first worker's constructor injection resolves `offlineLibrary` on a WorkManager thread, which
     * walks `htmlClient → transport → okHttpClient → userAgent` and, holding those lazy locks, calls
     * `WebSettings.getDefaultUserAgent` — whose provider init blocks until the main thread runs a
     * task for it. The main thread meanwhile is composing the first frame, creating a ViewModel whose
     * repository walks `jsonClient → transport` and parks on the lock the worker holds. Neither side
     * can move; the launch screen stays up forever. Whether the two collide is pure timing — screen
     * density changed layout timing enough to flip it — which is why it passed on one emulator and
     * hung on another.
     *
     * Resolving here instead — in the container's constructor, on the main thread, holding no lazy
     * lock — pays the provider-init cost at a moment that cannot deadlock, and every later reader on
     * any thread gets a plain value. If another main-thread-dependent initialisation ever joins the
     * graph, it needs this same treatment, not a `lazy`.
     */
    override val userAgent: UserAgent = resolveUserAgent(appContext, NodeSeekSite.CONFIG)

    /**
     * The other header a browser always sends and OkHttp never does — see [deviceAcceptLanguage].
     *
     * Without it a Cloudflare-fronted image host answered the app with a challenge and a 403 while
     * serving every browser on the same connection, and post-879848's attachment failed for
     * everyone using the app.
     */
    private val acceptLanguage: String by lazy { deviceAcceptLanguage() }

    /**
     * Lives as long as the process, and has two tenants: the update download, which has to survive
     * the 关于 screen being left (a ViewModel scope would cancel it the moment the user backs out to
     * read what changed), and [liveProxyConfig], which has to survive every screen.
     */
    private val appScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val proxySettings: ProxySettings by lazy {
        DataStoreProxySettings(appContext.proxyDataStore, KeystoreSecretCipher())
    }

    /**
     * One pool behind all three clients.
     *
     * Pooling is per-host and per-route, so nothing crosses between them — but owning it here means
     * [liveProxyConfig] can empty it when the proxy setting changes without reaching into a client
     * that may not have been built yet.
     */
    private val connectionPool = ConnectionPool()

    /**
     * See [LiveProxyConfig] — the synchronous read the clients' routing needs on their own threads.
     *
     * Evicting the pool on a change is what makes an edit take effect immediately: the selector below
     * only decides where new connections go, and a request that lands on a connection opened before
     * the change would otherwise still travel the old way.
     */
    private val liveProxyConfig: LiveProxyConfig by lazy {
        LiveProxyConfig(appScope, proxySettings.config, onRoutingChanged = connectionPool::evictAll)
    }

    private val dohSettings: DohSettings by lazy { DataStoreDohSettings(appContext.dnsDataStore) }

    /**
     * The client 加密 DNS's own queries travel on, and the one client in the graph that must not use
     * [appDns].
     *
     * It is built here rather than derived from [okHttpClient] for three reasons, and each of them is
     * a way the shared one would be wrong: its dispatcher is its own, because a lookup blocks the
     * thread that asked for it and a query queued behind the calls waiting on it never returns; it
     * carries neither the forum's cookie jar nor its `Referer`, because the resolver is a third party
     * with no part in that session; and it has a cache, which is what turns a DoH server's TTL into
     * lookups that cost nothing. `DnsOverHttps` replaces this client's own resolver with the
     * bootstrap one, so there is no recursion to avoid beyond that.
     *
     * The proxy is shared, on the same reasoning as the image host's: a user routing the forum
     * through a node is usually on a network that reaches a DoH endpoint no better.
     */
    private val dohClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(connectionPool)
            .proxySelector(AppProxySelector(liveProxyConfig, ProxyClientKind.FORUM))
            .proxyAuthenticator(AppProxyAuthenticator(liveProxyConfig))
            // Small on purpose: a DNS answer is a few hundred bytes and the TTL throws it away
            // shortly anyway. 清除缓存 may delete this directory out from under the running cache,
            // which OkHttp recovers from by rebuilding its journal — the same deal `updates` has.
            .cache(Cache(File(appContext.cacheDir, "doh"), DOH_CACHE_BYTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * The resolver behind all three clients below.
     *
     * Every one of them, rather than the forum's alone: an app whose posts arrive over a DoH-resolved
     * address while its avatars still ask the network's resolver is an app where the setting means
     * something different on every screen — the same argument `ProxyScope.EVERYTHING` is the default
     * for, minus the reason to opt out of it, since resolving a name costs nothing wherever it goes.
     */
    private val appDns: AppDns by lazy {
        AppDns(
            scope = appScope,
            config = dohSettings.config,
            resolvers = dnsOverHttpsResolvers { dohClient },
            // A connection already open to an address the old resolver handed out would otherwise
            // keep carrying requests, and the change would look like it had done nothing.
            onResolverChanged = connectionPool::evictAll,
            clock = clock,
        )
    }

    /**
     * Every capability is yes here, and the reason is the same one: on this platform the app *is*
     * the resolver. `AppDns` decides which record types to ask for, which server to ask next and what
     * to do when no answer comes — none of which is a question `NSURLSession` lets its side ask.
     */
    override val doh: DohSupport by lazy {
        DohSupport(
            settings = dohSettings,
            tester = OkHttpDnsResolutionTester(
                dns = { appDns },
                host = NodeSeekSite.BASE_URL.toHttpUrl().host,
                dispatchers = dispatchers,
                clock = clock,
            ),
            capabilities = DohCapabilities(
                canChooseRecordTypes = true,
                canFallBackToSystem = true,
                triesServersInOrder = true,
            ),
        )
    }

    /**
     * 网络自检, wired to the two clients whose numbers are worth comparing.
     *
     * The forum's client and GitHub's, because the pair is what makes either number mean anything —
     * see `ProbeTarget`. [gitHubClient] is deliberately the one used for the second probe rather than
     * a client built here: it is routed as `THIRD_PARTY`, so a reader who chose `FORUM_ONLY` sees a
     * proxied path and a direct one measured side by side, which is exactly the comparison that
     * setting creates and the one nothing else in the app displays.
     */
    override val networkDiagnostics: NetworkDiagnostics by lazy {
        OkHttpNetworkDiagnostics(
            forumClient = { okHttpClient },
            updatesClient = { gitHubClient },
            updatesUrl = SeekTopRelease.UPDATES_BASE_URL + UpdateManifestSource.CHANGELOG_MANIFEST,
            device = deviceIdentity(),
            appVersion = appVersion.name,
            proxyConfig = proxySettings.config,
            dohConfig = dohSettings.config,
            network = appContext::networkSnapshot,
            browsers = {
                BrowserIdentities(
                    customTabs = appContext.customTabsProvider(),
                    default = appContext.defaultBrowser(),
                )
            },
            // Names, never values — see [SessionSummary]. `peek` rather than the published state:
            // this screen reports what the jar says right now, and publishing from a diagnostics
            // read would invalidate caches and reload the feed behind it.
            session = {
                val state = sessionRepository.peek()
                SessionSummary(
                    signedIn = state.isSignedIn,
                    hasClearance = state.hasClearance,
                    cookieNames = sessionCookies.cookieNames(),
                    webView = appContext.webViewProvider(),
                )
            },
            dispatchers = dispatchers,
        )
    }

    override val okHttpClient: OkHttpClient by lazy {
        // SOCKS auth has no per-client hook, only this process-wide one — see [AppSocksAuthenticator].
        java.net.Authenticator.setDefault(AppSocksAuthenticator(liveProxyConfig))
        OkHttpClient
            .Builder()
            .cookieJar(WebViewCookieJar(cookieStore, NodeSeekSite.CONFIG.unreplayableCookies))
            .connectionPool(connectionPool)
            // 加密 DNS, or the platform resolver while it is off — see [AppDns].
            .dns(appDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // A ProxySelector, not `.proxy(Proxy)`: consulted on every call rather than baked in once,
            // so toggling or editing 代理设置 takes effect on the next request instead of needing this
            // client rebuilt.
            .proxySelector(AppProxySelector(liveProxyConfig, ProxyClientKind.FORUM))
            .proxyAuthenticator(AppProxyAuthenticator(liveProxyConfig))
            // Applied to page *and* image requests, which both have to look like the mobile site.
            .addInterceptor(
                BrowserHeadersInterceptor(
                    userAgent = userAgent.value,
                    acceptLanguage = acceptLanguage,
                    referer = "${NodeSeekSite.BASE_URL}/",
                ),
            )
            // A *network* interceptor, because it is about a hop the application layer never sees:
            // the `Referer` stamped above must not follow a redirect off the host it was addressed
            // to, or an image host that 302s to a CDN with hotlink protection refuses every image.
            .addNetworkInterceptor(CrossOriginRefererInterceptor())
            .build()
    }

    /**
     * [okHttpClient] for images that live somewhere else — same headers, no cookie jar.
     *
     * A post can embed a picture from any host its author liked, and for years those loads rode the
     * forum's client. The cookies that jar sends are scoped per domain, so nothing of NodeSeek's
     * session ever left home — but the jar also *saves*, which is the half that matters: a `Set-Cookie`
     * from a third-party image host went into the shared WebView store and came back on every later
     * load, a persistent tracking identifier planted through an `<img>` tag. The project already made
     * this exact call once for the 图床 API ([imageHostClient], "the cookies are not its business");
     * this client is the same judgment applied to the images themselves. No jar configured means
     * OkHttp's `CookieJar.NO_COOKIES`: nothing sent, nothing saved.
     *
     * The `Referer` stays, deliberately — a browser rendering the page would send one to every
     * embedded image, hotlink-protected hosts that whitelist nodeseek.com depend on it, and
     * [CrossOriginRefererInterceptor] still strips it the moment a redirect leaves the addressed
     * host. The proxy kind is `THIRD_PARTY` for the same reason the 图床's is: `FORUM_ONLY` is the
     * user saying only the forum goes through the node, and these hosts are not the forum.
     */
    override val imageContentClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(connectionPool)
            .dns(appDns)
            .proxySelector(AppProxySelector(liveProxyConfig, ProxyClientKind.THIRD_PARTY))
            .proxyAuthenticator(AppProxyAuthenticator(liveProxyConfig))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(
                BrowserHeadersInterceptor(
                    userAgent = userAgent.value,
                    acceptLanguage = acceptLanguage,
                    referer = "${NodeSeekSite.BASE_URL}/",
                ),
            )
            .addNetworkInterceptor(CrossOriginRefererInterceptor())
            .build()
    }

    /**
     * The one place OkHttp is named on the way *up*: everything above this line is written against
     * `HttpTransport`, which is `commonMain`, and this is the Android implementation of it.
     *
     * The vote signature wraps it rather than sitting in the chain above. It was an interceptor
     * until step D3c, ordered after `BrowserHeadersInterceptor` so that it could read the finished
     * `User-Agent` off the chain; as a decorator it writes that header itself, which is what lets the
     * same code sign a request on a platform with no interceptors — see [DynamicSignTransport].
     */
    private val transport by lazy {
        // Retry sits outside the signature: a retried vote is signed once and sent once, because
        // [RetryingTransport] never repeats a write.
        RetryingTransport(
            DynamicSignTransport(
                CookieScrubbingTransport(OkHttpTransport(okHttpClient), sessionCookies),
                userAgent.value,
            ),
        )
    }

    private val htmlClient by lazy { SiteHtmlClient(transport, dispatchers, NodeSeekSite.CONFIG) }

    private val jsonClient by lazy { NodeSeekJsonClient(transport, dispatchers) }

    override val proxyConnectionTester: ProxyConnectionTester by lazy {
        NetworkProxyConnectionTester(jsonClient)
    }

    /** The offline-first SSOT. Everything below reads from it; only the data sources write to it. */
    private val database by lazy { createNodeSeekDatabase(appContext) }

    private val remotePosts by lazy { NetworkPostDataSource(htmlClient, dispatchers, clock) }

    override val backgroundAlerts: BackgroundAlertChecker by lazy {
        BackgroundAlertChecker(
            loadList = { sort, page -> remotePosts.loadList(categorySlug = null, page = page, sort = sort).posts },
            keywords = titleKeywordStore,
            tracked = trackedThreadStore,
            settings = settingsRepository,
        )
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
            // The downloaded copy, for the reads the network cannot serve. Handed over as the read
            // interface only: the repository must not be able to start a download.
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
            dataStore = appContext.settingsDataStore,
            // Whoever is running a `-dev.N` build was handed it on purpose, so that is what the
            // update check follows until they say otherwise: the stable manifest cannot name anything
            // newer than a test build cut ahead of it, and 已是最新 is what they would be told for as
            // long as that stays true. Flipping the switch off still sticks.
            devChannelDefault = isPreReleaseVersionName(appVersion.name),
        )
    }

    override val reportCompareStore: ReportCompareStore get() = settingsRepository.reportCompare

    override val notificationRepository: NotificationRepository by lazy {
        NotificationRepository(jsonClient)
    }

    override val messageRepository: MessageRepository by lazy {
        NetworkMessageRepository(jsonClient) {
            // Only consulted when a one-conversation inbox leaves the rows ambiguous, and allowed to
            // fail: a missing uid degrades the guess, it does not fail the screen.
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
        DefaultPostComposerRepository(appContext.postComposerDataStore, transport, dispatchers, clock)
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
            dao = database.offlineDao(),
            remote = remotePosts,
            files = AndroidOfflineFileStore.of(appContext.filesDir),
            // The app's own client, so a stored picture arrives under the same cookies and headers
            // as one on screen — see [OkHttpOfflineImageSource]. Lazily, because building it starts
            // the proxy machinery and a graph that never downloads anything should not pay for that.
            images = OkHttpOfflineImageSource({ okHttpClient }, dispatchers),
            collectedMeta = collectedPostMetaStore,
            settingsStore = OfflineSettingsStore(appContext.offlineDataStore),
            scheduler = WorkManagerOfflineScheduler(appContext),
            clock = clock,
            dispatchers = dispatchers,
        )
    }

    override val assetsRepository: AssetsRepository by lazy {
        NetworkAssetsRepository(profileRepository, creditRepository, jsonClient, dispatchers, clock)
    }

    override val creditRepository: CreditRepository by lazy {
        NetworkCreditRepository(jsonClient, dispatchers)
    }

    override val stardustRepository: StardustRepository by lazy {
        NetworkStardustRepository(jsonClient, dispatchers)
    }

    override val awardRepository: AwardRepository by lazy {
        NetworkAwardRepository(htmlClient, dispatchers)
    }

    override val communityRepository: CommunityRepository by lazy {
        NetworkCommunityRepository(htmlClient, dispatchers)
    }

    override val termsRepository: TermsRepository by lazy {
        NetworkTermsRepository(htmlClient)
    }

    override val followRepository: FollowRepository by lazy {
        // The signed-in check is read off the published session state rather than the cookie store:
        // `/api/fans/*` answers a signed-out read with an empty list, and this is called on the main
        // thread. See [NetworkFollowRepository].
        NetworkFollowRepository(jsonClient, dispatchers) { sessionRepository.state.value.isSignedIn }
    }

    override val voteRepository: VoteRepository by lazy { NetworkVoteRepository(jsonClient) }

    override val rulingRepository: RulingRepository by lazy {
        NetworkRulingRepository(jsonClient, dispatchers)
    }

    override val commentComposerRepository: CommentComposerRepository by lazy {
        DefaultCommentComposerRepository(
            appContext.commentComposerDataStore,
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
            // `extend`, not `refresh`: the reader may have several pages loaded, and a refresh would
            // drop every one but the edited page out from under the scroll they are in.
            threads = postRepository::extendThread,
        )
    }

    /**
     * A client of its own for the image host, sharing with [okHttpClient] only what the user set once
     * for the whole app.
     *
     * That client carries the WebView cookie jar and stamps `Referer: nodeseek.com` on anything
     * without one. Neither is appropriate for a third-party host we hand an API key to: the key is
     * the credential, the cookies are not its business, and the referrer would tell nodeimage.com
     * about a browsing session it has no part in. What is shared is the connection pool, because
     * pooling is per-host anyway, and the proxy, because that one is a setting rather than a
     * property of this host.
     */
    private val imageHostClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(connectionPool)
            .dns(appDns)
            // The proxy is shared even though nothing else is: a user who routes the forum through a
            // node is usually on a network that reaches the image host no better. `FORUM_ONLY` is how
            // they say otherwise, and this selector is what reads it.
            .proxySelector(AppProxySelector(liveProxyConfig, ProxyClientKind.THIRD_PARTY))
            .proxyAuthenticator(AppProxyAuthenticator(liveProxyConfig))
            .connectTimeout(15, TimeUnit.SECONDS)
            // Uploads are the slow call here, and a photo on a weak connection takes longer than a
            // page read ever does.
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // The UA is shared but the cookie jar is not, and that is the point: every host this
            // client reaches is a third party holding a bearer-equivalent secret, so the forum's
            // session must never ride along. `api.nodeimage.com` is behind a Cloudflare managed
            // challenge and OkHttp's default `okhttp/4.x` is the single most reliable way to be
            // served the interstitial instead of JSON, and a self-hosted host may well be behind one
            // too; the device's real browser UA is what a challenge expects to see.
            // No `referer`: see the comment above, and [BrowserHeadersInterceptor].
            .addInterceptor(
                BrowserHeadersInterceptor(userAgent = userAgent.value, acceptLanguage = acceptLanguage),
            ).build()
    }

    override val imageHostRepository: ImageHostRepository by lazy {
        DefaultImageHostRepository(
            settings = DataStoreImageHostSettings(appContext.imageHostDataStore, KeystoreSecretCipher()),
            // Not wrapped in `DynamicSignTransport`: that signature is NodeSeek's, and these are six
            // third parties. [transport] is the forum's.
            http = OkHttpTransport(imageHostClient),
            dispatchers = dispatchers,
        )
    }

    private val imagePreparer: ImagePreparer by lazy { DefaultImagePreparer(appContext, dispatchers) }

    override val imageUploader: ImageUploader by lazy {
        ImageHostUploader(repository = imageHostRepository, preparer = imagePreparer)
    }

    override val stickerLibrary: StickerLibrary by lazy {
        val thirdParty = OkHttpTransport(imageHostClient)
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

    /**
     * Shares the cookie jar rather than owning a store of its own: the cookies OkHttp sends and the
     * ones the WebView collects have to be the same cookies, or "am I signed in" gets two answers.
     */
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

    /**
     * The same `crash/` directory `SeekTopCrashHandler` writes into — the handler is installed in
     * `SeekTopApp.onCreate` with a plain `File` because it must not touch this lazy graph while the
     * process is dying.
     */
    override val crashReportStore: CrashReportStore by lazy {
        FileCrashReportStore(File(appContext.filesDir, "crash"), dispatchers)
    }

    override val appVersion: AppVersion by lazy { readAppVersion(appContext) }

    /**
     * A third client, for github.com only.
     *
     * Same reasoning as [imageHostClient]: [okHttpClient] carries the NodeSeek session cookies and
     * stamps `Referer: nodeseek.com` on anything without one, and neither belongs on a call to a
     * host that has no part in that session. The User-Agent names the app and its version instead of
     * borrowing the device's browser UA — GitHub's API asks callers to identify themselves, and
     * there is no challenge here to impersonate a browser for.
     */
    private val gitHubClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionPool(connectionPool)
            .dns(appDns)
            // Same reasoning as `imageHostClient`: whatever reaches the forum is the user's best guess
            // at what reaches a release download too, unless they picked `FORUM_ONLY`.
            .proxySelector(AppProxySelector(liveProxyConfig, ProxyClientKind.THIRD_PARTY))
            .proxyAuthenticator(AppProxyAuthenticator(liveProxyConfig))
            .connectTimeout(15, TimeUnit.SECONDS)
            // An APK download is minutes of streaming, not a page read.
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    override val appUpdateRepository: AppUpdateRepository by lazy {
        DefaultAppUpdateRepository(
            source =
            UpdateManifestSource(
                okHttpClient = gitHubClient,
                dispatchers = dispatchers,
                userAgent = "SeekTop/${appVersion.name} (+https://github.com/${NodysseyRelease.REPOSITORY})",
                manifestBaseUrl = SeekTopRelease.UPDATES_BASE_URL,
                repository = SeekTopRelease.REPOSITORY,
            ),
            store = settingsRepository,
            clock = clock,
            dispatchers = dispatchers,
            scope = appScope,
            currentVersionName = appVersion.name,
            // The cache: an APK that has been installed is dead weight, and one that never got
            // installed is not worth keeping across a system cleanup either.
            downloadDirectory = File(appContext.cacheDir, "updates"),
        )
    }

    override val apkInstaller: ApkInstaller by lazy { AndroidApkInstaller(appContext, dispatchers) }

    private companion object {
        /** A DNS answer is a few hundred bytes; this holds far more of them than the app ever asks for. */
        const val DOH_CACHE_BYTES = 1L * 1024 * 1024
    }

    override val appCacheStore: AppCacheStore by lazy {
        DefaultAppCacheStore(
            cacheDirectory = appContext.cacheDir,
            dispatchers = dispatchers,
            imageCaches = { CoilImageCaches(SingletonImageLoader.get(appContext)) },
        )
    }
}
