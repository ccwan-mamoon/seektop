package io.github.seektop.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seektop.data.mail.SeekLiException
import io.github.seektop.data.mail.SeekLiRepository
import io.github.seektop.data.mail.mailQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal data class MailboxCreationState(
    val prefix: String = "",
    val domain: String = "nodeseek.org",
    val checking: Boolean = false,
    val available: Boolean? = null,
    val checkedAddress: String = "",
    val error: String = "",
) {
    val address: String get() = "$prefix@$domain"
    val valid: Boolean get() = prefix.isNotBlank() && isSeekLiMailboxAddress(address)
    val canCreate: Boolean get() = valid && !checking && available == true && checkedAddress == address
}

internal class MailboxCreationViewModel(
    private val repository: SeekLiRepository,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    private val mutable = MutableStateFlow(MailboxCreationState())
    val state = mutable.asStateFlow()
    private var checking: Job? = null
    private var revision = 0

    fun edit(prefix: String = state.value.prefix, domain: String = state.value.domain) {
        require(domain in listOf("nodeseek.org", "seek.li"))
        checking?.cancel()
        val generation = ++revision
        val next = MailboxCreationState(prefix = prefix, domain = domain)
        mutable.value = next
        if (!next.valid) return
        mutable.value = next.copy(checking = true)
        checking = (scope ?: viewModelScope).launch {
            try {
                delay(500)
                val reply = repository.manage("/mailboxes/check?address=${mailQuery(next.address)}", "POST")
                val available = ((reply["data"] as? JsonObject)?.get("available") as? JsonPrimitive)?.booleanOrNull
                    ?: throw SeekLiException(502)
                if (revision == generation) mutable.value = next.copy(available = available, checkedAddress = next.address)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == generation) mutable.value = next.copy(error = (e as? SeekLiException)?.let { listOfNotNull(it.apiCode, it.apiMessage, "(${it.status})").joinToString(" ") }.orEmpty())
            }
        }
    }
}
