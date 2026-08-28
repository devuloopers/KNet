package com.devuloopers.knet.companion.sharedui.screen.certificate

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.devuloopers.knet.companion.sharedui.generated.resources.Res
import com.devuloopers.knet.companion.sharedui.generated.resources.certificate_verified_illustration
import org.jetbrains.compose.resources.painterResource

/** Branded trusted-certificate artwork used only by the verified phase. */
@Composable
internal fun CertificateVerifiedHeroIllustration(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.certificate_verified_illustration),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
