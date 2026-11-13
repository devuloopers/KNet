package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.companion.sharedui.generated.resources.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Official vector symbol and wordmark; navigation gets its own row to preserve mobile text space. */
@Composable
internal fun CompanionBrandHeader(onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(Res.drawable.knet_companion_logo), null, Modifier.size(44.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
            Text(stringResource(Res.string.app_title), style = KNetTheme.typography.display, color = KNetTheme.colors.textPrimary)
        }
        if (onBack != null) TextButton(onClick = onBack, contentPadding = PaddingValues(end = 12.dp)) {
            Icon(KNetIcons.Back, null, Modifier.size(20.dp), tint = KNetTheme.colors.textSecondary)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.back), color = KNetTheme.colors.textSecondary)
        }
    }
}
