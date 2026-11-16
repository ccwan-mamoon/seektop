package io.github.seektop.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seektop.data.imagehost.ImageHostUpload

@Composable
internal actual fun clipboardImageModifier(onImage: (ImageHostUpload) -> Unit): Modifier = Modifier
