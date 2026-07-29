package com.devuloopers.knet.ui.apistudio.view.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.widgets.TableCellTextField

private data class FormFieldPair(
    val key: String,
    val value: String,
    val isEnabled: Boolean = true
)

/**
 * Key-Value Table Grid for `x-www-form-urlencoded` and `form-data` payload modes.
 *
 * Provides a clean table interface using [TableCellTextField] for managing form parameters,
 * auto-parsing and serializing the body payload string in real-time.
 *
 * @param bodyPayload Raw body payload string.
 * @param onBodyChange Callback when the form key-value payload changes.
 * @param modifier Modifier applied to container.
 */
@Composable
internal fun FormDataTableGrid(
    bodyPayload: String,
    onBodyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pairs by remember(bodyPayload) {
        mutableStateOf(parseFormPairs(bodyPayload))
    }

    fun syncToBody(updatedPairs: List<FormFieldPair>) {
        pairs = updatedPairs
        val formattedBody = updatedPairs
            .filter { it.isEnabled && (it.key.isNotBlank() || it.value.isNotBlank()) }
            .joinToString("&") { "${it.key}=${it.value}" }
        onBodyChange(formattedBody)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(22.dp))
            Text("Key", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Value", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            Box(modifier = Modifier.width(22.dp))
        }
        HorizontalDivider(thickness = 1.dp, color = KNetColors.BorderDark.copy(alpha = 0.5f))

        // Table Rows
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            if (pairs.isEmpty()) {
                Text(
                    text = "No form parameters. Click '+ Add Parameter' below.",
                    color = KNetColors.TextSecondary.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                pairs.forEachIndexed { index, pair ->
                    if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = KNetColors.BorderDark.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Checkbox
                        Box(
                            modifier = Modifier.size(16.dp).clickable {
                                val updated = pairs.toMutableList()
                                updated[index] = pair.copy(isEnabled = !pair.isEnabled)
                                syncToBody(updated)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Toggle",
                                tint = if (pair.isEnabled) KNetColors.ActiveBlue else KNetColors.TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))

                        // Key Field
                        var keyTf by remember(pair.key) { mutableStateOf(TextFieldValue(pair.key, selection = TextRange(pair.key.length))) }
                        TableCellTextField(
                            value = keyTf,
                            onValueChange = { newKey ->
                                keyTf = newKey
                                val updated = pairs.toMutableList()
                                updated[index] = pair.copy(key = newKey.text)
                                syncToBody(updated)
                            },
                            placeholder = "Parameter Key",
                            textColor = if (pair.isEnabled) Color.White else KNetColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Value Field
                        var valTf by remember(pair.key, pair.value) { mutableStateOf(TextFieldValue(pair.value, selection = TextRange(pair.value.length))) }
                        TableCellTextField(
                            value = valTf,
                            onValueChange = { newVal ->
                                valTf = newVal
                                val updated = pairs.toMutableList()
                                updated[index] = pair.copy(value = newVal.text)
                                syncToBody(updated)
                            },
                            placeholder = "Parameter Value",
                            textColor = KNetColors.ActiveBlue,
                            modifier = Modifier.weight(1.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Delete row button
                        Box(
                            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clickable {
                                val updated = pairs.toMutableList()
                                updated.removeAt(index)
                                syncToBody(updated)
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Remove", tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = KNetColors.BorderDark.copy(alpha = 0.5f))

        // Footer Action Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val updated = pairs + FormFieldPair("", "")
                        syncToBody(updated)
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = KNetColors.ActiveBlue, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Parameter", color = KNetColors.ActiveBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun parseFormPairs(rawBody: String): List<FormFieldPair> {
    if (rawBody.isBlank()) return listOf(FormFieldPair("", ""))
    val lines = rawBody.split("&", "\n")
    val result = lines.mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) null
        else if (trimmed.contains("=")) {
            val parts = trimmed.split("=", limit = 2)
            FormFieldPair(key = parts[0].trim(), value = parts[1].trim())
        } else {
            FormFieldPair(key = trimmed, value = "")
        }
    }
    return result.ifEmpty { listOf(FormFieldPair("", "")) }
}
