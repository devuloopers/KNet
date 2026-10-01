package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.viewmodel.NetworkConditionsViewModel

@Composable
fun NetworkConditionsScreen(
    viewModel: NetworkConditionsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showCustomProfileDialog by remember { mutableStateOf(false) }
    var customProfileSeed by remember { mutableStateOf<NetworkConditionProfile?>(null) }
    val colors = KNetTheme.colors

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Network Conditions", style = KNetTheme.typography.titleLarge, color = colors.textPrimary)
                Text(
                    if (state.configuration.enabled) "Shaping captured and API Studio proxy traffic"
                    else "Saved conditions are currently bypassed",
                    style = KNetTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Switch(state.configuration.enabled, viewModel::setEnabled)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = viewModel::reset) { Text("Reset") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Global condition", style = KNetTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.configuration.globalProfileId == null,
                        onClick = { viewModel.selectGlobalProfile(null) },
                        label = { Text("Off") },
                    )
                    state.configuration.profiles.forEach { profile ->
                        FilterChip(
                            selected = state.configuration.globalProfileId == profile.id,
                            onClick = { viewModel.selectGlobalProfile(profile.id) },
                            label = { Text(profile.name) },
                        )
                    }
                }
                Button(onClick = {
                    customProfileSeed = null
                    showCustomProfileDialog = true
                }) { Text("New custom profile") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live application", style = KNetTheme.typography.titleMedium)
                Text(
                    "${state.runtime.activeFlows} active flows · ${state.runtime.queuedBytes} B queued · " +
                        "${state.runtime.downloadedBytes} B downloaded · ${state.runtime.uploadedBytes} B uploaded",
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Advanced packet conditions", style = KNetTheme.typography.titleMedium)
                Text(
                    "Android Companion VPN: UDP, QUIC/HTTP/3, and WebRTC datagrams use the global profile " +
                        "fetched over the authenticated control channel when the VPN starts.",
                    color = colors.textPrimary,
                )
                Text(
                    "iOS and desktop packet adapters remain unavailable until their signed TUN implementations " +
                        "and physical-device qualification pass. Proxy traffic is never shaped twice.",
                    color = colors.textSecondary,
                )
            }
        }

        Text("Domain rules", style = KNetTheme.typography.titleMedium, color = colors.textPrimary)
        Button(onClick = viewModel::beginAddRule) { Text("Add domain rule") }
        if (state.configuration.rules.isEmpty()) {
            Text("No domain overrides yet. Add one here or from a Traffic row.", color = colors.textSecondary)
        }
        state.configuration.rules.forEach { rule ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.target.displayValue)
                        Text(
                            state.configuration.profile(rule.profileId)?.let(::profileSummary).orEmpty(),
                            color = colors.textSecondary,
                        )
                    }
                    Switch(rule.enabled, { enabled -> viewModel.setRuleEnabled(rule, enabled) })
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.beginEditRule(rule) }) { Text("Edit") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.deleteRule(rule.id) }) { Text("Delete") }
                }
            }
        }

        Text("Profiles", style = KNetTheme.typography.titleMedium, color = colors.textPrimary)
        state.configuration.profiles.forEach { profile ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name)
                        Text(profileSummary(profile), color = colors.textSecondary)
                    }
                    if (!profile.builtIn) {
                        OutlinedButton(onClick = {
                            customProfileSeed = profile
                            showCustomProfileDialog = true
                        }) { Text("Edit") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { viewModel.deleteProfile(profile.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }

    state.ruleDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRuleDraft,
            title = { Text(if (draft.existingRuleId == null) "Add domain condition" else "Edit domain condition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.hostPattern,
                        onValueChange = viewModel::updateDraftHostPattern,
                        label = { Text("Host or wildcard (for example *.example.com)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.port,
                        onValueChange = viewModel::updateDraftPort,
                        label = { Text("Port (blank = any)") },
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rule enabled", modifier = Modifier.weight(1f))
                        Switch(draft.enabled, viewModel::updateDraftEnabled)
                    }
                    state.configuration.profiles.forEach { profile ->
                        FilterChip(
                            selected = draft.profileId == profile.id,
                            onClick = { viewModel.selectDraftProfile(profile.id) },
                            label = { Text(profile.name) },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = draft.hostPattern.isNotBlank(),
                    onClick = viewModel::confirmRuleDraft,
                ) { Text("Save rule") }
            },
            dismissButton = { OutlinedButton(onClick = viewModel::dismissRuleDraft) { Text("Cancel") } },
        )
    }

    state.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Network Conditions") },
            text = { Text(error) },
            confirmButton = { Button(onClick = viewModel::clearError) { Text("OK") } },
        )
    }

    if (showCustomProfileDialog) {
        CustomProfileDialog(
            initial = customProfileSeed,
            onDismiss = { showCustomProfileDialog = false },
            onSave = { profile ->
                viewModel.saveProfile(profile)
                showCustomProfileDialog = false
            },
        )
    }
}

@Composable
private fun CustomProfileDialog(
    initial: NetworkConditionProfile?,
    onDismiss: () -> Unit,
    onSave: (NetworkConditionProfile) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "Custom") }
    var download by remember(initial) { mutableStateOf(initial?.download?.bitsPerSecond?.div(1_000L)?.toString() ?: "100") }
    var upload by remember(initial) { mutableStateOf(initial?.upload?.bitsPerSecond?.div(1_000L)?.toString().orEmpty()) }
    var downloadUtilization by remember(initial) { mutableStateOf(initial?.download?.utilizationPercent?.toString() ?: "100") }
    var uploadUtilization by remember(initial) { mutableStateOf(initial?.upload?.utilizationPercent?.toString() ?: "100") }
    var latency by remember(initial) { mutableStateOf(initial?.latencyMillis?.toString() ?: "0") }
    var jitter by remember(initial) { mutableStateOf(initial?.jitterMillis?.toString() ?: "0") }
    var mtu by remember(initial) { mutableStateOf(initial?.virtualMtuBytes?.toString() ?: "1500") }
    var failureKind by remember(initial) {
        mutableStateOf((initial?.failure ?: NetworkFailureBehavior.None).toEditorKind())
    }
    var probability by remember(initial) {
        mutableStateOf((initial?.failure as? NetworkFailureBehavior.SeededReset)?.probabilityPercent?.toString() ?: "10")
    }
    var seed by remember(initial) {
        mutableStateOf((initial?.failure as? NetworkFailureBehavior.SeededReset)?.seed?.toString() ?: "1263420756")
    }
    var packetLoss by remember(initial) { mutableStateOf(initial?.packetLossPercent?.toString() ?: "0") }
    var packetDuplication by remember(initial) { mutableStateOf(initial?.packetDuplicationPercent?.toString() ?: "0") }
    var packetReordering by remember(initial) { mutableStateOf(initial?.packetReorderingPercent?.toString() ?: "0") }
    val downKbps = download.toLongOrNull()
    val upKbps = upload.toLongOrNull()
    val downUtilization = downloadUtilization.toIntOrNull()
    val upUtilization = uploadUtilization.toIntOrNull()
    val latencyValue = latency.toLongOrNull()
    val jitterValue = jitter.toLongOrNull()
    val mtuValue = mtu.toIntOrNull()
    val maximumKbps = NetworkDirectionCondition.MAXIMUM_BITS_PER_SECOND / 1_000L
    val valid = (download.isBlank() || downKbps?.let { it in 1..maximumKbps } == true) &&
        (upload.isBlank() || upKbps?.let { it in 1..maximumKbps } == true) &&
        (downUtilization ?: 0) in 1..100 && (upUtilization ?: 0) in 1..100 &&
        latencyValue?.let { it in 0L..NetworkConditionProfile.MAXIMUM_DELAY_MILLIS } == true &&
        jitterValue?.let { it in 0L..NetworkConditionProfile.MAXIMUM_DELAY_MILLIS } == true &&
        mtuValue?.let {
            it in NetworkConditionProfile.MINIMUM_VIRTUAL_MTU_BYTES..NetworkConditionProfile.MAXIMUM_VIRTUAL_MTU_BYTES
        } == true &&
        (packetLoss.toIntOrNull() ?: -1) in 0..100 &&
        (packetDuplication.toIntOrNull() ?: -1) in 0..100 &&
        (packetReordering.toIntOrNull() ?: -1) in 0..100 &&
        (failureKind != FailureEditorKind.SEEDED_RESET ||
            ((probability.toIntOrNull() ?: 0) in 1..100 && seed.toLongOrNull() != null))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom profile") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(download, { download = it.filter(Char::isDigit) }, label = { Text("Download kbps (blank = unlimited)") })
                OutlinedTextField(upload, { upload = it.filter(Char::isDigit) }, label = { Text("Upload kbps (blank = unlimited)") })
                OutlinedTextField(downloadUtilization, { downloadUtilization = it.filter(Char::isDigit) }, label = { Text("Download utilization %") })
                OutlinedTextField(uploadUtilization, { uploadUtilization = it.filter(Char::isDigit) }, label = { Text("Upload utilization %") })
                OutlinedTextField(latency, { latency = it.filter(Char::isDigit) }, label = { Text("Latency ms") })
                OutlinedTextField(jitter, { jitter = it.filter(Char::isDigit) }, label = { Text("Jitter ms") })
                OutlinedTextField(mtu, { mtu = it.filter(Char::isDigit) }, label = { Text("Virtual MTU bytes") })
                Text("VPN/TUN packet conditions (UDP, QUIC, WebRTC)")
                OutlinedTextField(packetLoss, { packetLoss = it.filter(Char::isDigit) }, label = { Text("Packet loss %") })
                OutlinedTextField(packetDuplication, { packetDuplication = it.filter(Char::isDigit) }, label = { Text("Packet duplication %") })
                OutlinedTextField(packetReordering, { packetReordering = it.filter(Char::isDigit) }, label = { Text("Packet reordering %") })
                Text("Failure behaviour")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FailureEditorKind.entries.forEach { kind ->
                        FilterChip(
                            selected = failureKind == kind,
                            onClick = { failureKind = kind },
                            label = { Text(kind.label) },
                        )
                    }
                }
                if (failureKind == FailureEditorKind.SEEDED_RESET) {
                    OutlinedTextField(probability, { probability = it.filter(Char::isDigit) }, label = { Text("Reset probability %") })
                    OutlinedTextField(seed, { seed = it.filter { character -> character.isDigit() || character == '-' } }, label = { Text("Deterministic seed") })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid && name.isNotBlank(),
                onClick = {
                    val id = initial?.id ?: NetworkConditionProfileId(
                        "custom-" + System.currentTimeMillis().toString(36),
                    )
                    onSave(
                        NetworkConditionProfile(
                            id = id,
                            name = name.trim(),
                            download = NetworkDirectionCondition(downKbps?.times(1_000L), checkNotNull(downUtilization)),
                            upload = NetworkDirectionCondition(upKbps?.times(1_000L), checkNotNull(upUtilization)),
                            latencyMillis = checkNotNull(latencyValue),
                            jitterMillis = checkNotNull(jitterValue),
                            virtualMtuBytes = checkNotNull(mtuValue),
                            failure = failureKind.toDomain(probability.toIntOrNull(), seed.toLongOrNull()),
                            packetLossPercent = checkNotNull(packetLoss.toIntOrNull()),
                            packetDuplicationPercent = checkNotNull(packetDuplication.toIntOrNull()),
                            packetReorderingPercent = checkNotNull(packetReordering.toIntOrNull()),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun profileSummary(profile: NetworkConditionProfile): String {
    fun rate(value: Long?) = value?.let { "${it / 1_000} kbps" } ?: "unlimited"
    return "Down ${rate(profile.download.effectiveBitsPerSecond)} · Up ${rate(profile.upload.effectiveBitsPerSecond)} · " +
        "${profile.latencyMillis} ms latency · ±${profile.jitterMillis} ms jitter · MTU ${profile.virtualMtuBytes} · " +
        profile.failure.summary() + " · UDP loss ${profile.packetLossPercent}% · duplicate " +
        "${profile.packetDuplicationPercent}% · reorder ${profile.packetReorderingPercent}%"
}

private enum class FailureEditorKind(val label: String) {
    NONE("None"),
    OFFLINE("Offline"),
    TIMEOUT("Timeout"),
    RESET("Reset flow"),
    SEEDED_RESET("Seeded reset"),
}

private fun NetworkFailureBehavior.toEditorKind(): FailureEditorKind = when (this) {
    NetworkFailureBehavior.None -> FailureEditorKind.NONE
    NetworkFailureBehavior.Offline -> FailureEditorKind.OFFLINE
    NetworkFailureBehavior.Timeout -> FailureEditorKind.TIMEOUT
    NetworkFailureBehavior.ResetFlow -> FailureEditorKind.RESET
    is NetworkFailureBehavior.SeededReset -> FailureEditorKind.SEEDED_RESET
}

private fun FailureEditorKind.toDomain(probability: Int?, seed: Long?): NetworkFailureBehavior = when (this) {
    FailureEditorKind.NONE -> NetworkFailureBehavior.None
    FailureEditorKind.OFFLINE -> NetworkFailureBehavior.Offline
    FailureEditorKind.TIMEOUT -> NetworkFailureBehavior.Timeout
    FailureEditorKind.RESET -> NetworkFailureBehavior.ResetFlow
    FailureEditorKind.SEEDED_RESET -> NetworkFailureBehavior.SeededReset(
        checkNotNull(probability),
        checkNotNull(seed),
    )
}

private fun NetworkFailureBehavior.summary(): String = when (this) {
    NetworkFailureBehavior.None -> "no faults"
    NetworkFailureBehavior.Offline -> "offline"
    NetworkFailureBehavior.Timeout -> "timeout"
    NetworkFailureBehavior.ResetFlow -> "reset flow"
    is NetworkFailureBehavior.SeededReset -> "$probabilityPercent% seeded reset"
}
