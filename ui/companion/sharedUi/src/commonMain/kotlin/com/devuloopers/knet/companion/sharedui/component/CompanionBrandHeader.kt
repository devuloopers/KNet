package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.brand_companion
import com.devuloopers.knet.companion.sharedui.generated.resources.brand_knet
import com.devuloopers.knet.companion.sharedui.generated.resources.knet_companion_logo
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Centered companion product mark reusable across onboarding screens. */
@Composable
internal fun CompanionBrandHeader(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < 300.dp
        val brandStyle = if (compact) KNetTheme.typography.heading else KNetTheme.typography.hero
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.knet_companion_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(if (compact) 30.dp else 36.dp),
            )
            Text(
                text = stringResource(Res.string.brand_knet),
                style = brandStyle,
                color = KNetTheme.colors.accent,
            )
            Text(
                text = stringResource(Res.string.brand_companion),
                style = brandStyle,
                color = KNetTheme.colors.textPrimary,
            )
        }
    }
}
