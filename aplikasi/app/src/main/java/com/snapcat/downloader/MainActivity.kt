package com.snapcat.downloader

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    companion object {
        private var activeInstance: MainActivity? = null

        fun sendProgressToJs(
            downloadId: String,
            percent: Int,
            speedText: String,
            status: String,
            fileUriStr: String = ""
        ) {
            activeInstance?.let { activity ->
                activity.runOnUiThread {
                    val safeUri = fileUriStr.replace("'", "\\'")
                    val safeSpeed = speedText.replace("'", "\\'")
                    val safeId = downloadId.replace("'", "\\'")
                    val jsScript = "if (window.onNativeDownloadProgress) { window.onNativeDownloadProgress('$safeId', $percent, '$safeSpeed', '$status', '$safeUri'); }"
                    activity.webView.evaluateJavascript(jsScript, null)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this

        // True Edge-to-Edge Layout
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false

        webView = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(webView)

        setupWebViewSettings()
        setupJavaScriptBridge()

        // Auto Request Permissions (Notification + Storage) on app launch
        PermissionHelper.requestPermissions(this)

        // Initialize yt-dlp & FFmpeg asynchronously in background
        CoroutineScope(Dispatchers.IO).launch {
            YtDlpDownloader.init(applicationContext)
            runOnUiThread {
                webView.evaluateJavascript(
                    "window.isNativeEngineReady = true; if(window.onNativeEngineReady) window.onNativeEngineReady();",
                    null
                )
            }
        }

        // Load HTML UI from assets
        webView.loadUrl("file:///android_asset/index.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSettings() {
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.evaluateJavascript(
                    "console.log('SnapCat Native Android Bridge Connected!');",
                    null
                )
            }
        }
    }

    private fun setupJavaScriptBridge() {
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")
    }

    override fun onDestroy() {
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
