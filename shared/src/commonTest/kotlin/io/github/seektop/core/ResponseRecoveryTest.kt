package io.github.seektop.core

import io.github.seektop.foundation.core.net.ChallengeDetector
import io.github.seektop.foundation.core.net.SiteError
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseRecoveryTest {
    @Test
    fun `authentication challenge permission and server failures keep distinct recovery actions`() {
        val detector = ChallengeDetector(NodeSeekSite.CONFIG.markers)
        val url = NodeSeekSite.BASE_URL
        assertEquals(SiteError.LoginRequired, detector.detect("Unauthorized", 401, emptyMap(), url))
        assertEquals(SiteError.Cloudflare(url), detector.detect("Challenge", 403, mapOf("cf-mitigated" to "challenge"), url))
        assertEquals(SiteError.Http(403), detector.detect("Forbidden", 403, emptyMap(), url))
        assertEquals(SiteError.Http(500), detector.detect("Unavailable", 500, emptyMap(), url))
        assertEquals(SiteError.Unparsable, detector.detect("Unrecognised HTML", 200, emptyMap(), url))
    }
}
