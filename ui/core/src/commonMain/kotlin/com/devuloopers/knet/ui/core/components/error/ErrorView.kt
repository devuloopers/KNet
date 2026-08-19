package com.devuloopers.knet.ui.core.components.error

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Full-pane error presentation with an optional feature-owned retry action. */
@Composable
fun ErrorView(
    message: String,
    modifier: Modifier = Modifier,
    retryAction: (@Composable () -> Unit)? = null
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = KNetIcons.Warning,
            contentDescription = "Error",
            modifier = Modifier.size(32.dp).padding(bottom = 8.dp),
            tint = themeColors.semantic.error
        )
        Text(
            text = message,
            style = typography.bodyMedium.copy(color = themeColors.semantic.error)
        )
        if (retryAction != null) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                retryAction()
            }
        }
    }
}
