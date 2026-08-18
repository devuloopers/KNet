package com.devuloopers.knet.ui.core.components.badge

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

/**
 * Closed set of HTTP status categories according to RFC 9110 standard.
 *
 * @property icon Vector icon associated with the status category.
 * @property color Text and icon tint color associated with the status category.
 */
enum class HttpStatusCategory(
    val icon: ImageVector,
    val color: Color
) {
    INFORMATIONAL(KNetIcons.Info, Color(0xFF89B4FA)),
    SUCCESS(KNetIcons.Check, Color(0xFF99D595)),
    REDIRECTION(KNetIcons.Refresh, Color(0xFFFAB387)),
    CLIENT_ERROR(KNetIcons.Warning, Color(0xFFF38BA8)),
    SERVER_ERROR(KNetIcons.Warning, Color(0xFFF38BA8)),
    UNKNOWN(KNetIcons.Info, Color(0xFFA6ADC8));

    companion object {
        /**
         * Resolves the strongly-typed [HttpStatusCategory] for a raw HTTP status code integer.
         *
         * @param statusCode Integer status code (e.g. 200, 302, 404, 500).
         * @return Resolved [HttpStatusCategory].
         */
        fun fromCode(statusCode: Int): HttpStatusCategory = when (statusCode) {
            in 100..199 -> INFORMATIONAL
            in 200..299 -> SUCCESS
            in 300..399 -> REDIRECTION
            in 400..499 -> CLIENT_ERROR
            in 500..599 -> SERVER_ERROR
            0 -> CLIENT_ERROR
            else -> UNKNOWN
        }
    }
}

/**
 * Reusable HTTP status badge rendering a strongly-typed category icon, color palette, and text label.
 *
 * @param statusCode Raw HTTP status code integer (e.g. 200, 404, 500).
 * @param statusText Descriptive status string (e.g. "OK", "Not Found").
 * @param modifier Layout modifier.
 */
@Composable
fun KNetHttpStatusBadge(
    statusCode: Int,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val category = HttpStatusCategory.fromCode(statusCode)
    val typography = KNetTheme.typography

    val displayText = when {
        statusCode == 0 -> statusText.ifBlank { "ERR" }
        statusText.isNotBlank() -> "$statusCode $statusText"
        else -> "$statusCode"
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = "HTTP Status $displayText",
            modifier = Modifier.size(18.dp),
            tint = category.color
        )
        Text(
            text = displayText,
            style = typography.titleSmall.copy(
                color = category.color,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}
