package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.application.usecase.protectedtraffic.ProtectedTrafficFlowHandling
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorContent
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorActions
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorParagraph
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.time.KNetDateTime
import com.devuloopers.knet.ui.desktop.traffic.model.*

/** Captured diagnostics are immutable; the separate handling state describes future connections only. */
@Composable
internal fun OpaqueFlowInspectorPanel(flow: TrafficRowUiState, state: TrafficProtectionState,
    actions: TrafficProtectionActions?, modifier: Modifier = Modifier) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    val handling = state.handling?.takeIf { it.flowId.value == flow.transactionId }
    val selectedState = state.takeIf { it.flowId?.value == flow.transactionId }
        ?: TrafficProtectionState(isLoading = true)
    var expanded by remember(flow.transactionId) { mutableStateOf(false) }
    val diagnostic = opaqueDiagnostic(flow)
    KNetInspectorContent(modifier.testTag("Opaque flow inspector")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(KNetIcons.Shield, null, Modifier.size(KNetTheme.dimensions.iconSizeLarge), tint = colors.textSecondary)
            InspectorText(if (flow.opaqueSecurity == OpaqueSecurityProtocol.TLS) "TLS connection" else "Transport connection",
                Modifier.weight(1f), KNetTheme.typography.titleMedium)

        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            InspectorText(flow.host, style = KNetTheme.typography.titleLarge)
            KNetBadge(flow.displayStatusLabel, containerColor = if (diagnostic.failure) colors.semantic.errorContainer else colors.surfaceVariant,
                contentColor = if (diagnostic.failure) colors.semantic.error else colors.textSecondary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            InspectorText(if (diagnostic.failure) "WHY IT FAILED" else "ABOUT THIS CONNECTION",
                style = KNetTheme.typography.caption, color = colors.textMuted)
            KNetInspectorParagraph(diagnostic.title, style = KNetTheme.typography.titleMedium, color = colors.textPrimary)
            KNetInspectorParagraph(diagnostic.explanation)
            diagnostic.evidence?.let { KNetInspectorParagraph(it, color = if (diagnostic.failure) colors.semantic.error else colors.textSecondary) }
            if (flow.protectionOrigin == ProtectionDecisionOrigin.AUTOMATIC)
                KNetInspectorParagraph("Automatically tunnelled — device rejected KNet’s certificate. This temporary decision preserves encryption; it does not prove certificate pinning.")
            diagnostic.possibleCauses?.let { KNetInspectorParagraph(it, style = KNetTheme.typography.caption) }
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            InspectorText("Future connections", style = KNetTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                InspectorText("Current handling", Modifier.weight(1f), color = colors.textMuted)
                InspectorText(handling?.let(::handlingLabel) ?: if (selectedState.isLoading) "Loading…" else "Unavailable",
                    Modifier.weight(1.5f))
            }
            handling?.let { current -> handlingNote(current)?.let { note ->
                KNetInspectorParagraph(note, color = colors.semantic.warning)
            } }
            val tunnel = selectedState.destinationAction(ProtectedTrafficAction.TUNNEL)
            val inspect = selectedState.destinationAction(ProtectedTrafficAction.INSPECT)
            KNetInspectorActions(Modifier.selectableGroup()) {
                ProtectionActionButton(tunnel, actions, handling != null,
                    preferPrimary = !inspect.selected)
                ProtectionActionButton(inspect, actions, handling != null)
                ProtectionActionButton(selectedState.destinationAction(ProtectedTrafficAction.BLOCK), actions, handling != null)
                val inherited = handling?.let { it.destinationRule == null } == true
                KNetButton(onClick = { actions?.useDefault?.invoke() }, variant = ButtonVariant.Secondary,
                    enabled = handling?.configurationReady == true && !selectedState.isApplying && actions?.useDefault != null && !inherited,
                    modifier = Modifier.testTag("Protection use default")) {
                    Text(if (inherited && handling.winningRule != null) "Inherited rule" else if (inherited) "Using default" else "Use default", maxLines = 1, softWrap = false)
                }
            }
            if (state.isApplying && state.applyingAction == null)
                KNetInspectorParagraph("Saving connection handling…", style = KNetTheme.typography.caption)
            KNetInspectorParagraph("Applies to new connections for this destination.", style = KNetTheme.typography.caption)
            if (state.flowId?.value == flow.transactionId) state.feedback?.let { feedback ->
                KNetInspectorParagraph(when (feedback) {
                    is TrafficProtectionFeedback.Saved -> when (feedback.action) {
                        ProtectedTrafficAction.TUNNEL -> "Tunnel rule saved. Applies to new connections."
                        ProtectedTrafficAction.INSPECT -> "Inspection rule saved. Applies to new connections."
                        ProtectedTrafficAction.BLOCK -> "Block rule saved. Applies to new connections."
                        ProtectedTrafficAction.BYPASS -> "Bypass rule saved. Applies to new connections."
                    }
                    TrafficProtectionFeedback.DefaultRestored -> "Destination override removed. Inherited handling applies to new connections."
                    is TrafficProtectionFeedback.Failed -> feedback.message
                }, color = if (feedback is TrafficProtectionFeedback.Failed) colors.semantic.error else colors.semantic.success,
                    modifier = Modifier.testTag("Connection action feedback"))
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Metric("Duration", flow.formattedTime, Modifier.weight(1f))
            Metric("Uploaded", formatOpaqueBytes(flow.transferredBytes - flow.responseBytes), Modifier.weight(1f))
            Metric("Downloaded", formatOpaqueBytes(flow.responseBytes), Modifier.weight(1f))
        }
        KNetButton(onClick = { expanded = !expanded }, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Icon(if (expanded) KNetIcons.ChevronDown else KNetIcons.ChevronRight, null,
                    Modifier.size(KNetTheme.dimensions.iconSizeMedium))
                InspectorText("Technical details")
            }
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(spacing.md), modifier = Modifier.testTag("Opaque technical details")) {
            if (flow.terminalOutcome != null) {
                Detail("Closed by", when (flow.closure?.initiator) {
                    OpaqueFlowCloseInitiator.DEVICE -> "Device"
                    OpaqueFlowCloseInitiator.SERVER -> "Server"
                    OpaqueFlowCloseInitiator.KNET -> "KNet"
                    OpaqueFlowCloseInitiator.UNKNOWN, null -> "Unknown"
                })
                Detail("Close reason", closureReasonLabel(flow.closure?.reason))
                Detail("Close observed at", flow.closure?.observedAtEpochMillis?.let { timestamp ->
                    "${KNetDateTime.dateKey(timestamp)} ${KNetDateTime.time(timestamp, includeMilliseconds = true)}"
                } ?: "Unavailable")
                KNetInspectorParagraph("Identifies the first close signal observed by KNet; the remote app’s reason remains unknown.",
                    style = KNetTheme.typography.caption)
            }
            Detail("Captured policy action", flow.policyAction?.name ?: "Unavailable")
            Detail("Captured decision source", flow.protectionOrigin?.let { when (it) {
                ProtectionDecisionOrigin.AUTOMATIC -> "Automatic certificate-rejection fallback"
                ProtectionDecisionOrigin.SMART -> "Automatic decision (historical)"
                ProtectionDecisionOrigin.MANUAL -> "Manual rule"
                ProtectionDecisionOrigin.PRESET -> "Imported exception"
                ProtectionDecisionOrigin.GLOBAL -> "Global default"
            } } ?: "Unavailable")
            Detail("Policy rule", flow.policyRuleId ?: if (flow.protectionOrigin == ProtectionDecisionOrigin.AUTOMATIC) "Temporary automatic decision" else "Global default")
            Detail("Source application", flow.sourceApplicationId ?: "Unavailable")
            Detail("Security", flow.opaqueSecurity?.name ?: "Unknown")
            Detail("Transport", flow.opaqueTransport?.name ?: "Unknown")
            Detail("Offered ALPN", flow.offeredApplicationProtocols.joinToString().ifEmpty { "Unavailable" })
            Detail("Offered TLS versions", flow.offeredTlsVersions.joinToString().ifEmpty { "Unavailable" })
            Detail("Started", flow.formattedTimestamp)
            flow.terminalOutcome?.reason?.code?.value?.let { Detail("Termination", it) }
            flow.appliedNetworkCondition?.let { Detail("Network condition", it.profileId); Detail("Condition rule", it.ruleId ?: "Global") }
        }
    }
}

