package io.github.seektop.ui.mail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seektop.data.mail.SeekLiAccount

@Composable
internal fun MailNavigationIcon(account: SeekLiAccount) {
    val model = viewModel(key = "seekli-mail") { MailViewModel(account) }
    val state by model.state.collectAsStateWithLifecycle()
    val count = state.unread.values.sum()
    BadgedBox(badge = { if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) } }) { Icon(Icons.Default.Email, null) }
}
