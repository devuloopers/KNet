package com.devuloopers.knet.ui.core.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.badge.MethodBadge
import com.devuloopers.knet.ui.core.badge.ProtocolBadge
import com.devuloopers.knet.ui.core.badge.StatusBadge
import com.devuloopers.knet.ui.core.badge.TagBadge
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetTheme

/**
 * Preview composable showcasing MethodBadge, StatusBadge, TagBadge, and ProtocolBadge.
 */
@Composable
public fun BadgePreview() {
    KNetTheme {
        Row(
            modifier = Modifier
                .background(KNetColors.SurfaceDark)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MethodBadge(method = "GET")
            MethodBadge(method = "POST")
            StatusBadge(statusCode = 200)
            StatusBadge(statusCode = 404)
            ProtocolBadge(protocol = "HTTP/2")
            TagBadge(tag = "auth")
        }
    }
}
