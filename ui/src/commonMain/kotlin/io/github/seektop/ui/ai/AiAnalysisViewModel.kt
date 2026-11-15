package io.github.seektop.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seektop.ai.AiAnalysisRequest
import io.github.seektop.ai.AiPost
import io.github.seektop.ai.AiSettings
import io.github.seektop.ai.AiTurn
import io.github.seektop.ai.PostAnalysisService
import io.github.seektop.ai.promptFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiAnalysisState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val settings: AiSettings? = null,
    val prompt: String = "",
    val output: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val stopped: Boolean = false,
    val history: List<AiTurn> = emptyList(),
)

class AiAnalysisViewModel(private val service: PostAnalysisService, private val post: AiPost) : ViewModel() {
    private val mutable = MutableStateFlow(AiAnalysisState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var lastRequest: AiAnalysisRequest? = null
    init {
        reload()
    }

    fun reload() {
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
    fun prompt(value: String) {
        if (value.length <= 12000) mutable.update { it.copy(prompt = value) }
    }
    fun start(images: List<String>, language: String) = run(AiAnalysisRequest(post, state.value.prompt, images, language = language))
    fun ask(question: String) {
        val previous = lastRequest ?: return
        if (question.isNotBlank()) run(previous.copy(history = state.value.history.takeLast(8), question = question.trim()))
    }
    private fun run(request: AiAnalysisRequest) {
        if (job?.isCompleted == false || !state.value.available) return
        val history = if (request.question.isBlank()) emptyList() else state.value.history
        mutable.update { it.copy(busy = true, output = "", error = null, stopped = false, history = history) }
        job = viewModelScope.launch {
            try {
                service.analyze(request).collect { update -> mutable.update { it.copy(output = update.text) } }
                lastRequest = request.copy(history = emptyList(), question = "")
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
