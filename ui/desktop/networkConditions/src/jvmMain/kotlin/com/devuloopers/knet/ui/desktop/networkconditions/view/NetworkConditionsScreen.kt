package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfile
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRule
import com.devuloopers.knet.domain.networkconditions.NetworkConditionRuleId
import com.devuloopers.knet.domain.networkconditions.NetworkDirectionCondition
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.chip.KNetChip
import com.devuloopers.knet.ui.core.components.dialog.AlertDialog
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.InputFieldState
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionRuleDraft
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkConditionsState
import com.devuloopers.knet.ui.desktop.networkconditions.model.NetworkThroughputHistory
import com.devuloopers.knet.ui.desktop.networkconditions.model.hasObservedTraffic
import com.devuloopers.knet.ui.desktop.networkconditions.model.niceChartMaximum
import com.devuloopers.knet.ui.desktop.networkconditions.model.throughputReference
import com.devuloopers.knet.ui.desktop.networkconditions.viewmodel.NetworkConditionsViewModel
import kotlin.math.max

/** KNet-native workspace for global, domain-specific, and packet-level network simulation. */
@Composable
fun NetworkConditionsScreen(
    viewModel: NetworkConditionsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        viewModel.startRuntimeMonitoring()
        onDispose(viewModel::stopRuntimeMonitoring)
    }
    var showCustomProfileDialog by remember { mutableStateOf(false) }
    var customProfileSeed by remember { mutableStateOf<NetworkConditionProfile?>(null) }
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = spacing.xxl, vertical = spacing.xl)
                .padding(end = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            ScreenHeader(
                enabled = state.configuration.enabled,
                selectedProfile = state.configuration.profile(state.configuration.globalProfileId),
                onEnabledChange = viewModel::setEnabled,
                onReset = viewModel::reset,
            )

            Overview(
                state = state,
                onSelectProfile = viewModel::selectGlobalProfile,
                onNewCustomProfile = {
                    customProfileSeed = null
                    showCustomProfileDialog = true
                },
            )

            PacketConditionsNotice()

            DomainRulesSection(
                state = state,
                onAdd = viewModel::beginAddRule,
                onEnabledChange = viewModel::setRuleEnabled,
                onEdit = viewModel::beginEditRule,
                onDelete = viewModel::deleteRule,
            )

            ProfilesSection(
                profiles = state.configuration.profiles,
                onAdd = {
                    customProfileSeed = null
                    showCustomProfileDialog = true
                },
                onEdit = { profile ->
                    customProfileSeed = profile
                    showCustomProfileDialog = true
                },
                onDelete = viewModel::deleteProfile,
            )
        }

        KNetVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }

    state.ruleDraft?.let { draft ->
        RuleDialog(
            draft = draft,
            profiles = state.configuration.profiles,
            onHostChange = viewModel::updateDraftHostPattern,
            onPortChange = viewModel::updateDraftPort,
            onEnabledChange = viewModel::updateDraftEnabled,
            onProfileSelected = viewModel::selectDraftProfile,
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
private fun ScreenHeader(
    enabled: Boolean,
    selectedProfile: NetworkConditionProfile?,
    onEnabledChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Network Conditions",
                    style = typography.display.copy(color = colors.textPrimary),
                )
                KNetBadge(
                    text = if (enabled) "ACTIVE" else "BYPASSED",
                    containerColor = if (enabled) colors.semantic.successContainer else colors.surfaceVariant,
                    contentColor = if (enabled) colors.semantic.success else colors.textMuted,
                )
            }
            Text(
                text = when {
                    !enabled -> "Profiles and rules are saved, but traffic shaping is currently bypassed."
                    selectedProfile != null -> "Applying ${selectedProfile.name} globally, with domain rules taking priority."
                    else -> "Domain rules are active; unmatched traffic passes through unchanged."
                },
                style = typography.bodyMedium.copy(color = colors.textSecondary),
            )
        }

        KNetSurface(
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shape = KNetTheme.shapes.small,
        ) {
            KNetSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                label = if (enabled) "Enabled" else "Disabled",
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            )
        }
        KNetButton(onClick = onReset, variant = ButtonVariant.Secondary) {
            Icon(
                imageVector = KNetIcons.Refresh,
                contentDescription = null,
                modifier = Modifier.size(KNetTheme.dimensions.iconSizeSmall),
            )
            Spacer(Modifier.width(spacing.xs))
            Text("Reset")
        }
    }
}

