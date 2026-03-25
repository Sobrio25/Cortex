package com.aiagents.app.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * WebView preview dialog with two modes:
 * - Inline HTML: renders raw HTML string (existing behavior)
 * - URL mode: loads a localhost URL from LocalWebServer (multi-file projects)
 *
 * @param html Raw HTML content (used when url is null)
 * @param url Localhost URL to load (takes priority over html)
 * @param title Title shown in the top bar
 * @param onDismiss Called when the dialog is closed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewDialog(
    html: String = "",
    url: String? = null,
    title: String = "Preview",
    onDismiss: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var consoleErrors by remember { mutableStateOf(listOf<String>()) }
    var showConsole by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(url ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (url != null) {
                                Text(
                                    text = currentUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    },
                    actions = {
                        // Console errors badge
                        if (consoleErrors.isNotEmpty()) {
                            IconButton(onClick = { showConsole = !showConsole }) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text("${consoleErrors.size}") }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Console errors",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            isLoading = true
                            webViewRef?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recargar")
                        }
                    }
                )

                // Loading indicator
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Console error panel
                AnimatedVisibility(visible = showConsole && consoleErrors.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .background(Color(0xFF1E1E1E))
                            .padding(8.dp)
                    ) {
                        items(consoleErrors) { error ->
                            Text(
                                text = error,
                                color = Color(0xFFFF6B6B),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                // WebView
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            @SuppressLint("SetJavaScriptEnabled")
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowUniversalAccessFromFileURLs = false

                            // URL mode needs file access for local assets
                            if (url != null) {
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                // Enable ES module imports and modern JS features
                                settings.javaScriptCanOpenWindowsAutomatically = true
                            } else {
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    isLoading = false
                                    pageUrl?.let { currentUrl = it }
                                }
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    val msg = "[${error?.errorCode}] ${error?.description} — ${request?.url}"
                                    Log.w("WebPreview", msg)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                    message?.let { msg ->
                                        if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                            consoleErrors = consoleErrors + "${msg.sourceId()}:${msg.lineNumber()} ${msg.message()}"
                                        }
                                    }
                                    return true
                                }
                            }

                            // Load content based on mode
                            if (url != null) {
                                loadUrl(url)
                            } else {
                                loadDataWithBaseURL(
                                    null,
                                    html,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                            webViewRef = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }
}
