package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_limited_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_limited_title
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_private_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_private_title
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_secure_summary
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_secure_title
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.stringResource

/** Compact security and privacy explanation matching the certificate onboarding design. */
@Composable
internal fun CertificateBenefitList(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        CertificateBenefit(
            icon = KNetIcons.Shield,
            title = stringResource(Res.string.certificate_secure_title),
            summary = stringResource(Res.string.certificate_secure_summary),
        )
        HorizontalDivider(color = KNetTheme.colors.border)
        CertificateBenefit(
            icon = KNetIcons.Lock,
            title = stringResource(Res.string.certificate_private_title),
            summary = stringResource(Res.string.certificate_private_summary),
        )
        HorizontalDivider(color = KNetTheme.colors.border)
        CertificateBenefit(
            icon = KNetIcons.Schedule,
            title = stringResource(Res.string.certificate_limited_title),
            summary = stringResource(Res.string.certificate_limited_summary),
        )
    }
}

@Composable
private fun CertificateBenefit(
    icon: ImageVector,
    title: String,
    summary: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = KNetTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KNetSurface(
            modifier = Modifier.size(52.dp),
            color = KNetTheme.colors.surfaceVariant.copy(alpha = 0.64f),
            shape = KNetTheme.shapes.large,
            border = BorderStroke(1.dp, KNetTheme.colors.border),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KNetTheme.colors.accent,
                modifier = Modifier.size(27.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs),
        ) {
            Text(
                text = title,
                style = KNetTheme.typography.titleLarge,
                color = KNetTheme.colors.textPrimary,
            )
            Text(
                text = summary,
                style = KNetTheme.typography.bodyMedium,
                color = KNetTheme.colors.textSecondary,
            )
        }
    }
}