@Composable
private fun Overview(
    state: NetworkConditionsState,
    onSelectProfile: (NetworkConditionProfileId?) -> Unit,
    onNewCustomProfile: () -> Unit,
) {
    val spacing = KNetTheme.spacing
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 880.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                GlobalConditionCard(
                    state = state,
                    onSelectProfile = onSelectProfile,
                    onNewCustomProfile = onNewCustomProfile,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                LiveApplicationCard(state = state, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                GlobalConditionCard(
                    state = state,
                    onSelectProfile = onSelectProfile,
                    onNewCustomProfile = onNewCustomProfile,
                    modifier = Modifier.fillMaxWidth(),
                )
                LiveApplicationCard(state = state, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GlobalConditionCard(
    state: NetworkConditionsState,
    onSelectProfile: (NetworkConditionProfileId?) -> Unit,
    onNewCustomProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = state.configuration
    val selected = configuration.profile(configuration.globalProfileId)
    FeatureCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("GLOBAL CONDITION")
            Text(
                text = if (configuration.enabled) "APPLYING NOW" else "SAVED · BYPASSED",
                style = KNetTheme.typography.caption.copy(
                    color = if (configuration.enabled) {
                        KNetTheme.colors.semantic.success
                    } else {
                        KNetTheme.colors.textMuted
                    },
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Text(
            text = selected?.name ?: "Domain rules only",
            style = KNetTheme.typography.titleLarge.copy(color = KNetTheme.colors.textPrimary),
        )
        Text(
            text = when {
                !configuration.enabled -> "This profile is saved and will resume when Network Conditions is enabled."
                selected == null -> "Unmatched hosts continue without throttling."
                else -> "Applied to unmatched traffic; enabled domain rules take priority."
            },
            style = KNetTheme.typography.bodySmall.copy(color = KNetTheme.colors.textSecondary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        ) {
            MetricTile("DOWNLOAD", selected?.let { rate(it.download.effectiveBitsPerSecond) } ?: "—", Modifier.weight(1f))
            MetricTile("UPLOAD", selected?.let { rate(it.upload.effectiveBitsPerSecond) } ?: "—", Modifier.weight(1f))
            MetricTile("LATENCY", selected?.let { "${it.latencyMillis} ms" } ?: "—", Modifier.weight(1f))
        }
        Text(
            text = selected?.let { profile ->
                "Jitter ±${profile.jitterMillis} ms   ·   MTU ${profile.virtualMtuBytes}   ·   " +
                    profile.failure.summary()
            } ?: "Select a global profile or continue with domain-specific rules only.",
            style = KNetTheme.typography.caption.copy(color = KNetTheme.colors.textMuted),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        ) {
            KNetChip(
                text = "Domain rules only",
                selected = configuration.globalProfileId == null,
                onClick = { onSelectProfile(null) },
            )
            configuration.profiles.forEach { profile ->
                KNetChip(
                    text = profile.name,
                    selected = configuration.globalProfileId == profile.id,
                    onClick = { onSelectProfile(profile.id) },
                )
            }
        }
        KNetButton(
            onClick = onNewCustomProfile,
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Compact,
        ) {
            Icon(
                imageVector = KNetIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(KNetTheme.dimensions.iconSizeSmall),
            )
            Spacer(Modifier.width(KNetTheme.spacing.xs))
            Text("Custom profile")
        }
    }
}

@Composable
private fun LiveApplicationCard(state: NetworkConditionsState, modifier: Modifier = Modifier) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    val reference = throughputReference(state)
    val history = state.throughput
    val hasObservedTraffic = history.hasObservedTraffic()
    FeatureCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("LIVE APPLICATION")
            Text(
                text = reference.label,
                style = KNetTheme.typography.caption.copy(color = colors.textMuted),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            RateSummary(
                label = "DOWNLOAD",
                value = if (history.currentSampleIsValid && hasObservedTraffic) {
                    rate(history.currentDownloadBitsPerSecond)
                } else {
                    "—"
                },
                color = colors.accent,
                modifier = Modifier.weight(1f),
            )
            RateSummary(
                label = "UPLOAD",
                value = if (history.currentSampleIsValid && hasObservedTraffic) {
                    rate(history.currentUploadBitsPerSecond)
                } else {
                    "—"
                },
                color = colors.semantic.success,
                modifier = Modifier.weight(1f),
            )
        }

        LiveThroughputGraph(
            history = history,
            downloadReferenceBitsPerSecond = reference.downloadBitsPerSecond,
            uploadReferenceBitsPerSecond = reference.uploadBitsPerSecond,
            activeFlows = state.runtime.activeFlows,
            queuedBytes = state.runtime.queuedBytes,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            MetricTile("ACTIVE FLOWS", state.runtime.activeFlows.toString(), Modifier.weight(1f))
            MetricTile("QUEUED", formatBytes(state.runtime.queuedBytes), Modifier.weight(1f))
        }
        Text(
            text = "Observed payload  ↓ ${formatBytes(state.runtime.downloadedBytes)}" +
                "   ↑ ${formatBytes(state.runtime.uploadedBytes)}",
            style = KNetTheme.typography.caption.copy(color = colors.textMuted),
        )
    }
}

@Composable
private fun RateSummary(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, KNetTheme.shapes.small))
        Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
            Text(label, style = KNetTheme.typography.caption.copy(color = KNetTheme.colors.textMuted))
            Text(value, style = KNetTheme.typography.titleLarge.copy(color = KNetTheme.colors.textPrimary))
        }
    }
}

