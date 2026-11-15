package io.github.seektop.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import io.github.seektop.ai.TradeCalculator
import io.github.seektop.ai.TradeValuation
import io.github.seektop.ai.TradeValuationInput
import io.github.seektop.foundation.designsys.component.rememberClipboardCopy
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.ai_add
import io.github.seektop.ui.resources.ai_asking
import io.github.seektop.ui.resources.ai_basis
import io.github.seektop.ui.resources.ai_board
import io.github.seektop.ui.resources.ai_calculate
import io.github.seektop.ui.resources.ai_close
import io.github.seektop.ui.resources.ai_common
import io.github.seektop.ui.resources.ai_consent
import io.github.seektop.ui.resources.ai_copied
import io.github.seektop.ui.resources.ai_copy
import io.github.seektop.ui.resources.ai_currency
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
import io.github.seektop.ui.resources.ai_expiry
import io.github.seektop.ui.resources.ai_extract
import io.github.seektop.ui.resources.ai_failed
import io.github.seektop.ui.resources.ai_fee
import io.github.seektop.ui.resources.ai_follow
import io.github.seektop.ui.resources.ai_images
import io.github.seektop.ui.resources.ai_invalid_trade
import io.github.seektop.ui.resources.ai_key
import io.github.seektop.ui.resources.ai_key_saved
import io.github.seektop.ui.resources.ai_model
import io.github.seektop.ui.resources.ai_name
import io.github.seektop.ui.resources.ai_no_proposal
import io.github.seektop.ui.resources.ai_period
import io.github.seektop.ui.resources.ai_plugin_needed
import io.github.seektop.ui.resources.ai_prompt
import io.github.seektop.ui.resources.ai_prompt_hint
import io.github.seektop.ui.resources.ai_provider
import io.github.seektop.ui.resources.ai_result
import io.github.seektop.ui.resources.ai_save
import io.github.seektop.ui.resources.ai_saved
import io.github.seektop.ui.resources.ai_security
import io.github.seektop.ui.resources.ai_send
import io.github.seektop.ui.resources.ai_settings
import io.github.seektop.ui.resources.ai_start
import io.github.seektop.ui.resources.ai_stop
import io.github.seektop.ui.resources.ai_test
import io.github.seektop.ui.resources.ai_test_ok
import io.github.seektop.ui.resources.ai_title
import io.github.seektop.ui.resources.ai_trade
import io.github.seektop.ui.resources.ai_trade_note
import io.github.seektop.ui.resources.ai_truncated
import io.github.seektop.ui.resources.ai_vision
import io.github.seektop.ui.resources.ai_vision_note
import io.github.seektop.ui.richtext.PostRichContent
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.widthIn(max = 1140.dp).fillMaxWidth().heightIn(max = 820.dp).padding(16.dp)) {
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
    var settingsOpen by remember { mutableStateOf(false) }
    var images by remember(post) { mutableStateOf(emptySet<String>()) }
    var question by remember(post) { mutableStateOf("") }
    var calculator by remember { mutableStateOf(false) }
    val copy = rememberClipboardCopy()
    val copied = stringResource(Res.string.ai_copied)
    val language = Locale.current.toLanguageTag()
    val vision = state.settings?.providers?.find { it.id == state.settings?.selectedProvider }?.vision == true
    AiWindow(stringResource(Res.string.ai_title), onClose) {
        Text(post.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { settingsOpen = true }, enabled = !state.busy) { Text(stringResource(Res.string.ai_settings)) }
            TextButton(onClick = { calculator = !calculator }) { Text(stringResource(Res.string.ai_trade)) }
            TextButton(onClick = { copy("ai", state.output, copied) }, enabled = state.output.isNotBlank()) { Text(stringResource(Res.string.ai_copy)) }
        }
        if (state.loading) CircularProgressIndicator()
        if (!state.loading && !state.available) Text(stringResource(Res.string.ai_plugin_needed))
        state.error?.let { AiError(it) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 780.dp
            if (calculator) {
                TradeForm(service.today(), state.output)
            } else if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.width(330.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnalysisInput(post, state, vision, images, { images = it }, vm, language)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { AnalysisOutput(state.output) }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnalysisInput(post, state, vision, images, { images = it }, vm, language)
                    HorizontalDivider()
                    AnalysisOutput(state.output)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = question, onValueChange = { if (it.length <= 8000) question = it }, label = { Text(stringResource(Res.string.ai_follow)) }, modifier = Modifier.weight(1f), enabled = !state.busy)
            Button(onClick = {
                vm.ask(question)
                question = ""
            }, enabled = !state.busy && state.history.isNotEmpty() && state.error == null && question.isNotBlank()) { Text(stringResource(Res.string.ai_send)) }
        }
    }
    if (settingsOpen) {
        AiSettingsDialog(service) { saved ->
            settingsOpen = false
            if (saved) {
                vm.reload()
                images = emptySet()
            }
        }
    }
}

