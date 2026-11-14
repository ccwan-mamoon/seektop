package io.github.seektop.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.seektop.LocalDesktopExperience
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.action_close
import org.jetbrains.compose.resources.stringResource

/** Desktop dialogs share their content and dismissal guards with the mobile sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopModalSurface(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    shape: Shape,
    containerColor: Color,
    dragHandle: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalDesktopExperience.current) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState, shape = shape, containerColor = containerColor, dragHandle = dragHandle, content = content)
        return
    }
    Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier.widthIn(max = 760.dp).fillMaxWidth().heightIn(max = 640.dp), shape = RoundedCornerShape(12.dp), color = containerColor) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_close)) }
                }
                content()
            }
        }
    }
}
