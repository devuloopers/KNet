package com.devuloopers.knet.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Reusable placeholder view for menu tabs currently under development.
 * Renders a centered card with an icon, title, subtitle, and "coming soon" badge.
 * Does not include TopHeader or SystemStatusBar — those are centralized in [AppNavDisplay].
 */
@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(KNetColors.ActiveBlue.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = KNetColors.ActiveBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    color = KNetColors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⚡ Feature Ready in Next Build",
                        color = KNetColors.ActiveBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


@Composable
fun CollectionsPlaceholderScreen() {
    PlaceholderScreen(
        title = "API Collections",
        subtitle = "Organize, save, and group captured HTTP requests into Postman and Insomnia compatible test suites.",
        icon = Icons.Default.Folder
    )
}

@Composable
fun RulesPlaceholderScreen() {
    PlaceholderScreen(
        title = "Interception & Rewrite Rules",
        subtitle = "Manage global breakpoint rules, header rewrites, latency injection, and mock payload responses.",
        icon = Icons.Default.Build
    )
}

@Composable
fun CertificatesPlaceholderScreen() {
    PlaceholderScreen(
        title = "SSL / TLS Certificates Manager",
        subtitle = "Install KNet Root CA certificate, configure custom client SSL keypairs, and domain bypass lists.",
        icon = Icons.Default.Lock
    )
}

@Composable
fun SettingsPlaceholderScreen() {
    PlaceholderScreen(
        title = "Application Settings",
        subtitle = "Configure local proxy server port, upstream proxy chaining, payload cache limits, and dark theme.",
        icon = Icons.Default.Settings
    )
}
