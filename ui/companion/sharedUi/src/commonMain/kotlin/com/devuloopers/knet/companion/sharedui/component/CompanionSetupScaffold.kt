package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Persistent mobile chrome with optional desktop selector and onboarding progress. */
@Composable
internal fun CompanionSetupScaffold(
    progress: CompanionSetupProgress? = null,
    modifier: Modifier = Modifier,
    registration: CompanionRegistration? = null,
    enabled: Boolean = true,
    onDesktops: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(KNetTheme.colors.background).safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CompanionBrandHeader(onBack = onBack)
            if (registration != null) CompanionDesktopSelector(registration, enabled, onDesktops)
            if (progress != null) {
                CompanionSetupStepper(progress, Modifier.padding(top = 4.dp, bottom = 8.dp))
                HorizontalDivider(color = KNetTheme.colors.border)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

/** The selected identity stays visible during setup, runtime changes and handoffs. */
@Composable
internal fun CompanionDesktopSelector(registration: CompanionRegistration, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button,
            onClickLabel = stringResource(Res.string.manage_desktops), onClick = onClick).padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(KNetIcons.Desktop, null, tint = KNetTheme.colors.textSecondary, modifier = Modifier.size(34.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(Res.string.desktop_caption), style = KNetTheme.typography.labelMedium, color = KNetTheme.colors.textSecondary)
                Text(registration.desktopDisplayName.value, style = KNetTheme.typography.heading,
                    color = KNetTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(registration.controlEndpoint.host, style = KNetTheme.typography.bodyMedium,
                    color = KNetTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(KNetIcons.ChevronRight, null, tint = KNetTheme.colors.textSecondary)
        }
        HorizontalDivider(color = KNetTheme.colors.border)
    }
}
