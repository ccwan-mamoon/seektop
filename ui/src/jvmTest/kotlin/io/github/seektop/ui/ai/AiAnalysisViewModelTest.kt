package io.github.seektop.ui.ai

import io.github.seektop.ai.AiAnalysisRequest
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