@Composable
private fun LiveThroughputGraph(
    history: NetworkThroughputHistory,
    downloadReferenceBitsPerSecond: Long?,
    uploadReferenceBitsPerSecond: Long?,
    activeFlows: Int,
    queuedBytes: Long,
) {
    val colors = KNetTheme.colors
    val downloadPath = remember { Path() }
    val uploadPath = remember { Path() }
    val referenceDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 7f)) }
    val measuredPeak = max(
        history.downloadBitsPerSecond.maxOrNull() ?: 0L,
        history.uploadBitsPerSecond.maxOrNull() ?: 0L,
    )
    val referencePeak = max(downloadReferenceBitsPerSecond ?: 0L, uploadReferenceBitsPerSecond ?: 0L)
    val chartMaximum = niceChartMaximum(max(measuredPeak, referencePeak))
    val hasObservedTraffic = history.hasObservedTraffic()
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

    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.small,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasObservedTraffic) "Actual throughput" else "Live monitor",
                    style = KNetTheme.typography.labelMedium.copy(color = colors.textSecondary),
                )
                Text(
                    text = if (!hasObservedTraffic && referencePeak == 0L) {
                        "Auto scale"
                    } else {
                        "Scale ${rate(chartMaximum)}"
                    },
                    style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .semantics { contentDescription = accessibilityText },
            ) {
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

                    fun populatePath(path: Path, values: LongArray) {
                        path.reset()
                        var previousValid = false
                        val denominator = (values.size - 1).coerceAtLeast(1)
                        values.indices.forEach { index ->
                            val valid = history.validSamples.getOrElse(index) { false }
                            if (valid) {
                                val x = size.width * index / denominator.toFloat()
                                val y = referenceY(values[index])
                                if (previousValid) path.lineTo(x, y) else path.moveTo(x, y)
                            }
                            previousValid = valid
                        }
                    }

                    if (hasObservedTraffic) {
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
                        )
                        Text(
                            "Actual rates will appear here",
                            style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("60s", style = KNetTheme.typography.caption.copy(color = colors.textMuted))
                Text("Now", style = KNetTheme.typography.caption.copy(color = colors.textMuted))
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = KNetTheme.colors
    KNetSurface(
        modifier = modifier,
        color = colors.surfaceVariant,
        shape = KNetTheme.shapes.small,
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
        ) {
            Text(label, style = KNetTheme.typography.caption.copy(color = colors.textMuted))
            Text(value, style = KNetTheme.typography.titleMedium.copy(color = colors.textPrimary))
        }
    }
}

