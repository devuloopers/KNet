package com.devuloopers.knet.companion.sharedui.screen.connect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.presentation.state.CompanionConnectFeedback
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.checking_local_network
import com.devuloopers.knet.companion.sharedui.generated.resources.connect_security_note
import com.devuloopers.knet.companion.sharedui.generated.resources.network_pairing_unavailable
import com.devuloopers.knet.ui.core.components.placeholder.KNetShimmerBox
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Fixed-height status card that changes reactively without moving the surrounding Connect content. */
@Composable
internal fun ConnectFeedbackCard(feedback: CompanionConnectFeedback) {
    val motion = KNetTheme.motion
    val duration = if (motion.animationsEnabled) motion.durationNormal else motion.durationInstant
    KNetSurface(
        modifier = Modifier.fillMaxWidth().height(112.dp),
        color = KNetTheme.colors.surfaceVariant.copy(alpha = 0.62f),
        shape = KNetTheme.shapes.large,
        border = BorderStroke(1.dp, KNetTheme.colors.border),
    ) {
        AnimatedContent(
            targetState = feedback,
            modifier = Modifier.fillMaxSize().padding(horizontal = KNetTheme.spacing.xl),
            transitionSpec = {
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
            contentAlignment = Alignment.CenterStart,
            label = "ConnectFeedback",
        ) { current ->
            when (current) {
                CompanionConnectFeedback.CheckingNetwork -> CheckingNetworkFeedback()
                CompanionConnectFeedback.SecureQrReady -> ConnectFeedbackMessage(
                    icon = KNetIcons.Shield,
                    message = stringResource(Res.string.connect_security_note),
                    positive = true,
                )
                CompanionConnectFeedback.NetworkUnavailable -> ConnectFeedbackMessage(
                    icon = KNetIcons.Warning,
                    message = stringResource(Res.string.network_pairing_unavailable),
                    positive = false,
                )
            }
        }
    }
}

@Composable
private fun CheckingNetworkFeedback() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KNetShimmerBox(modifier = Modifier.size(28.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.checking_local_network),
                style = KNetTheme.typography.bodySmall,
                color = KNetTheme.colors.textMuted,
            )
            KNetShimmerBox(modifier = Modifier.fillMaxWidth(0.86f).height(12.dp))
            KNetShimmerBox(modifier = Modifier.fillMaxWidth(0.58f).height(12.dp))
        }
    }
}

@Composable
private fun ConnectFeedbackMessage(
    icon: ImageVector,
    message: String,
    positive: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (positive) KNetTheme.colors.accent else KNetTheme.colors.semantic.warning,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = message,
            style = KNetTheme.typography.bodyMedium,
            color = KNetTheme.colors.textSecondary,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
    }
}
