package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointPortCriteria
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.StandardHttpMethod
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawer
import com.devuloopers.knet.ui.core.components.drawer.KNetSideDrawerSize
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.InputFieldState
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Responsive side-drawer editor for creating or editing a breakpoint interception rule.
 *
 * The shell is shared with the other desktop workspace drawers, while this feature owns its form,
 * protocol-contributed fields, scrolling, validation, and save semantics.
 */
@Composable
fun AddEditBreakpointRuleDrawer(
    visible: Boolean,
    rule: BreakpointRule?,
    isEditingExistingRule: Boolean = rule != null,
    protocolDefinitions: List<BreakpointProtocolDefinition>,
    initialProtocolValues: List<ProtocolCriteriaValue>,
    onDismiss: () -> Unit,
    onSave: (
        urlPattern: String,
        portCriteria: BreakpointPortCriteria,
        method: HttpMethod?,
        phase: BreakpointPhase,
        enabled: Boolean,
        protocolId: BreakpointProtocolId,
        protocolValues: List<ProtocolCriteriaValue>,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetSideDrawer(
        visible = visible,
        size = KNetSideDrawerSize.STANDARD,
        modifier = modifier,
    ) {
        val initialRule = remember { rule }
        val initialIsEditingExistingRule = remember { isEditingExistingRule }
        var urlPattern by remember { mutableStateOf(initialRule?.urlPattern ?: "") }
        var portInput by remember {
            mutableStateOf((initialRule?.portCriteria as? BreakpointPortCriteria.Exact)?.value?.toString().orEmpty())
        }
        var portWasEdited by remember { mutableStateOf(initialRule != null) }
        var selectedMethod by remember { mutableStateOf(initialRule?.method) }
        var selectedPhase by remember { mutableStateOf(initialRule?.phase ?: BreakpointPhase.BOTH) }
        var enabled by remember { mutableStateOf(initialRule?.enabled ?: true) }

        val defaultProtocolId = protocolDefinitions.firstOrNull {
            it.protocolId == BreakpointProtocolId.HTTP
        }?.protocolId ?: protocolDefinitions.firstOrNull()?.protocolId ?: BreakpointProtocolId.HTTP
        var selectedProtocolId by remember {
            mutableStateOf(
                initialRule?.protocolCriteria?.protocolId
                    ?.takeIf { selected -> protocolDefinitions.any { it.protocolId == selected } }
                    ?: defaultProtocolId,
            )
        }
        var protocolValues by remember {
            mutableStateOf(initialProtocolValues)
        }
        val selectedProtocol = protocolDefinitions.firstOrNull { it.protocolId == selectedProtocolId }
        val parsedPort = portInput.toIntOrNull()?.takeIf { it in 1..65_535 }
        val isPortValid = portInput.isBlank() || parsedPort != null
        val portCriteria = parsedPort?.let(BreakpointPortCriteria::Exact) ?: BreakpointPortCriteria.Any
        val drawerTitle = if (initialIsEditingExistingRule) "Edit Breakpoint Rule" else "Add Breakpoint Rule"

        Column(
            modifier = Modifier.fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeColors.surfaceVariant)
                    .border(width = 1.dp, color = themeColors.border)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = drawerTitle,
                        style = typography.heading.copy(
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "Choose the transport match and any protocol-specific message criteria.",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary),
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close breakpoint rule editor",
                        tint = themeColors.textSecondary,
                    )
                }
            }

            val formScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(formScrollState)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
            // URL and port are authored independently so default ports never become hidden matching behavior.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 420.dp
                val updateUrl: (String) -> Unit = { updated ->
                    urlPattern = updated
                    if (!portWasEdited) {
                        portInput = suggestedPort(updated)?.toString().orEmpty()
                    }
                }
                val updatePort: (String) -> Unit = { updated ->
                    portWasEdited = true
                    portInput = updated.filter(Char::isDigit).take(5)
                }
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BreakpointUrlField(
                            value = urlPattern,
                            onValueChange = updateUrl,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BreakpointPortField(
                            value = portInput,
                            hint = suggestedPort(urlPattern) ?: DEFAULT_PORT_HINT,
                            isValid = isPortValid,
                            onValueChange = updatePort,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BreakpointUrlField(
                            value = urlPattern,
                            onValueChange = updateUrl,
                            modifier = Modifier.weight(1f),
                        )
                        BreakpointPortField(
                            value = portInput,
                            hint = suggestedPort(urlPattern) ?: DEFAULT_PORT_HINT,
                            isValid = isPortValid,
                            onValueChange = updatePort,
                            modifier = Modifier.widthIn(min = 96.dp, max = 112.dp),
                        )
                    }
                }
            }

            // Protocol options are supplied by registered extensions rather than hardcoded here.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Protocol Matching Criteria",
                    style = typography.caption.copy(color = themeColors.textMuted, fontWeight = FontWeight.SemiBold)
                )
                BreakpointChoiceFlow(
                    choices = protocolDefinitions.map { definition ->
                        BreakpointChoice(
                            value = definition,
                            label = definition.displayName,
                        )
                    },
                    isSelected = { definition -> selectedProtocolId == definition.protocolId },
                    onSelect = { definition ->
                        selectedProtocolId = definition.protocolId
                        protocolValues = definition.defaultValues()
                    },
                    minimumItemWidth = 120.dp,
                    maximumItemWidth = 240.dp,
                    minimumItemHeight = 40.dp,
                    horizontalContentPadding = 14.dp,
                    verticalContentPadding = 9.dp,
                )
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
                val availableMethods: List<HttpMethod?> = listOf(null) +
                    StandardHttpMethod.entries
                        .filterNot { it == StandardHttpMethod.CONNECT }
                        .map(HttpMethod::Standard)
                BreakpointChoiceFlow(
                    choices = availableMethods.map { method ->
                        BreakpointChoice(
                            value = method,
                            label = method?.token ?: "ALL",
                        )
                    },
                    isSelected = { method -> selectedMethod == method },
                    onSelect = { method -> selectedMethod = method },
                )
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
                                textAlign = TextAlign.Center,
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

                Spacer(Modifier.height(2.dp))
                }
                KNetVerticalScrollbar(
                    scrollState = formScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }

            // Keep primary actions visible while long contributed forms scroll independently.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeColors.surfaceVariant)
                    .border(width = 1.dp, color = themeColors.border)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
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
                        if (urlPattern.isNotBlank() && isPortValid && selectedProtocol != null) {
                            onSave(
                                urlPattern.trim(),
                                portCriteria,
                                selectedMethod,
                                selectedPhase,
                                enabled,
                                selectedProtocolId,
                                protocolValues,
                            )
                        }
                    },
                    variant = ButtonVariant.Primary,
                    enabled = urlPattern.isNotBlank() && isPortValid && selectedProtocol != null
                ) {
                    Text("Save Rule")
                }
            }
        }
    }
}

