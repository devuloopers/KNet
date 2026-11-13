package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Space available to the hero after native insets and persistent chrome, including large text. */
internal val LocalCompanionCompactBody = staticCompositionLocalOf { false }

/** Instruction page: scrollable guidance and a separate, reachable native Settings/Continue action. */
@Composable
internal fun CompanionPage(
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                content = actions)
        }
    }
}

/** Centered hero pages grow only when needed; compact screens scroll instead of clipping controls. */
@Composable
internal fun CompanionHeroPage(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val viewportHeight = maxHeight
        val compact = viewportHeight < 540.dp
        CompositionLocalProvider(LocalCompanionCompactBody provides compact) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight).padding(horizontal = 24.dp, vertical = if (compact) 16.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp, Alignment.CenterVertically),
                content = content)
        }
    }
}

/** Consistent mobile title and explanatory copy. */
@Composable
internal fun CompanionHeading(title: String, summary: String? = null, centered: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.fillMaxWidth(), style = KNetTheme.typography.hero,
            color = KNetTheme.colors.textPrimary, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        if (!summary.isNullOrBlank()) Text(summary, Modifier.fillMaxWidth(), style = KNetTheme.typography.bodyLarge,
            color = KNetTheme.colors.textSecondary, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
    }
}

/** Single quiet inset surface shared by guidance, certificate metadata and runtime status. */
@Composable
internal fun CompanionPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    KNetSurface(modifier.fillMaxWidth(), color = KNetTheme.colors.surfaceVariant,
        shape = KNetTheme.shapes.extraLarge, border = BorderStroke(1.dp, KNetTheme.colors.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Touch-size action shared by instruction screens and modal footers. */
@Composable
internal fun CompanionActionButton(text: String, onClick: () -> Unit, variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true, loading: Boolean = false, leadingIcon: ImageVector? = null) {
    KNetButton(onClick, Modifier.fillMaxWidth(), variant = variant, size = ButtonSize.Touch,
        enabled = enabled, loading = loading) {
        leadingIcon?.let { Icon(it, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = KNetTheme.typography.titleLarge)
    }
}

/** Compact informational row with optional accessible, reduced-motion-aware progress. */
@Composable
internal fun CompanionInfo(text: String, icon: ImageVector = KNetIcons.Info,
    tint: Color = KNetTheme.colors.textSecondary, busy: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (busy) CompanionProgressGlyph(tint, Modifier.size(24.dp))
        else Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Text(text, Modifier.weight(1f), style = KNetTheme.typography.bodyMedium, color = KNetTheme.colors.textSecondary)
    }
}

/** Aligned mobile values wrap when necessary rather than being hidden at larger accessibility scales. */
@Composable
internal fun CompanionStatusRow(label: String, value: String, tint: Color = KNetTheme.colors.textSecondary) {
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.42f), style = KNetTheme.typography.titleMedium, color = KNetTheme.colors.textPrimary)
        Text(value, Modifier.weight(0.58f), style = KNetTheme.typography.bodyMedium, color = tint, textAlign = TextAlign.End)
    }
}

/** Quiet centered footer without claiming that capture has started before native confirmation. */
@Composable
internal fun CompanionFootnote(text: String) {
    Text(text, Modifier.fillMaxWidth(), style = KNetTheme.typography.bodySmall,
        color = KNetTheme.colors.textSecondary, textAlign = TextAlign.Center)
}
