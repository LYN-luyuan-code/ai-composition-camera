package com.luyuan.silkzoom

import org.json.JSONObject
import java.net.URI

/** Only stable, fully published releases with our versioned APK are install candidates. */
data class GitHubRelease(val versionName: String, val versionCode: Long, val notes: String, val apkUrl: String) {
    companion object {
        fun parse(repository: String, json: String): GitHubRelease {
            require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
            val release = JSONObject(json)
            require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "没有可用的正式版本" }
            val tag = release.getString("tag_name")
            require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+"))) { "发布版本号无效" }
            val version = tag.removePrefix("v")
            val pattern = Regex("ai-composition-camera-${Regex.escape(version)}-([1-9][0-9]*)\\.apk")
            val assets = release.getJSONArray("assets")
            val candidates = (0 until assets.length()).mapNotNull { index ->
                val asset = assets.getJSONObject(index)
                val name = asset.optString("name")
                val match = pattern.matchEntire(name) ?: return@mapNotNull null
                require(asset.optString("state") == "uploaded" && asset.optLong("size") > 0) { "安装包尚未上传完成" }
                val url = asset.getString("browser_download_url")
                val uri = URI(url)
                require(uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
                    uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                    uri.rawPath == "/$repository/releases/download/$tag/$name") { "安装包下载地址无效" }
                GitHubRelease(version, match.groupValues[1].toLong(), release.optString("body").take(4000), url)
            }
            require(candidates.size == 1) { "这个版本还没有可用的正式安装包，请稍后重试" }
            return candidates.single()
        }
    }
}
