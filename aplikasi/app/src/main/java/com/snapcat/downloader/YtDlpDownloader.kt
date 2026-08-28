package com.snapcat.downloader

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

object YtDlpDownloader {

    private var isInitialized = false

    fun isReady(): Boolean = isInitialized

    fun init(context: Context): Boolean {
        if (!isInitialized) {
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return isInitialized
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadMedia(
        context: Context,
        url: String,
        quality: String,
        isAudio: Boolean,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        init(context)

        val formatOption = if (isAudio) {
            "bestaudio/best"
        } else {
            when (quality) {
                "1080p" -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                "720p" -> "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                "480p" -> "bestvideo[height<=480]+bestaudio/best[height<=480]/best"
                else -> "bestvideo+bestaudio/best"
            }
        }

        val request = YoutubeDLRequest(url).apply {
            addOption("-f", formatOption)
            addOption("-g") // Get direct download URL
        }

        try {
            val response = YoutubeDL.getInstance().execute(request)
            val directUrls = response.out.trim().split("\n")

            if (directUrls.isNotEmpty() && directUrls[0].startsWith("http")) {
                val mediaUrlString: String = directUrls[0].trim()
                val ext = if (isAudio) "mp3" else "mp4"
                val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
                val timeStamp = System.currentTimeMillis()
                val fileName = "SnapCat_Media_$timeStamp.$ext"

                val httpReq = Request.Builder()
                    .url(mediaUrlString)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val httpResp = client.newCall(httpReq).execute()
                val body = httpResp.body ?: throw Exception("Body respon media kosong.")
                val contentLength = body.contentLength()
                val inputStream: InputStream = body.byteStream()

                return@withContext MediaUtils.saveFileToDownloads(
                    context = context,
                    inputStream = inputStream,
                    fileName = fileName,
                    mimeType = mimeType,
                    isAudio = isAudio,
                    onProgress = { downloaded, total ->
                        val calcTotal = if (total > 0) total else contentLength
                        val percent = if (calcTotal > 0) ((downloaded * 100) / calcTotal).toInt() else 0
                        onProgress(percent, downloaded, calcTotal)
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        throw Exception("Gagal mengekstrak media dengan yt-dlp. Periksa tautan video.")
    }

    suspend fun getVideoInfo(url: String): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext null
        val request = YoutubeDLRequest(url).apply {
            addOption("-J")
        }
        try {
            val response = YoutubeDL.getInstance().execute(request)
            val jsonStr = response.out
            val json = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
            val title = json.get("title")?.asString?.replace("\"", "\\\"")?.replace("\n", " ") ?: "Video"
            val thumbnail = json.get("thumbnail")?.asString ?: ""
            val uploader = json.get("uploader")?.asString?.replace("\"", "\\\"") ?: "YouTube"
            val platform = if (url.contains("youtube", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)) "youtube" else "generic"
            "{\"title\": \"$title\", \"thumbnail\": \"$thumbnail\", \"uploader\": \"$uploader\", \"platform\": \"$platform\"}"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
