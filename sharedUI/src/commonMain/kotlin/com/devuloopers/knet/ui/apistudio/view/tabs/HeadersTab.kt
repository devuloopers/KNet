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
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.widgets.TableCellTextField

/**
 * Headers tab content for the Request Builder panel.
 *
 * Renders a scrollable key-value header table with per-header enable/disable
 * toggles, editable fields, AUTO badge for system-injected headers, and
 * add / remove / restore-defaults actions.
 *
 * @param request The currently selected or draft [SavedApiRequest].
 * @param onToggleHeader Callback invoked when a header's enabled state is toggled.
 * @param onUpdateHeaderKey Callback invoked when a header key is renamed.
 * @param onUpdateHeaderValue Callback invoked when a header value changes.
 * @param onAddHeader Callback invoked when the "+ Add Header" button is clicked.
 * @param onRemoveHeader Callback invoked when a header row is deleted.
 * @param onRestoreDefaultHeaders Callback invoked when "Restore Auto Headers" is clicked.
 */
@Composable
internal fun HeadersTab(
    request: SavedApiRequest,
    onToggleHeader: (String) -> Unit,
    onUpdateHeaderKey: (String, String) -> Unit,
    onUpdateHeaderValue: (String, String) -> Unit,
    onAddHeader: () -> Unit,
    onRemoveHeader: (String) -> Unit,
    onRestoreDefaultHeaders: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        val enabledCount = request.headers.count { it.isEnabled }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("HTTP Request Headers ($enabledCount active)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.background(KNetColors.FieldDark, RoundedCornerShape(4.dp)).border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp)).clickable { onRestoreDefaultHeaders() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("↺ Restore Auto Headers", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(modifier = Modifier.background(KNetColors.ActiveBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp)).border(1.dp, KNetColors.ActiveBlue, RoundedCornerShape(4.dp)).clickable { onAddHeader() }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text("+ Add Header", color = KNetColors.ActiveBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, KNetColors.BorderDark.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // Table header
                Row(modifier = Modifier.fillMaxWidth().background(KNetColors.FieldDark.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(22.dp))
                    Text("Key", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Value", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                    Box(modifier = Modifier.width(22.dp))
                }
                HorizontalDivider(thickness = 1.dp, color = KNetColors.BorderDark.copy(alpha = 0.5f))

                if (request.headers.isEmpty()) {
                    Text("No headers. Click '+ Add Header' or '↺ Restore Auto Headers'.", color = KNetColors.TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                } else {
                    request.headers.forEachIndexed { index, header ->
                        if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = KNetColors.BorderDark.copy(alpha = 0.3f))
                        Row(modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Toggle checkbox
                            Box(modifier = Modifier.size(16.dp).clickable { onToggleHeader(header.key) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = "Toggle", tint = if (header.isEnabled) KNetColors.ActiveBlue else KNetColors.TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))

                            // Editable key field
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                var keyTf by remember(header.key) { mutableStateOf(TextFieldValue(header.key, selection = TextRange(header.key.length))) }
                                TableCellTextField(
                                    value = keyTf,
                                    onValueChange = { newKey -> keyTf = newKey; onUpdateHeaderKey(header.key, newKey.text) },
                                    placeholder = "Key",
                                    textColor = if (header.isEnabled) Color.White else KNetColors.TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (header.isAuto) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(modifier = Modifier.background(KNetColors.ActiveBlue.copy(alpha = 0.12f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text("AUTO", color = KNetColors.ActiveBlue, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            // Editable value field
                            var valTf by remember(header.key, header.value) { mutableStateOf(TextFieldValue(header.value, selection = TextRange(header.value.length))) }
                            TableCellTextField(
                                value = valTf,
                                onValueChange = { newValue -> valTf = newValue; onUpdateHeaderValue(header.key, newValue.text) },
                                placeholder = if (header.isAuto) "<auto>" else "Value",
                                textColor = KNetColors.ActiveBlue,
                                modifier = Modifier.weight(1.5f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // Remove button
                            Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clickable { onRemoveHeader(header.key) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove", tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
