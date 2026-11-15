package io.github.seektop.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seektop.ai.AiPost
import io.github.seektop.ai.AiProvider
import io.github.seektop.ai.PostAnalysisService
import io.github.seektop.foundation.designsys.component.rememberClipboardCopy
import io.github.seektop.foundation.designsys.theme.readableWidth
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_back
import io.github.seektop.ui.resources.ai_add
import io.github.seektop.ui.resources.ai_analyzing
import io.github.seektop.ui.resources.ai_board
import io.github.seektop.ui.resources.ai_close
import io.github.seektop.ui.resources.ai_common
import io.github.seektop.ui.resources.ai_copied
import io.github.seektop.ui.resources.ai_copy
import io.github.seektop.ui.resources.ai_delete
import io.github.seektop.ui.resources.ai_empty
import io.github.seektop.ui.resources.ai_endpoint
import io.github.seektop.ui.resources.ai_error_auth
import io.github.seektop.ui.resources.ai_error_busy
import io.github.seektop.ui.resources.ai_error_config
import io.github.seektop.ui.resources.ai_error_key
import io.github.seektop.ui.resources.ai_error_limit
import io.github.seektop.ui.resources.ai_error_network
import io.github.seektop.ui.resources.ai_error_storage
import io.github.seektop.ui.resources.ai_failed
import io.github.seektop.ui.resources.ai_include_images
import io.github.seektop.ui.resources.ai_key
import io.github.seektop.ui.resources.ai_key_saved
import io.github.seektop.ui.resources.ai_model
import io.github.seektop.ui.resources.ai_name
import io.github.seektop.ui.resources.ai_prompt_hint
import io.github.seektop.ui.resources.ai_retry
import io.github.seektop.ui.resources.ai_save
import io.github.seektop.ui.resources.ai_saved
import io.github.seektop.ui.resources.ai_security
import io.github.seektop.ui.resources.ai_settings
import io.github.seektop.ui.resources.ai_stop
import io.github.seektop.ui.resources.ai_test
import io.github.seektop.ui.resources.ai_test_ok
import io.github.seektop.ui.resources.ai_title
import io.github.seektop.ui.resources.ai_truncated
import io.github.seektop.ui.resources.ai_vision
import io.github.seektop.ui.resources.ai_vision_note
import io.github.seektop.ui.richtext.PostRichContent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
private fun rememberAiOwner(): ViewModelStoreOwner {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return owner
}

val LocalOpenAiSettings = staticCompositionLocalOf<() -> Unit> { {} }

val LocalPostAnalysis = staticCompositionLocalOf<PostAnalysisService?> { null }
val LocalAiBoardKeys = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
fun PostAnalysisAction(post: AiPost?) {
    val service = LocalPostAnalysis.current ?: return
    var open by remember(post?.id) { mutableStateOf(false) }
    TextButton(onClick = { open = true }, enabled = post != null) { Text(stringResource(Res.string.ai_title)) }
    if (open && post != null) {
        val vm: AiAnalysisViewModel = viewModel(viewModelStoreOwner = rememberAiOwner(), key = "analysis-${post.id}-${post.hashCode()}", factory = AiAnalysisViewModel.factory(service, post))
        DisposableEffect(vm) { onDispose { vm.stop() } }
        AnalysisDialog(service, post, vm) { open = false }
    }
}

@Composable
private fun AiWindow(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().heightIn(max = 720.dp).padding(16.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text(stringResource(Res.string.ai_close)) }
                }
                content()
            }
        }
    }
}

