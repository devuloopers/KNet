package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.drawer.*
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.*
import com.devuloopers.knet.ui.core.components.inspector.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.*

/** Shared animated drawer shell with an independently scrolling form and fixed actions. */
@Composable
internal fun ProtectedRuleDrawer(state: ProtectedTrafficState, onChange: (ProtectedTrafficRuleDraft) -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit) {
    val draft = state.ruleDraft
    KNetSideDrawer(visible = draft != null, size = KNetSideDrawerSize.STANDARD) {
        // Keep the last draft visible through the shared exit animation; callbacks stay disabled once closed.
        var retained by remember { mutableStateOf(requireNotNull(draft)) }
        SideEffect { if (draft != null) retained = draft }
        val shown = draft ?: retained
        val editable = draft != null && !state.isSaving
        Column(Modifier.fillMaxSize().testTag("Protected rule editor")) {
            Row(Modifier.fillMaxWidth().background(KNetTheme.colors.panelHeader).padding(KNetTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
                ManagerText(if (shown.id == null) "Add protected traffic rule" else "Edit protected traffic rule", Modifier.weight(1f))
                KNetIconButton(onClick = onDismiss, icon = KNetIcons.Close, contentDescription = "Close protected rule editor", enabled = editable)
            }
            key(shown.id) {
                KNetInspectorContent(Modifier.weight(1f).fillMaxWidth()) {
                    KNetInspectorParagraph("Match a destination, application, port or transport. Matching saved rules override the default TLS action.")
                    if (state.configuration.rules.any { it.id == shown.id && it.origin == ProtectedTrafficRuleOrigin.MIGRATED_PRESET })
                        KNetInspectorParagraph("Imported exception. Changing its match or action converts it to a user rule.")
                    RuleField("Application identity", shown.sourceApplication, { onChange(shown.copy(sourceApplication = it)) },
                        "Package or signing ID (optional)", editable)
                    RuleField("Destination", shown.destination, { onChange(shown.copy(destination = it)) },
                        "Host, *.domain, IP, or CIDR", editable)
                    RuleField("Port", shown.port, { onChange(shown.copy(port = it)) }, "443", editable,
                        supporting = if (shown.isPortValid) "Leave empty to match all ports." else "Use 1–65535, or leave empty for all ports.", numeric = true,
                        invalid = !shown.isPortValid)
                    RuleChoice("Transport") {
                        KNetDropdown(selectedItem = TransportChoice.entries.first { it.value == shown.transport }, items = TransportChoice.entries,
                            itemText = { it.label }, onItemSelected = { onChange(shown.copy(transport = it.value)) },
                            enabled = editable, modifier = Modifier.fillMaxWidth().testTag("Rule transport"))
                    }
                    RuleChoice("IP family") {
                        KNetDropdown(selectedItem = IpChoice.entries.first { it.value == shown.ipFamily }, items = IpChoice.entries,
                            itemText = { it.label }, onItemSelected = { onChange(shown.copy(ipFamily = it.value)) },
                            enabled = editable, modifier = Modifier.fillMaxWidth().testTag("Rule IP family"))
                    }
                    RuleChoice("Action") {
                        KNetDropdown(selectedItem = shown.action,
                            items = listOf(ProtectedTrafficAction.INSPECT, ProtectedTrafficAction.TUNNEL, ProtectedTrafficAction.BLOCK),
                            itemText = { it.displayLabel }, onItemSelected = { onChange(shown.copy(action = it)) }, enabled = editable,
                            modifier = Modifier.fillMaxWidth().testTag("Rule action"))
                    }
                    if (shown.action == ProtectedTrafficAction.BYPASS)
                        KNetInspectorParagraph("Saved Bypass uses encrypted tunnelling on this desktop proxy. Direct bypass requires a supported packet provider.")
                    state.draftError?.let { KNetInspectorParagraph(it, color = KNetTheme.colors.semantic.error,
                        modifier = Modifier.testTag("Protected rule validation")) }
                }
            }
            HorizontalDivider()
            KNetInspectorActions(Modifier.background(KNetTheme.colors.panelHeader).padding(KNetTheme.spacing.lg), horizontalAlignment = Alignment.End) {
                KNetButton(onClick = onDismiss, enabled = editable, variant = ButtonVariant.Secondary) {
                    Text("Cancel", maxLines = 1, softWrap = false)
                }
                KNetButton(onClick = onSave, enabled = editable && shown.isPortValid && shown.id !in state.pendingRuleIds,
                    loading = state.isSaving, variant = ButtonVariant.Primary) {
                    Text("Save rule", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun RuleChoice(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) { ManagerText(label); content() }
}

@Composable
private fun RuleField(label: String, value: String, onChange: (String) -> Unit, hint: String, editable: Boolean,
    supporting: String? = null, numeric: Boolean = false, invalid: Boolean = false) {
    RuleChoice(label) {
        KNetTextField(value, onChange, Modifier.fillMaxWidth().testTag("Protected rule $label"),
            config = InputFieldConfig(placeholder = hint, supportingText = supporting, showHoverPopupOnOverflow = false,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)),
            state = InputFieldState(enabled = editable, isError = invalid))
    }
}

/** Non-null UI choices keep a valid all-transports selection distinct from an unselected dropdown. */
private enum class TransportChoice(val value: ProtectedTransportProtocol?, val label: String) {
    ALL(null, "All transports"), TCP(ProtectedTransportProtocol.TCP, "TCP"), UDP(ProtectedTransportProtocol.UDP, "UDP"),
}

/** IP-family wildcard remains an explicit selectable value in the editor. */
private enum class IpChoice(val value: ProtectedIpFamily?, val label: String) {
    ANY(null, "Any IP"), IPV4(ProtectedIpFamily.IPV4, "IPV4"), IPV6(ProtectedIpFamily.IPV6, "IPV6"),
}
