package com.aiagents.app.data.terminal

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight embedded HTTP server that serves files from a workspace directory.
 * Enables multi-file web project previews (React, Vite, etc.) in WebView.
 *
 * Two modes:
 * - Local only (default): binds to localhost — only accessible from this device
 * - LAN mode: binds to 0.0.0.0 — accessible from any device on the same network
 *
 * Usage:
 *   server.start("/path/to/workspace/project")
 *   // WebView loads http://localhost:{port}/index.html
 *   server.stop()
 *
 *   // LAN mode:
 *   server.start("/path/to/project", lanAccess = true)
 *   // Other devices load http://192.168.x.x:{port}/
 */
@Singleton
class LocalWebServer @Inject constructor() : NanoHTTPD("localhost", 0) {

    companion object {
        private const val TAG = "LocalWebServer"
        private const val LIVE_RELOAD_SCRIPT = """
<script>
(function(){
  var _lrTs = Date.now();
  setInterval(function(){
    fetch('/__livereload?ts=' + _lrTs)
      .then(function(r){ return r.json(); })
      .then(function(d){ if(d.changed) location.reload(); })
      .catch(function(){});
  }, 1500);
})();
</script>
"""
    }

    private var rootDir: File? = null
    private var enableLiveReload: Boolean = false
    private var lastModifiedTs: Long = 0L
    private var lanMode: Boolean = false

    /** Whether the server is currently running and serving files */
    val isActive: Boolean get() = isAlive

    /** The port the server is listening on (only valid when isActive) */
    val activePort: Int get() = listeningPort

    /** Whether the server is accessible from the local network */
    val isLanAccessEnabled: Boolean get() = lanMode

    /** Full base URL for local access (e.g. "http://localhost:8080") */
    val localUrl: String get() = "http://localhost:$activePort"

    /** Full base URL for LAN access (e.g. "http://192.168.1.42:8080"), or null if not on WiFi */
    val lanUrl: String? get() {
        if (!lanMode || !isAlive) return null
        val ip = getDeviceLanIp() ?: return null
        return "http://$ip:$activePort"
    }

    /**
     * Start serving files from [directory].
     * @param directory Root directory to serve files from
     * @param liveReload If true, inject a live-reload script into HTML files
     * @param lanAccess If true, bind to 0.0.0.0 so other devices on the network can access
     * @return The URL to load in WebView (localhost for local, LAN IP for LAN mode)
     */
    fun start(directory: String, liveReload: Boolean = true, lanAccess: Boolean = false): String {
        // Stop any previous instance
        if (isAlive) {
            stop()
        }

        rootDir = File(directory)
        enableLiveReload = liveReload
        lanMode = lanAccess
        lastModifiedTs = System.currentTimeMillis()

        if (!rootDir!!.exists() || !rootDir!!.isDirectory) {
            throw IllegalArgumentException("Directory does not exist: $directory")
        }

        // Rebind to the correct hostname
        // NanoHTTPD doesn't support changing hostname after construction,
        // so we use the underlying ServerSocket binding approach
        if (lanAccess) {
            setHostname("0.0.0.0")
        } else {
            setHostname("localhost")
        }

        start(SOCKET_READ_TIMEOUT, false)

        val url = if (lanAccess) lanUrl ?: localUrl else localUrl
        Log.d(TAG, "Server started on port $activePort serving: $directory (LAN: $lanAccess, URL: $url)")
        return url
    }

    /**
     * Rebind hostname for the next start() call.
     * NanoHTTPD stores hostname internally — we use reflection to update it
     * since there's no public setter.
     */
    private fun setHostname(hostname: String) {
        try {
            val field = NanoHTTPD::class.java.getDeclaredField("hostname")
            field.isAccessible = true
            field.set(this, hostname)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set hostname via reflection: ${e.message}")
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"

        // Live reload polling endpoint
        if (uri == "/__livereload") {
            val currentMax = findMaxModified(rootDir!!)
            val changed = currentMax > lastModifiedTs
            if (changed) lastModifiedTs = currentMax
            val json = """{"changed":$changed,"ts":$currentMax}"""
            return newFixedLengthResponse(Response.Status.OK, "application/json", json)
        }

        // Resolve file path (prevent directory traversal)
        val sanitized = uri.removePrefix("/").ifEmpty { "index.html" }
        val requestedFile = File(rootDir, sanitized).canonicalFile

        if (!requestedFile.path.startsWith(rootDir!!.canonicalPath)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "403 Forbidden")
        }

        // If directory, look for index.html inside it
        val targetFile = if (requestedFile.isDirectory) {
            File(requestedFile, "index.html")
        } else {
            requestedFile
        }

        if (!targetFile.exists() || !targetFile.isFile) {
            // SPA fallback: serve root index.html for client-side routing
            val spaFallback = File(rootDir, "index.html")
            if (spaFallback.exists() && !sanitized.contains('.')) {
                return serveFile(spaFallback)
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found: $uri")
        }

        return serveFile(targetFile)
    }

    private fun serveFile(file: File): Response {
        val mimeType = getMimeType(file.name)
        val isHtml = mimeType == "text/html"

        return if (isHtml && enableLiveReload) {
            // Inject live-reload script before </body>
            val content = file.readText()
            val injected = if (content.contains("</body>", ignoreCase = true)) {
                content.replace("</body>", "$LIVE_RELOAD_SCRIPT</body>", ignoreCase = true)
            } else {
                content + LIVE_RELOAD_SCRIPT
            }
            newFixedLengthResponse(Response.Status.OK, mimeType, injected).apply {
                addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                addHeader("Access-Control-Allow-Origin", "*")
            }
        } else {
            val fis = FileInputStream(file)
            newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length()).apply {
                addHeader("Cache-Control", if (isHtml) "no-cache" else "public, max-age=3600")
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return FILE_MIME_TYPES[ext] ?: "application/octet-stream"
    }

    private fun findMaxModified(dir: File): Long {
        var max = dir.lastModified()
        val files = dir.listFiles() ?: return max
        for (f in files) {
            if (f.name.startsWith(".") || f.name == "node_modules") continue
            val m = if (f.isDirectory) findMaxModified(f) else f.lastModified()
            if (m > max) max = m
        }
        return max
    }
}

/** Returns the device's LAN IPv4 address, or null if not connected to a network. */
private fun getDeviceLanIp(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (intf in interfaces) {
            // Skip loopback and down interfaces
            if (intf.isLoopback || !intf.isUp) continue
            for (addr in intf.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        Log.w("LocalWebServer", "Could not determine LAN IP: ${e.message}")
    }
    return null
}

private val FILE_MIME_TYPES = mapOf(
    "html" to "text/html",
    "htm" to "text/html",
    "css" to "text/css",
    "js" to "application/javascript",
    "mjs" to "application/javascript",
    "jsx" to "application/javascript",
    "ts" to "application/javascript",
    "tsx" to "application/javascript",
    "json" to "application/json",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "svg" to "image/svg+xml",
    "ico" to "image/x-icon",
    "webp" to "image/webp",
    "woff" to "font/woff",
    "woff2" to "font/woff2",
    "ttf" to "font/ttf",
    "otf" to "font/otf",
    "eot" to "application/vnd.ms-fontobject",
    "mp4" to "video/mp4",
    "webm" to "video/webm",
    "mp3" to "audio/mpeg",
    "ogg" to "audio/ogg",
    "wav" to "audio/wav",
    "pdf" to "application/pdf",
    "xml" to "application/xml",
    "txt" to "text/plain",
    "md" to "text/plain",
    "map" to "application/json",
    "wasm" to "application/wasm"
)
