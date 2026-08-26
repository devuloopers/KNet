package com.devuloopers.knet.companion.sharedui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.presentation.CompanionUiState
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.app_title
import com.devuloopers.knet.companion.sharedui.generated.resources.foundation_badge
import com.devuloopers.knet.companion.sharedui.generated.resources.foundation_pending
import com.devuloopers.knet.companion.sharedui.generated.resources.foundation_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.network_ready
import com.devuloopers.knet.companion.sharedui.generated.resources.network_unavailable
import com.devuloopers.knet.companion.sharedui.generated.resources.network_unknown
import com.devuloopers.knet.companion.sharedui.generated.resources.paired_desktop_count_one
import com.devuloopers.knet.companion.sharedui.generated.resources.paired_desktop_count_other
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Root companion UI rendered by every product that adopts Compose Multiplatform. */
@Composable
public fun KNetCompanionApp(
    state: CompanionUiState,
    modifier: Modifier = Modifier,
) {
    KNetTheme(themeMode = ThemeMode.System) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CompanionFoundationScreen(state = state)
        }
    }
}

@Composable
private fun CompanionFoundationScreen(state: CompanionUiState) {
    val spacing = KNetTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xxl, vertical = spacing.xxxl),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(spacing.giant),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "K",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(spacing.lg))
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = stringResource(Res.string.app_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.foundation_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    Text(
                        text = stringResource(Res.string.foundation_badge),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusRow(
                        color = state.network.statusColor(),
                        text = stringResource(state.network.statusResource()),
                    )
                    StatusRow(
                        color = MaterialTheme.colorScheme.primary,
                        text = stringResource(pairedDesktopCountResource(state.registrations.size), state.registrations.size),
                    )
                }
            }

            Text(
                text = stringResource(Res.string.foundation_pending),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatusRow(color: Color, text: String) {
    val spacing = KNetTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(spacing.md))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

internal fun CompanionNetworkState.statusResource(): StringResource = when (this) {
    is CompanionNetworkState.Available -> Res.string.network_ready
    CompanionNetworkState.Unavailable -> Res.string.network_unavailable
    CompanionNetworkState.Unknown -> Res.string.network_unknown
}

internal fun pairedDesktopCountResource(count: Int): StringResource =
    if (count == 1) {
        Res.string.paired_desktop_count_one
    } else {
        Res.string.paired_desktop_count_other
    }

@Composable
private fun CompanionNetworkState.statusColor(): Color = when (this) {
    is CompanionNetworkState.Available -> KNetTheme.colors.semantic.success
    CompanionNetworkState.Unavailable -> KNetTheme.colors.semantic.error
    CompanionNetworkState.Unknown -> KNetTheme.colors.semantic.warning
}
