package io.github.seektop.di

import io.github.seektop.data.AppCacheStore
import io.github.seektop.data.AssetsRepository
import io.github.seektop.data.AwardRepository
import io.github.seektop.data.CategoryRepository
import io.github.seektop.data.CollectedPostMetaStore
import io.github.seektop.data.CommunityRepository
import io.github.seektop.data.CreditRepository
import io.github.seektop.data.FollowRepository
import io.github.seektop.data.MessageRepository
import io.github.seektop.data.NotificationRepository
import io.github.seektop.data.OfflineLibrary
import io.github.seektop.data.PostRepository
import io.github.seektop.data.ProfileRepository
import io.github.seektop.data.ReadingPositionStore
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
import io.github.seektop.data.composer.CommentComposerRepository
import io.github.seektop.data.composer.ImageUploader
import io.github.seektop.data.composer.PostComposerRepository
import io.github.seektop.data.composer.PostEditor
import io.github.seektop.data.diagnostics.NetworkDiagnostics
import io.github.seektop.data.dns.DohSupport
import io.github.seektop.data.imagehost.ImageHostRepository
import io.github.seektop.data.proxy.ProxyConnectionTester
import io.github.seektop.data.proxy.ProxySettings
import io.github.seektop.data.session.AccountSignOut
import io.github.seektop.data.session.SessionRepository
import io.github.seektop.data.session.SignInRepository
import io.github.seektop.data.settings.ReportCompareStore
import io.github.seektop.data.settings.SettingsRepository
import io.github.seektop.data.sticker.StickerLibrary
import io.github.seektop.data.update.AppUpdateRepository
import io.github.seektop.foundation.core.AppClock
import io.github.seektop.foundation.core.AppDispatchers
import io.github.seektop.foundation.core.AppVersion
import io.github.seektop.foundation.core.crash.CrashReportStore
import io.github.seektop.foundation.core.net.UserAgent
import io.github.seektop.foundation.core.update.ApkInstaller

/**
 * The application's dependency graph, as the screens above it see one.
 *
 * Manual constructor injection rather than Hilt: the graph is still small enough that a framework
 * would add more machinery than value. This interface keeps dependencies explicit and swappable;
 * see `docs/architecture.md` for the migration trigger.
 *
 * Nothing here is a global: the container is created by the Application and handed down. That is
 * what makes a fake container's substitution possible in tests.
 *
 * Every member is a type this module can name. The two that were not — the `OkHttpClient` and the
 * `SessionCookies` read off the cookie jar it is built with — are on `AndroidAppContainer` in `:app`
 * instead, and neither was ever read from a screen: the first is Coil's call factory, the second is
 * consumed inside the container itself. That is the whole of what step D1 had to take off this
 * interface to bring it down here with the ViewModels that name it.
 */
interface AppContainer {
    /** Reserved metadata boundary. Platform shells opt in only after plugin execution is implemented. */
    val pluginRegistry: io.github.seektop.plugins.PluginRegistry
        get() = io.github.seektop.plugins.ReservedPluginRegistry

    val dispatchers: AppDispatchers
    val clock: AppClock

    val postRepository: PostRepository

    /** Where each thread was left off — its own store, not part of a screen's settings. */
    val readingPositionStore: ReadingPositionStore

    /** 备注, 屏蔽/提醒关键词 and 追踪新回复 — this device's owner's, so none is cleared on sign-out. */
    val userNoteStore: UserNoteStore
    val titleKeywordStore: TitleKeywordStore
    val trackedThreadStore: TrackedThreadStore
    val categoryRepository: CategoryRepository
    val settingsRepository: SettingsRepository

    /** 测评对比's basket — the reports set aside to compare. Lives in [settingsRepository]'s store. */
    val reportCompareStore: ReportCompareStore
    val notificationRepository: NotificationRepository
    val messageRepository: MessageRepository
    val profileRepository: ProfileRepository
    val searchRepository: SearchRepository
    val postComposerRepository: PostComposerRepository
    val accountSettingsRepository: AccountSettingsRepository
    val commentComposerRepository: CommentComposerRepository

    /** 编辑 — reads a floor back as Markdown and writes it again. See [PostEditor]. */
    val postEditor: PostEditor
    val imageUploader: ImageUploader

    /** The selected image host — a service of its own, with its own credential. See [ImageHostRepository]. */
    val imageHostRepository: ImageHostRepository

    /** 我的表情 and the GitHub sticker packs — see [StickerLibrary]. The device owner's; kept on sign-out. */
    val stickerLibrary: StickerLibrary
    val sessionRepository: SessionRepository

