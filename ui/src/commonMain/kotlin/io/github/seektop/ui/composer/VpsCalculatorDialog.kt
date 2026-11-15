package io.github.seektop.ui.composer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seektop.foundation.designsys.component.SeekTopIcons
import io.github.seektop.foundation.designsys.editor.insertText
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_close
import io.github.seektop.ui.resources.vps_buyer
import io.github.seektop.ui.resources.vps_buyer_premium
import io.github.seektop.ui.resources.vps_convention
import io.github.seektop.ui.resources.vps_daily
import io.github.seektop.ui.resources.vps_dates
import io.github.seektop.ui.resources.vps_days
import io.github.seektop.ui.resources.vps_error_amount
import io.github.seektop.ui.resources.vps_error_date
import io.github.seektop.ui.resources.vps_error_period
import io.github.seektop.ui.resources.vps_error_rate
import io.github.seektop.ui.resources.vps_expiry
import io.github.seektop.ui.resources.vps_hint
import io.github.seektop.ui.resources.vps_insert
import io.github.seektop.ui.resources.vps_item
import io.github.seektop.ui.resources.vps_manual
import io.github.seektop.ui.resources.vps_middleman
import io.github.seektop.ui.resources.vps_middleman_percent
import io.github.seektop.ui.resources.vps_na
import io.github.seektop.ui.resources.vps_payment
import io.github.seektop.ui.resources.vps_period
import io.github.seektop.ui.resources.vps_precise
import io.github.seektop.ui.resources.vps_push
import io.github.seektop.ui.resources.vps_remaining
import io.github.seektop.ui.resources.vps_remaining_days
import io.github.seektop.ui.resources.vps_renewal
import io.github.seektop.ui.resources.vps_residual_mode
import io.github.seektop.ui.resources.vps_result
import io.github.seektop.ui.resources.vps_seller
import io.github.seektop.ui.resources.vps_seller_premium
import io.github.seektop.ui.resources.vps_split
import io.github.seektop.ui.resources.vps_title
import io.github.seektop.ui.resources.vps_total
import io.github.seektop.ui.resources.vps_trade_date
import io.github.seektop.ui.resources.vps_trade_mode
import io.github.seektop.vps.VPS_CURRENCIES
import io.github.seektop.vps.VpsCalculatorService
import io.github.seektop.vps.VpsFeePayer
import io.github.seektop.vps.VpsInput
import io.github.seektop.vps.VpsResult
import org.jetbrains.compose.resources.stringResource

val LocalVpsCalculator = staticCompositionLocalOf<VpsCalculatorService?> { null }

@Composable
fun VpsCalculatorEntry(enabled: Boolean = true, onOpen: () -> Unit) {
    if (LocalVpsCalculator.current == null) return
    TextButton(onClick = onOpen, enabled = enabled, modifier = Modifier.padding(horizontal = 12.dp)) {
        Icon(SeekTopIcons.Calculate, contentDescription = null)
        Text(stringResource(Res.string.vps_title), Modifier.padding(start = 8.dp))
    }
}

