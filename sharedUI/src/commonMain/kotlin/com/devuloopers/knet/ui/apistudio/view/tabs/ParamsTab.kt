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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.widgets.TableCellTextField

/**
 * Params tab content for the Request Builder panel.
 *
 * Renders a key-value table for managing URL query parameters. Edits sync back
 * to the URL string via [onUrlChange].
 *
 * @param request The currently selected or draft [SavedApiRequest].
 * @param onUrlChange Callback invoked whenever a param change requires URL update.
 */
@Composable
internal fun ParamsTab(
    request: SavedApiRequest,
    onUrlChange: (String) -> Unit
) {
    val baseUrl = request.url.substringBefore("?")
    val queryString = if (request.url.contains("?")) request.url.substringAfter("?").substringBefore("#") else ""

    var paramList by remember(request.id) {
        mutableStateOf(
            if (queryString.isNotBlank()) {
                queryString.split("&").mapNotNull { pair ->
                    val parts = pair.split("=")
                    if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                        parts[0] to (if (parts.size > 1) parts[1] else "")
                    } else null
                }.toMutableList()
            } else mutableListOf()
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Query Parameters (${paramList.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .background(KNetColors.ActiveBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, KNetColors.ActiveBlue, RoundedCornerShape(4.dp))
                    .clickable {
                        val updated = paramList + ("" to "")
                        paramList = updated.toMutableList()
                        val newQuery = updated.filter { it.first.isNotBlank() }.joinToString("&") { "${it.first}=${it.second}" }
                        onUrlChange(if (newQuery.isNotBlank()) "$baseUrl?$newQuery" else baseUrl)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("+ Add Parameter", color = KNetColors.ActiveBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, KNetColors.BorderDark.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().background(KNetColors.FieldDark.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Key", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Value", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                    Box(modifier = Modifier.width(20.dp))
                }

                HorizontalDivider(thickness = 1.dp, color = KNetColors.BorderDark.copy(alpha = 0.5f))

                if (paramList.isEmpty()) {
                    Text("No query parameters. Click '+ Add Parameter' or type ?key=value in URL.", color = KNetColors.TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                } else {
                    paramList.forEachIndexed { index, (key, value) ->
                        if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = KNetColors.BorderDark.copy(alpha = 0.3f))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var keyTf by remember(key) { mutableStateOf(TextFieldValue(key, selection = TextRange(key.length))) }
                            TableCellTextField(
                                value = keyTf,
                                onValueChange = { newKey ->
                                    keyTf = newKey
                                    val updated = paramList.toMutableList().apply { this[index] = newKey.text to value }
                                    paramList = updated
                                    val newQuery = updated.filter { it.first.isNotBlank() }.joinToString("&") { "${it.first}=${it.second}" }
                                    onUrlChange(if (newQuery.isNotBlank()) "$baseUrl?$newQuery" else baseUrl)
                                },
                                placeholder = "Key", textColor = Color.White, modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            var valTf by remember(value) { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }
                            TableCellTextField(
                                value = valTf,
                                onValueChange = { newVal ->
                                    valTf = newVal
                                    val updated = paramList.toMutableList().apply { this[index] = key to newVal.text }
                                    paramList = updated
                                    val newQuery = updated.filter { it.first.isNotBlank() }.joinToString("&") { "${it.first}=${it.second}" }
                                    onUrlChange(if (newQuery.isNotBlank()) "$baseUrl?$newQuery" else baseUrl)
                                },
                                placeholder = "Value", textColor = KNetColors.ActiveBlue, modifier = Modifier.weight(1.5f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clickable {
                                    val updated = paramList.toMutableList().apply { removeAt(index) }
                                    paramList = updated
                                    val newQuery = if (updated.isNotEmpty()) "?${updated.joinToString("&") { "${it.first}=${it.second}" }}" else ""
                                    onUrlChange("$baseUrl$newQuery")
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove", tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