@Composable
private fun PacketConditionsNotice() {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.semantic.infoContainer,
        border = BorderStroke(1.dp, colors.semantic.info.copy(alpha = 0.45f)),
        shape = KNetTheme.shapes.medium,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = KNetIcons.Shield,
                contentDescription = null,
                tint = colors.semantic.info,
                modifier = Modifier.size(KNetTheme.dimensions.iconSizeLarge),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    "Advanced packet conditions",
                    style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary),
                )
                Text(
                    "Android Companion VPN applies the global profile to UDP, QUIC/HTTP/3, and WebRTC datagrams. " +
                        "Proxy traffic is shaped once at the appropriate transport boundary.",
                    style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                )
                Text(
                    "iOS and desktop packet adapters remain in qualification until their signed TUN implementations " +
                        "pass physical-device validation.",
                    style = KNetTheme.typography.caption.copy(color = colors.textMuted),
                )
            }
            KNetBadge(
                text = "ANDROID READY",
                containerColor = colors.semantic.successContainer,
                contentColor = colors.semantic.success,
            )
        }
    }
}

@Composable
private fun DomainRulesSection(
    state: NetworkConditionsState,
    onAdd: () -> Unit,
    onEnabledChange: (NetworkConditionRule, Boolean) -> Unit,
    onEdit: (NetworkConditionRule) -> Unit,
    onDelete: (NetworkConditionRuleId) -> Unit,
) {
    val rules = state.configuration.rules
    SectionHeader(
        title = "Domain rules",
        subtitle = "Override the global profile for an exact host, wildcard domain, or port.",
        actionText = "Add domain rule",
        onAction = onAdd,
    )
    if (rules.isEmpty()) {
        EmptyRulesCard(onAdd = onAdd)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
            rules.forEach { rule ->
                RuleRow(
                    rule = rule,
                    profile = state.configuration.profile(rule.profileId),
                    onEnabledChange = { enabled -> onEnabledChange(rule, enabled) },
                    onEdit = { onEdit(rule) },
                    onDelete = { onDelete(rule.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyRulesCard(onAdd: () -> Unit) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = KNetIcons.Hub,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(KNetTheme.dimensions.iconSizeLarge),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "No domain overrides",
                    style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary),
                )
                Text(
                    "Add one here or use Add to Network Conditions from any Traffic row.",
                    style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                )
            }
            KNetButton(onClick = onAdd, variant = ButtonVariant.Secondary, size = ButtonSize.Compact) {
                Text("Create first rule")
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: NetworkConditionRule,
    profile: NetworkConditionProfile?,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.small,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rule.target.displayValue,
                        style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (profile != null) KNetBadge(profile.name)
                }
                Text(
                    text = profile?.let(::compactProfileSummary) ?: "Missing profile",
                    style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            KNetSwitch(checked = rule.enabled, onCheckedChange = onEnabledChange)
            KNetIconButton(
                onClick = onEdit,
                icon = KNetIcons.Edit,
                contentDescription = "Edit ${rule.target.displayValue}",
                tint = colors.textSecondary,
            )
            KNetIconButton(
                onClick = onDelete,
                icon = KNetIcons.Delete,
                contentDescription = "Delete ${rule.target.displayValue}",
                tint = colors.semantic.error,
            )
        }
    }
}

@Composable
private fun ProfilesSection(
    profiles: List<NetworkConditionProfile>,
    onAdd: () -> Unit,
    onEdit: (NetworkConditionProfile) -> Unit,
    onDelete: (NetworkConditionProfileId) -> Unit,
) {
    SectionHeader(
        title = "Profiles",
        subtitle = "Reusable bandwidth, delay, reliability, and packet-quality presets.",
        actionText = "New custom profile",
        onAction = onAdd,
    )
    Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
        profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                onEdit = { onEdit(profile) },
                onDelete = { onDelete(profile.id) },
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: NetworkConditionProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    KNetSurface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.small,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        profile.name,
                        style = KNetTheme.typography.titleSmall.copy(color = colors.textPrimary),
                    )
                    KNetBadge(
                        text = if (profile.builtIn) "BUILT-IN" else "CUSTOM",
                        containerColor = if (profile.builtIn) colors.surfaceVariant else colors.semantic.infoContainer,
                        contentColor = if (profile.builtIn) colors.textMuted else colors.semantic.info,
                    )
                }
                Text(
                    text = profileSummary(profile),
                    style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!profile.builtIn) {
                KNetIconButton(
                    onClick = onEdit,
                    icon = KNetIcons.Edit,
                    contentDescription = "Edit ${profile.name}",
                    tint = colors.textSecondary,
                )
                KNetIconButton(
                    onClick = onDelete,
                    icon = KNetIcons.Delete,
                    contentDescription = "Delete ${profile.name}",
                    tint = colors.semantic.error,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
) {
    val colors = KNetTheme.colors
    val spacing = KNetTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(title, style = KNetTheme.typography.titleLarge.copy(color = colors.textPrimary))
            Text(subtitle, style = KNetTheme.typography.bodySmall.copy(color = colors.textSecondary))
        }
        KNetButton(onClick = onAction, variant = ButtonVariant.Primary) {
            Icon(
                imageVector = KNetIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(KNetTheme.dimensions.iconSizeSmall),
            )
            Spacer(Modifier.width(spacing.xs))
            Text(actionText)
        }
    }
}

