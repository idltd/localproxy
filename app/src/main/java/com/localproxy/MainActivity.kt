package com.localproxy

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.localproxy.data.AddressHistoryRepository
import com.localproxy.proxy.LogEntry
import com.localproxy.proxy.LogType
import com.localproxy.proxy.ProxyLogger
import com.localproxy.service.ProxyService
import com.localproxy.ui.MainScreen
import com.localproxy.ui.ProxyState
import com.localproxy.ui.theme.LocalProxyTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var proxyService: ProxyService? = null
    private var bound = false

    private lateinit var historyRepository: AddressHistoryRepository

    private val targetAddress = MutableStateFlow("")
    private val port = MutableStateFlow("8080")
    private val history = MutableStateFlow<List<String>>(emptyList())
    private val isRunning = MutableStateFlow(false)
    private val lastError = MutableStateFlow<String?>(null)
    private val logs = MutableStateFlow<List<LogEntry>>(emptyList())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ProxyService.LocalBinder
            proxyService = binder.getService()
            bound = true

            lifecycleScope.launch {
                proxyService?.isRunning?.collect { running ->
                    isRunning.value = running
                }
            }

            lifecycleScope.launch {
                proxyService?.lastError?.collect { error ->
                    lastError.value = error
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            proxyService = null
            bound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        historyRepository = AddressHistoryRepository(this)
        history.value = historyRepository.getHistory()

        requestNotificationPermission()

        lifecycleScope.launch {
            ProxyLogger.logs.collect { logEntries ->
                logs.value = logEntries
            }
        }

        setContent {
            LocalProxyTheme {
                val currentTargetAddress by targetAddress.collectAsState()
                val currentPort by port.collectAsState()
                val currentHistory by history.collectAsState()
                val currentIsRunning by isRunning.collectAsState()
                val currentLastError by lastError.collectAsState()
                val currentLogs by logs.collectAsState()

                val state = ProxyState(
                    isRunning = currentIsRunning,
                    targetAddress = currentTargetAddress,
                    port = currentPort,
                    history = currentHistory,
                    error = currentLastError,
                    logs = currentLogs
                )

                MainScreen(
                    state = state,
                    onTargetAddressChange = { targetAddress.value = it },
                    onPortChange = { port.value = it },
                    onStartStop = { toggleProxy() },
                    onHistoryItemClick = { address ->
                        targetAddress.value = address
                    },
                    onHistoryItemDelete = { address ->
                        historyRepository.removeAddress(address)
                        history.value = historyRepository.getHistory()
                    },
                    onClearLogs = { ProxyLogger.clear() },
                    onCopyLogs = { copyLogsToClipboard() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, ProxyService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
    }

    private fun toggleProxy() {
        if (isRunning.value) {
            stopProxy()
        } else {
            startProxy()
        }
    }

    private fun startProxy() {
        val address = targetAddress.value.trim()
        val portNum = port.value.toIntOrNull() ?: 8080

        if (address.isBlank()) return

        historyRepository.addAddress(address)
        history.value = historyRepository.getHistory()

        val serviceIntent = ProxyService.getStartIntent(this)
        ContextCompat.startForegroundService(this, serviceIntent)

        proxyService?.startProxy(address, portNum)
    }

    private fun stopProxy() {
        proxyService?.stopProxy()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun copyLogsToClipboard() {
        val logText = logs.value.reversed().joinToString("\n") { entry ->
            val prefix = when (entry.type) {
                LogType.ERROR -> "ERR"
                LogType.CONNECTION -> "CON"
                LogType.REQUEST -> "REQ"
                LogType.INFO -> "INF"
            }
            "${entry.timestamp} [$prefix] ${entry.message}"
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Proxy Logs", logText)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
