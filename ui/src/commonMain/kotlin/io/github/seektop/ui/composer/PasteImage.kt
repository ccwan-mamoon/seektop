package io.github.seektop.ui.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.seektop.data.imagehost.ImageHostProvider
import io.github.seektop.data.imagehost.ImageHostUpload
import io.github.seektop.data.imagehost.preferredUploadHost
import io.github.seektop.foundation.core.runCatchingExceptCancellation
import io.github.seektop.ui.account.nameRes
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.paste_image_choose
import io.github.seektop.ui.resources.paste_image_close
import io.github.seektop.ui.resources.paste_image_failed
import io.github.seektop.ui.resources.paste_image_missing
import io.github.seektop.ui.resources.paste_image_priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal expect fun clipboardImageModifier(onImage: (ImageHostUpload) -> Unit): Modifier

@Composable
internal fun rememberPasteImageModifier(state: TextFieldState): Modifier {
    val repository = LocalImageHostRepository.current ?: return Modifier
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ImageHostUpload?>(null) }
    var choices by remember { mutableStateOf<List<ImageHostProvider>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(Res.string.paste_image_failed)
    val missing = stringResource(Res.string.paste_image_missing)
    fun upload(host: ImageHostProvider, image: ImageHostUpload) {
        busy = true
        scope.launch {
            runCatchingExceptCancellation { repository.uploadTo(host, image) }.fold(
                onSuccess = { result ->
                    state.edit {
                        val start = minOf(selection.start, selection.end)
                        val end = maxOf(selection.start, selection.end)
                        replace(start, end, "\n![](${result.url})\n")
                    }
                    pending = null
                },
                onFailure = { message = failed },
            )
            busy = false
        }
    }
    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
    }
    if (choices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                choices = emptyList()
                pending = null
            },
            title = { Text(stringResource(Res.string.paste_image_choose)) },
            text = {
                Column {
                    choices.forEach { host ->
                        TextButton(onClick = {
                            val image = pending ?: return@TextButton
                            val order = listOf(host) + choices.filter { it != host }
                            choices = emptyList()
                            busy = true
                            scope.launch {
                                runCatchingExceptCancellation { repository.setUploadPriority(order) }.fold(
                                    onSuccess = { upload(host, image) },
                                    onFailure = {
                                        busy = false
                                        message = failed
                                    },
                                )
                            }
                        }) { Text(stringResource(host.nameRes())) }
                    }
                }
            },
            confirmButton = {},
        )
    }
    if (message != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(message.orEmpty()) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(Res.string.paste_image_close)) } },
        )
    }
    return clipboardImageModifier { image ->
        if (!busy && choices.isEmpty()) {
            pending = image
            busy = true
            scope.launch {
                runCatchingExceptCancellation {
                    val hosts = ImageHostProvider.entries.filter { repository.config(it).first().isConfigured }
                    hosts to preferredUploadHost(hosts, repository.uploadPriority.first())
                }.fold(
                    onSuccess = { (hosts, preferred) ->
                        busy = false
                        when {
                            hosts.isEmpty() -> message = missing
                            preferred != null -> upload(preferred, image)
                            else -> choices = hosts
                        }
                    },
                    onFailure = {
                        busy = false
                        message = failed
                    },
                )
            }
        }
    }
}

@Composable
internal fun UploadPrioritySettings() {
    val repository = LocalImageHostRepository.current ?: return
    val scope = rememberCoroutineScope()
    val order by repository.uploadPriority.collectAsState(emptyList())
    var available by remember { mutableStateOf<List<ImageHostProvider>>(emptyList()) }
    LaunchedEffect(repository) {
        kotlinx.coroutines.flow.combine(ImageHostProvider.entries.map { repository.config(it) }) { configs ->
            configs.filter { it.isConfigured }.map { it.provider }
        }.collect { available = it }
    }
    if (available.isNotEmpty()) {
        Column {
            Text(stringResource(Res.string.paste_image_priority))
            val hosts = order.filter { it in available } + available.filter { it !in order }
            hosts.forEachIndexed { index, host ->
                TextButton(onClick = {
                    scope.launch { repository.setUploadPriority(listOf(host) + hosts.filter { it != host }) }
                }) { Text("${index + 1}. ${stringResource(host.nameRes())}") }
            }
        }
    }
}