@Composable
private fun FeatureCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = KNetTheme.colors
    KNetSurface(
        modifier = modifier,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shape = KNetTheme.shapes.medium,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(KNetTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md),
        ) {
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = KNetTheme.typography.caption.copy(
            color = KNetTheme.colors.accent,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun RuleDialog(
    draft: NetworkConditionRuleDraft,
    profiles: List<NetworkConditionProfile>,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (NetworkConditionProfileId) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    KNetDialog(
        onDismissRequest = onDismiss,
        title = if (draft.existingRuleId == null) "Add domain condition" else "Edit domain condition",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        ) {
            LabeledTextField(
                label = "Host or wildcard",
                value = draft.hostPattern,
                onValueChange = onHostChange,
                placeholder = "api.example.com or *.example.com",
                supportingText = "Use a wildcard to include every subdomain.",
            )
            LabeledTextField(
                label = "Port",
                value = draft.port,
                onValueChange = onPortChange,
                placeholder = "Any port",
                supportingText = "Leave blank to match every destination port.",
            )
            KNetSwitch(
                checked = draft.enabled,
                onCheckedChange = onEnabledChange,
                label = "Rule enabled",
            )
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                Text(
                    "Condition profile",
                    style = KNetTheme.typography.labelMedium.copy(color = KNetTheme.colors.textPrimary),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
                ) {
                    profiles.forEach { profile ->
                        KNetChip(
                            text = profile.name,
                            selected = draft.profileId == profile.id,
                            onClick = { onProfileSelected(profile.id) },
                        )
                    }
                }
            }
            DialogActions(
                confirmText = "Save rule",
                confirmEnabled = draft.hostPattern.isNotBlank(),
                onDismiss = onDismiss,
                onConfirm = onSave,
            )
        }
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

private fun compactProfileSummary(profile: NetworkConditionProfile): String = buildString {
    append("↓ ${rate(profile.download.effectiveBitsPerSecond)}")
    append("  ↑ ${rate(profile.upload.effectiveBitsPerSecond)}")
    append("  •  ${profile.latencyMillis} ms")
    if (profile.jitterMillis > 0) append(" ±${profile.jitterMillis} ms")
    if (profile.failure != NetworkFailureBehavior.None) append("  •  ${profile.failure.summary()}")
}

private fun profileSummary(profile: NetworkConditionProfile): String = buildString {
    append(compactProfileSummary(profile))
    append("  •  MTU ${profile.virtualMtuBytes}")
    if (profile.packetLossPercent > 0) append("  •  ${profile.packetLossPercent}% loss")
    if (profile.packetDuplicationPercent > 0) append("  •  ${profile.packetDuplicationPercent}% duplicate")
    if (profile.packetReorderingPercent > 0) append("  •  ${profile.packetReorderingPercent}% reorder")
}

private fun rate(value: Long?): String = when {
    value == null -> "Unlimited"
    value >= 1_000_000_000L -> "${value / 1_000_000_000L} Gbps"
    value >= 1_000_000L -> "${value / 1_000_000L} Mbps"
    else -> "${value / 1_000L} kbps"
}

private fun formatBytes(value: Long): String = when {
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

private fun NetworkFailureBehavior.summary(): String = when (this) {
    NetworkFailureBehavior.None -> "No faults"
    NetworkFailureBehavior.Offline -> "Offline"
    NetworkFailureBehavior.Timeout -> "Timeout"
    NetworkFailureBehavior.ResetFlow -> "Reset flow"
    is NetworkFailureBehavior.SeededReset -> "$probabilityPercent% seeded reset"
}
