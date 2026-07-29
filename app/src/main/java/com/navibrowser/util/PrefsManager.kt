package com.navibrowser.util

import android.content.Context
import androidx.core.content.edit

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("navi_prefs", Context.MODE_PRIVATE)

    // Search
    var selectedSearchEngineIndex: Int
        get() = prefs.getInt("search_engine_index", 0).coerceIn(0, 5)
        set(v) = prefs.edit { putInt("search_engine_index", v.coerceIn(0, 5)) }
    var customSearchUrl: String
        get() = prefs.getString("custom_search_url", "") ?: ""
        set(v) = prefs.edit { putString("custom_search_url", v) }
    var searchSuggestionEnabled: Boolean
        get() = prefs.getBoolean("search_suggestion_enabled", true)
        set(v) = prefs.edit { putBoolean("search_suggestion_enabled", v) }

    // Home / New tab
    var homePage: String
        get() = prefs.getString("home_page", "navi://home") ?: "navi://home"
        set(v) = prefs.edit { putString("home_page", v) }
    var newTabBehavior: Int
        get() = prefs.getInt("new_tab_behavior", 0).coerceIn(0, 2)
        set(v) = prefs.edit { putInt("new_tab_behavior", v.coerceIn(0, 2)) }
    var newTabCustomUrl: String
        get() = prefs.getString("new_tab_custom_url", "https://www.bing.com") ?: "https://www.bing.com"
        set(v) = prefs.edit { putString("new_tab_custom_url", v) }

    // Appearance
    /** 0=light, 1=dark, 2=system */
    var nightMode: Int
        get() = prefs.getInt("night_mode", 2).coerceIn(0, 2)
        set(v) = prefs.edit { putInt("night_mode", v.coerceIn(0, 2)) }
    var nightFilterStrength: Int
        get() = prefs.getInt("night_filter_strength", 80).coerceIn(0, 255)
        set(v) = prefs.edit { putInt("night_filter_strength", v.coerceIn(0, 255)) }
    var hideStatusBar: Boolean
        get() = prefs.getBoolean("hide_status_bar", false)
        set(v) = prefs.edit { putBoolean("hide_status_bar", v) }
    /** 0=auto, 1=portrait, 2=landscape */
    var screenOrientation: Int
        get() = prefs.getInt("screen_orientation", 0).coerceIn(0, 2)
        set(v) = prefs.edit { putInt("screen_orientation", v.coerceIn(0, 2)) }
    var textSize: Int
        get() = prefs.getInt("text_size", 100).coerceIn(50, 200)
        set(v) = prefs.edit { putInt("text_size", v.coerceIn(50, 200)) }
    var accentColorIndex: Int
        get() = prefs.getInt("accent_color_index", 0).coerceIn(0, 7)
        set(v) = prefs.edit { putInt("accent_color_index", v.coerceIn(0, 7)) }
    var volumeKeyGestures: Boolean
        get() = prefs.getBoolean("volume_key_gestures", false)
        set(v) = prefs.edit { putBoolean("volume_key_gestures", v) }
    var backForwardGesture: Boolean
        get() = prefs.getBoolean("back_forward_gesture", false)
        set(v) = prefs.edit { putBoolean("back_forward_gesture", v) }
    var pullToRefresh: Boolean
        get() = prefs.getBoolean("pull_to_refresh", true)
        set(v) = prefs.edit { putBoolean("pull_to_refresh", v) }

    // Web
    var isIncognitoDefault: Boolean
        get() = prefs.getBoolean("incognito_default", false)
        set(v) = prefs.edit { putBoolean("incognito_default", v) }
    var javascriptEnabled: Boolean
        get() = prefs.getBoolean("js_enabled", true)
        set(v) = prefs.edit { putBoolean("js_enabled", v) }
    var imagesEnabled: Boolean
        get() = prefs.getBoolean("images_enabled", true)
        set(v) = prefs.edit { putBoolean("images_enabled", v) }
    var cookiesEnabled: Boolean
        get() = prefs.getBoolean("cookies_enabled", true)
        set(v) = prefs.edit { putBoolean("cookies_enabled", v) }
    var blockThirdPartyCookies: Boolean
        get() = prefs.getBoolean("block_3rd_cookies", false)
        set(v) = prefs.edit { putBoolean("block_3rd_cookies", v) }
    var blockPopups: Boolean
        get() = prefs.getBoolean("block_popups", true)
        set(v) = prefs.edit { putBoolean("block_popups", v) }
    var doNotTrack: Boolean
        get() = prefs.getBoolean("do_not_track", false)
        set(v) = prefs.edit { putBoolean("do_not_track", v) }
    var locationAccess: Boolean
        get() = prefs.getBoolean("location_access", false)
        set(v) = prefs.edit { putBoolean("location_access", v) }
    var requestDesktopSite: Boolean
        get() = prefs.getBoolean("request_desktop_site", false)
        set(v) = prefs.edit { putBoolean("request_desktop_site", v) }
    /** 0=Android default, 1=Desktop Chrome, 2=iPhone Safari, 3=Custom */
    var userAgentMode: Int
        get() = prefs.getInt("user_agent_mode", 0).coerceIn(0, 3)
        set(v) = prefs.edit { putInt("user_agent_mode", v.coerceIn(0, 3)) }
    var customUserAgent: String
        get() = prefs.getString("custom_user_agent", "") ?: ""
        set(v) = prefs.edit { putString("custom_user_agent", v) }

    // ── 桌面模式（新增）──────────────────────────────────────────────────
    /** 快速切换桌面/移动 UA，独立于 userAgentMode 设置 */
    var desktopModeEnabled: Boolean
        get() = prefs.getBoolean("desktop_mode_enabled", false)
        set(v) = prefs.edit { putBoolean("desktop_mode_enabled", v) }

    // Privacy / Security
    var savePasswordPromptEnabled: Boolean
        get() = prefs.getBoolean("save_password_prompt", true)
        set(v) = prefs.edit { putBoolean("save_password_prompt", v) }
    var passwordAccessAuthEnabled: Boolean
        get() = prefs.getBoolean("password_access_auth", false)
        set(v) = prefs.edit { putBoolean("password_access_auth", v) }
    var passwordFillAuthEnabled: Boolean
        get() = prefs.getBoolean("password_fill_auth", false)
        set(v) = prefs.edit { putBoolean("password_fill_auth", v) }
    var adBlockEnabled: Boolean
        get() = prefs.getBoolean("ad_block_enabled", true)
        set(v) = prefs.edit { putBoolean("ad_block_enabled", v) }
    var clearDataOnExitMask: Int
        get() = prefs.getInt("clear_data_on_exit_mask", 0)
        set(v) = prefs.edit { putInt("clear_data_on_exit_mask", v) }
    var ignoreSslWarnings: Boolean
        get() = prefs.getBoolean("ignore_ssl_warnings", false)
        set(v) = prefs.edit { putBoolean("ignore_ssl_warnings", v) }

    // Downloads
    var downloadDir: String
        get() = prefs.getString("download_dir", "") ?: ""
        set(v) = prefs.edit { putString("download_dir", v) }
    var downloadOverWifiOnly: Boolean
        get() = prefs.getBoolean("download_wifi_only", false)
        set(v) = prefs.edit { putBoolean("download_wifi_only", v) }
    var askBeforeDownload: Boolean
        get() = prefs.getBoolean("ask_before_download", true)
        set(v) = prefs.edit { putBoolean("ask_before_download", v) }

    // Reading mode
    var readerTextSize: Int
        get() = prefs.getInt("reader_text_size", 17).coerceIn(12, 32)
        set(v) = prefs.edit { putInt("reader_text_size", v.coerceIn(12, 32)) }
    /** 0=light, 1=sepia, 2=dark, 3=black */
    var readerTheme: Int
        get() = prefs.getInt("reader_theme", 0).coerceIn(0, 3)
        set(v) = prefs.edit { putInt("reader_theme", v.coerceIn(0, 3)) }

    // ── 朗读模式（新增）──────────────────────────────────────────────────
    /** TTS 语速，0.25–2.0，默认 1.0 */
    var readAloudSpeed: Float
        get() = prefs.getFloat("read_aloud_speed", 1.0f).coerceIn(0.25f, 2.0f)
        set(v) = prefs.edit { putFloat("read_aloud_speed", v.coerceIn(0.25f, 2.0f)) }
    /** TTS 音调 */
    var readAloudPitch: Float
        get() = prefs.getFloat("read_aloud_pitch", 1.0f).coerceIn(0.5f, 2.0f)
        set(v) = prefs.edit { putFloat("read_aloud_pitch", v.coerceIn(0.5f, 2.0f)) }

    // ── 搜索引擎自定义列表（设置页用，与底部栏隔离）──────────────────────
    var searchEngineCustomList: String
        get() = prefs.getString("search_engine_custom_list", "") ?: ""
        set(v) = prefs.edit { putString("search_engine_custom_list", v) }

    // ── 下载分类自定义（新增）────────────────────────────────────────────
    var downloadCategoryList: String
        get() = prefs.getString("download_category_list", "") ?: ""
        set(v) = prefs.edit { putString("download_category_list", v) }

    // ── 搜索引擎快捷切换（新增）──────────────────────────────────────────
    var searchEngineQuickSwitchEnabled: Boolean
        get() = prefs.getBoolean("search_engine_quick_switch_enabled", true)
        set(v) = prefs.edit { putBoolean("search_engine_quick_switch_enabled", v) }
    var searchEngineQuickSwitchList: String
        get() = prefs.getString("search_engine_quick_switch_list", "") ?: ""
        set(v) = prefs.edit { putString("search_engine_quick_switch_list", v) }

    // ── 视频嗅探（新增）──────────────────────────────────────────────────
    var videoSnifferEnabled: Boolean
        get() = prefs.getBoolean("video_sniffer_enabled", true)
        set(v) = prefs.edit { putBoolean("video_sniffer_enabled", v) }
    /** 用户指定的外部视频播放器包名，空=系统默认 */
    var externalVideoPlayer: String
        get() = prefs.getString("external_video_player", "") ?: ""
        set(v) = prefs.edit { putString("external_video_player", v) }

    // ── 手势工具栏（新增）────────────────────────────────────────────────
    /** 左滑手势动作 ID，见 GestureAction */
    var gestureLeft: Int
        get() = prefs.getInt("gesture_left", GestureAction.BACK)
        set(v) = prefs.edit { putInt("gesture_left", v) }
    var gestureRight: Int
        get() = prefs.getInt("gesture_right", GestureAction.FORWARD)
        set(v) = prefs.edit { putInt("gesture_right", v) }
    var gestureUp: Int
        get() = prefs.getInt("gesture_up", GestureAction.SCROLL_TOP)
        set(v) = prefs.edit { putInt("gesture_up", v) }
    var gestureDown: Int
        get() = prefs.getInt("gesture_down", GestureAction.SCROLL_BOTTOM)
        set(v) = prefs.edit { putInt("gesture_down", v) }

    // Misc / stats
    var blockedAdsCount: Int
        get() = prefs.getInt("blocked_ads_count", 0)
        set(v) = prefs.edit { putInt("blocked_ads_count", v) }
    var savedDataBytes: Long
        get() = prefs.getLong("saved_data_bytes", 0L)
        set(v) = prefs.edit { putLong("saved_data_bytes", v) }
    var lastCleanTime: Long
        get() = prefs.getLong("last_clean_time", 0L)
        set(v) = prefs.edit { putLong("last_clean_time", v) }

    object ClearDataFlag {
        const val HISTORY = 1
        const val COOKIES = 2
        const val CACHE = 4
        const val FORM_DATA = 8
        const val PASSWORDS = 16
        const val DOWNLOADS = 32
    }

    object GestureAction {
        const val NONE = 0
        const val BACK = 1
        const val FORWARD = 2
        const val REFRESH = 3
        const val NEW_TAB = 4
        const val CLOSE_TAB = 5
        const val SCROLL_TOP = 6
        const val SCROLL_BOTTOM = 7
        const val HOME = 8

        fun label(id: Int) = when (id) {
            NONE -> "无操作"
            BACK -> "后退"
            FORWARD -> "前进"
            REFRESH -> "刷新"
            NEW_TAB -> "新建标签"
            CLOSE_TAB -> "关闭标签"
            SCROLL_TOP -> "回到顶部"
            SCROLL_BOTTOM -> "到达底部"
            HOME -> "主页"
            else -> "无操作"
        }

        val all = listOf(NONE, BACK, FORWARD, REFRESH, NEW_TAB, CLOSE_TAB, SCROLL_TOP, SCROLL_BOTTOM, HOME)
    }
}
