package com.localproxy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.localproxy.MainActivity
import com.localproxy.R
import com.localproxy.proxy.ProxyServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProxyService : Service() {

    private val binder = LocalBinder()
    private var proxyServer: ProxyServer? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private var currentTargetAddress: String = ""
    private var currentPort: Int = 8080

    inner class LocalBinder : Binder() {
        fun getService(): ProxyService = this@ProxyService
    }

    override fun onBind(intent: Intent?): IBinder {
        // Validate state when Activity binds - ensure service state matches actual server state
        validateRunningState()
        return binder
    }

    /**
     * Ensures the service's running state matches the actual proxy server state.
     * This handles cases where the server crashed but the service state wasn't updated.
     */
    private fun validateRunningState() {
        val serverActuallyRunning = proxyServer?.isRunning == true
        if (_isRunning.value && !serverActuallyRunning) {
            // Service thinks it's running, but server is not - fix the state
            _isRunning.value = false
            proxyServer = null
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProxy()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun startProxy(targetAddress: String, port: Int) {
        if (_isRunning.value) return

        _lastError.value = null
        currentTargetAddress = targetAddress
        currentPort = port

        val (host, targetPort) = parseAddress(targetAddress)

        proxyServer = ProxyServer(port, host, targetPort).apply {
            onError = { error ->
                _lastError.value = error
            }
            onStopped = {
                // Server stopped unexpectedly (crash, port conflict, etc.)
                // Sync the service state with the actual server state
                _isRunning.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            start()
        }

        _isRunning.value = true

        val notification = createNotification(targetAddress, port)
        startForeground(NOTIFICATION_ID, notification)
    }

    fun stopProxy() {
        proxyServer?.stop()
        proxyServer = null
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun parseAddress(address: String): Pair<String, Int> {
        val cleaned = address
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")

        val parts = cleaned.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80

        return host to port
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(targetAddress: String, port: Int): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ProxyService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, targetAddress, port))
            .setSmallIcon(R.drawable.ic_proxy)
            .setContentIntent(mainPendingIntent)
            .addAction(0, getString(R.string.stop_action), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "proxy_service_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.localproxy.STOP_PROXY"

        fun getStartIntent(context: Context): Intent {
            return Intent(context, ProxyService::class.java)
        }
    }
}
