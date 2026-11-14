package io.github.seektop.data

import io.github.seektop.core.NodeSeekSite
import io.github.seektop.core.html.CommunityStatsParser
import io.github.seektop.foundation.core.AppDispatchers
import io.github.seektop.foundation.core.net.HtmlSource
import kotlinx.coroutines.withContext

fun interface CommunityRepository {
    suspend fun memberCount(): Long
}

/** Fetches the public 首页 and reads the member total rendered by NodeSeek itself. */
class NetworkCommunityRepository(
    private val htmlSource: HtmlSource,
    private val dispatchers: AppDispatchers,
) : CommunityRepository {
    override suspend fun memberCount(): Long {
        val html = htmlSource.getHtml(NodeSeekSite.FRONT_PAGE_PATH)
        return withContext(dispatchers.default) {
            CommunityStatsParser.parseMemberCount(html)
        }
    }
}
