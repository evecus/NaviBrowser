package com.navibrowser.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebResourceRequest

/**
 * 视频嗅探器：拦截 WebView 请求，识别视频资源 URL。
 *
 * 检测策略：
 *   1. URL 扩展名匹配（mp4/m3u8/flv/avi 等）
 *   2. MIME 类型 Content-Type 包含 video/
 *   3. 常见流媒体 CDN 路径特征
 */
object VideoSniffer {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m3u8", "m3u", "flv", "avi", "mkv", "mov", "wmv",
        "webm", "ts", "mpd", "f4v", "3gp", "rmvb", "rm", "mp2ts"
    )

    private val VIDEO_MIME_FRAGMENTS = listOf("video/", "application/x-mpegurl",
        "application/vnd.apple.mpegurl", "application/dash+xml")

    private val STREAM_CDN_PATTERNS = listOf(
        "/hls/", "/dash/", "/live/", "/vod/", "/stream/",
        ".m3u8", ".mpd", "chunklist", "playlist.m3u",
        "videoplayback", "/manifest", "/seg-"
    )

    /** 嗅探到的视频链接集合（按标签去重）*/
    private val sniffed = LinkedHashMap<String, SniffedVideo>()

    data class SniffedVideo(
        val url: String,
        val title: String = "",
        val mimeType: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    var onVideoFound: ((SniffedVideo) -> Unit)? = null

    fun clear() = sniffed.clear()

    fun getAll(): List<SniffedVideo> = sniffed.values.toList()

    /** 在 shouldInterceptRequest 中调用，返回 true 表示识别为视频 */
    fun sniff(request: WebResourceRequest, pageTitle: String = ""): Boolean {
        val url = request.url.toString()
        if (!url.startsWith("http")) return false

        // 1. 扩展名
        val path = request.url.path ?: ""
        val ext = path.substringAfterLast('.', "").lowercase().substringBefore('?')
        if (ext in VIDEO_EXTENSIONS) {
            record(url, pageTitle, "video/$ext")
            return true
        }

        // 2. Accept/Content-Type 头
        val accept = request.requestHeaders["Accept"] ?: ""
        if (VIDEO_MIME_FRAGMENTS.any { accept.contains(it, ignoreCase = true) }) {
            record(url, pageTitle, "video")
            return true
        }

        // 3. CDN 路径特征
        if (STREAM_CDN_PATTERNS.any { url.contains(it, ignoreCase = true) }) {
            record(url, pageTitle, "stream")
            return true
        }

        return false
    }

    /** 对完整响应 Content-Type 二次校验（在 onResponse 中调用） */
    fun sniffByContentType(url: String, contentType: String, pageTitle: String = ""): Boolean {
        if (VIDEO_MIME_FRAGMENTS.any { contentType.contains(it, ignoreCase = true) }) {
            record(url, pageTitle, contentType)
            return true
        }
        return false
    }

    private fun record(url: String, title: String, mime: String) {
        if (sniffed.containsKey(url)) return
        val v = SniffedVideo(url, title, mime)
        sniffed[url] = v
        onVideoFound?.invoke(v)
    }

    /** 用系统/指定播放器打开视频 */
    fun openVideo(context: Context, video: SniffedVideo, playerPackage: String = "") {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(video.url), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (playerPackage.isNotEmpty()) {
            try {
                context.packageManager.getPackageInfo(playerPackage, 0)
                intent.setPackage(playerPackage)
            } catch (e: PackageManager.NameNotFoundException) { /* 包名无效，忽略 */ }
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 没有播放器时尝试下载
            val dlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(video.url))
            dlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(dlIntent) } catch (_: Exception) {}
        }
    }
}
