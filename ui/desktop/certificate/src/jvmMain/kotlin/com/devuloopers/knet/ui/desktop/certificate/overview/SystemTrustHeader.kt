package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
fun SystemTrustHeader(modifier: Modifier = Modifier) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Text(
        text = "SYSTEM TRUST / OPERATIONAL",
        style = typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        color = themeColors.textSecondary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(vertical = 4.dp, horizontal = 2.dp)
    )
}
