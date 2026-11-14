package io.github.seektop.ui.common

import androidx.compose.runtime.Composable

internal actual val clipboardPostPromptSupported: Boolean = false

@Composable
internal actual fun rememberNewClipboardText(): () -> String? = { null }
