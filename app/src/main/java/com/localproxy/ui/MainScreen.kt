package com.localproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localproxy.proxy.LogEntry
import com.localproxy.proxy.LogType

data class ProxyState(
    val isRunning: Boolean = false,
    val targetAddress: String = "",
    val port: String = "8080",
    val history: List<String> = emptyList(),
    val error: String? = null,
    val logs: List<LogEntry> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: ProxyState,
    onTargetAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onStartStop: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemDelete: (String) -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Proxy") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (selectedTab == 1 && state.logs.isNotEmpty()) {
                        IconButton(onClick = onCopyLogs) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Copy logs",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = onClearLogs) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear logs",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Proxy") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Logs")
                            if (state.logs.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Badge { Text("${state.logs.size}") }
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> ProxyTab(
                    state = state,
                    onTargetAddressChange = onTargetAddressChange,
                    onPortChange = onPortChange,
                    onStartStop = onStartStop,
                    onHistoryItemClick = onHistoryItemClick,
                    onHistoryItemDelete = onHistoryItemDelete
                )
                1 -> LogsTab(logs = state.logs)
            }
        }
    }
}

@Composable
private fun ProxyTab(
    state: ProxyState,
    onTargetAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onStartStop: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(isRunning = state.isRunning, error = state.error)

        OutlinedTextField(
            value = state.targetAddress,
            onValueChange = onTargetAddressChange,
            label = { Text("Target Address") },
            placeholder = { Text("e.g., example.com or 192.168.1.1:8080") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isRunning,
            singleLine = true
        )

        OutlinedTextField(
            value = state.port,
            onValueChange = { value ->
                if (value.all { it.isDigit() } && value.length <= 5) {
                    onPortChange(value)
                }
            },
            label = { Text("Local Port") },
            placeholder = { Text("8080") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isRunning,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Button(
            onClick = onStartStop,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isRunning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ),
            enabled = state.targetAddress.isNotBlank() && state.port.isNotBlank()
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isRunning) "Stop Proxy" else "Start Proxy")
        }

        if (state.history.isNotEmpty()) {
            Divider()

            Text(
                text = "Recent Addresses",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.history) { address ->
                    HistoryItem(
                        address = address,
                        onClick = { onHistoryItemClick(address) },
                        onDelete = { onHistoryItemDelete(address) },
                        enabled = !state.isRunning
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsTab(logs: List<LogEntry>) {
    val listState = rememberLazyListState()

    if (logs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No logs yet. Start the proxy to see activity.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E)),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(logs) { entry ->
                LogEntryRow(entry)
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val color = when (entry.type) {
        LogType.ERROR -> Color(0xFFFF6B6B)
        LogType.CONNECTION -> Color(0xFF4ECDC4)
        LogType.REQUEST -> Color(0xFFFFE66D)
        LogType.INFO -> Color(0xFFCCCCCC)
    }

    val prefix = when (entry.type) {
        LogType.ERROR -> "ERR"
        LogType.CONNECTION -> "CON"
        LogType.REQUEST -> "REQ"
        LogType.INFO -> "INF"
    }

    Text(
        text = "${entry.timestamp} [$prefix] ${entry.message}",
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatusCard(
    isRunning: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                error != null -> MaterialTheme.colorScheme.errorContainer
                isRunning -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusIndicator(isRunning = isRunning, hasError = error != null)

            Column {
                Text(
                    text = when {
                        error != null -> "Error"
                        isRunning -> "Proxy Running"
                        else -> "Proxy Stopped"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (isRunning) {
                    Text(
                        text = "Configure your device proxy settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    isRunning: Boolean,
    hasError: Boolean,
    modifier: Modifier = Modifier
) {
    val color = when {
        hasError -> MaterialTheme.colorScheme.error
        isRunning -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        modifier = modifier.size(12.dp),
        shape = MaterialTheme.shapes.small,
        color = color
    ) {}
}

@Composable
private fun HistoryItem(
    address: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = address,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )

            IconButton(
                onClick = onDelete,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
