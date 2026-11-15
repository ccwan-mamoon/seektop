package io.github.seektop.ai

import io.github.seektop.model.PostDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Read a separate, bounded snapshot without changing the reader's cached page or scroll position. */
suspend fun collectThreadAnalysis(
    seed: AiPost,
    scope: AiAnalysisScope,
    refresh: Boolean,
    loadPage: suspend (Long, Int) -> PostDetail,
    sessionStamp: () -> String,
    onProgress: (Int, Int) -> Unit,
    pause: suspend () -> Unit = { delay(500) },
): AiPost {
    val session = sessionStamp()
    suspend fun fetch(number: Int): PostDetail = try {
        loadPage(seed.id, number)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        throw IllegalStateException("AI_THREAD_LOAD_FAILED", e)
    }
    if (scope == AiAnalysisScope.ORIGINAL_POST && !refresh) return seed.copy(originSession = session)
    val first = fetch(1)
    check(sessionStamp() == session) { "AI_SESSION_CHANGED" }
    val body = checkNotNull(first.body) { "AI_THREAD_INCOMPLETE" }
    check(first.postId == seed.id && first.page == 1 && !body.isBlocked) { "AI_THREAD_INCOMPLETE" }
    val base = analysisPost(seed.id, first.title, seed.boardKey.takeIf { body.categoryTitle == seed.boardTitle }, body, maxChars = if (scope == AiAnalysisScope.ALL_FLOORS) 120000 else 60000).copy(originSession = session)
    if (scope == AiAnalysisScope.ORIGINAL_POST) return base
    check(first.totalPages >= 1) { "AI_THREAD_INCOMPLETE" }
    check(first.totalPages <= 100) { "AI_THREAD_TOO_LARGE" }
    check(!base.truncated) { "AI_THREAD_TOO_LARGE" }
    val pages = first.totalPages
    val text = StringBuilder("[Opening post / 楼主 #0]\n").append(base.text)
    check(text.length <= 120000) { "AI_THREAD_TOO_LARGE" }
    val images = base.images.toMutableSet()
    val keys = mutableSetOf<String>()
    var replies = 0
    var skipped = 0
    fun append(page: PostDetail) {
        check(page.postId == seed.id && page.totalPages == pages && page.hasNextPage == (page.page < pages)) { "AI_THREAD_CHANGED" }
        page.comments.forEachIndexed { index, comment ->
            val key = comment.commentId?.let { "id:$it" } ?: comment.floor?.let { "floor:$it" } ?: "page:${page.page}:$index"
            check(keys.add(key)) { "AI_THREAD_CHANGED" }
            if (comment.isBlocked) {
                skipped++
                return@forEachIndexed
            }
            val part = analysisPost(seed.id, "", seed.boardKey, comment, maxChars = 120000)
            check(!part.truncated) { "AI_THREAD_TOO_LARGE" }
            val role = if (comment.isOriginalPoster) "楼主回复 / opening-post author" else "其他用户 / commenter"
            text.append("\n\n[Reply / 回复 ").append(comment.floor ?: "page ${page.page}, item ${index + 1}").append("; ").append(role).append("; ").append(comment.authorName.take(80)).append("]\n").append(part.text)
            check(text.length <= 120000) { "AI_THREAD_TOO_LARGE" }
            images.addAll(part.images)
            replies++
        }
    }
    append(first)
    onProgress(1, pages)
    for (number in 2..pages) {
        pause()
        check(sessionStamp() == session) { "AI_SESSION_CHANGED" }
        val page = fetch(number)
        check(sessionStamp() == session) { "AI_SESSION_CHANGED" }
        check(page.page == number) { "AI_THREAD_INCOMPLETE" }
        append(page)
        onProgress(number, pages)
    }
    return base.copy(text = text.toString(), images = images.toList(), scope = scope, sourcePages = pages, replyCount = replies, skippedReplies = skipped)
}
