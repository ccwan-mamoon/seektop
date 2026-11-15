package io.github.seektop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seektop.links.ExternalLinkRouting
import io.github.seektop.links.LinkRoutingStatus
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.link_routing_active
import io.github.seektop.ui.resources.link_routing_browser
import io.github.seektop.ui.resources.link_routing_disable
import io.github.seektop.ui.resources.link_routing_enable
import io.github.seektop.ui.resources.link_routing_error
import io.github.seektop.ui.resources.link_routing_hint
import io.github.seektop.ui.resources.link_routing_off
import io.github.seektop.ui.resources.link_routing_pending
import io.github.seektop.ui.resources.link_routing_refresh
import io.github.seektop.ui.resources.link_routing_restore
import io.github.seektop.ui.resources.link_routing_system
import io.github.seektop.ui.resources.link_routing_title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

val LocalExternalLinkRouting = staticCompositionLocalOf<ExternalLinkRouting?> { null }

data class ExternalLinkState(val status: LinkRoutingStatus? = null, val busy: Boolean = false, val error: String? = null)

class ExternalLinkSettingsViewModel(private val service: ExternalLinkRouting) : ViewModel() {
    private val mutable = MutableStateFlow(ExternalLinkState())
    val state = mutable.asStateFlow()
    fun refresh() = operation { service.status() }
    fun enable() = operation { service.prepare().also { service.openSystemSettings() } }
    fun disable() = operation { service.disable() }
    fun openSettings() = operation {
        service.openSystemSettings()
        service.status()
    }
    private fun operation(action: suspend () -> LinkRoutingStatus) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val status = action()
                mutable.update { it.copy(status = status) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                mutable.update { it.copy(error = e.message ?: "LINK_OPERATION_FAILED") }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}

@Composable
fun ExternalLinkSettingsEntry() {
    val service = LocalExternalLinkRouting.current ?: return
    val vm: ExternalLinkSettingsViewModel = viewModel(key = "external-links", factory = viewModelFactory { initializer { ExternalLinkSettingsViewModel(service) } })
    val state by vm.state.collectAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) vm.refresh() }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.link_routing_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.link_routing_hint), style = MaterialTheme.typography.bodySmall)
            val status = state.status
            Text(
                stringResource(
                    when {
                        status?.enabled != true -> Res.string.link_routing_off
                        status.httpActive && status.httpsActive -> Res.string.link_routing_active
                        else -> Res.string.link_routing_pending
                    },
                ),
            )
            if (status?.prepared == true) Text(stringResource(Res.string.link_routing_browser, listOf(status.httpName, status.httpsName).filter(String::isNotBlank).distinct().joinToString(" / ")), style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(stringResource(Res.string.link_routing_error, it.take(80)), color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (status?.enabled == true) vm.disable() else vm.enable() }, enabled = !state.busy) { Text(stringResource(if (status?.enabled == true) Res.string.link_routing_disable else Res.string.link_routing_enable)) }
                if (status?.prepared == true) TextButton(onClick = vm::openSettings, enabled = !state.busy) { Text(stringResource(Res.string.link_routing_system)) }
                TextButton(onClick = vm::refresh, enabled = !state.busy) { Text(stringResource(Res.string.link_routing_refresh)) }
            }
            if (status?.prepared == true) Text(stringResource(Res.string.link_routing_restore), style = MaterialTheme.typography.bodySmall)
        }
    }
}