@Composable
private fun BreakpointUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "URL Regex / Wildcard Pattern",
            style = KNetTheme.typography.caption.copy(
                color = colors.textMuted,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        KNetTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            config = InputFieldConfig(
                placeholder = "e.g. https://api.example.com/graphql",
                backgroundColor = colors.surfaceVariant,
                borderColor = colors.border,
            ),
        )
    }
}

@Composable
private fun BreakpointPortField(
    value: String,
    hint: Int,
    isValid: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = KNetTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Port",
            style = KNetTheme.typography.caption.copy(
                color = colors.textMuted,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        KNetTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            config = InputFieldConfig(
                placeholder = hint.toString(),
                supportingText = if (isValid) null else "Use 1–65535",
                backgroundColor = colors.surfaceVariant,
                borderColor = if (isValid) colors.border else null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            ),
            state = InputFieldState(isError = !isValid),
        )
    }
}

private fun suggestedPort(urlPattern: String): Int? = when {
    urlPattern.trimStart().startsWith("https://", ignoreCase = true) -> 443
    urlPattern.trimStart().startsWith("http://", ignoreCase = true) -> 80
    else -> null
}

private const val DEFAULT_PORT_HINT: Int = 443

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

            is ProtocolCriteriaFieldDefinition.Choice -> {
                BreakpointChoiceFlow(
                    choices = field.options.map { option ->
                        BreakpointChoice(
                            value = option,
                            label = option.label,
                        )
                    },
                    isSelected = { option -> value == option.value },
                    onSelect = { option -> onValueChange(option.value) },
                    minimumItemWidth = 128.dp,
                    maximumItemWidth = 260.dp,
                    minimumItemHeight = 40.dp,
                    horizontalContentPadding = 14.dp,
                    verticalContentPadding = 9.dp,
                )
            }
        }
        field.description?.let { description ->
            Text(
                text = description,
                style = typography.caption.copy(color = themeColors.textMuted, fontSize = 10.sp),
            )
        }
    }
}

private data class BreakpointChoice<T>(
    val value: T,
    val label: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> BreakpointChoiceFlow(
    choices: List<BreakpointChoice<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    minimumItemWidth: Dp = Dp.Unspecified,
    maximumItemWidth: Dp = Dp.Unspecified,
    minimumItemHeight: Dp = Dp.Unspecified,
    horizontalContentPadding: Dp = 8.dp,
    verticalContentPadding: Dp = 6.dp,
) {
    val themeColors = KNetTheme.colors
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEach { choice ->
            val selected = isSelected(choice.value)
            Box(
                modifier = Modifier
                    .widthIn(min = minimumItemWidth, max = maximumItemWidth)
                    .heightIn(min = minimumItemHeight)
                    .background(
                        if (selected) themeColors.accent.copy(alpha = 0.2f) else themeColors.surfaceVariant,
                        RoundedCornerShape(4.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) themeColors.accent else themeColors.border,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelect(choice.value) }
                    .handCursor()
                    .padding(
                        horizontal = horizontalContentPadding,
                        vertical = verticalContentPadding,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = choice.label,
                    color = if (selected) themeColors.accent else themeColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
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
