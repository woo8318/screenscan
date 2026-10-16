package com.woo.screenscan.update

import org.json.JSONObject

/** The latest GitHub release that ships an APK. */
data class ReleaseInfo(
    val version: String,
    val apkUrl: String,
    val pageUrl: String,
) {
    companion object {
        private const val APK_SUFFIX = ".apk"

        /** Reads the GitHub "releases/latest" payload; null when it carries no APK asset. */
        fun fromJson(json: JSONObject): ReleaseInfo? {
            val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(APK_SUFFIX) }
                ?.optString("browser_download_url")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            return ReleaseInfo(tag, apkUrl, json.optString("html_url"))
        }
    }
}
