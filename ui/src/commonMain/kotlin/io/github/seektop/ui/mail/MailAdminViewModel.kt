package io.github.seektop.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seektop.data.mail.SeekLiAccount
import io.github.seektop.data.mail.SeekLiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class MailAdminState(
    val resource: String = "mailboxes",
    val rows: List<JsonObject> = emptyList(),
    val busy: Boolean = false,
    val error: Int? = null,
    val errorDescription: String = "",
    val detail: JsonObject? = null,
    val result: JsonObject? = null,
    val nextCursor: String = "",
)

class MailAdminViewModel(private val account: SeekLiAccount) : ViewModel() {
    private val mutable = MutableStateFlow(MailAdminState())
    val state = mutable.asStateFlow()
    private var loading: Job? = null
    init {
        load("mailboxes")
    }
    fun clearError() {
        mutable.update { it.copy(error = null) }
    }
    fun closeDetail() {
        mutable.update { it.copy(detail = null, result = null) }
    }
    fun load(resource: String = state.value.resource, cursor: String = "") {
        loading?.cancel()
        loading = viewModelScope.launch {
            mutable.update { it.copy(resource = resource, busy = true, rows = emptyList(), error = null) }
            try {
                if (resource == "upload") {
                    mutable.update { it.copy(busy = false) }
                    return@launch
                }
                val path = "/$resource" + if (resource == "mails/unread") "?limit=100" + cursor.takeIf { it.isNotBlank() }?.let { "&cursor=${io.github.seektop.data.mail.mailQuery(it)}" }.orEmpty() else ""
                val response = account.manage(path)
                val data = response["data"]
                val rows = when (data) {
                    is JsonArray -> data.mapNotNull { it as? JsonObject }
                    is JsonObject -> listOf(data)
                    else -> emptyList()
                }
                mutable.update { it.copy(rows = rows, busy = false, nextCursor = (response["page"] as? JsonObject)?.get("nextCursor")?.jsonPrimitive?.content.orEmpty()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun inspect(path: String) {
        viewModelScope.launch {
            try {
                val reply = account.manage(path)
                mutable.update { it.copy(detail = reply) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun mutate(path: String, method: String, body: JsonObject? = null) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val reply = account.manage(path, method, body)
                mutable.update { it.copy(result = reply.takeIf { value -> value.isNotEmpty() }, busy = false) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun upload() {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val reply = account.uploadAttachment()
                mutable.update { it.copy(busy = false, result = reply) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    private fun failed(e: Exception) {
        mutable.update { it.copy(busy = false, error = (e as? SeekLiException)?.status ?: 0, errorDescription = (e as? SeekLiException)?.let { failure -> listOfNotNull(failure.apiCode, failure.apiMessage).joinToString("\n") }.orEmpty()) }
    }
}
