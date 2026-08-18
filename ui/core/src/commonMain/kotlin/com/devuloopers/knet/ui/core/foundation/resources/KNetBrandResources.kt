package com.devuloopers.knet.ui.core.foundation.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import knet.ui.core.generated.resources.Res
import knet.ui.core.generated.resources.knet_logo
import org.jetbrains.compose.resources.painterResource

/** Returns the shared KNet application logo through Compose Multiplatform resources. */
@Composable
fun kNetLogoPainter(): Painter = painterResource(Res.drawable.knet_logo)
