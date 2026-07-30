package com.devuloopers.knet.ui.apistudio.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Reusable Compose component rendering script execution syntax and runtime errors in a clean monospace code block.
 *
 * Preserves line breaks, indentation, and caret pointers (`^`) for GraalJS and Kotlin script stacktraces.
 *
 * @param title Error title header (e.g. "Syntax Error", "Runtime Error").
 * @param errorMessage Full error trace or stacktrace message.
 * @param modifier Compose Modifier.
 */
@Composable
fun ScriptErrorBlock(
    title: String = "Script Execution Error",
    errorMessage: String,
    modifier: Modifier = Modifier
) {
    val errorBorder = Color(0xFFEF4444)
    val errorBg = Color(0xFF1E1E24)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(errorBg, RoundedCornerShape(6.dp))
            .border(1.dp, errorBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Script Error",
                        tint = errorBorder,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title.uppercase(),
                        color = errorBorder,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "FAIL",
                    color = errorBorder,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Monospace Stacktrace Code Container
            SelectionContainer {
                Text(
                    text = errorMessage,
                    color = Color(0xFFF87171),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
