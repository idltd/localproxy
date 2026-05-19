package com.localproxy.proxy

import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class ProxyServer(
    private val port: Int,
    private val targetHost: String,
    private val targetPort: Int
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isRunning = false
        private set

    var onError: ((String) -> Unit)? = null
    var onStopped: (() -> Unit)? = null

    fun start() {
        if (isRunning) return

        ProxyLogger.info("Starting proxy server on port $port")
        ProxyLogger.info("Target: $targetHost:$targetPort")

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                ProxyLogger.info("Server listening on port $port")

                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        val clientAddr = clientSocket.inetAddress.hostAddress
                        ProxyLogger.connection("Client connected: $clientAddr")
                        launch { handleClient(clientSocket) }
                    } catch (e: SocketException) {
                        if (isRunning) {
                            ProxyLogger.error("Socket error: ${e.message}")
                            onError?.invoke("Socket error: ${e.message}")
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                ProxyLogger.error("Server error: ${e.message}")
                onError?.invoke("Server error: ${e.message}")
            } finally {
                isRunning = false
                ProxyLogger.info("Server stopped")
                onStopped?.invoke()
            }
        }
    }

    fun stop() {
        ProxyLogger.info("Stopping proxy server...")
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private suspend fun handleClient(clientSocket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                clientSocket.use { client ->
                    val clientIn = BufferedInputStream(client.getInputStream())
                    val clientOut = BufferedOutputStream(client.getOutputStream())

                    val requestLine = readLine(clientIn)
                    if (requestLine == null) {
                        ProxyLogger.error("Empty request from client")
                        return@withContext
                    }

                    ProxyLogger.request(requestLine)
                    val headers = readHeaders(clientIn)

                    if (requestLine.startsWith("CONNECT ")) {
                        handleConnect(client, clientIn, clientOut, requestLine)
                    } else {
                        handleHttp(clientIn, clientOut, requestLine, headers)
                    }
                }
            } catch (e: Exception) {
                ProxyLogger.error("Client error: ${e.message}")
            }
        }
    }

    private fun handleConnect(
        clientSocket: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        requestLine: String
    ) {
        val parts = requestLine.split(" ")
        if (parts.size < 2) {
            ProxyLogger.error("Invalid CONNECT request: $requestLine")
            return
        }

        val hostPort = parts[1].split(":")
        val connectHost = hostPort.getOrNull(0) ?: targetHost
        val connectPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

        ProxyLogger.info("CONNECT tunnel to $connectHost:$connectPort")

        try {
            Socket().apply { connect(InetSocketAddress(connectHost, connectPort), 10_000) }.use { targetSocket ->
                val response = "HTTP/1.1 200 Connection Established\r\n\r\n"
                clientOut.write(response.toByteArray())
                clientOut.flush()
                ProxyLogger.info("Tunnel established to $connectHost:$connectPort")

                val targetIn = targetSocket.getInputStream()
                val targetOut = targetSocket.getOutputStream()

                val clientToTarget = Thread {
                    try {
                        relay(clientIn, targetOut)
                    } catch (e: Exception) {
                        // Connection closed
                    }
                }

                val targetToClient = Thread {
                    try {
                        relay(targetIn, clientOut)
                    } catch (e: Exception) {
                        // Connection closed
                    }
                }

                clientToTarget.start()
                targetToClient.start()

                clientToTarget.join()
                targetToClient.join()
                ProxyLogger.info("Tunnel closed: $connectHost:$connectPort")
            }
        } catch (e: Exception) {
            ProxyLogger.error("CONNECT failed to $connectHost:$connectPort - ${e.message}")
            val errorResponse = "HTTP/1.1 502 Bad Gateway\r\nCache-Control: no-store, no-cache\r\n\r\n"
            try {
                clientOut.write(errorResponse.toByteArray())
                clientOut.flush()
            } catch (e: Exception) {
                // Ignore write errors
            }
        }
    }

    private fun handleHttp(
        clientIn: InputStream,
        clientOut: OutputStream,
        requestLine: String,
        headers: Map<String, String>
    ) {
        // Handle CORS preflight locally — no need to forward to target
        if (requestLine.startsWith("OPTIONS ")) {
            ProxyLogger.info("CORS preflight -> responding locally")
            val response = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "\r\n"
            try {
                clientOut.write(response.toByteArray())
                clientOut.flush()
            } catch (e: Exception) {
                // Ignore write errors
            }
            return
        }

        ProxyLogger.info("HTTP request -> $targetHost:$targetPort")

        try {
            Socket().apply { connect(InetSocketAddress(targetHost, targetPort), 10_000) }.use { targetSocket ->
                val targetIn = BufferedInputStream(targetSocket.getInputStream())
                val targetOut = BufferedOutputStream(targetSocket.getOutputStream())

                targetOut.write("$requestLine\r\n".toByteArray())

                val modifiedHeaders = headers.toMutableMap()
                modifiedHeaders["Host"] = if (targetPort == 80) targetHost else "$targetHost:$targetPort"
                // Force Connection: close so the target sends EOF after the response body.
                // Without this, HTTP/1.1 keep-alive means relay() blocks forever
                // waiting for more data on an idle connection.
                modifiedHeaders["Connection"] = "close"

                for ((key, value) in modifiedHeaders) {
                    targetOut.write("$key: $value\r\n".toByteArray())
                }
                targetOut.write("\r\n".toByteArray())

                val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
                if (contentLength > 0) {
                    val body = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = clientIn.read(body, read, contentLength - read)
                        if (n == -1) break
                        read += n
                    }
                    targetOut.write(body, 0, read)
                }
                targetOut.flush()

                relayResponseWithCors(targetIn, clientOut)
                clientOut.flush()
                ProxyLogger.info("HTTP response sent")
            }
        } catch (e: Exception) {
            ProxyLogger.error("HTTP forward failed: ${e.message}")
            val body = "502 Bad Gateway - target server unreachable"
            val errorResponse = "HTTP/1.1 502 Bad Gateway\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Cache-Control: no-store, no-cache\r\n" +
                "\r\n" +
                body
            try {
                clientOut.write(errorResponse.toByteArray())
                clientOut.flush()
            } catch (e: Exception) {
                // Ignore write errors
            }
        }
    }

    // Parse the HTTP response header block, strip any existing CORS headers, inject our own,
    // then relay the body verbatim.
    private fun relayResponseWithCors(targetIn: InputStream, clientOut: OutputStream) {
        val statusLine = readLine(targetIn) ?: return
        val responseHeaders = mutableListOf<String>()
        while (true) {
            val line = readLine(targetIn) ?: break
            if (line.isEmpty()) break
            // Drop any existing CORS headers — we'll add canonical ones below
            if (!line.lowercase().startsWith("access-control-")) {
                responseHeaders.add(line)
            }
        }

        val sb = StringBuilder()
        sb.append("$statusLine\r\n")
        for (header in responseHeaders) {
            sb.append("$header\r\n")
        }
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD\r\n")
        sb.append("Access-Control-Allow-Headers: *\r\n")
        sb.append("\r\n")
        clientOut.write(sb.toString().toByteArray())

        relay(targetIn, clientOut)
    }

    private fun relay(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            output.flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (prev == '\r'.code && b == '\n'.code) {
                sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break

            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        return headers
    }
}
