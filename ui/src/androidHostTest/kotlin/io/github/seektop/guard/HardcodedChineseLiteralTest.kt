package io.github.seektop.guard

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * A ratchet on hard-coded Chinese in screen and component code.
 *
 * The externalization effort left the user-visible copy in Compose Resources — over a thousand
 * strings — and this test is what keeps it there: every Chinese string literal remaining in `:ui`
 * and `:designsys` production sources is counted into the baseline below, and a count that grows
 * fails the build with the file's name.
 *
 * A per-file count rather than a clean zero, because most of what remains is legitimate: `@Preview`
 * sample data, which is developer-facing and belongs in the file that previews it — and a second
 * kind that must never move to resources: strings the app matches against or sends to the site
 * (`BoardTag`/`RoleBadge` matching the site's own labels, the markdown placeholders and quote
 * header that become post content). Those are protocol, not copy; a translation would break them. A lexer cannot
 * tell a preview's sample post title from a screen's hard-coded label — but it does not have to.
 * The ratchet makes every new literal a deliberate act: either the string is user-visible and goes
 * to `strings.xml`, or it is sample data and the author raises the file's count in the same commit,
 * where review sees both.
 *
 * Counts must match exactly, shrinking included: a stale over-baseline is room for a regression to
 * hide in, so removing literals means lowering the number here and banking the progress.
 */
class HardcodedChineseLiteralTest {
    @Test
    fun `chinese string literals stay at or below the externalization baseline`() {
        val root = repositoryRoot()
        val actual =
            listOf("ui", "designsys")
                .flatMap { module -> productionSources(File(root, module)) }
                .associate { file ->
                    val count = stringLiterals(file.readText()).count(::containsCjk)
                    file.relativeTo(root).invariantSeparatorsPath to count
                }.filterValues { it > 0 }

        if (actual != BASELINE) {
            val grown = actual.filter { (path, count) -> count > (BASELINE[path] ?: 0) }
            val shrunk = BASELINE.filter { (path, count) -> count > (actual[path] ?: 0) }
            fail(
                buildString {
                    appendLine("Hard-coded Chinese string literals moved against the baseline.")
                    if (grown.isNotEmpty()) {
                        appendLine()
                        appendLine("New literals (user-visible copy belongs in strings.xml; @Preview sample data means raising the baseline in the same commit):")
                        grown.forEach { (path, count) -> appendLine("  $path: $count (baseline ${BASELINE[path] ?: 0})") }
                    }
                    if (shrunk.isNotEmpty()) {
                        appendLine()
                        appendLine("Fewer literals than the baseline — good; lower these entries to bank it:")
                        shrunk.forEach { (path, count) -> appendLine("  $path: ${actual[path] ?: 0} (baseline $count)") }
                    }
                    appendLine()
                    appendLine("Current counts, in baseline form:")
                    actual.entries.sortedBy { it.key }.forEach { (path, count) -> appendLine("        \"$path\" to $count,") }
                },
            )
        }
    }

    private companion object {
        val BASELINE = mapOf(
            "designsys/src/commonMain/kotlin/io/github/seektop/foundation/designsys/component/ImageFallback.kt" to 2,
            "designsys/src/commonMain/kotlin/io/github/seektop/foundation/designsys/component/SkippedImagePlaceholder.kt" to 1,
            "designsys/src/commonMain/kotlin/io/github/seektop/foundation/designsys/component/ThreadRow.kt" to 1,
            "designsys/src/commonMain/kotlin/io/github/seektop/foundation/designsys/editor/EditorAction.kt" to 4,
            "designsys/src/commonMain/kotlin/io/github/seektop/foundation/designsys/richtext/RichContent.kt" to 30,
            "designsys/src/commonMain/kotlin/io/github/seektop/foundation/designsys/richtext/StardustReceiveCard.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/account/BlockListScreen.kt" to 2,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/account/ProfileFieldsScreen.kt" to 4,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/assets/AssetsScreen.kt" to 2,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/assets/CreditScreen.kt" to 8,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/assets/StardustScreen.kt" to 3,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/bookmarks/BookmarksScreen.kt" to 26,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/bookmarks/OfflineManageSheet.kt" to 2,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/common/BoardTag.kt" to 13,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/common/RoleBadge.kt" to 6,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/common/SpendConfirmDialog.kt" to 15,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/common/StatusStates.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/compare/ReportCompareScreen.kt" to 2,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/composer/ReplyComposerViewModel.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/history/ReadHistoryScreen.kt" to 4,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/login/SignInScreen.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/messages/MessageThreadScreen.kt" to 5,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/notifications/ConversationList.kt" to 3,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/notifications/NotificationsScreen.kt" to 3,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/postdetail/PostDetailScreen.kt" to 10,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/postlist/PostListScreen.kt" to 34,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/profile/ProfileScreen.kt" to 3,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/search/SearchScreen.kt" to 8,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/settings/AboutAppScreen.kt" to 4,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/settings/AboutCommunityScreen.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/settings/ChangelogScreen.kt" to 2,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/settings/PrivacyScreen.kt" to 6,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/settings/SettingsScreen.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/settings/TitleKeywordsScreen.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/settings/UpdateReminderDialog.kt" to 3,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/settings/theme/ThemeSettingsScreen.kt" to 2,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/space/FollowScreen.kt" to 5,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/space/UserSpaceScreen.kt" to 22,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/stardust/StardustReceiveCard.kt" to 4,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/tools/AwardScreen.kt" to 13,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/tools/CommunityToolsScreen.kt" to 2,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/tools/InviteScreen.kt" to 2,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/tools/LuckyScreen.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/tools/RulingScreen.kt" to 12,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/viewer/ImageViewerScreen.kt" to 1,
            "ui/src/commonMain/kotlin/io/github/seektop/ui/vote/VoteCard.kt" to 8,
        )
    }
}
