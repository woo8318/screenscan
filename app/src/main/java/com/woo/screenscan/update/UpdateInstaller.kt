package com.woo.screenscan.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a release APK into the app cache and hands it to the system package installer. */
class UpdateInstaller(private val context: Context) {

    suspend fun download(url: String, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val target = File(directory, APK_FILE_NAME)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download failed with HTTP ${connection.responseCode}")
            }
            copyWithProgress(connection, target, onProgress)
        } finally {
            connection.disconnect()
        }
        target
    }

    private fun copyWithProgress(connection: HttpURLConnection, target: File, onProgress: (Int) -> Unit) {
        val total = connection.contentLengthLong
        var copied = 0L
        var lastPercent = -1
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    val percent = if (total > 0) (copied * 100 / total).toInt() else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
            }
        }
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$PROVIDER_SUFFIX", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        const val CACHE_DIR = "updates"
        const val APK_FILE_NAME = "update.apk"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val PROVIDER_SUFFIX = ".fileprovider"
        const val TIMEOUT_MS = 15_000
        const val BUFFER_SIZE = 64 * 1024
    }
}
