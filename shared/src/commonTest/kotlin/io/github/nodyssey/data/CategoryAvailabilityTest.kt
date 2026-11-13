package io.github.nodyssey.data

import io.github.nodyssey.core.net.JsonSource
import io.github.nodyssey.data.local.BoardDao
import io.github.nodyssey.data.local.BoardEntity
import io.github.plaza.core.AppClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryAvailabilityTest {
    @Test fun `first launch has offline boards while refresh waits and replaces them with server boards`() = runTest {
        val rows = MutableStateFlow<List<BoardEntity>>(emptyList())
        val dao = object : BoardDao {
            override fun observeBoards() = rows
            override suspend fun count() = rows.value.size
            override suspend fun deleteAll() {
                rows.value = emptyList()
            }
            override suspend fun upsertAll(boards: List<BoardEntity>) {
                rows.value = boards
            }
        }
        val response = CompletableDeferred<String>()
        val source = object : JsonSource {
            override suspend fun getJson(path: String, referer: String) = response.await()
        }
        val clock = object : AppClock {
            override fun nowMillis() = 1000L
        }
        val repository = CategoryRepository(source, dao, clock)
        val refresh = async { repository.refreshIfNeeded() }
        val offline = repository.boards.first()
        assertTrue(offline.any { it.slug == "tech" })
        assertEquals(null, offline.first().slug)
        response.complete("""{"success":true,"data":[{"key":"fresh","cn_text":"Fresh board"}]}""")
        refresh.await()
        assertEquals(listOf(null, "fresh"), repository.boards.first().map { it.slug })
    }
}
