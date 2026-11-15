package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

data class EditorFormatState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val ul: Boolean = false,
    val ol: Boolean = false,
    val block: String = ""
)

class PriyoWebEditorController {
    internal var webView: WebView? = null
    internal var isReady: Boolean = false
    internal var pendingHtml: String? = null
    internal var pendingMode: String = "edit"
    internal var pendingTheme: Pair<String, String?>? = null

    fun execFormat(command: String, value: String? = null) {
        val view = webView ?: return
        val cmdEsc = JSONObject.quote(command)
        val valEsc = if (value != null) JSONObject.quote(value) else "null"
        view.post {
            view.evaluateJavascript("window.PriyoEditor && window.PriyoEditor.execFormat($cmdEsc, $valEsc);", null)
        }
    }

    fun setHtml(html: String) {
        pendingHtml = html
        val view = webView
        if (view != null && isReady) {
            val escaped = JSONObject.quote(html)
            view.post {
                view.evaluateJavascript("window.PriyoEditor && window.PriyoEditor.setHtml($escaped);", null)
            }
        }
    }

    fun setTheme(isDark: Boolean, bgHex: String?) {
        val themeStr = if (isDark) "dark" else "light"
        pendingTheme = themeStr to bgHex
        val view = webView
        if (view != null && isReady) {
            val themeArg = JSONObject.quote(themeStr)
            val hexArg = if (bgHex != null) JSONObject.quote(bgHex) else "null"
            view.post {
                view.evaluateJavascript("window.PriyoEditor && window.PriyoEditor.setTheme($themeArg, $hexArg);", null)
            }
        }
    }

    fun requestSnapshot() {
        val view = webView ?: return
        if (isReady) {
            view.post {
                view.evaluateJavascript("window.PriyoEditor && window.PriyoEditor.requestSnapshot();", null)
            }
        }
    }
}

private class PriyoJsBridge(
    private val mainHandler: Handler,
    private val onReady: () -> Unit,
    private val onContent: (String, String, Int) -> Unit,
    private val onFormat: (EditorFormatState) -> Unit
) {
    @JavascriptInterface
    fun onEditorReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onContentChanged(html: String?, plainText: String?, wordCount: Int, charCount: Int) {
        val safeHtml = html.orEmpty()
        val safePlain = plainText.orEmpty()
        mainHandler.post { onContent(safeHtml, safePlain, wordCount) }
    }

    @JavascriptInterface
    fun onFormatStateChanged(jsonState: String?) {
        if (jsonState.isNullOrBlank()) return
        val parsed = runCatching {
            val obj = JSONObject(jsonState)
            EditorFormatState(
                bold = obj.optBoolean("bold", false),
                italic = obj.optBoolean("italic", false),
                underline = obj.optBoolean("underline", false),
                strike = obj.optBoolean("strike", false),
                ul = obj.optBoolean("ul", false),
                ol = obj.optBoolean("ol", false),
                block = obj.optString("block", "")
            )
        }.getOrNull() ?: return
        mainHandler.post { onFormat(parsed) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PriyoRichTextWebView(
    initialHtml: String,
    isEditable: Boolean,
    isDarkTheme: Boolean,
    backgroundHex: String?,
    controller: PriyoWebEditorController,
    modifier: Modifier = Modifier,
    onContentChanged: (html: String, plainText: String, wordCount: Int) -> Unit = { _, _, _ -> },
    onFormatStateChanged: (EditorFormatState) -> Unit = {}
) {
    val latestOnContent by rememberUpdatedState(onContentChanged)
    val latestOnFormat by rememberUpdatedState(onFormatStateChanged)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var bridgeReady by remember { mutableStateOf(false) }

    LaunchedEffect(isDarkTheme, backgroundHex, bridgeReady) {
        if (bridgeReady) {
            controller.setTheme(isDarkTheme, backgroundHex)
        }
    }

    LaunchedEffect(initialHtml, bridgeReady) {
        if (bridgeReady && !isEditable) {
            controller.setHtml(initialHtml)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.webView?.removeJavascriptInterface("PriyoBridge")
            controller.webView?.destroy()
            controller.webView = null
            controller.isReady = false
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                controller.pendingMode = if (isEditable) "edit" else "view"
                controller.pendingHtml = initialHtml
                controller.pendingTheme = (if (isDarkTheme) "dark" else "light") to backgroundHex

                val bridge = PriyoJsBridge(
                    mainHandler = mainHandler,
                    onReady = {
                        controller.isReady = true
                        bridgeReady = true
                        val modeEsc = JSONObject.quote(controller.pendingMode)
                        val htmlEsc = JSONObject.quote(controller.pendingHtml.orEmpty())
                        val themePair = controller.pendingTheme ?: ("light" to null)
                        val themeArg = JSONObject.quote(themePair.first)
                        val hexArg = if (themePair.second != null) JSONObject.quote(themePair.second) else "null"
                        evaluateJavascript(
                            "window.PriyoEditor.setMode($modeEsc);" +
                                "window.PriyoEditor.setTheme($themeArg, $hexArg);" +
                                "window.PriyoEditor.setHtml($htmlEsc);",
                            null
                        )
                    },
                    onContent = { html, plain, words ->
                        latestOnContent(html, plain, words)
                    },
                    onFormat = { state ->
                        latestOnFormat(state)
                    }
                )
                addJavascriptInterface(bridge, "PriyoBridge")

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:")) {
                            if (!isEditable) {
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                            return true
                        }
                        return !url.startsWith("file:///android_asset/priyo_editor/")
                    }
                }

                controller.webView = this
                loadUrl("file:///android_asset/priyo_editor/editor.html")
            }
        }
    )
}
