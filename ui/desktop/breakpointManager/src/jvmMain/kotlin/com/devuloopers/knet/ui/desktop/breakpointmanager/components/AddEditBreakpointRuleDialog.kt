package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.StandardHttpMethod
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Modal form dialog for creating or editing a breakpoint interception rule.
 */
@Composable
fun AddEditBreakpointRuleDialog(
    rule: BreakpointRule?,
    protocolDefinitions: List<BreakpointProtocolDefinition>,
    initialProtocolValues: List<ProtocolCriteriaValue>,
    onDismiss: () -> Unit,
    onSave: (
        urlPattern: String,
        method: HttpMethod?,
        phase: BreakpointPhase,
        enabled: Boolean,
        protocolId: BreakpointProtocolId,
        protocolValues: List<ProtocolCriteriaValue>,
    ) -> Unit,
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    var urlPattern by remember(rule) { mutableStateOf(rule?.urlPattern ?: "") }
    var selectedMethod by remember(rule) { mutableStateOf(rule?.method) }
    var selectedPhase by remember(rule) { mutableStateOf(rule?.phase ?: BreakpointPhase.BOTH) }
    var enabled by remember(rule) { mutableStateOf(rule?.enabled ?: true) }

    val defaultProtocolId = protocolDefinitions.firstOrNull {
        it.protocolId == BreakpointProtocolId.HTTP
    }?.protocolId ?: protocolDefinitions.firstOrNull()?.protocolId ?: BreakpointProtocolId.HTTP
    var selectedProtocolId by remember(rule, protocolDefinitions) {
        mutableStateOf(
            rule?.protocolCriteria?.protocolId
                ?.takeIf { selected -> protocolDefinitions.any { it.protocolId == selected } }
                ?: defaultProtocolId,
        )
    }
    var protocolValues by remember(rule, initialProtocolValues) {
        mutableStateOf(initialProtocolValues)
    }
    val selectedProtocol = protocolDefinitions.firstOrNull { it.protocolId == selectedProtocolId }

    val dialogTitle = if (rule != null) "Edit Breakpoint Rule" else "Add Breakpoint Rule"

    KNetDialog(
        onDismissRequest = onDismiss,
        title = dialogTitle,
        modifier = Modifier.width(480.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // URL Pattern Input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "URL Regex / Wildcard Pattern",
                    style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.SemiBold)
                )
                KNetTextField(
                    value = urlPattern,
                    onValueChange = { urlPattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    config = InputFieldConfig(
                        placeholder = "e.g. http://stg-04astra.cnbc.com/graphql",
                        backgroundColor = themeColors.surfaceVariant,
                        borderColor = themeColors.border
                    )
                )
            }

            // Protocol options are supplied by registered extensions rather than hardcoded here.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Protocol Matching Criteria",
                    style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    protocolDefinitions.forEach { definition ->
                        val isSelected = selectedProtocolId == definition.protocolId
                        Box(
                            modifier = Modifier
                                .widthIn(min = 112.dp, max = 200.dp)
                                .background(
                                    if (isSelected) themeColors.accent.copy(alpha = 0.2f) else themeColors.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) themeColors.accent else themeColors.border,
                                    RoundedCornerShape(4.dp)
                                )
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    selectedProtocolId = definition.protocolId
                                    protocolValues = definition.defaultValues()
                                }
                                .handCursor()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = definition.displayName,
                                color = if (isSelected) themeColors.accent else themeColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            selectedProtocol?.fields.orEmpty().forEach { field ->
                ProtocolCriteriaField(
                    field = field,
                    value = protocolValues.valueFor(field),
                    onValueChange = { value ->
                        protocolValues = protocolValues.withValue(field, value)
                    },
                )
            }

            // HTTP Method Selector (ALL + Enum Values)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "HTTP Method",
                    style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.SemiBold)
                )
                val methodScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(methodScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val availableMethods: List<HttpMethod?> = listOf(null) +
                        StandardHttpMethod.entries
                            .filterNot { it == StandardHttpMethod.CONNECT }
                            .map(HttpMethod::Standard)
                    availableMethods.forEach { methodOpt ->
                        val isSelected = selectedMethod == methodOpt
                        val label = methodOpt?.token ?: "ALL"
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) themeColors.accent.copy(alpha = 0.2f) else themeColors.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) themeColors.accent else themeColors.border,
                                    RoundedCornerShape(4.dp)
                                )
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { selectedMethod = methodOpt }
                                .handCursor()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) themeColors.accent else themeColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Interception Phase Segmented Control
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Interception Phase",
                    style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val phases = listOf(
                        BreakpointPhase.REQUEST to "Request",
                        BreakpointPhase.RESPONSE to "Response",
                        BreakpointPhase.BOTH to "Both"
                    )
                    phases.forEach { (phaseEnum, label) ->
                        val isSelected = selectedPhase == phaseEnum
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) themeColors.accent.copy(alpha = 0.2f) else themeColors.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) themeColors.accent else themeColors.border,
                                    RoundedCornerShape(4.dp)
                                )
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { selectedPhase = phaseEnum }
                                .handCursor()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) themeColors.accent else themeColors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Enable Rule Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable rule immediately",
                    style = typography.bodyMedium.copy(color = themeColors.textPrimary)
                )
                KNetSwitch(
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
            }

            // Form Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KNetButton(
                    onClick = onDismiss,
                    variant = ButtonVariant.Ghost,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Cancel")
                }
                KNetButton(
                    onClick = {
                        if (urlPattern.isNotBlank() && selectedProtocol != null) {
                            onSave(
                                urlPattern.trim(),
                                selectedMethod,
                                selectedPhase,
                                enabled,
                                selectedProtocolId,
                                protocolValues,
                            )
                        }
                    },
                    variant = ButtonVariant.Primary,
                    enabled = urlPattern.isNotBlank() && selectedProtocol != null
                ) {
                    Text("Save Rule")
                }
            }
        }
    }
}

