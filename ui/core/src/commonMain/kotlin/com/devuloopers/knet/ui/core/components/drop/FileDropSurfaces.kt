package com.devuloopers.knet.ui.core.components.drop

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.focusable
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Shared finite dialog body with reserved scrollbar clearance; small windows never clip actions. */
@Composable
fun KNetScrollableDialogBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val dimensions = KNetTheme.dimensions
    val spacing = KNetTheme.spacing
    Box(modifier.heightIn(max = dimensions.dialogContentMaxHeight)) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll)
            .padding(end = if (scroll.maxValue > 0) dimensions.scrollbarWidth + spacing.sm else spacing.none),
            verticalArrangement = Arrangement.spacedBy(spacing.sm), content = content)
        KNetVerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Themed dashed file target; dimensions and stroke layout stay in the shared design system. */
@Composable
fun KNetFileDropZone(highlighted: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = KNetTheme.colors
    val dimensions = KNetTheme.dimensions
    val spacing = KNetTheme.spacing
    val background by animateColorAsState(if (highlighted) colors.accent.copy(alpha = 0.08f) else colors.background)
    val border by animateColorAsState(if (highlighted) colors.accent else colors.border)
    Box(modifier.fillMaxWidth().heightIn(min = dimensions.fileDropTargetHeight)
        .background(background, KNetTheme.shapes.medium)
        .drawBehind {
            drawRoundRect(border,
                cornerRadius = CornerRadius(spacing.sm.toPx()),
                style = Stroke(dimensions.borderWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(spacing.sm.toPx(), spacing.xs.toPx()))))
        }.padding(spacing.lg), contentAlignment = Alignment.Center, content = content)
}

/**
 * Continuous in-window import surface: fixed theme bounds, background input blocking and keyboard containment.
 * The caller keeps this node mounted across hover, accepted native handoff, validation and ready/error states.
 */
@Composable
fun KNetFileImportOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f))
        .pointerInput(Unit) { detectTapGestures { currentDismiss() } }
        .border(KNetTheme.dimensions.borderWidth, colors.accent, KNetTheme.shapes.medium)
        .onPreviewKeyEvent { event ->
            when {
                event.type == KeyEventType.KeyDown && event.key == Key.Escape -> { onDismiss(); true }
                event.type == KeyEventType.KeyDown && event.key == Key.Tab -> {
                    focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                    true
                }
                else -> false
            }
        }
        .onKeyEvent { true }
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusRequester(focus).focusable().focusGroup()
        .padding(spacing.lg), contentAlignment = Alignment.Center) {
        KNetSurface(Modifier.widthIn(max = KNetTheme.dimensions.dialogWidthLarge).fillMaxWidth()
            .height(minOf(maxHeight, KNetTheme.dimensions.dialogContentMaxHeight + spacing.lg * 2))
            .semantics { paneTitle = "Import capture" }
            .pointerInput(Unit) {
                // Surface hits cannot reach the scrim or captured workspace; child controls consume first.
                awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() } }
            }, color = colors.surface,
            border = BorderStroke(KNetTheme.dimensions.borderWidth, colors.border), shape = KNetTheme.shapes.medium,
            contentAlignment = Alignment.TopStart) {
            Box(Modifier.fillMaxSize().padding(spacing.lg)) { content() }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
