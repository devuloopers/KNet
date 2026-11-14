package com.devuloopers.knet.ui.core.components.policy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Duration owned by the ViewModel so navigation cannot replay an expired save confirmation. */
const val RULE_REPLACEMENT_NOTICE_MILLIS: Long = 4_000L

/**
 * Confirmation for one committed device-local replacement, independent of subsequent tab selection.
 * @property commandId Captured editor invocation used to fence dismissal of a newer confirmation.
 * @property deviceName Captured display name; never used to authenticate or select the policy owner.
 */
data class RuleReplacementNoticeState(val commandId: String, val deviceName: String) {
    /** Plain-language confirmation shared by every rule manager and the Traffic shortcut. */
    val message: String get() = "Existing rule replaced for $deviceName."
}

/** Paints a non-blocking themed confirmation without resizing the workspace or taking keyboard focus. */
@Composable
fun RuleReplacementNotice(notice: RuleReplacementNoticeState?, modifier: Modifier = Modifier) {
    if (notice == null) return
    KNetSurface(
        modifier = modifier.testTag("Rule replacement confirmation")
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = KNetTheme.colors.surfaceVariant,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
        shape = KNetTheme.shapes.medium,
    ) {
        Text(notice.message, modifier = Modifier.padding(KNetTheme.spacing.md),
            style = KNetTheme.typography.bodySmall, color = KNetTheme.colors.textPrimary,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
