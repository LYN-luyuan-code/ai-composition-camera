package com.luyuan.silkzoom

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Manual checks never interrupt shooting or send camera frames/provider credentials. */
class AppUpdateController(private val activity: Activity) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private var dialog: AlertDialog? = null
    private var generation = 0
    private var closed = false

    fun check() {
        if (closed || dialog?.isShowing == true) return
        val request = ++generation
        dialog = AlertDialog.Builder(activity)
            .setTitle("软件更新")
            .setMessage("当前版本 ${BuildConfig.VERSION_NAME}\n正在检查 GitHub 上的新版本…")
            .setNegativeButton("取消", null).create().apply {
                setOnDismissListener { if (generation == request) generation++ }
                show()
            }
        executor.execute {
            val result = runCatching { fetch() }
            activity.runOnUiThread {
                if (closed || activity.isDestroyed || activity.isFinishing || generation != request) return@runOnUiThread
                generation++
                dialog?.dismiss()
                result.fold(onSuccess = { release ->
                    if (release.versionCode <= BuildConfig.VERSION_CODE) {
                        showMessage("已是最新版本", "当前版本 ${BuildConfig.VERSION_NAME}")
                    } else {
                        dialog = AlertDialog.Builder(activity)
                            .setTitle("发现新版本 ${release.versionName}")
                            .setMessage("当前版本 ${BuildConfig.VERSION_NAME}\n\n${release.notes.ifBlank { "性能与体验改进" }}\n\n将通过浏览器下载 APK，下载后打开文件并按系统提示安装。照片和设置会保留。")
                            .setPositiveButton("下载更新") { _, _ -> openDownload(release.apkUrl) }
                            .setNegativeButton("稍后", null).show()
                    }
                }, onFailure = { error ->
                    showMessage("暂时无法检查更新", error.message ?: "请检查网络，稍后重试。")
                })
            }
        }
    }

    private fun fetch(): GitHubRelease {
        val connection = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "AI-Composition-Camera/${BuildConfig.VERSION_NAME}")
            when (connection.responseCode) {
                200 -> Unit
                404 -> error("还没有可用的正式版本，请稍后再试。")
                403, 429 -> error("GitHub 暂时限制了请求，请稍后重试。")
                else -> error("更新服务暂时不可用，请检查网络后重试。")
            }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 1_048_576) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check(bytes.size <= 1_048_576) { "更新信息过大，请稍后重试。" }
            return GitHubRelease.parse(BuildConfig.GITHUB_REPOSITORY, bytes.toString(Charsets.UTF_8))
        } catch (error: java.io.IOException) {
            throw java.io.IOException("连接 GitHub 失败，请检查网络后重试。", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openDownload(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            showMessage("无法打开下载", "请先安装浏览器，再重试。")
        }
    }

    private fun showMessage(title: String, message: String) {
        dialog = AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton("知道了", null).show()
    }

    override fun close() {
        closed = true
        generation++
        dialog?.dismiss()
        executor.shutdownNow()
    }
}
