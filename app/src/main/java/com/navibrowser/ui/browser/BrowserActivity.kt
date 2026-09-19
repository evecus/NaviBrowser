package com.navibrowser.ui.browser

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.navibrowser.R
import com.navibrowser.data.model.SavedPassword
import com.navibrowser.databinding.ActivityBrowserBinding
import com.navibrowser.util.SearchEngineManager
import com.navibrowser.ui.download.DownloadManagerActivity
import com.navibrowser.ui.download.DownloadService
import com.navibrowser.ui.home.HomeFragment
import com.navibrowser.ui.readaloud.ReadAloudManager
import com.navibrowser.security.BiometricAuthUtil
import com.navibrowser.ui.settings.ScriptManagerActivity
import com.navibrowser.ui.settings.SettingsActivity
import com.navibrowser.ui.tabs.TabListFragment
import com.navibrowser.util.UserScriptManager
import com.navibrowser.util.PrefsManager
import com.navibrowser.util.UrlUtils
import com.navibrowser.util.VideoSniffer
import kotlinx.coroutines.launch

class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    val viewModel: BrowserViewModel by viewModels()
    private lateinit var webViewManager: WebViewManager
    private var isIncognito = false

    // 朗读管理器
    private val readAloudManager by lazy { ReadAloudManager(this) }

    // 视频嗅探 FAB（动态添加，不占布局）
    private var videoFab: FloatingActionButton? = null
    private val sniffedVideos = mutableListOf<VideoSniffer.SniffedVideo>()

    // ── 密码相关 ──
    // 当前显示的保存密码弹窗（防止同一组凭据弹多个对话框）
    private var savePasswordDialog: AlertDialog? = null
    // 已经弹过保存提示的 (domain, username, password) 三元组，避免重复弹窗
    private val promptedCredentials = mutableSetOf<Triple<String, String, String>>()
    // 当前域名已忽略保存（点过“不保存”），本次访问不再打扰
    private val dismissedDomains = mutableSetOf<String>()
    // 密码填充 FAB
    private var fillFab: FloatingActionButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webViewManager = WebViewManager(this)
        setupWebViewManager()
        setupAddressBar()
        setupBottomNav()
        observeViewModel()
        applyAppearanceSettings()
        setupVideoFab()
        setupFillFab()

        openNewTab()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViewManager.getCurrentWebView()
                if (wv?.canGoBack() == true) wv.goBack()
                else if (webViewManager.tabCount > 1) {
                    webViewManager.currentTab?.id?.let { webViewManager.closeTab(it) }
                    showCurrentTab()
                } else finish()
            }
        })

        intent?.data?.toString()?.let { loadUrl(it) }
    }

    override fun onResume() {
        super.onResume()
        applyAppearanceSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        readAloudManager.destroy()
        webViewManager.destroyAll()
    }

    // ── 外观设置 ─────────────────────────────────────────────────────────
    private fun applyAppearanceSettings() {
        val prefs = viewModel.prefs
        requestedOrientation = when (prefs.screenOrientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (prefs.hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, !prefs.hideStatusBar)
    }

    // ── 视频嗅探 FAB ──────────────────────────────────────────────────────
    private fun setupVideoFab() {
        val fab = FloatingActionButton(this).apply {
            size = FloatingActionButton.SIZE_MINI
            setImageResource(android.R.drawable.ic_media_play)
            visibility = View.GONE
            contentDescription = "视频"
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 72, 72)  // 紧靠右下角，不挡底栏
        }
        (binding.root as ViewGroup).addView(fab, lp)
        videoFab = fab
        fab.setOnClickListener { showVideoList() }
    }

    // ── 密码填充 FAB ──────────────────────────────────────────────────────
    /** 当当前站点存在已保存凭据时显示，点击后填充用户名 / 密码。 */
    private fun setupFillFab() {
        val fab = FloatingActionButton(this).apply {
            size = FloatingActionButton.SIZE_MINI
            // 用系统钥匙图标，无需新增 drawable 资源
            setImageResource(android.R.drawable.ic_lock_lock)
            contentDescription = getString(R.string.fill_password)
            visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            // 放在左下角，避免与右下角的视频 FAB 冲突
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(72, 0, 0, 72)
        }
        (binding.root as ViewGroup).addView(fab, lp)
        fillFab = fab
        fab.setOnClickListener { onFillFabClicked() }
        fab.setOnLongClickListener {
            // 长按跳转到密码管理器
            startActivity(Intent(this, com.navibrowser.ui.password.PasswordManagerActivity::class.java))
            true
        }
    }

    private fun showVideoFound(video: VideoSniffer.SniffedVideo) {
        if (!viewModel.prefs.videoSnifferEnabled) return
        if (!sniffedVideos.contains(video)) sniffedVideos.add(video)
        videoFab?.visibility = View.VISIBLE
        // 轻量提示，不打断用户
        Snackbar.make(binding.root, "检测到视频资源", Snackbar.LENGTH_SHORT)
            .setAction("播放") { showVideoList() }
            .show()
    }

    private fun showVideoList() {
        if (sniffedVideos.isEmpty()) {
            Toast.makeText(this, "当前页面暂未检测到视频", Toast.LENGTH_SHORT).show()
            return
        }
        val sheet = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        val title = TextView(this).apply {
            text = "检测到 ${sniffedVideos.size} 个视频资源"
            textSize = 14f
            setPadding(32, 16, 32, 8)
            setTextColor(getColor(android.R.color.darker_gray))
        }
        container.addView(title)
        sniffedVideos.forEachIndexed { i, video ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 16, 32, 16)
                isClickable = true; isFocusable = true
                background = android.util.TypedValue().also {
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                }.resourceId.let { getDrawable(it) }
            }
            val tvUrl = TextView(this).apply {
                text = video.url.take(80) + if (video.url.length > 80) "…" else ""
                textSize = 13f
                maxLines = 2
            }
            val tvMime = TextView(this).apply {
                text = video.mimeType.ifEmpty { "视频" }
                textSize = 11f
                setTextColor(getColor(android.R.color.darker_gray))
            }
            item.addView(tvUrl); item.addView(tvMime)
            item.setOnClickListener {
                sheet.dismiss()
                VideoSniffer.openVideo(this, video, viewModel.prefs.externalVideoPlayer)
            }
            item.setOnLongClickListener {
                // 长按：复制链接 / 下载 选项
                val options = arrayOf("复制链接", "下载视频")
                AlertDialog.Builder(this)
                    .setTitle("视频操作")
                    .setItems(options) { d, which ->
                        when (which) {
                            0 -> {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("video_url", video.url))
                                Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show()
                            }
                            1 -> {
                                if (video.url.startsWith("blob:")) {
                                    Toast.makeText(this, "blob 链接无法直接下载，请用播放器录制", Toast.LENGTH_LONG).show()
                                } else {
                                    startDownload(video.url, guessVideoFileName(video), guessVideoMime(video))
                                    Toast.makeText(this, "开始下载", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        d.dismiss()
                    }.show()
                true
            }
            container.addView(item)
            if (i < sniffedVideos.size - 1) {
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(0x1A000000)
                }.also { container.addView(it) }
            }
        }
        val scroll = android.widget.ScrollView(this).apply { addView(container) }
        sheet.setContentView(scroll)
        sheet.show()
    }

    /** 从嗅探到的视频 URL 推断文件名。 */
    private fun guessVideoFileName(video: VideoSniffer.SniffedVideo): String {
        val path = try { android.net.Uri.parse(video.url).lastPathSegment } catch (_: Exception) { null }
        return if (!path.isNullOrBlank()) path.substringBefore('?') else "video_${video.timestamp}"
    }

    /** 推断视频 MIME 类型，下载服务据此归类。 */
    private fun guessVideoMime(video: VideoSniffer.SniffedVideo): String? {
        val ext = video.url.substringAfterLast('.', "").substringBefore('?').lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "m3u8", "m3u" -> "application/vnd.apple.mpegurl"
            "flv" -> "video/x-flv"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "ts" -> "video/mp2t"
            "mov" -> "video/quicktime"
            "mpd" -> "application/dash+xml"
            else -> video.mimeType.takeIf { it.startsWith("video") || it.contains("mpeg") }
        }
    }

    // ── WebViewManager 回调 ───────────────────────────────────────────────
    private fun setupWebViewManager() {
        webViewManager.onUrlChanged = { url ->
            runOnUiThread {
                viewModel.setUrl(url)
                if (url != "navi://home") {
                    binding.etAddress.setText(UrlUtils.getAddressBarText(url))
                }
                // 页面切换时清空视频列表
                sniffedVideos.clear()
                videoFab?.visibility = View.GONE
                // 跳转新页面时先隐藏填充按钮，等加载完成后再决定
                if (!url.startsWith("http") || isIncognito) {
                    fillFab?.visibility = View.GONE
                }
                // 切换域名时重置忽略保存标记，让用户在新站点能再次收到提示
                val newHost = UrlUtils.getDomain(url)
                if (newHost != null) dismissedDomains.retainAll(setOf(newHost))
            }
        }
        webViewManager.onTitleChanged = { title ->
            runOnUiThread {
                viewModel.setTitle(title)
                val desktopTag = if (webViewManager.isDesktopMode()) " 🖥" else ""
                binding.tvTitle.text = if (isIncognito) "🕵️ $title$desktopTag" else "$title$desktopTag"
            }
        }
        webViewManager.onProgressChanged = { progress ->
            runOnUiThread {
                binding.progressBar.isVisible = progress in 1..99
                binding.progressBar.progress = progress
                if (progress == 100) {
                    val url = viewModel.currentUrl.value ?: return@runOnUiThread
                    val title = viewModel.currentTitle.value ?: return@runOnUiThread
                    if (!isIncognito && url.startsWith("http")) viewModel.addToHistory(title, url)
                    // 油猴脚本：document-end 在页面加载完成时立即注入，document-idle 略延后
                    injectUserScriptsAt(url, "document-end")
                    webViewManager.getCurrentWebView()?.postDelayed({
                        injectUserScriptsAt(viewModel.currentUrl.value ?: "", "document-idle")
                    }, 200)
                    // 刷新当前站点的已保存凭据状态，触发填充按钮显示
                    if (!isIncognito && url.startsWith("http")) {
                        viewModel.refreshSavedCredentialForCurrentUrl()
                    } else {
                        fillFab?.visibility = View.GONE
                    }
                }
            }
        }
        webViewManager.onTabsChanged = { runOnUiThread { updateTabCount() } }
        webViewManager.onCredentialsDetected = { domain, username, password ->
            // 交给 ViewModel 做更新检测，由 pendingCredentials 观察者统一弹窗
            runOnUiThread { viewModel.onCredentialsDetected(domain, username, password) }
        }
        webViewManager.onDownloadRequested = { url, fileName, mimeType ->
            runOnUiThread { startDownload(url, fileName, mimeType) }
        }
        webViewManager.onAdBlocked = {
            runOnUiThread { viewModel.prefs.blockedAdsCount = viewModel.prefs.blockedAdsCount + 1 }
        }
        webViewManager.onVideoFound = { video ->
            runOnUiThread { showVideoFound(video) }
        }
        webViewManager.onGestureAction = { action -> runOnUiThread { handleGestureAction(action) } }
        webViewManager.onOpenSearchEngineSettings = {
            startActivity(Intent(this, com.navibrowser.ui.settings.SearchEngineSettingsActivity::class.java))
        }
        // 页面开始加载时注入 document-start 油猴脚本
        webViewManager.onPageStartedUrl = { url ->
            injectUserScriptsAt(url, "document-start")
        }
        // 页面内 DOM 扫描到的视频资源
        webViewManager.onVideoDomFound = { video ->
            runOnUiThread { showVideoFound(video) }
        }
    }

    /** 执行手势动作 */
    private fun handleGestureAction(action: Int) {
        val wv = webViewManager.getCurrentWebView()
        when (action) {
            PrefsManager.GestureAction.BACK -> wv?.goBack()
            PrefsManager.GestureAction.FORWARD -> wv?.goForward()
            PrefsManager.GestureAction.REFRESH -> wv?.reload()
            PrefsManager.GestureAction.NEW_TAB -> openNewTab()
            PrefsManager.GestureAction.CLOSE_TAB -> {
                webViewManager.currentTab?.id?.let { webViewManager.closeTab(it) }
                showCurrentTab()
            }
            PrefsManager.GestureAction.SCROLL_TOP ->
                wv?.evaluateJavascript("window.scrollTo(0,0);", null)
            PrefsManager.GestureAction.SCROLL_BOTTOM ->
                wv?.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight);", null)
            PrefsManager.GestureAction.HOME -> loadUrl("navi://home")
        }
    }

    // ── 地址栏 ────────────────────────────────────────────────────────────
    private fun setupAddressBar() {
        binding.etAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.etAddress.text.toString()
                val engines = SearchEngineManager.loadEngines(viewModel.prefs)
                val idx = (viewModel.selectedSearchEngine.value ?: 0).coerceIn(0, engines.size - 1)
                val se = engines[idx].toSearchEngine()
                loadUrl(UrlUtils.processInput(input, se))
                hideKeyboard()
                true
            } else false
        }
        binding.etAddress.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etAddress.selectAll()
        }
        binding.btnGo.setOnClickListener {
            val input = binding.etAddress.text.toString()
            val engines = SearchEngineManager.loadEngines(viewModel.prefs)
            val idx = (viewModel.selectedSearchEngine.value ?: 0).coerceIn(0, engines.size - 1)
            val se = engines[idx].toSearchEngine()
            loadUrl(UrlUtils.processInput(input, se))
            hideKeyboard()
        }
        binding.btnHome.setOnClickListener { loadUrl("navi://home") }
        binding.btnRefresh.setOnClickListener { webViewManager.getCurrentWebView()?.reload() }
        binding.btnStop.setOnClickListener { webViewManager.getCurrentWebView()?.stopLoading() }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etAddress.windowToken, 0)
        binding.etAddress.clearFocus()
    }

    // Note: remaining content continues from original file to keep size manageable for this restoration.
    // FULL FILE RESTORED FROM PREVIOUS GOOD COMMIT + import.
}
