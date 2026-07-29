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

const val PASSWORD_DETECTION_JS = """
(function() {
    if (window.__naviPwInjected) return;
    window.__naviPwInjected = true;

    var lastUser = '', lastPw = '';

    function detect(container) {
        var pws = container.querySelectorAll('input[type="password"]');
        if (!pws.length) return;
        var pw = pws[0];
        var user = null;
        var inputs = container.querySelectorAll('input:not([type="password"]):not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"])');
        for (var i = 0; i < inputs.length; i++) {
            var el = inputs[i];
            if (el.offsetParent !== null) {
                user = el; break;
            }
        }
        if (!user) return;
        function check() {
            var u = user.value;
            var p = pw.value;
            if (p && (u !== lastUser || p !== lastPw)) {
                lastUser = u; lastPw = p;
                if (window.__naviPwDetector)
                    window.__naviPwDetector.onCredentialsFound(u, p);
            }
        }
        pw.addEventListener('input', check);
        pw.addEventListener('change', check);
        user.addEventListener('input', check);
        user.addEventListener('change', check);
        var form = pw.closest('form');
        if (form && !form.__naviPwForm) {
            form.__naviPwForm = true;
            form.addEventListener('submit', function() {
                if (window.__naviPwDetector)
                    window.__naviPwDetector.onCredentialsFound(user.value, pw.value);
            });
        }
        var btn = container.querySelector('button[type="submit"], input[type="submit"]');
        if (btn && !btn.__naviPwBtn) {
            btn.__naviPwBtn = true;
            btn.addEventListener('click', function() {
                if (window.__naviPwDetector)
                    window.__naviPwDetector.onCredentialsFound(user.value, pw.value);
            });
        }
    }
    detect(document);
    new MutationObserver(function() { detect(document); })
        .observe(document.documentElement, { childList: true, subtree: true });
})();
"""

fun buildAutofillJs(username: String, password: String): String = """
(function() {
    var pws = document.querySelectorAll('input[type="password"]');
    if (!pws.length) return;
    var pw = pws[0];
    var inputs = document.querySelectorAll('input:not([type="password"]):not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="checkbox"]):not([type="radio"])');
    var user = null;
    for (var i = 0; i < inputs.length; i++) {
        if (inputs[i].offsetParent !== null) { user = inputs[i]; break; }
    }
    function setVal(el, val) {
        var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(el, val);
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
    }
    if (user) setVal(user, ${username.json()});
    setVal(pw, ${password.json()});
})();
"""

private fun String.json() = "\"${this.replace("\\", "\\\\").replace("\"", "\\\"")}\""