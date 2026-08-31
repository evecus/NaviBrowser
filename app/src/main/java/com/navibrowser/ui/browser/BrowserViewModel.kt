package com.navibrowser.ui.browser

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.*
import com.navibrowser.data.db.AppDatabase
import com.navibrowser.data.model.*
import com.navibrowser.data.repository.BrowserRepository
import com.navibrowser.data.repository.PasswordRepository
import com.navibrowser.data.repository.ScriptRepository
import com.navibrowser.util.PrefsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val browserRepo = BrowserRepository(db.bookmarkDao(), db.historyDao(), db.shortcutDao())
    val passwordRepo = PasswordRepository(db.passwordDao())
    val scriptRepo = ScriptRepository(db.userScriptDao())
    val prefs = PrefsManager(application)

    val bookmarks = browserRepo.bookmarks.asLiveData()
    val shortcuts = browserRepo.shortcuts.asLiveData()
    val passwords = passwordRepo.passwords.asLiveData()
    val userScripts = scriptRepo.scripts.asLiveData()

    private val _currentUrl = MutableLiveData<String>("")
    val currentUrl: LiveData<String> = _currentUrl

    private val _currentTitle = MutableLiveData<String>("新标签页")
    val currentTitle: LiveData<String> = _currentTitle

    private val _progress = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = _progress

    private val _tabs = MutableLiveData<List<WebViewManager.Tab>>(emptyList())
    val tabs: LiveData<List<WebViewManager.Tab>> = _tabs

    private val _isBookmarked = MutableLiveData<Boolean>(false)
    val isBookmarked: LiveData<Boolean> = _isBookmarked

    private val _selectedSearchEngine = MutableLiveData<Int>(0)
    val selectedSearchEngine: LiveData<Int> = _selectedSearchEngine

    private val _pendingCredentials = MutableLiveData<Triple<String, String, String>?>()
    val pendingCredentials: LiveData<Triple<String, String, String>?> = _pendingCredentials

    /** 当前站点已保存的凭据，用于驱动“填充”按钮的显示。null 表示无可用凭据。 */
    private val _savedCredentialForSite = MutableLiveData<SavedPassword?>(null)
    val savedCredentialForSite: LiveData<SavedPassword?> = _savedCredentialForSite

    /** 当前站点命中的全部已保存凭据（含子域名），用于多账号选择。 */
    private val _savedCredentialsForSite = MutableLiveData<List<SavedPassword>>(emptyList())
    val savedCredentialsForSite: LiveData<List<SavedPassword>> = _savedCredentialsForSite

    /** 自动填充事件：页面加载完成后若开启自动填充，则触发一次填充。 */
    private val _autoFillRequest = MutableLiveData<SavedPassword?>(null)
    val autoFillRequest: LiveData<SavedPassword?> = _autoFillRequest

    init {
        _selectedSearchEngine.value = prefs.selectedSearchEngineIndex
    }

    fun setUrl(url: String) {
        _currentUrl.value = url
        viewModelScope.launch {
            _isBookmarked.value = browserRepo.isBookmarked(url)
        }
    }

    fun setTitle(title: String) { _currentTitle.value = title }
    fun setProgress(progress: Int) { _progress.value = progress }
    fun setTabs(tabs: List<WebViewManager.Tab>) { _tabs.value = tabs }

    fun selectSearchEngine(index: Int) {
        _selectedSearchEngine.value = index
        prefs.selectedSearchEngineIndex = index
    }

    fun addToHistory(title: String, url: String) {
        viewModelScope.launch { browserRepo.addHistory(title, url) }
    }

    fun toggleBookmark(title: String, url: String) {
        viewModelScope.launch {
            val existing = browserRepo.isBookmarked(url)
            if (existing) {
                val list = browserRepo.bookmarks.first()
                list.find { it.url == url }?.let { browserRepo.removeBookmark(it) }
            } else {
                browserRepo.addBookmark(title, url)
            }
            _isBookmarked.value = !existing
        }
    }

    fun onCredentialsDetected(domain: String, username: String, password: String) {
        viewModelScope.launch {
            // 若已存在同 (domain, username) 且密码相同，则无需提示
            val existing = passwordRepo.findForUpdate(domain, username)
            if (existing != null) {
                val savedPwd = try { com.navibrowser.security.CryptoManager.decrypt(existing.encryptedPassword) } catch (_: Exception) { null }
                if (savedPwd == password) {
                    _pendingCredentials.value = null
                    return@launch
                }
                // 密码不同 → 触发“更新”提示
                if (prefs.passwordUpdatePromptEnabled) {
                    _pendingCredentials.value = Triple(domain, username, password)
                }
            } else if (prefs.savePasswordPromptEnabled) {
                _pendingCredentials.value = Triple(domain, username, password)
            }
        }
    }

    fun savePassword(domain: String, username: String, password: String) {
        viewModelScope.launch { passwordRepo.savePassword(domain, username, password) }
        _pendingCredentials.value = null
    }

    fun updatePassword(id: Long, domain: String, username: String, password: String) {
        viewModelScope.launch { passwordRepo.updatePassword(id, domain, username, password) }
    }

    fun dismissPasswordPrompt() { _pendingCredentials.value = null }

    /** 精确域名查询，用于“填充”等场景。 */
    suspend fun getPasswordForDomain(domain: String) = passwordRepo.getPassword(domain)

    /**
     * 查询当前 URL 是否有匹配的已保存凭据。
     * 支持子域名匹配：保存 example.com 的密码，访问 login.example.com 时也能匹配。
     * 结果会更新 [savedCredentialForSite] 与 [savedCredentialsForSite] LiveData，供 UI 显示填充按钮。
     */
    fun refreshSavedCredentialForCurrentUrl() {
        val url = _currentUrl.value ?: return
        val host = com.navibrowser.util.UrlUtils.getDomain(url) ?: run {
            _savedCredentialForSite.value = null
            _savedCredentialsForSite.value = emptyList()
            _autoFillRequest.value = null
            return
        }
        viewModelScope.launch {
            val all = passwordRepo.findAllMatching(host)
            _savedCredentialsForSite.value = all
            val saved = all.firstOrNull()
            _savedCredentialForSite.value = saved
            // 自动填充：仅一条命中且用户开启了自动填充时触发
            if (saved != null && all.size == 1 && prefs.autoFillOnLoadEnabled) {
                _autoFillRequest.value = saved
            } else {
                _autoFillRequest.value = null
            }
        }
    }

    /** 标记自动填充请求已被消费。 */
    fun consumeAutoFillRequest() { _autoFillRequest.value = null }

    /** 解密一条已保存密码，返回 (username, password) */
    suspend fun decryptSaved(saved: SavedPassword): Pair<String, String> =
        Pair(saved.username, com.navibrowser.security.CryptoManager.decrypt(saved.encryptedPassword))

    fun addShortcut(title: String, url: String) {
        viewModelScope.launch { browserRepo.addShortcut(title, url) }
    }

    fun removeShortcut(shortcut: HomeShortcut) {
        viewModelScope.launch { browserRepo.removeShortcut(shortcut) }
    }

    fun clearHistory() {
        viewModelScope.launch { browserRepo.clearHistory() }
    }

    fun deletePassword(id: Long) {
        viewModelScope.launch { passwordRepo.deletePassword(id) }
    }

    // ------------------------------------------------------------------
    // Clear-data helpers
    // ------------------------------------------------------------------
    fun clearCache() {
        viewModelScope.launch {
            // WebView cache must be cleared on UI thread via the manager; here we just
            // signal callers. For simplicity, clear cookies via CookieManager.
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun clearAllPasswords() {
        viewModelScope.launch { passwordRepo.deleteAll() }
    }

    /** Apply the clear-data mask immediately. */
    // ------------------------------------------------------------------
    // UserScript helpers
    // ------------------------------------------------------------------
    fun addScript(name: String, code: String, matchPatterns: String, excludePatterns: String = "",
                  namespace: String = "", description: String = "", version: String = "1.0",
                  runAt: String = "document-idle", grants: String = "") {
        viewModelScope.launch {
            scriptRepo.add(name, code, matchPatterns, excludePatterns, namespace, description, version, runAt, grants)
        }
    }

    fun updateScript(script: UserScript) {
        viewModelScope.launch { scriptRepo.update(script) }
    }

    fun deleteScript(id: Long) {
        viewModelScope.launch { scriptRepo.delete(id) }
    }

    fun setScriptEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { scriptRepo.setEnabled(id, enabled) }
    }

    fun applyClearData(mask: Int) {
        viewModelScope.launch {
            if (mask and PrefsManager.ClearDataFlag.HISTORY != 0) browserRepo.clearHistory()
            if (mask and PrefsManager.ClearDataFlag.COOKIES != 0) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            if (mask and PrefsManager.ClearDataFlag.PASSWORDS != 0) passwordRepo.deleteAll()
            prefs.lastCleanTime = System.currentTimeMillis()
        }
    }
}
