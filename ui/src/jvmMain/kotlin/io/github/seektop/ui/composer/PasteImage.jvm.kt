package io.github.seektop.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.github.seektop.data.imagehost.ImageHostUpload
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Composable
internal actual fun clipboardImageModifier(onImage: (ImageHostUpload) -> Unit): Modifier =
    Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || event.key != Key.V) {
            false
        } else {
            val image = runCatching {
                val clip = Toolkit.getDefaultToolkit().systemClipboard
                if (!clip.isDataFlavorAvailable(DataFlavor.imageFlavor)) return@runCatching null
                val source = clip.getData(DataFlavor.imageFlavor) as Image
                val bitmap = BufferedImage(source.getWidth(null), source.getHeight(null), BufferedImage.TYPE_INT_ARGB)
                val graphics = bitmap.createGraphics()
                try {
                    graphics.drawImage(source, 0, 0, null)
                } finally {
                    graphics.dispose()
                }
                val bytes = ByteArrayOutputStream()
                ImageIO.write(bitmap, "png", bytes)
                ImageHostUpload(bytes.toByteArray(), "clipboard.png", "image/png")
            }.getOrNull()
            if (image == null) {
                false
            } else {
                onImage(image)
                true
            }
        }
    }
