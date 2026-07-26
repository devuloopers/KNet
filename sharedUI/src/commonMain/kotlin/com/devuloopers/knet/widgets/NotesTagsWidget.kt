package com.devuloopers.knet.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Provides a free-text notes area and a tag chip input for annotating the currently
 * selected HTTP transaction.
 *
 * Notes are stored as local Composable state (in-memory only). They are intended to
 * help developers record observations while debugging. Tags are entered as
 * comma-separated tokens displayed as chips below the notes field.
 *
 * @param modifier Optional [Modifier] for layout sizing and positioning.
 */
@Composable
fun NotesTagsWidget(modifier: Modifier = Modifier) {
    // Local state for notes text — stored per Composable instance (in-memory)
    var notes by remember { mutableStateOf("") }
    // Local state for the raw comma-separated tag input string
    var tagsInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Notes Section ---
        Text(
            text = "Notes",
            color = KNetColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        BasicTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    color = KNetColors.SurfaceDark,
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = 1.dp,
                    color = KNetColors.BorderDark,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(8.dp),
            textStyle = TextStyle(
                color = KNetColors.TextPrimary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(KNetColors.ActiveBlue),
            decorationBox = { innerTextField ->
                if (notes.isEmpty()) {
                    Text(
                        text = "Add notes for this request…",
                        color = KNetColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                innerTextField()
            }
        )

        // --- Tags Section ---
        Text(
            text = "Tags",
            color = KNetColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        BasicTextField(
            value = tagsInput,
            onValueChange = { tagsInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = KNetColors.SurfaceDark,
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = 1.dp,
                    color = KNetColors.BorderDark,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(8.dp),
            textStyle = TextStyle(
                color = KNetColors.TextPrimary,
                fontSize = 12.sp
            ),
            cursorBrush = SolidColor(KNetColors.ActiveBlue),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (tagsInput.isEmpty()) {
                    Text(
                        text = "e.g. auth, slow, staging",
                        color = KNetColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                innerTextField()
            }
        )

        // Render parsed tag chips
        val tags = tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tags.forEach { tag ->
                    Text(
                        text = tag,
                        color = KNetColors.ActiveBlue,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(
                                color = KNetColors.ActiveBlue.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
