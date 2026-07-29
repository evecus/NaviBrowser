package com.navibrowser.ui.browser

import android.webkit.JavascriptInterface

class PasswordDetectorJs(
    private val onCredentialsDetected: (username: String, password: String) -> Unit
) {
    @JavascriptInterface
    fun onCredentialsFound(username: String, password: String) {
        if (username.isNotBlank() && password.isNotBlank()) {
            onCredentialsDetected(username, password)
        }
    }
}

// JS to inject into pages to detect form submissions
const val PASSWORD_DETECTION_JS = """
(function() {
    if (window.__naviPasswordInjected) return;
    window.__naviPasswordInjected = true;
    
    function scanForms() {
        var forms = document.querySelectorAll('form');
        forms.forEach(function(form) {
            if (form.__naviListening) return;
            form.__naviListening = true;
            form.addEventListener('submit', function(e) {
                var pwField = form.querySelector('input[type="password"]');
                if (!pwField || !pwField.value) return;
                var userField = form.querySelector('input[type="email"], input[type="text"], input[name*="user"], input[name*="email"], input[id*="user"], input[id*="email"]');
                var username = userField ? userField.value : '';
                if (window.PasswordDetector) {
                    window.PasswordDetector.onCredentialsFound(username, pwField.value);
                }
            }, true);
        });
    }
    
    scanForms();
    var observer = new MutationObserver(function() { scanForms(); });
    observer.observe(document.body || document.documentElement, { childList: true, subtree: true });
})();
"""

// JS to autofill credentials
fun buildAutofillJs(username: String, password: String): String = """
(function() {
    var pwField = document.querySelector('input[type="password"]');
    if (!pwField) return;
    var userField = document.querySelector('input[type="email"], input[type="text"], input[name*="user"], input[name*="email"], input[id*="user"], input[id*="email"]');
    function setVal(el, val) {
        var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        nativeInputValueSetter.call(el, val);
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
    }
    if (userField) setVal(userField, ${username.json()});
    setVal(pwField, ${password.json()});
})();
"""

private fun String.json() = "\"${this.replace("\\", "\\\\").replace("\"", "\\\"")}\""
