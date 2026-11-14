package io.github.seektop.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.seektop.data.composer.PickedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberImagePicker(
    maxItems: Int,
    fallbackName: String,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val selected = withContext(Dispatchers.IO) {
                val picker = JFileChooser().apply {
                    isMultiSelectionEnabled = maxItems > 1
                    fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "webp")
                }
                if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val files = if (maxItems > 1) picker.selectedFiles.toList() else listOf(picker.selectedFile)
                    files.take(maxItems).map { PickedImage(it.toURI().toString(), it.name.ifBlank { fallbackName }) }
                } else {
                    emptyList()
                }
            }
            if (selected.isNotEmpty()) onPicked(selected)
        }
    }
}
