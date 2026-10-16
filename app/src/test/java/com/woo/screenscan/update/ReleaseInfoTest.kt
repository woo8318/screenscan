package com.woo.screenscan.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseInfoTest {
    private val payload = """
        {
          "tag_name": "v0.2.0",
          "html_url": "https://github.com/woo8318/screenscan/releases/tag/v0.2.0",
          "assets": [
            {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"},
            {"name": "screenscan-debug.apk", "browser_download_url": "https://example.com/screenscan-debug.apk"}
          ]
        }
    """.trimIndent()

    @Test
    fun picksTheApkAsset() {
        val info = ReleaseInfo.fromJson(JSONObject(payload))
        assertEquals(
            ReleaseInfo(
                version = "v0.2.0",
                apkUrl = "https://example.com/screenscan-debug.apk",
                pageUrl = "https://github.com/woo8318/screenscan/releases/tag/v0.2.0",
            ),
            info,
        )
    }

    @Test
    fun returnsNullWithoutApkAsset() {
        val json = JSONObject("""{"tag_name": "v0.2.0", "assets": [{"name": "a.txt", "browser_download_url": "x"}]}""")
        assertNull(ReleaseInfo.fromJson(json))
    }

    @Test
    fun returnsNullWithoutTag() {
        assertNull(ReleaseInfo.fromJson(JSONObject("""{"assets": []}""")))
    }
}
