package com.luyuan.silkzoom

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseTest {
    private val repo = "owner/camera"
    private fun release(code: String = "4", url: String? = null): JSONObject {
        val name = "ai-composition-camera-0.3.1-$code.apk"
        return JSONObject().put("tag_name", "v0.3.1").put("draft", false).put("prerelease", false)
            .put("body", "新增检查更新")
            .put("assets", JSONArray().put(JSONObject().put("name", name).put("state", "uploaded").put("size", 100)
                .put("browser_download_url", url ?: "https://github.com/$repo/releases/download/v0.3.1/$name")))
    }
    private fun parse(json: JSONObject) = GitHubRelease.parse(repo, json.toString())
    private fun rejected(json: JSONObject) { assertThrows(IllegalArgumentException::class.java) { parse(json) } }

    @Test fun stableReleaseUsesAndroidVersionCode() {
        val result = parse(release("12"))
        assertEquals(12L, result.versionCode)
        assertEquals("0.3.1", result.versionName)
        assertEquals("新增检查更新", result.notes)
        assertTrue(result.versionCode > 4)
    }
    @Test fun equalAndOlderCodesAreNotUpdates() {
        assertFalse(parse(release()).versionCode > 4)
        assertFalse(parse(release("3")).versionCode > 4)
    }
    @Test fun draftAndPrereleaseAreRejected() {
        rejected(release().put("draft", true)); rejected(release().put("prerelease", true))
    }
    @Test fun invalidTagAndMissingApkAreRejected() {
        rejected(release().put("tag_name", "nightly")); rejected(release().put("assets", JSONArray()))
    }
    @Test fun insecureAndForeignDownloadsAreRejected() {
        rejected(release(url = "http://github.com/$repo/releases/download/v0.3.1/ai-composition-camera-0.3.1-4.apk"))
        rejected(release(url = "https://evil.test/file.apk"))
        rejected(release(url = "https://github.com/other/camera/releases/download/v0.3.1/ai-composition-camera-0.3.1-4.apk"))
    }
    @Test fun userinfoQueryAndWrongVersionAreRejected() {
        val url = "https://github.com/$repo/releases/download/v0.3.1/ai-composition-camera-0.3.1-4.apk"
        rejected(release(url = url.replace("github.com", "x@github.com")))
        rejected(release(url = "$url?redirect=1"))
        rejected(release(url = url.replace("/v0.3.1/", "/v0.3.0/")))
    }
    @Test fun incompleteAndAmbiguousAssetsAreRejected() {
        val pending = release(); pending.getJSONArray("assets").getJSONObject(0).put("state", "new"); rejected(pending)
        val empty = release(); empty.getJSONArray("assets").getJSONObject(0).put("size", 0); rejected(empty)
        val duplicate = release(); duplicate.getJSONArray("assets").put(duplicate.getJSONArray("assets").getJSONObject(0)); rejected(duplicate)
    }
    @Test fun invalidCodesAreRejected() { rejected(release("0")); rejected(release("99999999999999999999")) }
}
