package com.snapcat.downloader

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

object TikTokDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class TikTokMediaResult(
        val title: String,
        val videoUrl: String?,
        val audioUrl: String?,
        val coverUrl: String?,
        val author: String?
    )

    suspend fun extractTikTokMedia(url: String): TikTokMediaResult? = withContext(Dispatchers.IO) {
        try {
            // Method 1: TikWM Public Extractor API
            val requestBody = FormBody.Builder()
                .add("url", url)
                .add("hd", "1")
                .build()

            val request = Request.Builder()
                .url("https://www.tikwm.com/api/")
                .post(requestBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val responseText = response.body?.string() ?: ""

            if (responseText.isNotEmpty()) {
                val json = JsonParser.parseString(responseText).asJsonObject
                if (json.has("code") && json.get("code").asInt == 0) {
                    val data = json.getAsJsonObject("data")
                    val title = data.get("title")?.asString ?: "TikTok_Video"
                    val hdVideo = data.get("hdplay")?.asString ?: data.get("play")?.asString
                    val music = data.get("music")?.asString
                    val cover = data.get("cover")?.asString
                    val authorObj = data.getAsJsonObject("author")
                    val authorName = authorObj?.get("nickname")?.asString ?: "TikTok"

                    val finalVideoUrl = if (hdVideo != null && !hdVideo.startsWith("http")) {
                        "https://www.tikwm.com$hdVideo"
                    } else hdVideo

                    val finalMusicUrl = if (music != null && !music.startsWith("http")) {
                        "https://www.tikwm.com$music"
                    } else music

                    return@withContext TikTokMediaResult(
                        title = title,
                        videoUrl = finalVideoUrl,
                        audioUrl = finalMusicUrl,
                        coverUrl = cover,
                        author = authorName
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext null
    }

    suspend fun downloadTikTokMedia(
        context: Context,
        url: String,
        isAudio: Boolean,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Uri? = withContext(Dispatchers.IO) {
        val mediaResult = extractTikTokMedia(url)
            ?: throw Exception("Gagal mengekstrak video TikTok. Periksa koneksi atau tautan video.")

        val downloadUrl = if (isAudio) {
            mediaResult.audioUrl ?: mediaResult.videoUrl
        } else {
            mediaResult.videoUrl
        } ?: throw Exception("URL media tidak ditemukan.")

        val ext = if (isAudio) "mp3" else "mp4"
        val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
        val timeStamp = System.currentTimeMillis()
        val safeTitle = mediaResult.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_").take(30)
        val fileName = "SnapCat_TikTok_${safeTitle}_$timeStamp.$ext"

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body ?: throw Exception("Body respon kosong.")
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
}
