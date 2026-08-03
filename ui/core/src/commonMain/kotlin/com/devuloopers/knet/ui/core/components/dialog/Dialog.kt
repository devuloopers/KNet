package com.devuloopers.knet.ui.core.components.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun KNetDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    Dialog(onDismissRequest = onDismissRequest) {
        KNetSurface(
            modifier = modifier.width(400.dp),
            color = themeColors.surface,
            border = BorderStroke(1.dp, themeColors.border),
            shape = shapes.medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = typography.titleMedium.copy(color = themeColors.textPrimary),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}

@Composable
public fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel"
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title
    ) {
        Column {
            Text(
                text = message,
                style = typography.bodyMedium.copy(color = themeColors.textSecondary),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KNetButton(
                    onClick = onDismissRequest,
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Text(dismissText)
                }
                KNetButton(
                    onClick = {
                        onConfirm()
                        onDismissRequest()
                    },
                    variant = ButtonVariant.Primary,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}

@Composable
public fun AlertDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    ConfirmDialog(
        title = title,
        message = message,
        onConfirm = onDismissRequest,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        confirmText = "OK",
        dismissText = ""
    )
}

@Composable
public fun CustomDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    KNetDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content
    )
}