    /**
     * The one sign-out. Both screens that offer it go through this rather than each keeping its own
     * list of stores to clear — the lists are what drifted apart. See [AccountSignOut].
     */
    val accountSignOut: AccountSignOut

    /** 登录 · 原生表单 (h1). Apart from [sessionRepository], which only reads the cookie jar. */
    val signInRepository: SignInRepository
    val userSpaceRepository: UserSpaceRepository

    /**
     * 离线阅读 — what 收藏 draws its download states from, and what the download workers drive.
     *
     * Bound to `RoomOfflineLibrary`, which also implements `OfflineThreadReader` (the post cache's
     * fallback when the site cannot be reached) and `OfflineDownloads` (what WorkManager calls).
     * Both are found by casting off this one property rather than adding two more: the three are one
     * object with three audiences, and only this one belongs on a screen's graph.
     */
    val offlineLibrary: OfflineLibrary

    /**
     * What this device remembers about the threads this account has collected.
     *
     * Its own dependency rather than a corner of [postRepository] because three unrelated things
     * write into it — the star, the 收藏 list, an offline download — and only one of them is that
     * repository. See [CollectedPostMetaStore] for why the app has to remember any of this.
     */
    val collectedPostMetaStore: CollectedPostMetaStore
    val assetsRepository: AssetsRepository
    val creditRepository: CreditRepository
    val stardustRepository: StardustRepository
    val awardRepository: AwardRepository
    val communityRepository: CommunityRepository
    val termsRepository: TermsRepository

    val followRepository: FollowRepository

    /** 投票帖. Never cached — see [io.github.seektop.data.VoteRepository]. */
    val voteRepository: VoteRepository

    /** The installed build's own version. Read once; the About screen and the updater both use it. */
    val appVersion: AppVersion

    /** 应用内更新: asks GitHub Releases, holds the answer, fetches the APK. */
    val appUpdateRepository: AppUpdateRepository

    /** The other half of it — handing that APK to the platform installer. */
    val apkInstaller: ApkInstaller

    val rulingRepository: RulingRepository

    /** 清除缓存 — the image, WebView and update caches on disk. See [AppCacheStore]. */
    val appCacheStore: AppCacheStore

    /**
     * The last crash, kept locally for 关于 › 导出崩溃日志 — the zero-telemetry answer to "它闪退了".
     * See [CrashReportStore]; platforms with no capture wired provide `NoCrashReports`.
     */
    val crashReportStore: CrashReportStore

    /**
     * The UA the WebView and OkHttp both use. Shared rather than duplicated: `cf_clearance` is issued
     * against the UA that solved the challenge and rejected for any other.
     */
    val userAgent: UserAgent

    /**
     * The app's proxy — HTTP or SOCKS, with an optional credential.
     *
     * One setting routes all three clients below, which is the point: an app where the posts go
     * through a proxy and the avatars, uploads or update check quietly do not is an app whose proxy
     * setting means something different on every screen. `ProxyScope.FORUM_ONLY` is the one way out
     * of that, and it is the user's to choose. Every other app on the device is unaffected either
     * way — nothing here is a VPN.
     */
    val proxySettings: ProxySettings
    val proxyConnectionTester: ProxyConnectionTester

    /**
     * 加密 DNS — a DoH server for the app's own lookups, and the test that proves it answers.
     *
     * Both platforms have one, by two different routes. Android hands each of its `OkHttpClient`s a
     * `Dns`, so the resolver is the app's own object. Apple has no such parameter on `NSURLSession`
     * — what it has is `nw_privacy_context_require_encrypted_name_resolution` on
     * `NW_DEFAULT_PRIVACY_CONTEXT`, which the header describes as inherited by every other
     * resolution in the same process, `NSURLSession` included. What that costs is two of the
     * switches: see `DohCapabilities`.
     *
     * Nullable because a platform could still lack it, and 设置 reads the null to leave the row out
     * rather than offer a screen that stores a server nothing reads.
     */
    val doh: DohSupport?

    /**
     * 网络自检 — the app measuring its own connection, so "很慢" can arrive as numbers.
     *
     * Nullable on the same terms as [doh], and 设置 reads the null the same way. What a platform
     * needs in order to have one is a client that will report where the time in a request went;
     * Android's does, and until the Apple side has an equivalent this is null there rather than a
     * screen that would show two rows and shrug at the rest.
     */
    val networkDiagnostics: NetworkDiagnostics?
}
