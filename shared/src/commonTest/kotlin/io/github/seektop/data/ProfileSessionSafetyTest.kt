package io.github.seektop.data

import io.github.seektop.core.html.Fixtures
import io.github.seektop.core.net.JsonSource
import io.github.seektop.data.local.ProfileDao
import io.github.seektop.data.local.SelfProfileEntity
import io.github.seektop.foundation.core.AppClock
import io.github.seektop.foundation.core.net.HtmlSource
import io.github.seektop.foundation.core.net.SiteError
import io.github.seektop.foundation.core.net.SiteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfileSessionSafetyTest {
    private class Dao : ProfileDao {
        var saved: SelfProfileEntity? = null
        var reads = 0
        override fun observe(sessionFingerprint: Int): Flow<SelfProfileEntity?> = flowOf(saved)
        override suspend fun find(sessionFingerprint: Int): SelfProfileEntity? {
            reads++
            return saved
        }
        override suspend fun upsert(profile: SelfProfileEntity) {
            saved = profile
        }
        override suspend fun deleteAll() {
            saved = null
        }
    }

    @Test
    fun `signed out profile requests do not read account cache or issue network requests`() = runTest {
        val dao = Dao()
        val repository = repository(dao, { false }, { 1 }) { error("Guest profile request reached network") }
        assertEquals(SiteError.LoginRequired, assertFailsWith<SiteException> { repository.profile() }.error)
        assertEquals(SiteError.LoginRequired, assertFailsWith<SiteException> { repository.refreshProfile(1) }.error)
        assertEquals(0, dao.reads)
    }

    @Test
    fun `response from a previous account cannot become the new account profile`() = runTest {
        val dao = Dao()
        var fingerprint = 1
        val requested = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val repository = repository(dao, { true }, { fingerprint }) {
            requested.complete(Unit)
            response.await()
            Fixtures.load("page-1.html")
        }
        val result = async { runCatching { repository.profile() } }
        requested.await()
        fingerprint = 2
        response.complete(Unit)
        assertEquals(SiteError.SessionUnrecognised, (result.await().exceptionOrNull() as SiteException).error)
        assertNull(dao.saved)
        assertNull(repository.selfUid.value)
    }

    private fun repository(dao: Dao, signedIn: () -> Boolean, fingerprint: () -> Int, html: suspend () -> String) =
        NetworkProfileRepository(
            object : HtmlSource {
                override suspend fun getHtml(path: String) = html()
                override suspend fun resolveRedirect(path: String): String? = null
            },
            object : JsonSource {
                override suspend fun getJson(path: String, referer: String): String = throw SiteException(SiteError.Http(500))
            },
            dao,
            fingerprint,
            signedIn,
            AppClock { 0L },
        )
}
