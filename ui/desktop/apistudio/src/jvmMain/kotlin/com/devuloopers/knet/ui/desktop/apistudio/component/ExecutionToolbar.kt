package com.devuloopers.knet.ui.desktop.apistudio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState

/**
 * Execution toolbar containing Send / Cancel HTTP request execution action buttons.
 *
 * @param executionState Current HTTP execution state.
 * @param onSend Callback when user clicks Send.
 * @param onCancel Callback when user clicks Cancel.
 * @param modifier Layout modifier.
 */
@Composable
public fun ExecutionToolbar(
    executionState: ExecutionState,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (executionState == ExecutionState.EXECUTING) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = KNetColors.ErrorRed),
                shape = KNetShapes.Medium
            ) {
                Text("Cancel", fontSize = 12.sp)
            }
        } else {
            Button(
                onClick = onSend,
                colors = ButtonDefaults.buttonColors(containerColor = KNetColors.ActiveBlue),
                shape = KNetShapes.Medium
            ) {
                Text("Send", fontSize = 12.sp)
            }
        }
    }
}
