package com.localproxy.proxy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

object ProxyLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private const val MAX_LOGS = 100

    fun log(message: String, type: LogType = LogType.INFO) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            message = message,
            type = type
        )
        _logs.value = (listOf(entry) + _logs.value).take(MAX_LOGS)
    }

    fun info(message: String) = log(message, LogType.INFO)
    fun error(message: String) = log(message, LogType.ERROR)
    fun connection(message: String) = log(message, LogType.CONNECTION)
    fun request(message: String) = log(message, LogType.REQUEST)

    fun clear() {
        _logs.value = emptyList()
    }
}

data class LogEntry(
    val timestamp: String,
    val message: String,
    val type: LogType
)

enum class LogType {
    INFO, ERROR, CONNECTION, REQUEST
}
