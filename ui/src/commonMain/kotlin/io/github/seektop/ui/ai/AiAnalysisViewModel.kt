package io.github.seektop.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seektop.ai.AiAnalysisRequest
import io.github.seektop.ai.AiAnalysisScope
import io.github.seektop.ai.AiPost
import io.github.seektop.ai.AiSettings
import io.github.seektop.ai.AiTurn
import io.github.seektop.ai.PostAnalysisService
import io.github.seektop.ai.promptFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiAnalysisState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val settings: AiSettings? = null,
    val prompt: String = "",
    val output: String = "",
    val phase: String = "waiting",
    val busy: Boolean = false,
    val error: String? = null,
    val stopped: Boolean = false,
    val history: List<AiTurn> = emptyList(),
    val sourceTruncated: Boolean = false,
    val scope: AiAnalysisScope = AiAnalysisScope.ORIGINAL_POST,
    val sourcePages: Int = 1,
    val replyCount: Int = 0,
    val skippedReplies: Int = 0,
    val pagesLoaded: Int = 0,
    val totalPages: Int = 0,
    val lastQuestion: String = "",
    val supplements: List<String> = emptyList(),
    val canAsk: Boolean = false,
)

class AiAnalysisViewModel(private val service: PostAnalysisService, private val post: AiPost) : ViewModel() {
    private val mutable = MutableStateFlow(AiAnalysisState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var automaticRequested = false
    private var lastRequest: AiAnalysisRequest? = null
    init {
        reload()
    }

    fun reload() {
        mutable.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val settings = service.settings()
                val available = service.available()
                val prompt = if (available) settings.promptFor(post, service.templates()) else ""
                mutable.update { it.copy(loading = false, settings = settings, available = available, prompt = prompt, error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
    fun analyzeOnce(language: String, retry: Boolean = false, scope: AiAnalysisScope = state.value.scope, supplement: String = ""): Boolean {
        if (automaticRequested && !retry) return false
        if (job?.isCompleted == false) return false
        val addition = supplement.trim().take(8000)
        if (addition.isNotBlank()) {
            val notes = (state.value.supplements + addition).distinct()
            if (notes.size > 16 || notes.sumOf { it.length } > 16000) {
                mutable.update { it.copy(error = "AI_SUPPLEMENTS_LIMIT") }
                return false
            }
            mutable.update { it.copy(supplements = notes) }
        }
        automaticRequested = true
        if (retry) reload()
        execute {
            val loaded = state.first { !it.loading }
            val settings = checkNotNull(loaded.settings) { loaded.error ?: "AI_INVALID_SETTINGS" }
            val provider = settings.providers.find { it.id == settings.selectedProvider }
            check(provider?.keySet == true) { "AI_KEY_REQUIRED" }
            mutable.update { it.copy(phase = "collecting", pagesLoaded = 0, totalPages = 0) }
            val snapshot = service.collectPost(post, scope, retry) { page, total ->
                mutable.update { it.copy(phase = "collecting", pagesLoaded = page, totalPages = total) }
            }
            check(!settings.includeImages || snapshot.images.isEmpty() || provider.vision) { "AI_VISION_UNAVAILABLE" }
            AiAnalysisRequest(snapshot, settings.promptFor(snapshot, service.templates()), if (settings.includeImages) snapshot.images.take(8) else emptyList(), language = language, question = addition, supplements = state.value.supplements)
        }
        return true
    }
    fun prompt(value: String) {
        if (value.length <= 12000) mutable.update { it.copy(prompt = value) }
    }
    fun start(images: List<String>, language: String) = execute { AiAnalysisRequest(post, state.value.prompt, images, language = language, supplements = state.value.supplements) }
    fun ask(question: String): Boolean {
        val previous = lastRequest ?: return false
        if (job?.isCompleted == false || question.isBlank()) return false
        val q = question.trim().take(8000)
        val notes = (state.value.supplements + q).distinct()
        if (notes.size > 16 || notes.sumOf { it.length } > 16000) {
            mutable.update { it.copy(error = "AI_SUPPLEMENTS_LIMIT") }
            return false
        }
        mutable.update { it.copy(supplements = notes) }
        execute { previous.copy(history = state.value.history.takeLast(8), question = q, supplements = notes) }
        return true
    }
    private fun execute(build: suspend () -> AiAnalysisRequest) {
        if (job?.isCompleted == false) return
        mutable.update { it.copy(busy = true, phase = "waiting", error = null, stopped = false) }
        job = viewModelScope.launch {
            try {
                val request = build()
                val history = request.history
                lastRequest = request.copy(history = emptyList(), question = "")
                mutable.update { it.copy(output = "", phase = "waiting", history = history, sourceTruncated = request.post.truncated, scope = request.post.scope, sourcePages = request.post.sourcePages, replyCount = request.post.replyCount, skippedReplies = request.post.skippedReplies, lastQuestion = request.question, canAsk = true) }
                service.analyze(request).collect { update -> mutable.update { it.copy(output = update.text, phase = update.phase) } }
                val turns = history + (if (request.question.isNotBlank()) listOf(AiTurn("user", request.question)) else emptyList()) + AiTurn("assistant", state.value.output)
                mutable.update { it.copy(history = turns.takeLast(8)) }
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(stopped = true) }
                throw cancelled
            } catch (e: Exception) {
                mutable.update { it.copy(error = e.message) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
        job?.invokeOnCompletion { mutable.update { it.copy(busy = false) } }
    }
    fun stop() {
        job?.cancel()
        mutable.update { it.copy(stopped = true) }
    }
    companion object {
        fun factory(service: PostAnalysisService, post: AiPost): ViewModelProvider.Factory = viewModelFactory { initializer { AiAnalysisViewModel(service, post) } }
    }
}
