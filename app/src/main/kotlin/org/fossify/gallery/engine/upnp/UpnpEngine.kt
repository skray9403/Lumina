package org.fossify.gallery.engine.upnp

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import android.util.Xml
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fossify.gallery.engine.vault.VaultCrypto
import org.fossify.gallery.models.Medium
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LuminaUpnp"
private val INVALID_XML_CHARS = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")
private val BARE_AMP = Regex("&(?!#\\d+;|#x[0-9a-fA-F]+;|[a-zA-Z][a-zA-Z0-9._-]*;)")

@Immutable
data class UpnpDevice(
    val udn: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val controlUrl: String,
    val locationUrl: String
)

@Immutable
sealed interface UpnpCastState {
    data object Idle : UpnpCastState
    data class Connecting(val deviceName: String) : UpnpCastState
    data class Playing(val device: UpnpDevice, val isPaused: Boolean) : UpnpCastState
    data class Error(val message: String) : UpnpCastState
}

object UpnpEngine {
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"

    private val _castState = MutableStateFlow<UpnpCastState>(UpnpCastState.Idle)
    val castState: StateFlow<UpnpCastState> = _castState.asStateFlow()

    private val mediaServer = LocalMediaHttpServer()

    fun discoverDevices(context: Context, scanDurationMs: Long = 4_500L): Flow<List<UpnpDevice>> = channelFlow {
        val appCtx = context.applicationContext
        val cm = appCtx.getSystemService(ConnectivityManager::class.java)
        val wifiManager = appCtx.getSystemService(WifiManager::class.java)

        val wifiNetwork = findWifiNetwork(cm)
        val wifiIpv4 = findWifiIpv4(cm, wifiNetwork)
        val wifiInterface = wifiIpv4?.let { runCatching { NetworkInterface.getByInetAddress(it) }.getOrNull() }

        Log.d(TAG, "Scan start: wifiNet=$wifiNetwork ipv4=${wifiIpv4?.hostAddress} iface=${wifiInterface?.name}")

        val multicastLock = runCatching {
            wifiManager?.createMulticastLock("lumina_upnp_ssdp")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.w(TAG, "MulticastLock failed", it) }.getOrNull()

        val discovered = LinkedHashMap<String, UpnpDevice>()
        val emitMutex = Mutex()
        val seenLocations = ConcurrentHashMap.newKeySet<String>()

        try {
            send(emptyList())

            val bindAddr = wifiIpv4?.let { InetSocketAddress(it, 0) } ?: InetSocketAddress(0)
            val socket = MulticastSocket(bindAddr).apply {
                soTimeout = 400
                timeToLive = 4
                wifiInterface?.let { networkInterface = it }
                wifiNetwork?.let { net ->
                    runCatching { net.bindSocket(this) }
                        .onFailure { Log.w(TAG, "wifiNetwork.bindSocket failed (VPN lockdown?)", it) }
                }
            }

            try {
                val targetAddr = InetAddress.getByName(SSDP_ADDRESS)
                val searchTargets = listOf(
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    AV_TRANSPORT_SERVICE,
                    "upnp:rootdevice",
                    "ssdp:all"
                )

                // 3 staggered bursts so Wi-Fi multicast loss / TV sleep doesn't miss the scan
                val senderJob = launch(Dispatchers.IO) {
                    repeat(3) { round ->
                        for (st in searchTargets) {
                            val payload = buildString {
                                append("M-SEARCH * HTTP/1.1\r\n")
                                append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                                append("MAN: \"ssdp:discover\"\r\n")
                                append("MX: 2\r\n")
                                append("ST: $st\r\n")
                                append("USER-AGENT: Android/16 UPnP/1.1 Lumina/1.0\r\n\r\n")
                            }.toByteArray(Charsets.UTF_8)
                            runCatching {
                                socket.send(DatagramPacket(payload, payload.size, targetAddr, SSDP_PORT))
                            }.onFailure { Log.e(TAG, "M-SEARCH send failed (round=$round, st=$st)", it) }
                        }
                        delay(750)
                    }
                }

                val deadline = System.currentTimeMillis() + scanDurationMs
                val rxBuf = ByteArray(4096)

                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(rxBuf, rxBuf.size)
                    try {
                        socket.receive(packet)
                        val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val location = extractHeader(response, "LOCATION") ?: continue
                        val st = extractHeader(response, "ST") ?: extractHeader(response, "NT") ?: ""

                        if (seenLocations.add(location)) {
                            Log.d(TAG, "SSDP reply from ${packet.address.hostAddress} ST=$st LOC=$location")
                            launch(Dispatchers.IO) {
                                fetchDeviceDescription(location, wifiNetwork)?.let { device ->
                                    val snapshot = emitMutex.withLock {
                                        if (discovered.putIfAbsent(device.udn, device) == null) {
                                            discovered.values.toList()
                                        } else null
                                    }
                                    if (snapshot != null) {
                                        Log.i(TAG, "Renderer added: ${device.friendlyName} (${device.manufacturer}) -> ${device.controlUrl}")
                                        send(snapshot)
                                    }
                                }
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Normal poll timeout until deadline
                    } catch (e: Exception) {
                        Log.w(TAG, "SSDP receive error", e)
                    }
                }

                senderJob.cancel()
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP discovery crashed", e)
        } finally {
            runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun castMedium(context: Context, device: UpnpDevice, medium: Medium) = withContext(Dispatchers.IO) {
        if (medium.uri.scheme == VaultCrypto.VAULT_SCHEME) {
            _castState.value = UpnpCastState.Error("Locked Folder items cannot be cast over LAN.")
            return@withContext
        }

        val localIp = resolveWifiIpv4(context)
        if (localIp == null) {
            _castState.value = UpnpCastState.Error("Connect to Wi-Fi to cast to UPnP devices.")
            return@withContext
        }

        _castState.value = UpnpCastState.Connecting(device.friendlyName)

        try {
            val streamUrl = mediaServer.serve(context.applicationContext, localIp, medium)
            val didl = buildDidlLite(medium, streamUrl)

            val setUriBody = """
                <u:SetAVTransportURI xmlns:u="$AV_TRANSPORT_SERVICE">
                    <InstanceID>0</InstanceID>
                    <CurrentURI>${escapeXml(streamUrl)}</CurrentURI>
                    <CurrentURIMetaData>${escapeXml(didl)}</CurrentURIMetaData>
                </u:SetAVTransportURI>
            """.trimIndent()

            sendSoapAction(device.controlUrl, "SetAVTransportURI", setUriBody)

            val playBody = """
                <u:Play xmlns:u="$AV_TRANSPORT_SERVICE">
                    <InstanceID>0</InstanceID>
                    <Speed>1</Speed>
                </u:Play>
            """.trimIndent()

            sendSoapAction(device.controlUrl, "Play", playBody)
            _castState.value = UpnpCastState.Playing(device = device, isPaused = false)
        } catch (e: Exception) {
            _castState.value = UpnpCastState.Error("Cast failed: ${e.message ?: "Device unreachable"}")
        }
    }

    suspend fun togglePause(device: UpnpDevice) = withContext(Dispatchers.IO) {
        val current = _castState.value as? UpnpCastState.Playing ?: return@withContext
        try {
            if (current.isPaused) {
                val body = """
                    <u:Play xmlns:u="$AV_TRANSPORT_SERVICE">
                        <InstanceID>0</InstanceID>
                        <Speed>1</Speed>
                    </u:Play>
                """.trimIndent()
                sendSoapAction(device.controlUrl, "Play", body)
                _castState.value = current.copy(isPaused = false)
            } else {
                val body = """
                    <u:Pause xmlns:u="$AV_TRANSPORT_SERVICE">
                        <InstanceID>0</InstanceID>
                    </u:Pause>
                """.trimIndent()
                sendSoapAction(device.controlUrl, "Pause", body)
                _castState.value = current.copy(isPaused = true)
            }
        } catch (e: Exception) {
            _castState.value = UpnpCastState.Error("Control failed: ${e.message}")
        }
    }

    suspend fun stopCasting(device: UpnpDevice?) = withContext(Dispatchers.IO) {
        if (device != null) {
            try {
                val body = """
                    <u:Stop xmlns:u="$AV_TRANSPORT_SERVICE">
                        <InstanceID>0</InstanceID>
                    </u:Stop>
                """.trimIndent()
                sendSoapAction(device.controlUrl, "Stop", body)
            } catch (_: Exception) {
            }
        }
        mediaServer.stop()
        _castState.value = UpnpCastState.Idle
    }

    fun clearError() {
        if (_castState.value is UpnpCastState.Error) {
            _castState.value = UpnpCastState.Idle
        }
    }

    private fun sendSoapAction(controlUrl: String, action: String, actionXml: String) {
        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>$actionXml</s:Body>
            </s:Envelope>
        """.trimIndent()

        val conn = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4_000
            readTimeout = 4_000
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPACTION", "\"$AV_TRANSPORT_SERVICE#$action\"")
        }

        try {
            conn.outputStream.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from renderer")
            }
        } finally {
            conn.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun findWifiNetwork(cm: ConnectivityManager?): Network? {
        if (cm == null) return null
        return cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    private fun findWifiIpv4(cm: ConnectivityManager?, wifiNetwork: Network?): Inet4Address? {
        if (cm == null || wifiNetwork == null) return null
        return cm.getLinkProperties(wifiNetwork)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address as? Inet4Address
    }

    private fun fetchDeviceDescription(locationUrl: String, wifiNetwork: Network?): UpnpDevice? {
        val url = URL(locationUrl)
        val conn = ((wifiNetwork?.openConnection(url) ?: url.openConnection()) as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2_500
            readTimeout = 2_500
            setRequestProperty("Connection", "close")
        }

        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "XML fetch HTTP $code for $locationUrl")
                return null
            }

            val rawXml = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val sanitized = rawXml
                .replace(INVALID_XML_CHARS, "")
                .replace(BARE_AMP, "&amp;")

            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(sanitized))
            }

            var urlBase = ""
            var friendlyName = ""
            var manufacturer = ""
            var modelName = ""
            var udn = ""
            var avTransportControlUrl: String? = null

            var inService = false
            var currentServiceType = ""
            var currentControlUrl = ""
            val textBuf = StringBuilder()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        textBuf.setLength(0)
                        val tag = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                        if (tag == "service") {
                            inService = true
                            currentServiceType = ""
                            currentControlUrl = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        textBuf.append(parser.text.orEmpty())
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                        val value = textBuf.toString().trim()
                        if (inService) {
                            when (tag) {
                                "servicetype" -> currentServiceType = value
                                "controlurl" -> currentControlUrl = value
                                "service" -> {
                                    if (currentServiceType.contains("AVTransport", ignoreCase = true) &&
                                        currentControlUrl.isNotBlank()
                                    ) {
                                        val base = URL(urlBase.ifBlank { locationUrl })
                                        avTransportControlUrl = URL(base, currentControlUrl).toString()
                                    }
                                    inService = false
                                }
                            }
                        } else if (value.isNotEmpty()) {
                            when (tag) {
                                "urlbase" -> if (urlBase.isEmpty()) urlBase = value
                                "friendlyname" -> if (friendlyName.isEmpty()) friendlyName = value
                                "manufacturer" -> if (manufacturer.isEmpty()) manufacturer = value
                                "modelname" -> if (modelName.isEmpty()) modelName = value
                                "udn" -> if (udn.isEmpty()) udn = value
                            }
                        }
                        textBuf.setLength(0)
                    }
                }
                eventType = parser.next()
            }

            val control = avTransportControlUrl
            if (control == null) {
                Log.d(TAG, "Ignoring non-renderer device: $friendlyName ($locationUrl)")
                return null
            }

            UpnpDevice(
                udn = udn.ifBlank { locationUrl },
                friendlyName = friendlyName.ifBlank { modelName.ifBlank { "UPnP Renderer" } },
                manufacturer = manufacturer,
                modelName = modelName,
                controlUrl = control,
                locationUrl = locationUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "XML parse/fetch failed for $locationUrl", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun buildDidlLite(medium: Medium, streamUrl: String): String {
        val upnpClass = if (medium.isVideo) "object.item.videoItem" else "object.item.imageItem.photo"
        val mime = medium.mimeType.ifBlank { if (medium.isVideo) "video/mp4" else "image/jpeg" }
        val protocolInfo = "http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        val title = escapeXml(medium.displayName.ifBlank { "Lumina Media" })
        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                <item id="${medium.id}" parentID="0" restricted="1">
                    <dc:title>$title</dc:title>
                    <upnp:class>$upnpClass</upnp:class>
                    <res protocolInfo="$protocolInfo" size="${medium.sizeBytes}">${escapeXml(streamUrl)}</res>
                </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    private fun resolveWifiIpv4(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            return null
        }
        val linkProps = cm.getLinkProperties(network) ?: return null
        return linkProps.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    private fun extractHeader(raw: String, name: String): String? {
        val prefix = "$name:"
        for (line in raw.lineSequence()) {
            if (line.startsWith(prefix, ignoreCase = true)) {
                return line.substring(prefix.length).trim()
            }
        }
        return null
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

/**
 * Ephemeral LAN HTTP server streaming directly from ContentResolver with HTTP 206 Range support.
 */
private class LocalMediaHttpServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activeToken: String = ""

    @Volatile
    private var activeUri: Uri? = null

    @Volatile
    private var activeMime: String = "application/octet-stream"

    @Volatile
    private var activeLength: Long = -1L

    fun serve(context: Context, localIp: String, medium: Medium): String {
        appContext = context.applicationContext
        val tokenBytes = ByteArray(12).also { random.nextBytes(it) }
        activeToken = tokenBytes.joinToString("") { "%02x".format(it) }
        activeUri = medium.uri
        activeMime = medium.mimeType.ifBlank { if (medium.isVideo) "video/mp4" else "image/jpeg" }
        activeLength = resolveContentLength(context, medium.uri, medium.sizeBytes)

        val sock = serverSocket?.takeIf { !it.isClosed } ?: ServerSocket(0).also { created ->
            serverSocket = created
            acceptJob?.cancel()
            acceptJob = scope.launch {
                while (isActive && !created.isClosed) {
                    try {
                        val client = created.accept()
                        launch { handleClient(client) }
                    } catch (_: Exception) {
                        if (created.isClosed) break
                    }
                }
            }
        }

        val ext = when {
            activeMime.contains("mp4") -> "mp4"
            activeMime.contains("mkv") || activeMime.contains("matroska") -> "mkv"
            activeMime.contains("webm") -> "webm"
            activeMime.contains("png") -> "png"
            activeMime.contains("webp") -> "webp"
            activeMime.contains("gif") -> "gif"
            else -> if (medium.isVideo) "mp4" else "jpg"
        }
        return "http://$localIp:${sock.localPort}/stream/$activeToken/media.$ext"
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        activeUri = null
        activeToken = ""
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase(Locale.ROOT)
            val path = parts[1]

            var rangeHeader: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(':').trim()
                }
            }

            val out = client.getOutputStream()
            val expectedPrefix = "/stream/$activeToken/"
            val uri = activeUri
            val ctx = appContext
            if (uri == null || ctx == null || activeToken.isEmpty() || !path.startsWith(expectedPrefix)) {
                writeStatus(out, "404 Not Found")
                return
            }

            val totalLength = activeLength
            if (totalLength <= 0L) {
                writeStatus(out, "404 Not Found")
                return
            }

            var start = 0L
            var end = totalLength - 1L
            var isPartial = false

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val spec = rangeHeader.removePrefix("bytes=").substringBefore(',')
                val dashIdx = spec.indexOf('-')
                if (dashIdx >= 0) {
                    val startStr = spec.substring(0, dashIdx).trim()
                    val endStr = spec.substring(dashIdx + 1).trim()
                    if (startStr.isNotEmpty()) {
                        start = startStr.toLongOrNull()?.coerceIn(0L, totalLength - 1L) ?: 0L
                        end = endStr.toLongOrNull()?.coerceIn(start, totalLength - 1L) ?: (totalLength - 1L)
                        isPartial = true
                    }
                }
            }

            val contentLen = (end - start + 1L).coerceAtLeast(0L)
            val headerBlock = buildString {
                append(if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Type: $activeMime\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: $contentLen\r\n")
                if (isPartial) {
                    append("Content-Range: bytes $start-$end/$totalLength\r\n")
                }
                append("transferMode.dlna.org: Streaming\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(headerBlock.toByteArray(Charsets.US_ASCII))
            out.flush()

            if (method == "HEAD") return

            ctx.contentResolver.openInputStream(uri)?.use { input ->
                if (start > 0L) {
                    var skipped = 0L
                    while (skipped < start) {
                        val n = input.skip(start - skipped)
                        if (n <= 0L) break
                        skipped += n
                    }
                }
                val buf = ByteArray(65_536)
                var remaining = contentLen
                while (remaining > 0L) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val read = input.read(buf, 0, toRead)
                    if (read <= 0) break
                    out.write(buf, 0, read)
                    remaining -= read
                }
                out.flush()
            }
        }
    }

    private fun resolveContentLength(context: Context, uri: Uri, fallback: Long): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) afd.length else fallback
            } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun writeStatus(out: OutputStream, status: String) {
        out.write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
    }
}