@Composable
private fun AnalysisDialog(service: PostAnalysisService, post: AiPost, vm: AiAnalysisViewModel, onClose: () -> Unit) {
    val state by vm.state.collectAsState()
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.ai_copied)
    val language = Locale.current.toLanguageTag()
    val openSettings = LocalOpenAiSettings.current
    LaunchedEffect(vm) { vm.analyzeOnce(language) }
    AiWindow(stringResource(Res.string.ai_title), onClose) {
        Row(Modifier.fillMaxWidth()) {
            Text(post.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            TextButton(onClick = {
                vm.stop()
                onClose()
                openSettings()
            }) { Text(stringResource(Res.string.ai_settings)) }
        }
        if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (post.truncated) Text(stringResource(Res.string.ai_truncated), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.error?.let { AiError(it) }
            if (state.output.isNotBlank()) {
                AnalysisOutput(state.output)
            } else if (state.error == null) {
                Text(stringResource(Res.string.ai_analyzing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { copy("ai", state.output, copied) }, enabled = state.output.isNotBlank()) { Text(stringResource(Res.string.ai_copy)) }
            if (state.busy) {
                TextButton(onClick = vm::stop) { Text(stringResource(Res.string.ai_stop)) }
            } else {
                TextButton(onClick = { vm.analyzeOnce(language, retry = true) }, enabled = !state.loading) { Text(stringResource(Res.string.ai_retry)) }
            }
        }
    }
}

@Composable
private fun AnalysisOutput(output: String) {
    val uriHandler = LocalUriHandler.current
    if (output.isBlank()) {
        Text(stringResource(Res.string.ai_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        // Markdown image syntax is rendered as literal text: model output must never initiate image requests.
        val nodes = remember(output) { io.github.seektop.ai.analysisMarkdown(output) }
        PostRichContent(nodes, onLinkClick = { if (it.startsWith("https://")) uriHandler.openUri(it) }, onImageClick = {})
    }
}

@Composable
private fun AiError(code: String) {
    val explanation = when {
        code == "AI_KEY_REQUIRED" -> Res.string.ai_error_key
        code in setOf("AI_HTTP_401", "AI_HTTP_403") -> Res.string.ai_error_auth
        code in setOf("AI_HTTP_402", "AI_HTTP_429") -> Res.string.ai_error_limit
        code == "AI_BUSY" -> Res.string.ai_error_busy
        code.contains("SETTINGS_UNAVAILABLE") || code.contains("ENCRYPTION") -> Res.string.ai_error_storage
        code.contains("INVALID") || code == "AI_VISION_UNAVAILABLE" || code == "AI_HTTP_400" || code == "AI_HTTP_404" -> Res.string.ai_error_config
        else -> Res.string.ai_error_network
    }
    Text(stringResource(Res.string.ai_failed, stringResource(explanation) + " (" + code.take(80) + ")"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun AiSettingsEntry() {
    if (LocalPostAnalysis.current == null) return
    val open = LocalOpenAiSettings.current
    OutlinedButton(onClick = open) { Text(stringResource(Res.string.ai_settings)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(service: PostAnalysisService, onBack: () -> Unit) {
    val vm: AiSettingsViewModel = viewModel(viewModelStoreOwner = rememberAiOwner(), key = "ai-settings", factory = viewModelFactory { initializer { AiSettingsViewModel(service) } })
    val state by vm.state.collectAsState()
    var providerId by remember { mutableStateOf<String?>(null) }
    var key by remember(providerId, state.keyRevision) { mutableStateOf("") }
    var board by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }
    val boards = LocalAiBoardKeys.current
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(Res.string.ai_settings)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back)) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().readableWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.ai_security), style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { AiError(it) }
            if (state.saved) Text(stringResource(if (state.tested) Res.string.ai_test_ok else Res.string.ai_saved))
            val config = state.settings
            if (config == null) {
                CircularProgressIndicator()
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val provider = config.providers.find { it.id == (providerId ?: config.selectedProvider) } ?: config.providers.first()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            OutlinedButton(onClick = { menu = true }, enabled = !state.busy) { Text(provider.name) }
                            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                config.providers.forEach { p ->
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(p.name) }, onClick = {
                                        providerId = p.id
                                        menu = false
                                        vm.edit(config.copy(selectedProvider = p.id))
                                    })
                                }
                            }
                        }
                        TextButton(onClick = {
                            val id = "provider-" + (1..999).first { n -> config.providers.none { it.id == "provider-$n" } }
                            val added = AiProvider(id, id, "https://api.deepseek.com", "deepseek-flash", true)
                            providerId = id
                            vm.edit(config.copy(providers = config.providers + added, selectedProvider = id))
                        }, enabled = !state.busy && config.providers.size < 8) { Text(stringResource(Res.string.ai_add)) }
                        TextButton(onClick = {
                            val remaining = config.providers.filterNot { it.id == provider.id }
                            providerId = remaining.first().id
                            vm.edit(config.copy(providers = remaining, selectedProvider = remaining.first().id))
                        }, enabled = !state.busy && config.providers.size > 1) { Text(stringResource(Res.string.ai_delete)) }
                    }
                    fun update(p: AiProvider) {
                        vm.edit(config.copy(providers = config.providers.map { if (it.id == p.id) p else it }))
                    }
                    AiField(provider.name, { update(provider.copy(name = it)) }, Res.string.ai_name, !state.busy)
                    AiField(provider.baseUrl, { update(provider.copy(baseUrl = it)) }, Res.string.ai_endpoint, !state.busy)
                    AiField(provider.model, { update(provider.copy(model = it)) }, Res.string.ai_model, !state.busy)
                    OutlinedTextField(value = key, onValueChange = {
                        key = it
                        vm.key(provider.id, it)
                    }, label = { Text(stringResource(Res.string.ai_key)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    if (provider.keySet) Text(stringResource(Res.string.ai_key_saved), style = MaterialTheme.typography.bodySmall)
                    Row {
                        Switch(provider.vision, { update(provider.copy(vision = it)) }, enabled = !state.busy)
                        Text(stringResource(Res.string.ai_vision), Modifier.padding(12.dp))
                    }
                    HorizontalDivider()
                    Row {
                        Switch(config.includeImages, { vm.edit(config.copy(includeImages = it)) }, enabled = !state.busy)
                        Text(stringResource(Res.string.ai_include_images), Modifier.padding(12.dp))
                    }
                    Text(stringResource(Res.string.ai_vision_note), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(Res.string.ai_prompt_hint), style = MaterialTheme.typography.bodySmall)
                    AiField(config.defaultPrompt, { vm.edit(config.copy(defaultPrompt = it.take(12000))) }, Res.string.ai_common, !state.busy, singleLine = false)
                    Column {
                        OutlinedButton(onClick = { boardMenu = true }, enabled = !state.busy) { Text(boards.entries.find { it.value == board }?.key ?: board.ifBlank { stringResource(Res.string.ai_board) }) }
                        androidx.compose.material3.DropdownMenu(expanded = boardMenu, onDismissRequest = { boardMenu = false }) {
                            (boards.values + config.boardPrompts.keys).distinct().forEach { slug ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(boards.entries.find { it.value == slug }?.key ?: slug) }, onClick = {
                                    board = slug
                                    boardMenu = false
                                })
                            }
                        }
                    }
                    if (board.isNotBlank()) AiField(config.boardPrompts[board].orEmpty(), { vm.edit(config.copy(boardPrompts = config.boardPrompts + (board to it.take(12000)))) }, Res.string.ai_board, !state.busy, singleLine = false)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.save(false) }, enabled = !state.busy && config != null) { Text(stringResource(Res.string.ai_save)) }
                OutlinedButton(onClick = { vm.save(true) }, enabled = !state.busy && config != null) { Text(stringResource(Res.string.ai_test)) }
            }
        }
    }
}

@Composable
private fun AiField(value: String, onValueChange: (String) -> Unit, label: StringResource, enabled: Boolean = true, singleLine: Boolean = true) {
    OutlinedTextField(value, onValueChange, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, enabled = enabled, minLines = if (singleLine) 1 else 3)
}
