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
        if (prefs.savePasswordPromptEnabled) {
            _pendingCredentials.value = Triple(domain, username, password)
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

    suspend fun getPasswordForDomain(domain: String) = passwordRepo.getPassword(domain)

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
                  namespace: String = "", description: String = "", version: String = "1.0") {
        viewModelScope.launch { scriptRepo.add(name, code, matchPatterns, excludePatterns, namespace, description, version) }
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