/** Only typed captured evidence can select a certificate rejection diagnosis. */
internal data class OpaqueDiagnostic(val title: String, val explanation: String, val failure: Boolean,
    val evidence: String? = null, val possibleCauses: String? = null)

internal fun opaqueDiagnostic(flow: TrafficRowUiState): OpaqueDiagnostic {
    if (flow.policyAction == OpaqueFlowPolicyAction.TUNNEL || flow.policyAction == OpaqueFlowPolicyAction.BYPASS)
        return when ((flow.terminalOutcome as? ExchangeTerminalOutcome.Failed)?.reason) {
            TrafficTerminationReason.Transport.READ_TIMED_OUT -> OpaqueDiagnostic("Tunnel inactivity timeout",
                "KNet closed this connection after its configured inactivity limit. Earlier app operations may have finished; " +
                    "their HTTP results remain encrypted.", true)
            TrafficTerminationReason.Transport.WRITE_TIMED_OUT -> OpaqueDiagnostic("Tunnel write timed out",
                "A transport write did not finish within KNet’s deadline. HTTP results remain encrypted.", true)
            null -> OpaqueDiagnostic("End-to-end encryption preserved", "HTTP headers, bodies and status codes remain encrypted. This row tracks the connection lifetime, " +
                "which can continue after an app operation finishes.", false)
            else -> OpaqueDiagnostic("Tunnel connection failed", "The encrypted transport failed; no decrypted HTTP response is available.", true)
        }
    if (flow.policyAction == OpaqueFlowPolicyAction.BLOCK)
        return OpaqueDiagnostic("Connection blocked by policy", "KNet blocked this flow before forwarding application data.", false)
    return when (flow.inspectionFailureReason) {
        InspectionFailureReason.CERTIFICATE_REJECTED -> OpaqueDiagnostic("Device rejected KNet’s certificate",
            "The TLS handshake ended before an HTTP response was available.", true,
            "Evidence: certificate rejection alert", "Possible causes: app trust settings or certificate pinning.")
        InspectionFailureReason.CERTIFICATE_SETUP -> OpaqueDiagnostic("KNet certificate setup failed",
            "KNet could not establish a valid inspection certificate context.", true)
        InspectionFailureReason.SETUP_SUSPECTED -> OpaqueDiagnostic("Historical certificate setup warning",
            "The retired automatic learner recorded a setup suspicion; this is not an active handling decision.", true)
        InspectionFailureReason.UNKNOWN_HANDSHAKE -> OpaqueDiagnostic("TLS handshake failed",
            "The available evidence does not identify the exact cause.", true)
        InspectionFailureReason.TRIAL_FAILED -> OpaqueDiagnostic("Historical automatic tunnel trial failed",
            "The retired automatic learner recorded a failed transport trial.", true)
        null -> OpaqueDiagnostic(if (flow.terminalOutcome is ExchangeTerminalOutcome.Failed) "Connection failed" else "Encrypted transport metadata",
            "No decrypted HTTP response is available for this flow.", flow.terminalOutcome is ExchangeTerminalOutcome.Failed)
    }
}

