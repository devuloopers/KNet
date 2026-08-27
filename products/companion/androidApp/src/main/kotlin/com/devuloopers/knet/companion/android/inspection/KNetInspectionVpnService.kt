package com.devuloopers.knet.companion.android.inspection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.devuloopers.knet.companion.android.MainActivity
import com.devuloopers.knet.companion.android.R
import com.devuloopers.knet.companion.connectivity.inspection.AndroidInspectionBackendResult
import com.devuloopers.knet.companion.connectivity.transport.AndroidSocketProtector
import com.devuloopers.knet.companion.connectivity.transport.AndroidTunForwarder
import com.devuloopers.knet.companion.connectivity.transport.AndroidTunForwarderStartResult
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext
import java.net.DatagramSocket
import java.net.Socket

/** Android system component that owns the companion TUN descriptor and foreground lifecycle. */
class KNetInspectionVpnService : VpnService(), AndroidSocketProtector {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleLock = Mutex()
    private var tunDescriptor: ParcelFileDescriptor? = null

    private val coordinator: AndroidInspectionRuntimeCoordinator
        get() = GlobalContext.get().get()
    private val forwarder: AndroidTunForwarder
        get() = GlobalContext.get().get()

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                serviceScope.launch { startPendingInspection() }
            }

            ACTION_STOP -> serviceScope.launch {
                stopInspection()
                stopSelf()
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        serviceScope.launch {
            stopInspection()
            stopSelf()
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        serviceScope.launch {
            stopInspection()
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    override fun protect(socket: Socket): Boolean = super.protect(socket)

    override fun protect(socket: DatagramSocket): Boolean = super.protect(socket)

    private suspend fun startPendingInspection(): Unit = lifecycleLock.withLock {
        if (tunDescriptor != null) return
        val request = coordinator.claimStart() ?: run {
            stopSelf()
            return@withLock
        }
        val descriptor = establishInterface()
        if (descriptor == null) {
            coordinator.completeStart(request, startFailure())
            stopSelf()
            return@withLock
        }
        tunDescriptor = descriptor
        val result = try {
            when (forwarder.start(descriptor.fd, request.configuration, this)) {
                AndroidTunForwarderStartResult.Started -> AndroidInspectionBackendResult.Started
                AndroidTunForwarderStartResult.Failed -> startFailure()
            }
        } catch (cancelled: CancellationException) {
            coordinator.completeStart(request, startFailure())
            stopInspectionResources()
            throw cancelled
        } catch (failure: Throwable) {
            KNetLogger.error(LogTags.PROXY, failure) { "companion_event=inspection_backend_failed" }
            startFailure()
        }
        val accepted = coordinator.completeStart(request, result)
        if (!accepted || result is AndroidInspectionBackendResult.Failed) {
            stopInspectionResources()
            stopSelf()
            return@withLock
        }
        KNetLogger.info(LogTags.PROXY) { "companion_event=inspection_started mode=device_vpn" }
    }

    private fun establishInterface(): ParcelFileDescriptor? = try {
        Builder()
            .setSession(getString(R.string.inspection_notification_title))
            .setMtu(TUN_MTU)
            .addAddress(TUN_IPV4_ADDRESS, TUN_IPV4_PREFIX)
            .addAddress(TUN_IPV6_ADDRESS, TUN_IPV6_PREFIX)
            .addRoute(IPV4_DEFAULT_ROUTE, 0)
            .addRoute(IPV6_DEFAULT_ROUTE, 0)
            .addDnsServer(DNS_IPV4_ADDRESS)
            .addDnsServer(DNS_IPV6_ADDRESS)
            .addDisallowedApplication(packageName)
            .setBlocking(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
            }
            .establish()
    } catch (failure: Exception) {
        KNetLogger.error(LogTags.PROXY, failure) { "companion_event=vpn_interface_failed" }
        null
    }

    private suspend fun stopInspection(): Unit = lifecycleLock.withLock {
        stopInspectionResources()
    }

    private suspend fun stopInspectionResources() {
        val hadResources = tunDescriptor != null
        forwarder.stop()
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
        coordinator.completeStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (hadResources) {
            KNetLogger.info(LogTags.PROXY) { "companion_event=inspection_stopped" }
        }
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.inspection_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val openApplication = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_knet_companion)
            .setContentTitle(getString(R.string.inspection_notification_title))
            .setContentText(getString(R.string.inspection_notification_description))
            .setContentIntent(openApplication)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START: String = "com.devuloopers.knet.companion.action.START_INSPECTION"
        const val ACTION_STOP: String = "com.devuloopers.knet.companion.action.STOP_INSPECTION"

        private const val NOTIFICATION_CHANNEL_ID: String = "knet_companion_inspection"
        private const val NOTIFICATION_ID: Int = 4102
        private const val TUN_MTU: Int = 1_500
        private const val TUN_IPV4_ADDRESS: String = "10.254.0.2"
        private const val TUN_IPV4_PREFIX: Int = 30
        private const val TUN_IPV6_ADDRESS: String = "fd42:4b4e:4554::2"
        private const val TUN_IPV6_PREFIX: Int = 126
        private const val IPV4_DEFAULT_ROUTE: String = "0.0.0.0"
        private const val IPV6_DEFAULT_ROUTE: String = "::"
        private const val DNS_IPV4_ADDRESS: String = "1.1.1.1"
        private const val DNS_IPV6_ADDRESS: String = "2606:4700:4700::1111"
    }
}

private fun startFailure(): AndroidInspectionBackendResult.Failed = AndroidInspectionBackendResult.Failed(
    CompanionFailure(
        code = CompanionFailureCode.VPN_START_FAILED,
        message = "Android could not establish the KNet inspection VPN.",
        recoverable = true,
    ),
)
