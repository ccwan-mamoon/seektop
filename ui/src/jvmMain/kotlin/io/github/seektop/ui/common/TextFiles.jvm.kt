package io.github.seektop.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/** Desktop document import and export use the platform file chooser. */
@Composable
internal actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                val picker = JFileChooser()
                if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    runCatching { picker.selectedFile.readText() }.getOrNull()
                } else {
                    null
                }
            }
            onPicked(text)
        }
    }
}

@Composable
internal actual fun rememberTextFileSaver(
    fileName: String,
    text: suspend () -> String,
    onSaved: (Boolean) -> Unit,
): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val content = text()
            val saved = withContext(Dispatchers.IO) {
                val picker = JFileChooser().apply { selectedFile = File(fileName) }
                if (picker.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    runCatching { picker.selectedFile.writeText(content) }.isSuccess
                } else {
                    false
                }
            }
            onSaved(saved)
        }
    }
}
