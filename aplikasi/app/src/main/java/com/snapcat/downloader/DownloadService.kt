package com.snapcat.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class DownloadService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val CHANNEL_ID = "snapcat_download_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_DOWNLOAD = "com.snapcat.ACTION_START_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_PLATFORM = "extra_platform"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        fun startDownload(
            context: Context,
            url: String,
            platform: String,
            quality: String,
            format: String,
            downloadId: String
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_PLATFORM, platform)
                putExtra(EXTRA_QUALITY, quality)
                putExtra(EXTRA_FORMAT, format)
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_URL) ?: ""
            val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: ""
            val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "hd"
            val format = intent.getStringExtra(EXTRA_FORMAT) ?: "mp4"
            val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: System.currentTimeMillis().toString()

            startForeground(NOTIFICATION_ID, buildNotification("Memulai unduhan SnapCat...", 0, true))

            executeDownload(url, platform, quality, format, downloadId)
        }
        return START_NOT_STICKY
    }

    private fun executeDownload(
        url: String,
        platform: String,
        quality: String,
        format: String,
        downloadId: String
    ) {
        serviceScope.launch {
            val isAudio = format.equals("mp3", ignoreCase = true) || quality.equals("mp3", ignoreCase = true)
            var lastTime = System.currentTimeMillis()
            var lastBytes: Long = 0

            try {
                val isTikTok = platform.equals("tiktok", ignoreCase = true) ||
                        url.contains("tiktok.com", ignoreCase = true)

                val uri = if (isTikTok) {
                    TikTokDownloader.downloadTikTokMedia(
                        context = this@DownloadService,
                        url = url,
                        isAudio = isAudio,
                        onProgress = { percent, downloaded, total ->
                            val currentTime = System.currentTimeMillis()
                            val timeDiff = (currentTime - lastTime) / 1000.0
                            var speedText = ""
                            if (timeDiff >= 0.5) {
                                val bytesDiff = downloaded - lastBytes
                                val speedKb = (bytesDiff / 1024.0) / timeDiff
                                speedText = if (speedKb > 1024) {
                                    String.format(Locale.US, "%.1f MB/s", speedKb / 1024.0)
                                } else {
                                    String.format(Locale.US, "%.0f KB/s", speedKb)
                                }
                                lastTime = currentTime
                                lastBytes = downloaded
                            }

                            updateNotification(percent, speedText)
                            MainActivity.sendProgressToJs(downloadId, percent, speedText, "downloading")
                        }
                    )
                } else {
                    YtDlpDownloader.downloadMedia(
                        context = this@DownloadService,
                        url = url,
                        quality = quality,
                        isAudio = isAudio,
                        onProgress = { percent, downloaded, total ->
                            val currentTime = System.currentTimeMillis()
                            val timeDiff = (currentTime - lastTime) / 1000.0
                            var speedText = ""
                            if (timeDiff >= 0.5) {
                                val bytesDiff = downloaded - lastBytes
                                val speedKb = (bytesDiff / 1024.0) / timeDiff
                                speedText = if (speedKb > 1024) {
                                    String.format(Locale.US, "%.1f MB/s", speedKb / 1024.0)
                                } else {
                                    String.format(Locale.US, "%.0f KB/s", speedKb)
                                }
                                lastTime = currentTime
                                lastBytes = downloaded
                            }

                            updateNotification(percent, speedText)
                            MainActivity.sendProgressToJs(downloadId, percent, speedText, "downloading")
                        }
                    )
                }

                if (uri != null) {
                    showCompletedNotification("Unduhan Selesai! Tersimpan di Downloads/SnapCat/")
                    MainActivity.sendProgressToJs(downloadId, 100, "Selesai", "completed", uri.toString())
                } else {
                    showFailedNotification("Gagal menyimpan file.")
                    MainActivity.sendProgressToJs(downloadId, 0, "Gagal", "failed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showFailedNotification(e.message ?: "Terjadi kesalahan unduhan.")
                MainActivity.sendProgressToJs(downloadId, 0, e.message ?: "Gagal", "failed")
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SnapCat Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi progres unduhan SnapCat"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SnapCat Downloader")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .build()

    private fun updateNotification(progress: Int, speedText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = "Mengunduh... $progress% ($speedText)"
        manager.notify(NOTIFICATION_ID, buildNotification(text, progress, false))
    }

    private fun showCompletedNotification(msg: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SnapCat Downloader")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID + 1, notif)
    }

    private fun showFailedNotification(msg: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SnapCat Downloader")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID + 2, notif)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
