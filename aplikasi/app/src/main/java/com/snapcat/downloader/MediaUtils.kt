package com.snapcat.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object MediaUtils {

    fun getTargetDirectory(type: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetFolder = if (type.equals("audio", ignoreCase = true)) {
            File(downloadsDir, "SnapCat/audio")
        } else {
            File(downloadsDir, "SnapCat/video")
        }

        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }
        return targetFolder
    }

    fun saveFileToDownloads(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        isAudio: Boolean,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Uri? {
        val relativePath = if (isAudio) "Download/SnapCat/audio" else "Download/SnapCat/video"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        copyStreamWithProgress(inputStream, outputStream, onProgress)
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    return uri
                } catch (e: Exception) {
                    e.printStackTrace()
                    resolver.delete(uri, null, null)
                }
            }
        } else {
            // Legacy Storage for Android 9 and below
            val targetDir = getTargetDirectory(if (isAudio) "audio" else "video")
            val targetFile = File(targetDir, fileName)

            try {
                FileOutputStream(targetFile).use { outputStream ->
                    copyStreamWithProgress(inputStream, outputStream, onProgress)
                }

                // Notify MediaScanner to index the file into Gallery immediately
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )

                return Uri.fromFile(targetFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalDownloaded: Long = 0
        val contentLength: Long = try {
            input.available().toLong()
        } catch (e: Exception) {
            -1L
        }

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalDownloaded += bytesRead
            onProgress(totalDownloaded, contentLength)
        }
        output.flush()
    }
}
