package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.foundation.color.KNetColors
import com.devuloopers.knet.ui.core.foundation.typography.KNetTypography

@Composable
fun SystemTrustHeader(modifier: Modifier = Modifier) {
    Text(
        text = "SYSTEM TRUST / OPERATIONAL",
        style = KNetTypography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = KNetColors.Dark.textSecondary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
