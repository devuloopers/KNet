package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.chip.KNetChip
import com.devuloopers.knet.ui.core.components.dialog.AlertDialog
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.InputFieldState
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionRuleDraft
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputHistory
import com.devuloopers.knet.ui.desktop.networkconditions.model.hasObservedTraffic
import com.devuloopers.knet.ui.desktop.networkconditions.model.niceChartMaximum
import com.devuloopers.knet.ui.desktop.networkconditions.model.formatNetworkRate
import com.devuloopers.knet.ui.desktop.networkconditions.viewmodel.NetworkConditionsViewModel
import kotlin.math.max

/** KNet-native workspace; its host owns [NetworkConditionsRuntimeMonitoring] across throughput destinations. */
@Composable
fun NetworkConditionsScreen(
    viewModel: NetworkConditionsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showCustomProfileDialog by remember { mutableStateOf(false) }
    var customProfileSeed by remember { mutableStateOf<NetworkConditionProfile?>(null) }
    NetworkConditionsWorkspace(
        state = state,
        onEnabledChange = viewModel::setEnabled,
        onReset = viewModel::reset,
        onSelectProfile = viewModel::selectGlobalProfile,
        onSelectTab = viewModel::selectTab,
        onAddRule = viewModel::beginAddRule,
        onRuleEnabledChange = viewModel::setRuleEnabled,
        onEditRule = viewModel::beginEditRule,
        onDeleteRule = viewModel::deleteRule,
        onNewProfile = {
            customProfileSeed = null
            showCustomProfileDialog = true
        },
        onEditProfile = { profile ->
            customProfileSeed = profile
            showCustomProfileDialog = true
        },
        onDeleteProfile = viewModel::deleteProfile,
        modifier = modifier,
    )

    state.ruleDraft?.takeIf { state.activeTab == com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsTab.RULES }?.let { draft ->
        RuleDialog(
            draft = draft,
            profiles = state.configuration.profiles,
            protocolDefinitions = state.protocolDefinitions,
            onHostChange = viewModel::updateDraftHostPattern,
            onPortChange = viewModel::updateDraftPort,
            onPriorityChange = viewModel::updateDraftPriority,
            onEnabledChange = viewModel::updateDraftEnabled,
            onProfileSelected = viewModel::selectDraftProfile,
            onProtocolSelected = viewModel::selectDraftProtocol,
            onProtocolValueChange = viewModel::updateDraftProtocolValue,
            onDismiss = viewModel::dismissRuleDraft,
            onSave = viewModel::confirmRuleDraft,
        )
    }

    state.errorMessage?.let { error ->
        AlertDialog(
            title = "Network Conditions",
            message = error,
            onDismissRequest = viewModel::clearError,
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
internal fun LiveThroughputGraph(
    history: NetworkThroughputHistory,
    downloadReferenceBitsPerSecond: Long?,
    uploadReferenceBitsPerSecond: Long?,
    activeFlows: Int,
    queuedBytes: Long,
    measurementContext: String = "Measured KNet payload",
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val downloadPath = remember { Path() }
    val uploadPath = remember { Path() }
    val downloadArea = remember { Path() }
    val uploadArea = remember { Path() }
    val referenceDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 7f)) }
    val measuredPeak = max(
        history.downloadBitsPerSecond.maxOrNull() ?: 0L,
        history.uploadBitsPerSecond.maxOrNull() ?: 0L,
    )
    val referencePeak = max(downloadReferenceBitsPerSecond ?: 0L, uploadReferenceBitsPerSecond ?: 0L)
    val chartMaximum = niceChartMaximum(max(measuredPeak, referencePeak))
    val axisLabels = remember(chartMaximum) { listOf(rate(chartMaximum), rate(chartMaximum / 2), "0") }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val axisStyle = KNetTheme.typography.caption
    val axisLabelWidth = remember(axisLabels, axisStyle, textMeasurer, density) {
        with(density) {
            axisLabels.maxOf { textMeasurer.measure(it, style = axisStyle, maxLines = 1, softWrap = false).size.width }.toDp()
        }
    }
    // Measure with the same theme/font-scale contract as KNet fields, leaving a clear gap before the plot.
    val axisGutter = maxOf(KNetTheme.spacing.giant, axisLabelWidth + KNetTheme.spacing.md)
    val hasObservedTraffic = history.hasObservedTraffic()
    val referenceLabel = if (downloadReferenceBitsPerSecond != null && downloadReferenceBitsPerSecond == uploadReferenceBitsPerSecond) {
        "Configured limit ${rate(downloadReferenceBitsPerSecond)}"
    } else buildList {
        downloadReferenceBitsPerSecond?.let { add("Download reference ${rate(it)}") }
        uploadReferenceBitsPerSecond?.let { add("Upload reference ${rate(it)}") }
    }.joinToString(" · ").ifEmpty { measurementContext }
    val accessibilityText = buildString {
        append("Live throughput. Download ")
        append(
            if (history.currentSampleIsValid && hasObservedTraffic) {
                rate(history.currentDownloadBitsPerSecond)
            } else {
                "waiting"
            },
        )
        append(", upload ")
        append(
            if (history.currentSampleIsValid && hasObservedTraffic) {
                rate(history.currentUploadBitsPerSecond)
            } else {
                "waiting"
            },
        )
        downloadReferenceBitsPerSecond?.let { append(", configured download reference ${rate(it)}") }
        uploadReferenceBitsPerSecond?.let { append(", configured upload reference ${rate(it)}") }
        append(", $activeFlows active flows, ${formatBytes(queuedBytes)} queued")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = referenceLabel,
                modifier = Modifier.weight(1f),
                style = KNetTheme.typography.caption.copy(color = colors.textSecondary),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (!hasObservedTraffic && referencePeak == 0L) {
                    "Auto scale"
                } else {
                    "Scale ${rate(chartMaximum)}"
                },
                style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                maxLines = 1, softWrap = false,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics { contentDescription = accessibilityText },
        ) {
            Column(
                modifier = Modifier.width(axisGutter).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                axisLabels.forEach { label ->
                    Text(label, style = KNetTheme.typography.caption.copy(color = colors.textMuted), maxLines = 1, softWrap = false)
                }
            }
            Box(Modifier.fillMaxSize().padding(start = axisGutter)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    repeat(5) { index ->
                        val fraction = index / 4f
                        drawLine(
                            color = colors.border.copy(alpha = 0.72f),
                            start = Offset(0f, size.height * fraction),
                            end = Offset(size.width, size.height * fraction),
                            strokeWidth = 1f,
                        )
                    }
                    repeat(7) { index ->
                        val fraction = index / 6f
                        drawLine(
                            color = colors.border.copy(alpha = 0.45f),
                            start = Offset(size.width * fraction, 0f),
                            end = Offset(size.width * fraction, size.height),
                            strokeWidth = 1f,
                        )
                    }

                    fun referenceY(value: Long): Float = size.height -
                        (value.toDouble() / chartMaximum.toDouble()).coerceIn(0.0, 1.0).toFloat() * size.height

                    downloadReferenceBitsPerSecond?.let { reference ->
                        val y = referenceY(reference)
                        drawLine(
                            color = colors.accent.copy(alpha = 0.48f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f,
                            pathEffect = referenceDash,
                        )
                    }
                    uploadReferenceBitsPerSecond?.let { reference ->
                        val y = referenceY(reference)
                        drawLine(
                            color = colors.semantic.success.copy(alpha = 0.48f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f,
                            pathEffect = referenceDash,
                        )
                    }

                    fun populatePath(path: Path, values: LongArray, area: Boolean = false) {
                        path.reset()
                        var previousValid = false
                        var previousX = 0f
                        val denominator = (values.size - 1).coerceAtLeast(1)
                        values.indices.forEach { index ->
                            val valid = history.validSamples.getOrElse(index) { false }
                            if (valid) {
                                val x = size.width * index / denominator.toFloat()
                                val y = referenceY(values[index])
                                if (previousValid) path.lineTo(x, y) else if (area) {
                                    path.moveTo(x, size.height)
                                    path.lineTo(x, y)
                                } else path.moveTo(x, y)
                                previousX = x
                            } else if (area && previousValid) {
                                path.lineTo(previousX, size.height)
                                path.close()
                            }
                            previousValid = valid
                        }
                        if (area && previousValid) {
                            path.lineTo(previousX, size.height)
                            path.close()
                        }
                    }

                    if (hasObservedTraffic) {
                        // Fill only measured contiguous segments; missing samples remain gaps, without smoothing data.
                        populatePath(downloadArea, history.downloadBitsPerSecond, area = true)
                        populatePath(uploadArea, history.uploadBitsPerSecond, area = true)
                        drawPath(downloadArea, colors.accent.copy(alpha = 0.06f))
                        drawPath(uploadArea, colors.semantic.success.copy(alpha = 0.06f))
                        populatePath(downloadPath, history.downloadBitsPerSecond)
                        populatePath(uploadPath, history.uploadBitsPerSecond)
                        drawPath(
                            path = downloadPath,
                            color = colors.accent,
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                        )
                        drawPath(
                            path = uploadPath,
                            color = colors.semantic.success,
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                        )
                    }
                }
                if (!hasObservedTraffic) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
                    ) {
                        Text(
                            "Waiting for traffic",
                            style = KNetTheme.typography.labelMedium.copy(color = colors.textSecondary),
                            maxLines = 1, softWrap = false,
                        )
                        Text(
                            "Actual rates will appear here",
                            style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                            maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = axisGutter), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("60s ago", style = KNetTheme.typography.caption.copy(color = colors.textMuted), maxLines = 1, softWrap = false)
            Text("Now", style = KNetTheme.typography.caption.copy(color = colors.textMuted), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun RuleDialog(
    draft: NetworkConditionRuleDraft,
    profiles: List<NetworkConditionProfile>,
    protocolDefinitions: List<NetworkConditionProtocolDefinition>,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (NetworkConditionProfileId) -> Unit,
    onProtocolSelected: (NetworkConditionProtocolId) -> Unit,
    onProtocolValueChange: (ProtocolCriteriaFieldId, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    KNetDialog(
        onDismissRequest = onDismiss,
        title = if (draft.existingRuleId == null) "Add network condition" else "Edit network condition",
    ) {
        NetworkConditionRuleEditor(
            draft, profiles, protocolDefinitions, onHostChange, onPortChange, onPriorityChange,
            onEnabledChange, onProfileSelected, onProtocolSelected, onProtocolValueChange, onDismiss, onSave,
        )
    }
}

/** The dialog form is independently renderable so overflow and fixed actions can be verified at real sizes. */
@Composable
internal fun NetworkConditionRuleEditor(
    draft: NetworkConditionRuleDraft,
    profiles: List<NetworkConditionProfile>,
    protocolDefinitions: List<NetworkConditionProtocolDefinition>,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (NetworkConditionProfileId) -> Unit,
    onProtocolSelected: (NetworkConditionProtocolId) -> Unit,
    onProtocolValueChange: (ProtocolCriteriaFieldId, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = KNetTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                    FormFieldRow {
                        LabeledTextField(
                            label = "Host or wildcard", value = draft.hostPattern, onValueChange = onHostChange,
                            placeholder = "api.example.com or *.example.com", modifier = Modifier.weight(3f),
                        )
                        LabeledTextField(
                            label = "Port", value = draft.port, onValueChange = onPortChange,
                            placeholder = "e.g. 443", modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Blank port matches all ports.",
                        style = KNetTheme.typography.caption.copy(color = KNetTheme.colors.textMuted),
                    )
                }
                val definition = protocolDefinitions.firstOrNull { it.protocolId == draft.protocolId }
                LabeledDropdown(
                    label = "Match scope",
                    selected = definition,
                    items = protocolDefinitions,
                    text = { it.displayName },
                    onSelected = { onProtocolSelected(it.protocolId) },
                    placeholder = "Unavailable protocol",
                )
                definition?.fields?.forEach { field ->
                    NetworkConditionProtocolField(
                        field = field,
                        value = draft.protocolValues.firstOrNull { it.fieldId == field.id }?.value.orEmpty(),
                        onValueChange = { onProtocolValueChange(field.id, it) },
                    )
                }
                HorizontalDivider()
                LabeledDropdown(
                    label = "Condition profile",
                    selected = profiles.firstOrNull { it.id == draft.profileId },
                    items = profiles,
                    text = { it.name },
                    onSelected = { onProfileSelected(it.id) },
                    placeholder = "Select a profile",
                )
                FormFieldRow {
                    LabeledTextField(
                        label = "Priority", value = draft.priority, onValueChange = onPriorityChange,
                        supportingText = "Higher priority wins.", modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.weight(1f).padding(top = KNetTheme.spacing.xxl)) {
                        KNetSwitch(draft.enabled, onEnabledChange, label = "Rule enabled")
                    }
                }
            }
            KNetVerticalScrollbar(scrollState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        HorizontalDivider()
        DialogActions(
            confirmText = "Save rule",
            confirmEnabled = draft.hostPattern.isNotBlank() && profiles.any { it.id == draft.profileId } &&
                definitionAvailable(draft, protocolDefinitions),
            onDismiss = onDismiss,
            onConfirm = onSave,
        )
    }
}

private fun definitionAvailable(
    draft: NetworkConditionRuleDraft,
    definitions: List<NetworkConditionProtocolDefinition>,
): Boolean = definitions.any { it.protocolId == draft.protocolId }

@Composable
private fun <T : Any> LabeledDropdown(
    label: String,
    selected: T?,
    items: List<T>,
    text: (T) -> String,
    onSelected: (T) -> Unit,
    placeholder: String,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        Text(label, style = KNetTheme.typography.labelMedium.copy(color = KNetTheme.colors.textPrimary))
        KNetDropdown(
            selectedItem = selected,
            items = items,
            onItemSelected = { it?.let(onSelected) },
            itemText = { it?.let(text) ?: placeholder },
            defaultItem = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NetworkConditionProtocolField(
    field: ProtocolCriteriaFieldDefinition,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (field) {
        is ProtocolCriteriaFieldDefinition.Text -> LabeledTextField(
            label = field.label, value = value, onValueChange = onValueChange,
            placeholder = field.placeholder, supportingText = field.description,
        )
        is ProtocolCriteriaFieldDefinition.Choice -> LabeledDropdown(
            label = field.label,
            selected = field.options.firstOrNull { it.value == value },
            items = field.options,
            text = ProtocolCriteriaOption::label,
            onSelected = { onValueChange(it.value) },
            placeholder = "Select an option",
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
    var download by remember(initial) {
        mutableStateOf(initial?.download?.bitsPerSecond?.div(1_000L)?.toString() ?: "100")
    }
    var upload by remember(initial) {
        mutableStateOf(initial?.upload?.bitsPerSecond?.div(1_000L)?.toString().orEmpty())
    }
    var downloadUtilization by remember(initial) {
        mutableStateOf(initial?.download?.utilizationPercent?.toString() ?: "100")
    }
    var uploadUtilization by remember(initial) {
        mutableStateOf(initial?.upload?.utilizationPercent?.toString() ?: "100")
    }
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
    var packetDuplication by remember(initial) {
        mutableStateOf(initial?.packetDuplicationPercent?.toString() ?: "0")
    }
    var packetReordering by remember(initial) {
        mutableStateOf(initial?.packetReorderingPercent?.toString() ?: "0")
    }
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
    val dialogScrollState = rememberScrollState()

    KNetDialog(
        onDismissRequest = onDismiss,
        title = if (initial == null) "New custom profile" else "Edit ${initial.name}",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        ) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(dialogScrollState)
                        .padding(end = KNetTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
                ) {
                    FormGroup(title = "Profile") {
                        LabeledTextField(
                            label = "Name",
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "Profile name",
                        )
                    }

                    FormGroup(
                        title = "Bandwidth",
                        description = "Leave a rate blank for unlimited throughput.",
                    ) {
                        FormFieldRow {
                            LabeledTextField(
                                label = "Download (kbps)",
                                value = download,
                                onValueChange = { download = it.filter(Char::isDigit) },
                                placeholder = "Unlimited",
                                isError = download.isNotBlank() && downKbps?.let { it in 1..maximumKbps } != true,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledTextField(
                                label = "Upload (kbps)",
                                value = upload,
                                onValueChange = { upload = it.filter(Char::isDigit) },
                                placeholder = "Unlimited",
                                isError = upload.isNotBlank() && upKbps?.let { it in 1..maximumKbps } != true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        FormFieldRow {
                            LabeledTextField(
                                label = "Download utilization (%)",
                                value = downloadUtilization,
                                onValueChange = { downloadUtilization = it.filter(Char::isDigit) },
                                isError = (downUtilization ?: 0) !in 1..100,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledTextField(
                                label = "Upload utilization (%)",
                                value = uploadUtilization,
                                onValueChange = { uploadUtilization = it.filter(Char::isDigit) },
                                isError = (upUtilization ?: 0) !in 1..100,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    FormGroup(title = "Delay and transport") {
                        FormFieldRow {
                            LabeledTextField(
                                label = "Latency (ms)",
                                value = latency,
                                onValueChange = { latency = it.filter(Char::isDigit) },
                                isError = latencyValue?.let {
                                    it in 0L..NetworkConditionProfile.MAXIMUM_DELAY_MILLIS
                                } != true,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledTextField(
                                label = "Jitter (ms)",
                                value = jitter,
                                onValueChange = { jitter = it.filter(Char::isDigit) },
                                isError = jitterValue?.let {
                                    it in 0L..NetworkConditionProfile.MAXIMUM_DELAY_MILLIS
                                } != true,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledTextField(
                                label = "Virtual MTU",
                                value = mtu,
                                onValueChange = { mtu = it.filter(Char::isDigit) },
                                isError = mtuValue?.let {
                                    it in NetworkConditionProfile.MINIMUM_VIRTUAL_MTU_BYTES..
                                        NetworkConditionProfile.MAXIMUM_VIRTUAL_MTU_BYTES
                                } != true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    FormGroup(
                        title = "VPN / TUN packet quality",
                        description = "Applied to eligible UDP, QUIC, and WebRTC datagrams.",
                    ) {
                        FormFieldRow {
                            LabeledTextField(
                                label = "Loss (%)",
                                value = packetLoss,
                                onValueChange = { packetLoss = it.filter(Char::isDigit) },
                                isError = (packetLoss.toIntOrNull() ?: -1) !in 0..100,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledTextField(
                                label = "Duplicate (%)",
                                value = packetDuplication,
                                onValueChange = { packetDuplication = it.filter(Char::isDigit) },
                                isError = (packetDuplication.toIntOrNull() ?: -1) !in 0..100,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledTextField(
                                label = "Reorder (%)",
                                value = packetReordering,
                                onValueChange = { packetReordering = it.filter(Char::isDigit) },
                                isError = (packetReordering.toIntOrNull() ?: -1) !in 0..100,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    FormGroup(title = "Failure behaviour") {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                        ) {
                            FailureEditorKind.entries.forEach { kind ->
                                KNetChip(
                                    text = kind.label,
                                    selected = failureKind == kind,
                                    onClick = { failureKind = kind },
                                )
                            }
                        }
                        if (failureKind == FailureEditorKind.SEEDED_RESET) {
                            FormFieldRow {
                                LabeledTextField(
                                    label = "Reset probability (%)",
                                    value = probability,
                                    onValueChange = { probability = it.filter(Char::isDigit) },
                                    isError = (probability.toIntOrNull() ?: 0) !in 1..100,
                                    modifier = Modifier.weight(1f),
                                )
                                LabeledTextField(
                                    label = "Deterministic seed",
                                    value = seed,
                                    onValueChange = {
                                        seed = it.filter { character -> character.isDigit() || character == '-' }
                                    },
                                    isError = seed.toLongOrNull() == null,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                KNetVerticalScrollbar(
                    scrollState = dialogScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }

            HorizontalDivider()
            DialogActions(
                confirmText = "Save profile",
                confirmEnabled = valid && name.isNotBlank(),
                onDismiss = onDismiss,
                onConfirm = {
                    val id = initial?.id ?: NetworkConditionProfileId(
                        "custom-" + System.currentTimeMillis().toString(36),
                    )
                    onSave(
                        NetworkConditionProfile(
                            id = id,
                            name = name.trim(),
                            download = NetworkDirectionCondition(
                                downKbps?.times(1_000L),
                                checkNotNull(downUtilization),
                            ),
                            upload = NetworkDirectionCondition(
                                upKbps?.times(1_000L),
                                checkNotNull(upUtilization),
                            ),
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
            )
        }
    }
}

@Composable
private fun FormGroup(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = KNetTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
    ) {
        Text(title, style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary))
        if (description != null) {
            Text(description, style = KNetTheme.typography.caption.copy(color = colors.textMuted))
        }
        content()
    }
}

@Composable
private fun FormFieldRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supportingText: String? = null,
    isError: Boolean = false,
) {
    val colors = KNetTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
        Text(label, style = KNetTheme.typography.labelMedium.copy(color = colors.textPrimary))
        KNetTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            config = InputFieldConfig(
                placeholder = placeholder,
                supportingText = supportingText,
                showHoverPopupOnOverflow = false,
            ),
            state = InputFieldState(isError = isError),
        )
    }
}

@Composable
private fun DialogActions(
    confirmText: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KNetButton(onClick = onDismiss, variant = ButtonVariant.Secondary) { Text("Cancel") }
        Spacer(Modifier.width(KNetTheme.spacing.sm))
        KNetButton(
            onClick = onConfirm,
            variant = ButtonVariant.Primary,
            enabled = confirmEnabled,
        ) {
            Text(confirmText)
        }
    }
}

internal fun profileSummary(profile: NetworkConditionProfile): String = buildString {
    append("Jitter ±${profile.jitterMillis} ms  •  MTU ${profile.virtualMtuBytes}")
    append("  •  ${profile.failure.summary()}")
    if (profile.download.utilizationPercent != 100) append("  •  Download utilization ${profile.download.utilizationPercent}%")
    if (profile.upload.utilizationPercent != 100) append("  •  Upload utilization ${profile.upload.utilizationPercent}%")
    if (profile.packetLossPercent > 0) append("  •  ${profile.packetLossPercent}% loss")
    if (profile.packetDuplicationPercent > 0) append("  •  ${profile.packetDuplicationPercent}% duplicate")
    if (profile.packetReorderingPercent > 0) append("  •  ${profile.packetReorderingPercent}% reorder")
}

internal fun rate(value: Long?): String = formatNetworkRate(value)

internal fun formatBytes(value: Long): String = when {
    value >= 1_073_741_824L -> "${value / 1_073_741_824L} GB"
    value >= 1_048_576L -> "${value / 1_048_576L} MB"
    value >= 1_024L -> "${value / 1_024L} KB"
    else -> "$value B"
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

internal fun NetworkFailureBehavior.summary(): String = when (this) {
    NetworkFailureBehavior.None -> "No faults"
    NetworkFailureBehavior.Offline -> "Offline"
    NetworkFailureBehavior.Timeout -> "Timeout"
    NetworkFailureBehavior.ResetFlow -> "Reset flow"
    is NetworkFailureBehavior.SeededReset -> "$probabilityPercent% seeded reset"
}