@Composable
private fun ProtocolCriteriaField(
    field: ProtocolCriteriaFieldDefinition,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = field.label,
            style = typography.caption.copy(
                color = themeColors.textMuted,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        when (field) {
            is ProtocolCriteriaFieldDefinition.Text -> KNetTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                config = InputFieldConfig(
                    placeholder = field.placeholder,
                    backgroundColor = themeColors.surfaceVariant,
                    borderColor = themeColors.border,
                ),
            )

            is ProtocolCriteriaFieldDefinition.Choice -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                field.options.forEach { option ->
                    val isSelected = value == option.value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) themeColors.accent.copy(alpha = 0.2f)
                                else themeColors.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .border(
                                1.dp,
                                if (isSelected) themeColors.accent else themeColors.border,
                                RoundedCornerShape(4.dp),
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onValueChange(option.value) }
                            .handCursor()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            color = if (isSelected) themeColors.accent else themeColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        field.description?.let { description ->
            Text(
                text = description,
                style = typography.caption.copy(color = themeColors.textMuted, fontSize = 10.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun BreakpointProtocolDefinition.defaultValues(): List<ProtocolCriteriaValue> = fields.map { field ->
    ProtocolCriteriaValue(
        fieldId = field.id,
        value = when (field) {
            is ProtocolCriteriaFieldDefinition.Text -> ""
            is ProtocolCriteriaFieldDefinition.Choice -> field.defaultValue
        },
    )
}

private fun List<ProtocolCriteriaValue>.valueFor(field: ProtocolCriteriaFieldDefinition): String =
    firstOrNull { it.fieldId == field.id }?.value ?: when (field) {
        is ProtocolCriteriaFieldDefinition.Text -> ""
        is ProtocolCriteriaFieldDefinition.Choice -> field.defaultValue
    }

private fun List<ProtocolCriteriaValue>.withValue(
    field: ProtocolCriteriaFieldDefinition,
    value: String,
): List<ProtocolCriteriaValue> =
    filterNot { it.fieldId == field.id } + ProtocolCriteriaValue(field.id, value)
