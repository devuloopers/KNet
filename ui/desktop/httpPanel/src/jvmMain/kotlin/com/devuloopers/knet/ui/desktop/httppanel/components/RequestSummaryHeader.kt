package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.domain.network.model.NetworkRequestSpec
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Target URL and HTTP method summary header bar component for the request inspector.
 *
 * @param spec Strongly-typed domain request specification.
 * @param onOpenInApiStudio Optional action button callback for 1-click API Studio export.
 * @param modifier Composable layout modifier.
 */
@Composable
public fun RequestSummaryHeader(
    spec: NetworkRequestSpec,
    onOpenInApiStudio: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val urlScrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(themeColors.surface)
            .border(width = 1.dp, color = themeColors.border)
            .horizontalScroll(urlScrollState)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = spec.methodString,
            style = typography.codeSmall.copy(
                color = themeColors.accent,
                fontWeight = FontWeight.Bold
            )
        )

        VerticalDivider(modifier = Modifier.padding(vertical = 10.dp))

        Text(
            text = spec.url.ifEmpty { "(No URL)" },
            style = typography.codeSmall.copy(color = themeColors.textPrimary)
        )

        Box(modifier = Modifier.weight(1f))

        if (onOpenInApiStudio != null) {
            KNetButton(
                onClick = onOpenInApiStudio
            ) {
                Text("Open in API Studio")
            }
        } else {
            KNetCopyButton(
                textToCopy = spec.url
            )
        }
    }
}
