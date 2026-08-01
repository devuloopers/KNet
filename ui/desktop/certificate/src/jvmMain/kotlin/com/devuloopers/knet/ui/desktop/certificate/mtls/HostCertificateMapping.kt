package com.devuloopers.knet.ui.desktop.certificate.mtls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.certificate.model.MtlsRule

/**
 * HostCertificateMapping renders an active routing map mapping patterns to aliases.
 */
@Composable
public fun HostCertificateMapping(
    rules: List<MtlsRule>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(
            text = "Active Mapping Mappings",
            color = KNetColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        rules.forEach { rule ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = rule.hostPattern,
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "maps to",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(12.dp)
                )
                Text(
                    text = rule.certificateAlias,
                    color = KNetColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

