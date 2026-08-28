package com.snapcat.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun startDownload(
        url: String,
        platform: String,
        quality: String,
        format: String,
        downloadId: String
    ) {
        if (!PermissionHelper.hasPermissions(context)) {
            if (context is MainActivity) {
                context.runOnUiThread {
                    PermissionHelper.requestPermissions(context)
                    Toast.makeText(context, "Silakan izinkan akses penyimpanan & notifikasi terlebih dahulu.", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        DownloadService.startDownload(
            context = context,
            url = url,
            platform = platform,
            quality = quality,
            format = format,
            downloadId = downloadId
        )
    }

    @JavascriptInterface
    fun checkPermissions(): Boolean {
        return PermissionHelper.hasPermissions(context)
    }

    @JavascriptInterface
    fun isEngineReady(): Boolean {
        return YtDlpDownloader.isReady()
    }

    @JavascriptInterface
    fun fetchVideoInfo(url: String) {
        val mainActivity = context as? MainActivity ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isTikTok = url.contains("tiktok.com", ignoreCase = true) || url.contains("tikwm.com", ignoreCase = true)
                if (isTikTok) {
                    val result = TikTokDownloader.extractTikTokMedia(url)
                    if (result != null) {
                        val title = result.title.replace("\"", "\\\"").replace("\n", " ")
                        val cover = result.coverUrl ?: ""
                        val author = result.author?.replace("\"", "\\\"") ?: ""
                        val jsonStr = "{\"title\": \"$title\", \"thumbnail\": \"$cover\", \"uploader\": \"$author\", \"platform\": \"tiktok\"}"
                        withContext(Dispatchers.Main) {
                            mainActivity.webView.evaluateJavascript("if(window.onVideoInfoReady) window.onVideoInfoReady($jsonStr);", null)
                        }
                    } else {
                        throw Exception("Gagal mendapatkan info TikTok")
                    }
                } else {
                    // YouTube or others
                    val jsonStr = YtDlpDownloader.getVideoInfo(url)
                    if (jsonStr != null) {
                        withContext(Dispatchers.Main) {
                            mainActivity.webView.evaluateJavascript("if(window.onVideoInfoReady) window.onVideoInfoReady($jsonStr);", null)
                        }
                    } else {
                        throw Exception("Gagal mendapat info video")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.message?.replace("'", "\\'")?.replace("\n", " ") ?: "Error tidak diketahui"
                    mainActivity.webView.evaluateJavascript("if(window.onVideoInfoError) window.onVideoInfoError('$errorMsg');", null)
                }
            }
        }
    }

    @JavascriptInterface
    fun requestPermissions() {
        if (context is MainActivity) {
            context.runOnUiThread {
                PermissionHelper.requestPermissions(context)
            }
        }
    }

    @JavascriptInterface
    fun openDownloadedFile(fileUriStr: String) {
        try {
            val uri = Uri.parse(fileUriStr)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Tidak dapat membuka file.", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun shareFile(fileUriStr: String) {
        try {
            val uri = Uri.parse(fileUriStr)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan Media SnapCat").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal membagikan file.", Toast.LENGTH_SHORT).show()
        }
    }
}
