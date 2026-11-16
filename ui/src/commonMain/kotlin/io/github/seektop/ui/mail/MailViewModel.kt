package io.github.seektop.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.seektop.data.mail.SeekLiAccount
import io.github.seektop.data.mail.SeekLiException
import io.github.seektop.data.mail.SeekLiMail
import io.github.seektop.data.mail.SeekLiMailbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MailUiState(
    val pollSeconds: Int = 60,
    val unread: Map<Long, Int> = emptyMap(),
    val connected: Boolean = false,
    val busy: Boolean = false,
    val error: Int? = null,
    val errorDescription: String = "",
    val sendError: Boolean = false,
    val mailboxes: List<SeekLiMailbox> = emptyList(),
    val mailbox: Long? = null,
    val folder: String = "inbox",
    val offset: Int = 0,
    val total: Int = 0,
    val mails: List<SeekLiMail> = emptyList(),
    val detail: SeekLiMail? = null,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val search: String = "",
    val folderId: Long? = null,
    val tagId: Long? = null,
    val folders: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    val tags: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    val menu: String? = null,
    val choices: List<kotlinx.serialization.json.JsonObject> = emptyList(),

)

class MailViewModel(private val account: SeekLiAccount, private val taskScope: CoroutineScope? = null) : ViewModel() {
    private val tasks: CoroutineScope get() = taskScope ?: viewModelScope
    private val mutable = MutableStateFlow(MailUiState())
    val state = mutable.asStateFlow()
    private var loading: Job? = null
    private var reading: Job? = null
    private val polling by lazy {
        MailPolling(
            tasks,
            allowed = { state.value.connected && !state.value.busy && !state.value.sending && reading?.isActive != true },
            fetch = {
                refresh(silent = true)
                loading?.join()
            },
        )
    }
    init {
        refresh()
        tasks.launch {
            val seconds = try {
                account.pollSeconds()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                60
            }
            mutable.update { it.copy(pollSeconds = seconds) }
            startPolling(seconds)
        }
    }
    fun setPollSeconds(seconds: Int) {
        tasks.launch {
            try {
                account.setPollSeconds(seconds)
                mutable.update { it.copy(pollSeconds = seconds) }
                startPolling(seconds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    private fun startPolling(seconds: Int) {
        polling.start(seconds)
    }
    fun clearError() {
        mutable.update { it.copy(error = null, errorDescription = "") }
    }
    fun connect(key: String) {
        loading?.cancel()
        loading = tasks.launch {
            mutable.update { it.copy(busy = true, error = null) }
            try {
                account.connect(key)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun disconnect() {
        loading?.cancel()
        reading?.cancel()
        loading = tasks.launch {
            try {
                account.disconnect()
                mutable.value = MailUiState(pollSeconds = state.value.pollSeconds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun select(mailbox: Long?, folder: String, offset: Int = 0, folderId: Long? = null, tagId: Long? = null) {
        reading?.cancel()
        mutable.update { it.copy(mailbox = mailbox, folder = folder, offset = offset, folderId = folderId, tagId = tagId, detail = null, mails = emptyList()) }
        refresh()
    }
    fun refresh(silent: Boolean = false) {
        loading?.cancel()
        loading = tasks.launch {
            mutable.update { it.copy(busy = true, error = if (silent) it.error else null) }
            try {
                if (!account.connected()) {
                    mutable.value = MailUiState(pollSeconds = state.value.pollSeconds)
                    return@launch
                }
                val boxes = account.mailboxes()
                val unread = try {
                    val counts = mutableMapOf<Long, Int>()
                    val cursors = mutableSetOf<String>()
                    var cursor = ""
                    do {
                        val reply = account.manage("/mails/unread?limit=100" + cursor.takeIf { it.isNotEmpty() }?.let { "&cursor=${io.github.seektop.data.mail.mailQuery(it)}" }.orEmpty())
                        (reply["data"] as? kotlinx.serialization.json.JsonArray)?.forEach { row ->
                            val id = (row as? kotlinx.serialization.json.JsonObject)?.get("mailboxId")?.toString()?.trim('"')?.toLongOrNull()
                            if (id != null) counts[id] = (counts[id] ?: 0) + 1
                        }
                        cursor = (reply["page"] as? kotlinx.serialization.json.JsonObject)?.get("nextCursor")?.toString()?.trim('"').orEmpty().takeUnless { it == "null" }.orEmpty()
                    } while (cursor.isNotEmpty() && cursors.add(cursor))
                    counts
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    state.value.unread
                }
                val current = state.value
                val page = account.queryMails(current.mailbox, current.folder, current.offset, current.search, current.folderId, current.tagId)
                val folders = try {
                    (account.manage("/folders")["data"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                val tags = try {
                    (account.manage("/tags")["data"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                mutable.update { it.copy(unread = unread, connected = true, busy = false, mailboxes = boxes, mails = page.data, total = page.pagination.total, folders = folders, tags = tags) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (silent) mutable.update { it.copy(busy = false) } else failed(e)
            }
        }
    }
    fun open(mail: SeekLiMail) {
        reading?.cancel()
        reading = tasks.launch {
            try {
                val detail = account.mail(mail.id)
                mutable.update { it.copy(detail = detail) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun markRead() {
        val mail = state.value.detail ?: return
        tasks.launch {
            try {
                account.markRead(mail.id, !mail.isRead)
                mutable.update { it.copy(unread = it.unread + (mail.mailboxId to ((it.unread[mail.mailboxId] ?: 0) + if (mail.isRead) 1 else -1).coerceAtLeast(0)), detail = mail.copy(isRead = !mail.isRead), mails = it.mails.map { row -> if (row.id == mail.id) row.copy(isRead = !mail.isRead) else row }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun send(mailbox: Long, to: String, subject: String, body: String, cc: String = "", bcc: String = "") {
        if (state.value.sending) return
        mutable.update { it.copy(sending = true, sent = false, error = null, errorDescription = "") }
        tasks.launch {
            try {
                account.sendWithCopies(mailbox, to.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }, cc.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }, bcc.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }, subject, body)
                mutable.update { it.copy(sending = false, sent = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(sending = false) }
                failed(e, sending = true)
            }
        }
    }
    fun search(query: String) {
        mutable.update { it.copy(search = query, offset = 0, detail = null) }
        refresh()
    }
    fun closeMenu() {
        mutable.update { it.copy(menu = null, choices = emptyList()) }
    }
    fun choices(kind: String) {
        val mail = state.value.detail ?: return
        tasks.launch {
            try {
                val path = when (kind) {
                    "folder" -> "/folders"
                    "tag" -> "/tags"
                    "untag" -> "/mails/${mail.id}"
                    else -> "/mails/${mail.id}/attachments"
                }
                val reply = account.manage(path)
                val data = reply["data"]
                val rows = if (kind == "untag") {
                    (data as? kotlinx.serialization.json.JsonObject)?.get("tags") as? kotlinx.serialization.json.JsonArray
                } else {
                    data as? kotlinx.serialization.json.JsonArray
                }
                mutable.update { it.copy(menu = kind, choices = rows?.mapNotNull { value -> value as? kotlinx.serialization.json.JsonObject }.orEmpty()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun operate(method: String, suffix: String, body: kotlinx.serialization.json.JsonObject? = null) {
        val mail = state.value.detail ?: return
        tasks.launch {
            try {
                account.manage("/mails/${mail.id}$suffix", method, body)
                closeMenu()
                mutable.update { it.copy(detail = null) }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun download(index: Int? = null, fileName: String? = null) {
        val mail = state.value.detail ?: return
        tasks.launch {
            try {
                account.download("/mails/${mail.id}/" + if (index == null) "raw" else "attachments/$index", fileName ?: "message-${mail.id}.eml")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }
    fun resetSent() {
        mutable.update { it.copy(sent = false) }
    }
    private fun failed(e: Exception, sending: Boolean = false) {
        mutable.update {
            it.copy(
                busy = false,
                error = when (e) {
                    is SeekLiException -> e.status
                    is kotlinx.serialization.SerializationException, is IllegalArgumentException -> 502
                    else -> 0
                },
                sendError = sending,
                errorDescription = (e as? SeekLiException)?.let { failure -> listOfNotNull(failure.apiCode, failure.apiMessage).joinToString("\n") }.orEmpty(),
            )
        }
    }
}
