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
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.breakpointmanager.model.BreakpointRuleUiModel

/**
 * Modal form dialog for creating or editing a breakpoint interception rule.
 */
@Composable
public fun AddEditBreakpointRuleDialog(
    rule: BreakpointRuleUiModel?,
    onDismiss: () -> Unit,
    onSave: (urlPattern: String, method: HttpMethod?, phase: BreakpointPhase, enabled: Boolean, protocolCriteria: ProtocolMatchCriteria) -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    var urlPattern by remember(rule) { mutableStateOf(rule?.urlPattern ?: "") }
    var selectedMethod by remember(rule) { mutableStateOf(rule?.method) }
    var selectedPhase by remember(rule) { mutableStateOf(rule?.phase ?: BreakpointPhase.BOTH) }
    var enabled by remember(rule) { mutableStateOf(rule?.enabled ?: true) }

    val initialGraphqlOp = (rule?.protocolCriteria as? ProtocolMatchCriteria.GraphQL)?.operationName ?: ""
    var isGraphqlProtocol by remember(rule) { mutableStateOf(rule?.protocolCriteria is ProtocolMatchCriteria.GraphQL) }
    var graphqlOperationName by remember(rule) { mutableStateOf(initialGraphqlOp) }

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

            // Protocol Selector (HTTP vs GraphQL)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Protocol Matching Criteria",
                    style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val protocolOptions = listOf(false to "HTTP / REST", true to "GraphQL")
                    protocolOptions.forEach { (isGql, label) ->
                        val isSelected = isGraphqlProtocol == isGql
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
                                .clickable { isGraphqlProtocol = isGql }
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

            // Optional GraphQL Operation Name Input
            if (isGraphqlProtocol) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "GraphQL Operation Name (Optional)",
                        style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.SemiBold)
                    )
                    KNetTextField(
                        value = graphqlOperationName,
                        onValueChange = { graphqlOperationName = it },
                        modifier = Modifier.fillMaxWidth(),
                        config = InputFieldConfig(
                            placeholder = "e.g. GetUserProfile or UpdateCart",
                            backgroundColor = themeColors.surfaceVariant,
                            borderColor = themeColors.border
                        )
                    )
                    Text(
                        text = "Only requests matching this operationName in JSON payload will pause.",
                        style = typography.caption.copy(color = themeColors.textMuted, fontSize = 10.sp)
                    )
                }
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
                    val availableMethods: List<HttpMethod?> = listOf(null, HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH)
                    availableMethods.forEach { methodOpt ->
                        val isSelected = selectedMethod == methodOpt
                        val label = methodOpt?.name ?: "ALL"
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
                        if (urlPattern.isNotBlank()) {
                            val criteria: ProtocolMatchCriteria = if (isGraphqlProtocol) {
                                ProtocolMatchCriteria.GraphQL(operationName = graphqlOperationName.trim().ifEmpty { null })
                            } else {
                                ProtocolMatchCriteria.HttpDefault
                            }
                            onSave(urlPattern.trim(), selectedMethod, selectedPhase, enabled, criteria)
                        }
                    },
                    variant = ButtonVariant.Primary,
                    enabled = urlPattern.isNotBlank()
                ) {
                    Text("Save Rule")
                }
            }
        }
    }
}
