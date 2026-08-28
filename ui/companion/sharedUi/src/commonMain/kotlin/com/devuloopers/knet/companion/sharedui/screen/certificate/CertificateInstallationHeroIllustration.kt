package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_installation_illustration
import org.jetbrains.compose.resources.painterResource

/** Branded certificate-settings artwork used only by the installation phase. */
@Composable
internal fun CertificateInstallationHeroIllustration(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.certificate_installation_illustration),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
