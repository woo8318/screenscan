package com.woo.screenscan.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Looks up the latest release of a public GitHub repository. */
class UpdateChecker(private val repo: String) {

    /** Returns the latest release, or null when the repository has no release with an APK. */
    suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = URL("$API_BASE/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    ReleaseInfo.fromJson(JSONObject(body))
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub API returned ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API_BASE = "https://api.github.com"
        const val TIMEOUT_MS = 10_000
    }
}
