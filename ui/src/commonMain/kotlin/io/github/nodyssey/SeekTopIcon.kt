package io.github.nodyssey

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.seektop_mark
import org.jetbrains.compose.resources.painterResource

/** The same monitor-and-seek mark is exported to the Windows icon bundle. */
@Composable
fun seekTopIconPainter(): Painter = painterResource(Res.drawable.seektop_mark)
