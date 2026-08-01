package com.devuloopers.knet.ui.core.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.icon.KNetIcons
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Visual error banner featuring an error icon, error message text, and an optional retry action trigger.
 *
 * @param message Error message description.
 * @param onRetry Optional callback triggered when clicking retry.
 * @param modifier Layout modifier.
 */
@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.ErrorRed.copy(alpha = 0.1f), KNetShapes.Medium)
            .border(1.dp, KNetColors.ErrorRed.copy(alpha = 0.3f), KNetShapes.Medium)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = KNetIcons.ErrorIcon,
            contentDescription = "Error icon",
            tint = KNetColors.ErrorRed,
            modifier = Modifier.size(16.dp).padding(end = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Error Occurred",
                color = KNetColors.ErrorRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = KNetColors.TextPrimary,
                fontSize = 10.sp
            )
        }
        if (onRetry != null) {
            Icon(
                imageVector = KNetIcons.RefreshIcon,
                contentDescription = "Retry action",
                tint = KNetColors.ActiveBlue,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onRetry() }
            )
        }
    }
}
