package com.devuloopers.knet.ui.apistudio.view.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Single cookie parsed from HTTP Set-Cookie response headers.
 */
private data class ResponseCookie(
    val name: String,
    val value: String,
    val domain: String = "",
    val path: String = "",
    val isSecure: Boolean = false,
    val isHttpOnly: Boolean = false
)

/**
 * Response Cookies Tab content for the Response & Test panel.
 *
 * Extracts and displays Set-Cookie response headers in a structured table format,
 * showing cookie name, value, domain, path, and security flags.
 *
 * @param headers HTTP response headers map.
 * @param modifier Layout modifier applied to the container.
 */
@Composable
internal fun ResponseCookiesTab(
    headers: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val parsedCookies = remember(headers) {
        headers.entries
            .filter { it.key.equals("set-cookie", ignoreCase = true) || it.key.equals("cookie", ignoreCase = true) }
            .flatMap { entry ->
                entry.value.split("\n", ",").mapNotNull { rawCookie ->
                    parseCookieString(rawCookie.trim())
                }
            }
    }

    if (parsedCookies.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No cookies returned in response headers (Set-Cookie)",
                color = KNetColors.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "RESPONSE COOKIES (${parsedCookies.size})",
            color = KNetColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        parsedCookies.forEach { cookie ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cookie.name,
                            color = KNetColors.ActiveBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (cookie.isSecure) {
                                FlagBadge(label = "SECURE", color = KNetColors.SuccessGreen)
                            }
                            if (cookie.isHttpOnly) {
                                FlagBadge(label = "HTTPONLY", color = Color(0xFFA855F7))
                            }
                        }
                    }
                    Text(
                        text = cookie.value,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (cookie.domain.isNotBlank() || cookie.path.isNotBlank()) {
                        Text(
                            text = listOfNotNull(
                                cookie.domain.takeIf { it.isNotBlank() }?.let { "Domain: $it" },
                                cookie.path.takeIf { it.isNotBlank() }?.let { "Path: $it" }
                            ).joinToString("  •  "),
                            color = KNetColors.TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlagBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(text = label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

private fun parseCookieString(raw: String): ResponseCookie? {
    if (raw.isBlank() || !raw.contains("=")) return null
    val parts = raw.split(";")
    val nameValue = parts.firstOrNull()?.split("=", limit = 2) ?: return null
    if (nameValue.size < 2) return null

    val name = nameValue[0].trim()
    val value = nameValue[1].trim()
    var domain = ""
    var path = ""
    var isSecure = false
    var isHttpOnly = false

    parts.drop(1).forEach { attr ->
        val trimmedAttr = attr.trim()
        when {
            trimmedAttr.startsWith("Domain=", ignoreCase = true) -> domain = trimmedAttr.substringAfter("=").trim()
            trimmedAttr.startsWith("Path=", ignoreCase = true) -> path = trimmedAttr.substringAfter("=").trim()
            trimmedAttr.equals("Secure", ignoreCase = true) -> isSecure = true
            trimmedAttr.equals("HttpOnly", ignoreCase = true) -> isHttpOnly = true
        }
    }

    return ResponseCookie(
        name = name,
        value = value,
        domain = domain,
        path = path,
        isSecure = isSecure,
        isHttpOnly = isHttpOnly
    )
}
