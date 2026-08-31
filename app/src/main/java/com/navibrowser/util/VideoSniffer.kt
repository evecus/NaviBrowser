package com.navibrowser.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebResourceRequest
import com.navibrowser.util.UrlUtils

/**
 * 视频嗅探器：拦截 WebView 请求 + 页面 DOM 扫描，识别视频资源 URL。
 *
 * 检测策略：
 *   1. URL 扩展名匹配（mp4/m3u8/flv/avi 等）
 *   2. MIME 类型 / Accept 头包含 video/
 *   3. 常见流媒体 CDN 路径特征
 *   4. 页面 DOM 中的 <video>/<source>/<iframe> 等元素（经 VideoScannerJs 回调）
 *   5. blob: URL（经 URL.createObjectURL hook 回调）
 *
 * 改进点：
 *   - 按 tab 维度存储，切换标签不再丢失上个标签的嗅探结果。
 *   - 误报过滤：跳过常见统计 / 广告域名、明显非媒体的小资源请求。
 *   - 提供 m3u8 子播放列表解析（同步取首条子流）。
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

    /** 统计 / 广告 / 追踪域名，嗅探时直接跳过以减少误报。 */
    private val BLOCKED_HOST_KEYWORDS = listOf(
        "google-analytics.com", "googletagmanager.com", "doubleclick.net",
        "facebook.com/tr", "hotjar.com", "segment.io", "mixpanel.com",
        "umeng", "cnzz", "baidu.com/hm.js", "sentry.io", "clarity.ms",
        "scorecardresearch.com", "quantserve.com", "adnxs.com", "pubmatic",
        "criteo", "taboola", "outbrain"
    )

    /** 嗅探到的视频链接集合（按 tab 去重）。tabId 为空时使用 GLOBAL_KEY。 */
    private val sniffed = LinkedHashMap<String, LinkedHashMap<String, SniffedVideo>>()
    private const val GLOBAL_KEY = "__global__"

    data class SniffedVideo(
        val url: String,
        val title: String = "",
        val mimeType: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    var onVideoFound: ((SniffedVideo) -> Unit)? = null

    /** 清空指定 tab 的嗅探结果；tabId 为空则清空全部。 */
    @JvmOverloads
    fun clear(tabId: String? = null) {
        if (tabId == null) sniffed.clear()
        else sniffed.remove(tabId)
    }

    /** 获取指定 tab 的嗅探结果；tabId 为空时返回全部聚合。 */
    @JvmOverloads
    fun getAll(tabId: String? = null): List<SniffedVideo> =
        if (tabId == null) sniffed.values.flatMap { it.values.toList() }
        else sniffed[tabId]?.values?.toList() ?: emptyList()

    private fun bucket(tabId: String?): LinkedHashMap<String, SniffedVideo> {
        val key = tabId ?: GLOBAL_KEY
        return sniffed.getOrPut(key) { LinkedHashMap() }
    }

    /** 在 shouldInterceptRequest 中调用，返回 true 表示识别为视频 */
    @JvmOverloads
    fun sniff(request: WebResourceRequest, pageTitle: String = "", tabId: String? = null): Boolean {
        val url = request.url.toString()
        if (!url.startsWith("http")) return false
        if (isBlockedHost(url)) return false

        // 1. 扩展名
        val path = request.url.path ?: ""
        val ext = path.substringAfterLast('.', "").lowercase().substringBefore('?')
        if (ext in VIDEO_EXTENSIONS) {
            record(url, pageTitle, "video/$ext", tabId)
            return true
        }

        // 2. Accept/Content-Type 头
        val accept = request.requestHeaders["Accept"] ?: ""
        if (accept.isNotEmpty() && VIDEO_MIME_FRAGMENTS.any { accept.contains(it, ignoreCase = true) }) {
            record(url, pageTitle, "video", tabId)
            return true
        }

        // 3. CDN 路径特征
        if (STREAM_CDN_PATTERNS.any { url.contains(it, ignoreCase = true) }) {
            record(url, pageTitle, "stream", tabId)
            return true
        }

        return false
    }

    /** 对完整响应 Content-Type 二次校验（在 onResponse 中调用） */
    @JvmOverloads
    fun sniffByContentType(url: String, contentType: String, pageTitle: String = "", tabId: String? = null): Boolean {
        if (VIDEO_MIME_FRAGMENTS.any { contentType.contains(it, ignoreCase = true) }) {
            record(url, pageTitle, contentType, tabId)
            return true
        }
        return false
    }

    /** 记录 DOM/JS 扫描到的视频（去重，触发回调） */
    @JvmOverloads
    fun recordDom(url: String, title: String, mime: String, tabId: String? = null) {
        if (!url.startsWith("http") && !url.startsWith("blob:")) return
        if (isBlockedHost(url)) return
        record(url, title, mime, tabId)
    }

    private fun record(url: String, title: String, mime: String, tabId: String?) {
        val store = bucket(tabId)
        if (store.containsKey(url)) return
        val v = SniffedVideo(url, title, mime)
        store[url] = v
        onVideoFound?.invoke(v)
    }

    /** 判断是否为统计/广告/追踪域名，嗅探时直接忽略。 */
    private fun isBlockedHost(url: String): Boolean {
        val host = UrlUtils.getDomain(url) ?: return false
        return BLOCKED_HOST_KEYWORDS.any { host.contains(it, ignoreCase = true) || url.contains(it, ignoreCase = true) }
    }

    /**
     * 解析 m3u8 内容，返回内嵌的子播放列表 / 分片 URL 列表（仅 http(s)）。
     * 用于在嗅探到主 m3u8 后进一步发现真正的媒体流。
     */
    fun parseM3u8(content: String): List<String> {
        val result = mutableListOf<String>()
        content.lineSequence().map { it.trim() }.forEach { line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            if (line.startsWith("http://") || line.startsWith("https://")) result.add(line)
        }
        return result
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
