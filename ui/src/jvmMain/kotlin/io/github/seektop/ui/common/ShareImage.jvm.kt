package io.github.seektop.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import io.github.seektop.foundation.core.AppDispatchers

/** No share sheet on a desktop, for the reason [rememberShareText] gives. */
@Composable
actual fun rememberShareImage(dispatchers: AppDispatchers): suspend (image: ImageBitmap, chooserTitle: String?) -> Boolean =
    { _, _ -> false }