/** Presentation uses recorded evidence only, including an explicit absence for legacy captures. */
internal fun closureReasonLabel(reason: OpaqueFlowCloseReason?): String = when (reason) {
    OpaqueFlowCloseReason.PEER_EOF -> "Peer ended TCP input (EOF)"
    OpaqueFlowCloseReason.CONCURRENT_PEER_EOF -> "Peer closures observed together; initiator unknown"
    OpaqueFlowCloseReason.INACTIVITY_TIMEOUT -> "KNet inactivity timeout"
    OpaqueFlowCloseReason.WRITE_TIMEOUT -> "KNet write timeout"
    OpaqueFlowCloseReason.LOCAL_CLOSE -> "Explicit KNet close"
    OpaqueFlowCloseReason.PROXY_STOPPED -> "Proxy stopped"
    OpaqueFlowCloseReason.CONNECTIONS_RESET -> "Connections reset by KNet"
    OpaqueFlowCloseReason.IO_ERROR -> "Transport I/O error; initiator unknown"
    OpaqueFlowCloseReason.UNKNOWN, null -> "Unknown"
}

private fun handlingLabel(handling: ProtectedTrafficFlowHandling): String {
    if (!handling.configurationReady) return "Loading…"
    val source = when {
        handling.decision.evidence == ProtectedTrafficDecisionEvidence.AutomaticFallback -> "Automatic fallback"
        handling.decision.evidence == ProtectedTrafficDecisionEvidence.GlobalDefault -> if (handling.automaticFallbackEnabled) "Default with fallback" else "Global default"
        handling.winningRule?.sourceApplication != null -> "Application rule"
        handling.winningRule?.destination is ProtectedDestinationSelector.WildcardDomain -> "Domain rule"
        else -> "Saved rule"
    }
    val action = when (handling.decision.action) {
        ProtectedTrafficAction.INSPECT -> "Inspect"
        ProtectedTrafficAction.TUNNEL -> "Tunnel"
        ProtectedTrafficAction.BLOCK -> "Block"
        ProtectedTrafficAction.BYPASS -> "Bypass"
    }
    return "$action · $source"
}

