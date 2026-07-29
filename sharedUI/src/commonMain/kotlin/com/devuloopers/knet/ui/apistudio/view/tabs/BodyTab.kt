package com.devuloopers.knet.ui.apistudio.view.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.apistudio.view.CodeEditorWidget
import com.devuloopers.knet.ui.apistudio.view.bodyPayload
import com.devuloopers.knet.ui.apistudio.view.bodyType

/**
 * Body tab content for the Request Builder panel.
 *
 * Renders a body mode selector (none / json / form-data / x-www-form-urlencoded / raw / graphql)
 * and the corresponding code editor or placeholder for the selected mode.
 *
 * @param request The currently selected or draft [SavedApiRequest].
 * @param onBodyChange Callback invoked when the body text changes.
 * @param onBodyTypeChange Callback invoked when the body mode/type changes.
 */
@Composable
internal fun BodyTab(
    request: SavedApiRequest,
    onBodyChange: (String) -> Unit,
    onBodyTypeChange: (String) -> Unit
) {
    val bodyModes = listOf("none", "json", "form-data", "x-www-form-urlencoded", "raw", "graphql")
    val currentBodyType = request.bodyType.ifBlank { "json" }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Payload Mode:", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            bodyModes.forEach { mode ->
                val isSelected = currentBodyType.equals(mode, ignoreCase = true) ||
                        (mode == "raw" && currentBodyType.startsWith("raw"))
                Box(
                    modifier = Modifier
                        .background(if (isSelected) KNetColors.ActiveBlue else KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, if (isSelected) KNetColors.ActiveBlue else KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { onBodyTypeChange(if (mode == "raw") "raw-text" else mode) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(mode, color = if (isSelected) Color.White else KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = KNetColors.BorderDark.copy(alpha = 0.5f))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                currentBodyType.equals("none", ignoreCase = true) -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("This request does not have a body payload.", color = KNetColors.TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
                currentBodyType.equals("json", ignoreCase = true) -> {
                    CodeEditorWidget(code = request.bodyPayload, onCodeChange = onBodyChange, placeholder = "// Enter raw JSON payload content...\n{\n  \"key\": \"value\"\n}", textColor = Color(0xFFA855F7), modifier = Modifier.fillMaxSize())
                }
                currentBodyType.startsWith("raw", ignoreCase = true) -> {
                    RawBodySubEditor(currentBodyType = currentBodyType, bodyPayload = request.bodyPayload, onBodyChange = onBodyChange, onBodyTypeChange = onBodyTypeChange)
                }
                currentBodyType.equals("graphql", ignoreCase = true) -> {
                    CodeEditorWidget(code = request.bodyPayload, onCodeChange = onBodyChange, placeholder = "# Enter GraphQL Query / Mutation...\nquery GetUser {\n  user(id: 1) {\n    name\n  }\n}", textColor = Color(0xFFA855F7), modifier = Modifier.fillMaxSize())
                }
                currentBodyType.equals("form-data", ignoreCase = true) || currentBodyType.equals("x-www-form-urlencoded", ignoreCase = true) -> {
                    FormDataTableGrid(bodyPayload = request.bodyPayload, onBodyChange = onBodyChange, modifier = Modifier.fillMaxSize())
                }
                else -> {
                    CodeEditorWidget(code = request.bodyPayload, onCodeChange = onBodyChange, placeholder = "// Enter raw payload content...", textColor = Color(0xFFF59E0B), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Sub-editor for the "raw" body mode with a format dropdown.
 */
@Composable
private fun RawBodySubEditor(
    currentBodyType: String,
    bodyPayload: String,
    onBodyChange: (String) -> Unit,
    onBodyTypeChange: (String) -> Unit
) {
    val rawSubFormats = listOf("text", "json", "xml", "html", "javascript")
    val currentSubFormat = if (currentBodyType.contains("-")) currentBodyType.substringAfter("-") else "text"
    var rawFormatDropdownExpanded by remember { mutableStateOf(false) }
    var selectedSubFormat by remember(currentSubFormat) { mutableStateOf(currentSubFormat) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Format: ", color = KNetColors.TextSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Box {
                Box(
                    modifier = Modifier
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { rawFormatDropdownExpanded = !rawFormatDropdownExpanded }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedSubFormat.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                    }
                }
                DropdownMenu(
                    expanded = rawFormatDropdownExpanded,
                    onDismissRequest = { rawFormatDropdownExpanded = false },
                    modifier = Modifier.background(KNetColors.SurfaceDark).border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                ) {
                    rawSubFormats.forEach { fmt ->
                        DropdownMenuItem(
                            text = { Text(fmt.uppercase(), color = Color.White, fontSize = 10.sp) },
                            onClick = { selectedSubFormat = fmt; rawFormatDropdownExpanded = false; onBodyTypeChange("raw-$fmt") }
                        )
                    }
                }
            }
        }

        val rawSubFormat = if (currentBodyType.contains("-")) currentBodyType.substringAfter("-") else "text"
        val accentColor = when (rawSubFormat.lowercase()) { "xml" -> Color(0xFF06B6D4); "html" -> Color(0xFF0284C7); else -> Color(0xFFF8FAFC) }
        CodeEditorWidget(code = bodyPayload, onCodeChange = onBodyChange, placeholder = "// Enter raw $rawSubFormat payload content...", textColor = accentColor, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