@Composable
fun VpsCalculatorDialog(bodyState: TextFieldState, onDismiss: () -> Unit) {
    val service = LocalVpsCalculator.current ?: return
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val vm: VpsCalculatorViewModel = viewModel(viewModelStoreOwner = owner, factory = viewModelFactory { initializer { VpsCalculatorViewModel(service) } })
    val state by vm.state.collectAsState()
    val input = state.input
    val result = state.result
    var precise by remember { mutableStateOf(false) }
    val notes = buildList {
        @Composable fun source(code: String, rate: String): String {
            val cached = state.rates?.rates?.find { it.code == code && it.toCny == rate }
            return if (cached == null) "${stringResource(Res.string.vps_manual)} · ${service.today()}" else "${if (cached.manual) stringResource(Res.string.vps_manual) else "Frankfurter"} · ${cached.date}"
        }
        if (input.currency != "CNY") add("💱 1 ${input.currency} = ${input.toCny} CNY (${source(input.currency, input.toCny)})")
        if (input.transaction && input.pushCurrency != "CNY" && input.pushFee.toDoubleOrNull() != 0.0) add("💱 Push: 1 ${input.pushCurrency} = ${input.pushToCny} CNY (${source(input.pushCurrency, input.pushToCny)})")
        add(stringResource(Res.string.vps_convention))
    }
    val rows = buildList {
        if (result != null) {
            add("🔄 " + stringResource(Res.string.vps_renewal) to "${input.renewal} ${input.currency} / ${input.periodDays} ${stringResource(Res.string.vps_days)}")
            add("📅 " + stringResource(Res.string.vps_dates) to "${input.tradeDate} → ${input.expiryDate}")
            add("⏳ " + stringResource(Res.string.vps_remaining_days) to "${result.remainingDays} ${stringResource(Res.string.vps_days)}")
            add("💎 " + stringResource(Res.string.vps_remaining) to "**¥${result.remainingCny}** (${result.remainingOriginal} ${input.currency})")
            if (input.transaction) {
                add("🛒 " + stringResource(Res.string.vps_payment) to "¥${input.paymentCny}")
                add("🚀 " + stringResource(Res.string.vps_push) to "${stringResource(Res.string.vps_buyer)} ¥${result.buyerPush} / ${stringResource(Res.string.vps_seller)} ¥${result.sellerPush}")
                if (input.middleman) add("🤝 " + stringResource(Res.string.vps_middleman) to "${stringResource(Res.string.vps_buyer)} ¥${result.buyerMiddleman} / ${stringResource(Res.string.vps_seller)} ¥${result.sellerMiddleman}")
                add("📈 " + stringResource(Res.string.vps_seller_premium) to "¥${result.sellerPremium}")
                add("📊 " + stringResource(Res.string.vps_buyer_premium) to "¥${result.buyerPremium} / ${result.premiumPercent?.let { "$it%" } ?: stringResource(Res.string.vps_na)}")
                add("💳 " + stringResource(Res.string.vps_total) to "**¥${result.buyerTotal}**")
                add("📆 " + stringResource(Res.string.vps_daily) to (result.buyerDaily?.let { "¥$it" } ?: stringResource(Res.string.vps_na)))
            }
        }
    }
    val markdown = vpsMarkdown(stringResource(Res.string.vps_title), rows, notes, stringResource(Res.string.vps_item) to stringResource(Res.string.vps_result))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 920.dp).fillMaxWidth(0.92f).heightIn(max = 880.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧮 " + stringResource(Res.string.vps_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
                }
                BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                    val wide = maxWidth >= 740.dp
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(if (wide) 1.35f else 1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !input.transaction, onClick = { vm.edit { it.copy(transaction = false) } }, label = { Text(stringResource(Res.string.vps_residual_mode)) })
                                FilterChip(selected = input.transaction, onClick = { vm.edit { it.copy(transaction = true) } }, label = { Text(stringResource(Res.string.vps_trade_mode)) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                VpsField(input.renewal, { value -> vm.edit { it.copy(renewal = value) } }, stringResource(Res.string.vps_renewal), Modifier.weight(1f))
                                VpsSelect(input.currency, VPS_CURRENCIES, { vm.currency(it) })
                                VpsField(input.toCny, { value -> vm.edit { it.copy(toCny = value) } }, "1 ${input.currency} = CNY", Modifier.weight(1f), input.currency != "CNY")
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(30, 90, 180, 365, 730).forEach { days -> FilterChip(selected = input.periodDays == "$days", onClick = { vm.edit { it.copy(periodDays = "$days") } }, label = { Text("$days ${stringResource(Res.string.vps_days)}") }) }
                            }
                            VpsField(input.periodDays, { value -> vm.edit { it.copy(periodDays = value) } }, stringResource(Res.string.vps_period))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = precise, onCheckedChange = { enabled ->
                                    precise = enabled
                                    vm.edit {
                                        it.copy(
                                            tradeDate = if (enabled && it.tradeDate.length == 10) {
                                                it.tradeDate + " 00:00"
                                            } else if (!enabled) {
                                                it.tradeDate.take(10)
                                            } else {
                                                it.tradeDate
                                            },
                                            expiryDate = if (enabled && it.expiryDate.length == 10) {
                                                it.expiryDate + " 00:00"
                                            } else if (!enabled) {
                                                it.expiryDate.take(10)
                                            } else {
                                                it.expiryDate
                                            },
                                        )
                                    }
                                })
                                Text(stringResource(Res.string.vps_precise), Modifier.padding(start = 8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                VpsField(input.tradeDate, { value -> vm.edit { it.copy(tradeDate = value) } }, stringResource(Res.string.vps_trade_date), Modifier.weight(1f))
                                VpsField(input.expiryDate, { value -> vm.edit { it.copy(expiryDate = value) } }, stringResource(Res.string.vps_expiry), Modifier.weight(1f))
                            }
                            Text(if (precise) "YYYY-MM-DD HH:mm" else "YYYY-MM-DD", style = MaterialTheme.typography.bodySmall)
                            if (input.transaction) {
                                HorizontalDivider()
                                VpsField(input.paymentCny, { value -> vm.edit { it.copy(paymentCny = value) } }, stringResource(Res.string.vps_payment) + " (CNY)")
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    VpsField(input.pushFee, { value -> vm.edit { it.copy(pushFee = value) } }, stringResource(Res.string.vps_push), Modifier.weight(1f))
                                    VpsSelect(input.pushCurrency, VPS_CURRENCIES, { vm.currency(it, push = true) })
                                    VpsField(input.pushToCny, { value -> vm.edit { it.copy(pushToCny = value) } }, "1 ${input.pushCurrency} = CNY", Modifier.weight(1f), input.pushCurrency != "CNY")
                                }
                                PayerChoice(input.pushPayer) { value -> vm.edit { it.copy(pushPayer = value) } }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(input.middleman, { value -> vm.edit { it.copy(middleman = value) } })
                                    Text(stringResource(Res.string.vps_middleman), Modifier.padding(start = 8.dp))
                                }
                                if (input.middleman) {
                                    VpsField(input.middlemanPercent, { value -> vm.edit { it.copy(middlemanPercent = value) } }, stringResource(Res.string.vps_middleman_percent))
                                    PayerChoice(input.middlemanPayer) { value -> vm.edit { it.copy(middlemanPayer = value) } }
                                }
                            }
                            state.error?.let { code ->
                                Text(
                                    stringResource(
                                        when (code) {
                                            "VPS_RATE" -> Res.string.vps_error_rate
                                            "VPS_DATE" -> Res.string.vps_error_date
                                            "VPS_PERIOD" -> Res.string.vps_error_period
                                            else -> Res.string.vps_error_amount
                                        },
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (!wide) {
                                VpsSummary(input, result, rows, notes, state.error)
                            }
                        }
                        if (wide) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                VpsSummary(input, result, rows, notes, state.error)
                            }
                        }
                    }
                }
                Button(onClick = {
                    bodyState.edit {
                        val block = "\n\n$markdown\n\n"
                        val end = selection.min + block.length
                        insertText(block)
                        selection = TextRange(end)
                    }
                    onDismiss()
                }, enabled = result != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.vps_insert)) }
            }
        }
    }
}

