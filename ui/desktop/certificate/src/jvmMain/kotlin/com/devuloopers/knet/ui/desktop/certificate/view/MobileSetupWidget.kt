package com.devuloopers.knet.ui.desktop.certificate.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import java.awt.datatransfer.StringSelection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.engine.certificate.AdbResult
import com.devuloopers.knet.engine.certificate.AndroidAdbInstaller
import com.devuloopers.knet.engine.certificate.CertificateAuthority
import com.devuloopers.knet.engine.certificate.IosSimctlInstaller
import com.devuloopers.knet.engine.certificate.SimctlResult
import com.devuloopers.knet.engine.portal.PortalHtmlRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Compose Desktop UI widget surfacing KNet's Mobile Setup Center.
 *
 * Provides developers with local network host IP detection, portal URLs (`http://knet.local`),
 * automated 1-click ADB proxy settings, iOS simulator keychain certificate injection, and step-by-step trust guides.
 *
 * @param ca The active [CertificateAuthority] used for simulator injection.
 * @param proxyPort The active HTTP proxy port (default: 8080).
 * @param modifier Optional UI modifier.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MobileSetupWidget(
    ca: CertificateAuthority,
    proxyPort: Int = 8080,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var activeIp by remember { mutableStateOf("127.0.0.1") }
    var availableIps by remember { mutableStateOf(listOf("127.0.0.1")) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isActionInProgress by remember { mutableStateOf(false) }

    // Resolve local network IP addresses on launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val ips = mutableListOf<String>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            ips.add(addr.hostAddress)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to local host
            }
            if (ips.isEmpty()) {
                ips.add("127.0.0.1")
            }
            availableIps = ips
            activeIp = ips.first()
        }
    }

    val portalUrl = "http://$activeIp:$proxyPort/setup"
    val knetLocalUrl = "http://knet.local"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "[MOBILE SETUP CENTER]",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect physical Android & iOS devices or emulators to KNet Netty Proxy.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Host IP & Setup URL Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "[HOST NETWORK CONFIGURATION]",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Proxy Host IP:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(text = "$activeIp:$proxyPort", fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipEntry(StringSelection("$activeIp:$proxyPort")))
                                actionMessage = "[COPIED] Proxy address $activeIp:$proxyPort copied to clipboard."
                            }
                        }
                    ) {
                        Text(text = "[Copy IP:Port]", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Mobile Browser Portal URL:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(text = portalUrl, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipEntry(StringSelection(portalUrl)))
                                actionMessage = "[COPIED] Mobile portal URL copied to clipboard."
                            }
                        }
                    ) {
                        Text(text = "[Copy Setup URL]", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // 1-Click Automation Actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "[1-CLICK AUTOMATION TOOLING]",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = !isActionInProgress,
                        onClick = {
                            coroutineScope.launch {
                                isActionInProgress = true
                                val res = withContext(Dispatchers.IO) {
                                    AndroidAdbInstaller.configureProxy(activeIp, proxyPort)
                                }
                                actionMessage = when (res) {
                                    is AdbResult.Success -> res.message
                                    is AdbResult.Failure -> "[ADB FAILURE] ${res.error}"
                                }
                                isActionInProgress = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "[Configure ADB Proxy]", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        enabled = !isActionInProgress,
                        onClick = {
                            coroutineScope.launch {
                                isActionInProgress = true
                                val res = withContext(Dispatchers.IO) {
                                    AndroidAdbInstaller.clearProxy()
                                }
                                actionMessage = when (res) {
                                    is AdbResult.Success -> res.message
                                    is AdbResult.Failure -> "[ADB FAILURE] ${res.error}"
                                }
                                isActionInProgress = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "[Clear ADB Proxy]", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        enabled = !isActionInProgress,
                        onClick = {
                            coroutineScope.launch {
                                isActionInProgress = true
                                val res = withContext(Dispatchers.IO) {
                                    IosSimctlInstaller.injectCertificate(ca.certificate)
                                }
                                actionMessage = when (res) {
                                    is SimctlResult.Success -> res.message
                                    is SimctlResult.Failure -> "[SIMCTL FAILURE] ${res.error}"
                                }
                                isActionInProgress = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "[Inject iOS Simulator Cert]", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Action Status Banner
        actionMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
