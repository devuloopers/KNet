package com.devuloopers.knet.ui.apistudio.view.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.widgets.KNetInputField

/**
 * Modal dialog for creating a new Collection or Folder using standard [KNetInputField].
 *
 * @param title Dialog title (e.g. "Create New Collection", "Add Folder").
 * @param placeholder Default text field placeholder.
 * @param onConfirm Callback with the entered name.
 * @param onDismiss Callback when dialog is cancelled.
 */
@Composable
fun CreateItemDialog(
    title: String,
    placeholder: String = "Enter name...",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .background(KNetColors.SurfaceDark, RoundedCornerShape(10.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(10.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(14.dp))

                KNetInputField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    placeholder = placeholder,
                    height = 36.dp,
                    fontSize = 12.sp,
                    cornerRadius = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Cancel", color = KNetColors.TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (nameText.isNotBlank()) KNetColors.ActiveBlue else KNetColors.ActiveBlue.copy(alpha = 0.4f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = nameText.isNotBlank()) {
                                onConfirm(nameText.trim())
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Create", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