@Composable
private fun AnalysisInput(post: AiPost, state: AiAnalysisState, vision: Boolean, images: Set<String>, onImages: (Set<String>) -> Unit, vm: AiAnalysisViewModel, language: String) {
    state.settings?.let { config ->
        config.providers.find { it.id == config.selectedProvider }?.let { Text(stringResource(Res.string.ai_provider) + ": " + it.name + " · " + it.model, style = MaterialTheme.typography.titleSmall) }
    }
    Text(stringResource(Res.string.ai_consent), style = MaterialTheme.typography.bodySmall)
    if (post.truncated) Text(stringResource(Res.string.ai_truncated), color = MaterialTheme.colorScheme.error)
    OutlinedTextField(value = state.prompt, onValueChange = vm::prompt, label = { Text(stringResource(Res.string.ai_prompt)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), enabled = !state.busy)
    if (post.images.isNotEmpty()) {
        Text(stringResource(Res.string.ai_images), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(Res.string.ai_vision_note), style = MaterialTheme.typography.bodySmall)
        post.images.forEachIndexed { index, url ->
            Row {
                Checkbox(checked = url in images, enabled = vision && !state.busy && (url in images || images.size < 8), onCheckedChange = { onImages(if (it) images + url else images - url) })
                SelectionContainer { Text("${index + 1}. $url", maxLines = 2, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (state.busy) {
        Button(onClick = vm::stop) { Text(stringResource(Res.string.ai_stop)) }
    } else {
        Button(onClick = { vm.start(if (vision) images.toList() else emptyList(), language) }, enabled = state.available && !state.loading) { Text(stringResource(Res.string.ai_start)) }
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
    val service = LocalPostAnalysis.current ?: return
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(stringResource(Res.string.ai_settings)) }
    if (open) AiSettingsDialog(service) { open = false }
}

@Composable
private fun AiSettingsDialog(service: PostAnalysisService, onClose: (Boolean) -> Unit) {
    val vm: AiSettingsViewModel = viewModel(viewModelStoreOwner = rememberAiOwner(), key = "ai-settings", factory = viewModelFactory { initializer { AiSettingsViewModel(service) } })
    val state by vm.state.collectAsState()
    var providerId by remember { mutableStateOf<String?>(null) }
    var key by remember(providerId, state.keyRevision) { mutableStateOf("") }
    var board by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }
    val boards = LocalAiBoardKeys.current
    AiWindow(stringResource(Res.string.ai_settings), { if (!state.busy) onClose(state.keyRevision > 0) }) {
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

@Composable
private fun AiField(value: String, onValueChange: (String) -> Unit, label: StringResource, enabled: Boolean = true, singleLine: Boolean = true) {
    OutlinedTextField(value, onValueChange, label = { Text(stringResource(label)) }, modifier = Modifier.fillMaxWidth(), singleLine = singleLine, enabled = enabled, minLines = if (singleLine) 1 else 3)
}

@Composable
private fun TradeForm(today: String, output: String) {
    var basis by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var period by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<TradeValuation?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var noProposal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.ai_trade_note), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            try {
                val block = Regex("```seektop-valuation\\s*([\\s\\S]*?)```").find(output)?.groupValues?.get(1) ?: error("missing")
                val q = Json.parseToJsonElement(block).jsonObject
                fun value(k: String) = q[k]?.jsonPrimitive?.content?.takeUnless { it == "null" }.orEmpty()
                basis = value("basisPrice")
                asking = value("askingPrice")
                fee = value("fee").ifBlank { "0" }
                period = value("periodDays")
                expiry = value("expiryDate")
                currency = value("currency")
                result = null
                invalid = false
                noProposal = false
            } catch (_: Exception) {
                noProposal = true
            }
        }, enabled = output.isNotBlank()) { Text(stringResource(Res.string.ai_extract)) }
        if (noProposal) Text(stringResource(Res.string.ai_no_proposal))
        AiField(basis, {
            basis = it
            result = null
        }, Res.string.ai_basis)
        AiField(asking, {
            asking = it
            result = null
        }, Res.string.ai_asking)
        AiField(fee, {
            fee = it
            result = null
        }, Res.string.ai_fee)
        AiField(period, {
            period = it
            result = null
        }, Res.string.ai_period)
        AiField(expiry, {
            expiry = it
            result = null
        }, Res.string.ai_expiry)
        AiField(currency, {
            currency = it
            result = null
        }, Res.string.ai_currency)
        Button(onClick = {
            try {
                require(currency.isNotBlank())
                result = TradeCalculator.calculate(TradeValuationInput(basis, asking, fee, period.toInt(), expiry, currency), LocalDate.parse(today))
                invalid = false
            } catch (_: Exception) {
                result = null
                invalid = true
            }
        }) { Text(stringResource(Res.string.ai_calculate)) }
        if (invalid) Text(stringResource(Res.string.ai_invalid_trade), color = MaterialTheme.colorScheme.error)
        result?.let { Text(stringResource(Res.string.ai_result, it.remainingDays.toString(), "${TradeCalculator.money(it.remainingMinor)} $currency", "${TradeCalculator.money(it.totalMinor)} $currency", "${TradeCalculator.money(it.premiumMinor)} $currency", it.premiumPercent?.let { p -> "$p%" } ?: "—"), style = MaterialTheme.typography.titleMedium) }
    }
}