@Composable
private fun VpsField(value: String, change: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true) {
    OutlinedTextField(value, { if (it.length <= 64) change(it) }, label = { Text(label) }, modifier = modifier, enabled = enabled, singleLine = true)
}

@Composable
private fun VpsSelect(value: String, options: List<String>, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) { Text("$value ▾") }
        DropdownMenu(open, { open = false }) {
            options.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = {
                    open = false
                    change(code)
                })
            }
        }
    }
}

@Composable
private fun PayerChoice(value: VpsFeePayer, change: (VpsFeePayer) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VpsFeePayer.entries.forEach { payer ->
            FilterChip(selected = value == payer, onClick = { change(payer) }, label = {
                Text(
                    stringResource(
                        when (payer) {
                            VpsFeePayer.BUYER -> Res.string.vps_buyer
                            VpsFeePayer.SELLER -> Res.string.vps_seller
                            VpsFeePayer.SPLIT -> Res.string.vps_split
                        },
                    ),
                )
            })
        }
    }
}

@Composable
private fun VpsSummary(input: VpsInput, result: VpsResult?, rows: List<Pair<String, String>>, notes: List<String>, error: String?) {
    if (result != null) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("💎 " + stringResource(Res.string.vps_remaining) + "  ¥${result.remainingCny}", style = MaterialTheme.typography.headlineSmall)
                rows.drop(4).forEach { (label, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        Text(value.replace("**", ""), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(stringResource(Res.string.vps_remaining_days) + " ${result.remainingDays} ${stringResource(Res.string.vps_days)}" + if (input.currency == "CNY") "" else " · ${result.remainingOriginal} ${input.currency}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(notes.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
    } else if (error == null) {
        Text(stringResource(Res.string.vps_hint), style = MaterialTheme.typography.bodySmall)
    }
}