private fun handlingNote(handling: ProtectedTrafficFlowHandling): String? {
    if (!handling.configurationReady) return null
    val saved = handling.destinationRule?.takeIf { it.enabled }
    val winner = (handling.decision.evidence as? ProtectedTrafficDecisionEvidence.Rule)?.ruleId
    if (saved != null && saved.id != winner) {
        val source = if (handling.winningRule?.sourceApplication != null) "an application rule" else "another rule"
        return "Saved ${saved.action.name.lowercase()} choice is overridden by $source."
    }
    return null
}

/** Saved selection stays visibly active even though resubmitting it is disabled. */
@Composable
private fun ProtectionActionButton(state: TrafficProtectionActionState, actions: TrafficProtectionActions?,
    contextAvailable: Boolean, preferPrimary: Boolean = false) {
    val selected = state.selected && contextAvailable
    val variant = if (selected || preferPrimary) ButtonVariant.Primary else ButtonVariant.Secondary
    val baseColors = ButtonDefaults.colors(variant)
    val colors = if (selected) baseColors.copy(disabledContainerColor = baseColors.containerColor,
        disabledContentColor = baseColors.contentColor) else baseColors
    val label = when (state.status) {
        TrafficProtectionActionStatus.SAVING -> "Saving…"
        TrafficProtectionActionStatus.ACTIVE -> when (state.action) {
            ProtectedTrafficAction.TUNNEL -> "Tunnelling enabled"
            ProtectedTrafficAction.INSPECT -> "Inspection enabled"
            ProtectedTrafficAction.BLOCK -> "Blocking enabled"
            ProtectedTrafficAction.BYPASS -> "Bypass enabled"
        }
        TrafficProtectionActionStatus.OVERRIDDEN -> when (state.action) {
            ProtectedTrafficAction.TUNNEL -> "Tunnel rule overridden"
            ProtectedTrafficAction.INSPECT -> "Inspection rule overridden"
            ProtectedTrafficAction.BLOCK -> "Block rule overridden"
            ProtectedTrafficAction.BYPASS -> "Bypass rule overridden"
        }
        else -> when (state.action) {
            ProtectedTrafficAction.TUNNEL -> "Always tunnel"
            ProtectedTrafficAction.INSPECT -> "Inspect next time"
            ProtectedTrafficAction.BLOCK -> "Block"
            ProtectedTrafficAction.BYPASS -> "Bypass"
        }
    }
    KNetButton(onClick = { actions?.apply?.invoke(state.action) }, enabled = contextAvailable && actions != null && state.enabled,
        loading = state.status == TrafficProtectionActionStatus.SAVING, variant = variant, colors = colors,
        role = Role.RadioButton, modifier = Modifier.testTag("Protection action ${state.action.name}").semantics {
            this.selected = selected
            stateDescription = when {
                selected -> "Saved destination rule active"
                state.status == TrafficProtectionActionStatus.OVERRIDDEN -> "Saved destination rule overridden"
                state.status == TrafficProtectionActionStatus.SAVING -> "Saving destination rule"
                else -> "No active saved selection"
            }
        }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
            if (selected) Icon(KNetIcons.Check, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall))
            Text(label, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        InspectorText(label, style = KNetTheme.typography.caption, color = KNetTheme.colors.textMuted)
        KNetInspectorParagraph(value, color = KNetTheme.colors.textPrimary)
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        InspectorText(label, style = KNetTheme.typography.caption, color = KNetTheme.colors.textMuted)
        InspectorText(value)
    }
}

@Composable
private fun InspectorText(text: String, modifier: Modifier = Modifier, style: TextStyle = KNetTheme.typography.bodySmall,
    color: Color = KNetTheme.colors.textPrimary) {
    Text(text, modifier, style = style.copy(color = color), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

private fun formatOpaqueBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
    bytes >= 1_024 -> "${bytes / 1_024} KB"
    else -> "${bytes.coerceAtLeast(0)} B"
}
