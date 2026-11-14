package io.github.seektop

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.seektop_mark
import org.jetbrains.compose.resources.painterResource

/** The same monitor-and-seek mark is exported to the Windows icon bundle. */
@Composable
fun seekTopIconPainter(): Painter = painterResource(Res.drawable.seektop_mark)
