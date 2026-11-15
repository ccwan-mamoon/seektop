package io.github.seektop.ui.ai

import io.github.seektop.ai.AiAnalysisRequest
import io.github.seektop.ai.AiAnalysisScope
import io.github.seektop.ai.AiAnalysisUpdate
import io.github.seektop.ai.AiPost
import io.github.seektop.ai.AiSettings
import io.github.seektop.ai.AiTemplatePack
import io.github.seektop.ai.PostAnalysisService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiAnalysisViewModelTest {
    private class Service : PostAnalysisService {
        val requests = Channel<AiAnalysisRequest>(Channel.UNLIMITED)
        var events = Channel<AiAnalysisUpdate>(Channel.UNLIMITED)
        var cancelled = false
        var failCollection = false
        override suspend fun collectPost(post: AiPost, scope: AiAnalysisScope, refresh: Boolean, onProgress: (Int, Int) -> Unit): AiPost {
            check(!failCollection) { "AI_THREAD_CHANGED" }
            return post.copy(scope = scope, sourcePages = if (scope == AiAnalysisScope.ALL_FLOORS) 2 else 1, replyCount = if (scope == AiAnalysisScope.ALL_FLOORS) 3 else 0, text = post.text + if (scope == AiAnalysisScope.ALL_FLOORS) " with all replies" else "")
        }
        override fun today() = "2026-11-15"
        override suspend fun available() = true
        var config = AiSettings(defaultPrompt = "saved prompt")
        override suspend fun settings() = config
        override suspend fun save(settings: AiSettings, newKeys: Map<String, String>) = Unit
        override suspend fun test(providerId: String) = Unit
        override suspend fun templates() = AiTemplatePack(1, "system", "default", emptyMap())
        override fun analyze(request: AiAnalysisRequest): Flow<AiAnalysisUpdate> = flow {
            requests.send(request)
            try {
                for (e in events) emit(e)
            } finally {
                cancelled = true
            }
        }
    }

    @Test fun `one click waits for settings and submits matched board prompt once without images by default`(): Unit = runBlocking {
        withTimeout(10000) {
            val service = Service().apply { config = config.copy(providers = config.providers.map { it.copy(keySet = true) }, boardPrompts = mapOf("trade" to "board valuation")) }
            val vm = withContext(Dispatchers.Main) {
                AiAnalysisViewModel(service, AiPost(1, "title", "trade", "Trading", "body", listOf("https://public/image"))).also {
                    it.analyzeOnce("en")
                    it.analyzeOnce("en")
                }
            }
            val request = service.requests.receive()
            assertEquals("board valuation", request.prompt)
            assertEquals(emptyList(), request.images)
            withContext(Dispatchers.Main) { vm.stop() }
            vm.state.first { !it.busy }
            assertTrue(service.requests.tryReceive().isFailure)
        }
    }

    @Test fun `missing credential offers configuration without a paid request`(): Unit = runBlocking {
        withTimeout(10000) {
            val service = Service()
            val vm = withContext(Dispatchers.Main) { AiAnalysisViewModel(service, AiPost(1, "title", "", "", "body", emptyList())).also { it.analyzeOnce("en") } }
            vm.state.first { it.error == "AI_KEY_REQUIRED" }
            assertTrue(service.requests.tryReceive().isFailure)
            assertFalse(vm.state.value.busy)
        }
    }

    @Test fun `image setting controls one click image submission and caps it at eight`(): Unit = runBlocking {
        withTimeout(10000) {
            val service = Service().apply { config = config.copy(includeImages = true, providers = config.providers.map { it.copy(keySet = true, vision = true) }) }
            val images = (1..10).map { "https://public/image-$it" }
            val vm = withContext(Dispatchers.Main) { AiAnalysisViewModel(service, AiPost(1, "title", "", "", "body", images)).also { it.analyzeOnce("en") } }
            assertEquals(images.take(8), service.requests.receive().images)
            withContext(Dispatchers.Main) { vm.stop() }
            vm.state.first { !it.busy }
        }
    }

    @Test fun `supplemental expiry survives changing to a fresh full thread and later followups keep that scope`(): Unit = runBlocking {
        withTimeout(10000) {
            val service = Service().apply { config = config.copy(providers = config.providers.map { it.copy(keySet = true) }) }
            val vm = withContext(Dispatchers.Main) { AiAnalysisViewModel(service, AiPost(17, "title", "trade", "Trading", "opening post", emptyList())).also { it.analyzeOnce("en") } }
            service.requests.receive()
            service.events.send(AiAnalysisUpdate("missing expiry", true))
            service.events.close()
            vm.state.first { !it.busy }
            service.events = Channel(Channel.UNLIMITED)
            withContext(Dispatchers.Main) { vm.ask("Expiry is 2027-01-15; recalculate") }
            val question = service.requests.receive()
            assertEquals("Expiry is 2027-01-15; recalculate", question.question)
            service.events.send(AiAnalysisUpdate("updated valuation", true))
            service.events.close()
            vm.state.first { !it.busy }
            service.events = Channel(Channel.UNLIMITED)
            withContext(Dispatchers.Main) { vm.analyzeOnce("en", retry = true, scope = AiAnalysisScope.ALL_FLOORS) }
            val all = service.requests.receive()
            assertEquals(AiAnalysisScope.ALL_FLOORS, all.post.scope)
            assertEquals(listOf("Expiry is 2027-01-15; recalculate"), all.supplements)
            assertTrue(all.history.isEmpty())
            assertTrue("all replies" in all.post.text)
            service.events.send(AiAnalysisUpdate("full-thread answer", true))
            service.events.close()
            vm.state.first { !it.busy }
            service.events = Channel(Channel.UNLIMITED)
            withContext(Dispatchers.Main) { vm.ask("Add a 10 CNY transfer fee") }
            val next = service.requests.receive()
            assertEquals(AiAnalysisScope.ALL_FLOORS, next.post.scope)
            assertEquals(2, next.supplements.size)
            withContext(Dispatchers.Main) { vm.stop() }
        }
    }

    @Test fun `scope action submits an unsent expiry addition together with the new snapshot`(): Unit = runBlocking {
        withTimeout(10000) {
            val service = Service().apply { config = config.copy(providers = config.providers.map { it.copy(keySet = true) }) }
            val vm = withContext(Dispatchers.Main) { AiAnalysisViewModel(service, AiPost(17, "title", "trade", "Trading", "body", emptyList())).also { it.analyzeOnce("en") } }
            service.requests.receive()
            service.events.send(AiAnalysisUpdate("expiry missing", true))
            service.events.close()
            vm.state.first { !it.busy }
            service.events = Channel(Channel.UNLIMITED)
            withContext(Dispatchers.Main) { vm.analyzeOnce("en", retry = true, scope = AiAnalysisScope.ALL_FLOORS, supplement = "Expiry 2027-01-15") }
            val all = service.requests.receive()
            assertEquals(listOf("Expiry 2027-01-15"), all.supplements)
            assertEquals("Expiry 2027-01-15", all.question)
            assertTrue(all.history.isEmpty())
            assertEquals(AiAnalysisScope.ALL_FLOORS, all.post.scope)
            withContext(Dispatchers.Main) { vm.stop() }
        }
    }

    @Test fun `failed all floor collection retains the previous answer and sends no new AI request`(): Unit = runBlocking {
        withTimeout(10000) {
            val service = Service().apply { config = config.copy(providers = config.providers.map { it.copy(keySet = true) }) }
            val vm = withContext(Dispatchers.Main) { AiAnalysisViewModel(service, AiPost(17, "title", "trade", "Trading", "body", emptyList())).also { it.analyzeOnce("en") } }
            service.requests.receive()
            service.events.send(AiAnalysisUpdate("previous answer", true))
            service.events.close()
            vm.state.first { !it.busy }
            service.failCollection = true
            withContext(Dispatchers.Main) { vm.analyzeOnce("en", retry = true, scope = AiAnalysisScope.ALL_FLOORS) }
            vm.state.first { !it.busy && it.error != null }
            assertEquals("previous answer", vm.state.value.output)
            assertEquals(AiAnalysisScope.ORIGINAL_POST, vm.state.value.scope)
            assertTrue(service.requests.tryReceive().isFailure)
        }
    }

    @Test fun `followup uses analyzed post and prompt snapshot instead of unsent edits`(): Unit = runBlocking {
        withTimeout(10000) {
            val service = Service()
            val post = AiPost(17, "title", "trade", "Trading", "original body", emptyList())
            val vm = withContext(Dispatchers.Main) { AiAnalysisViewModel(service, post) }
            vm.state.first { !it.loading }
            withContext(Dispatchers.Main) { vm.start(emptyList(), "en") }
            assertEquals(post, service.requests.receive().post)
            service.events.send(AiAnalysisUpdate("first answer", true))
            service.events.close()
            vm.state.first { !it.busy && it.history.isNotEmpty() }
            service.events = Channel(Channel.UNLIMITED)
            withContext(Dispatchers.Main) {
                vm.prompt("unsent changed prompt")
                vm.ask("why?")
            }
            val follow = service.requests.receive()
            assertEquals("saved prompt", follow.prompt)
            assertEquals("why?", follow.question)
            assertEquals("first answer", follow.history.single().content)
            assertEquals(post, follow.post)
            withContext(Dispatchers.Main) { vm.stop() }
            vm.state.first { !it.busy }
            assertTrue(service.cancelled)
        }
    }

    @Test fun `stop before launched coroutine runs releases busy state without sending`(): Unit = runBlocking {
        val service = Service()
        val vm = withContext(Dispatchers.Main) { AiAnalysisViewModel(service, AiPost(1, "title", "", "", "body", emptyList())) }
        withTimeout(10000) { vm.state.first { !it.loading } }
        withContext(Dispatchers.Main) {
            vm.start(emptyList(), "en")
            vm.stop()
        }
        withTimeout(10000) { vm.state.first { !it.busy } }
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.stopped)
    }
}
