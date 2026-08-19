package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

private enum class HttpStatusCategory {
    INFORMATIONAL,
    SUCCESS,
    REDIRECTION,
    CLIENT_ERROR,
    SERVER_ERROR,
    UNKNOWN;

    companion object {
        fun fromCode(statusCode: Int): HttpStatusCategory = when (statusCode) {
            in 100..199 -> INFORMATIONAL
            in 200..299 -> SUCCESS
            in 300..399 -> REDIRECTION
            in 400..499, 0 -> CLIENT_ERROR
            in 500..599 -> SERVER_ERROR
            else -> UNKNOWN
        }
    }
}

/** HTTP-specific status label shared by API Studio, Traffic inspection, and HTTP editors. */
@Composable
fun KNetHttpStatusBadge(
    statusCode: Int,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val category = HttpStatusCategory.fromCode(statusCode)
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val statusColor: Color = when (category) {
        HttpStatusCategory.INFORMATIONAL -> colors.semantic.info
        HttpStatusCategory.SUCCESS -> colors.semantic.success
        HttpStatusCategory.REDIRECTION -> colors.semantic.warning
        HttpStatusCategory.CLIENT_ERROR, HttpStatusCategory.SERVER_ERROR -> colors.semantic.error
        HttpStatusCategory.UNKNOWN -> colors.textSecondary
    }
    val icon: ImageVector = when (category) {
        HttpStatusCategory.SUCCESS -> KNetIcons.Check
        HttpStatusCategory.REDIRECTION -> KNetIcons.Refresh
        HttpStatusCategory.CLIENT_ERROR, HttpStatusCategory.SERVER_ERROR -> KNetIcons.Warning
        HttpStatusCategory.INFORMATIONAL, HttpStatusCategory.UNKNOWN -> KNetIcons.Info
    }
    val displayText = when {
        statusCode == 0 -> statusText.ifBlank { "ERR" }
        statusText.isNotBlank() -> "$statusCode $statusText"
        else -> statusCode.toString()
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = statusColor)
        Text(
            text = displayText,
            style = typography.titleSmall.copy(color = statusColor, fontWeight = FontWeight.SemiBold)
        )
    }
}
