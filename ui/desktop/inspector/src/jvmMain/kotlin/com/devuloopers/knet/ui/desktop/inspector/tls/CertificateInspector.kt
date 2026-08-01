package com.devuloopers.knet.ui.desktop.inspector.tls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * TLS / Certificate Inspector view container.
 */
@Composable
public fun CertificateInspector(
    host: String,
    tlsVersion: String,
    cipherSuite: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CertificateChainView(host = host)
        HandshakeView(tlsVersion = tlsVersion)
        CipherSuiteView(cipherSuite = cipherSuite)
    }
}
