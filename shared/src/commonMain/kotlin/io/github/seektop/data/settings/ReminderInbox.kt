package io.github.seektop.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class ReminderKind { KEYWORD, TRACKED }

/** Device-local reminders survive sign-out, like the keywords and tracked threads that created them. */
@Serializable
data class Reminder(
    val id: String,
    val kind: ReminderKind,
    val postId: Long,
    val title: String,
    val detail: String,
    val createdAtMillis: Long,
    val isUnread: Boolean = true,
)

class ReminderInbox(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("desktop_reminder_inbox")
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(value: String?): List<Reminder> =
        value?.let { runCatching { json.decodeFromString<List<Reminder>>(it) }.getOrNull() }.orEmpty()

    val reminders: Flow<List<Reminder>> = store.data.map { decode(it[key]) }

    suspend fun add(reminders: List<Reminder>) {
        if (reminders.isEmpty()) return
        store.edit { prefs ->
            prefs[key] = json.encodeToString((decode(prefs[key]) + reminders).distinctBy { it.id }.sortedByDescending { it.createdAtMillis }.take(500))
        }
    }

    suspend fun markRead(id: String? = null) {
        store.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]).map { if (id == null || it.id == id) it.copy(isUnread = false) else it })
        }
    }
}
