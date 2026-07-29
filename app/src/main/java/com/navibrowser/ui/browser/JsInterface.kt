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

/**
 * 注入到页面中的密码检测脚本。
 *
 * 设计要点：
 * 1. 不在每次按键时弹窗（避免一堆对话框堆叠），只在以下时机回调：
 *    - 表单 submit 时
 *    - 点击 type=submit 的按钮 / button 时
 *    - 用户停止输入超过 1.2 秒（用于无 form 的 SPA 登录页）
 *    - password 失焦且非空时
 * 2. 同一组 (username, password) 在未变化前只回调一次，避免重复弹窗。
 * 3. 用户名检测策略：优先 type=email / type=text / autocomplete=username / name 含 user/email/account。
 */
const val PASSWORD_DETECTION_JS = """
(function() {
    if (window.__naviPwInjected) return;
    window.__naviPwInjected = true;

    var lastUser = '', lastPw = '';
    var debounceTimer = null;
    var reported = false;  // 同一组凭据是否已上报

    function isVisible(el) {
        if (!el) return false;
        // offsetParent 对 display:none 返回 null，但 fixed 元素也返回 null，所以再补充检查
        if (el.offsetParent === null && getComputedStyle(el).position !== 'fixed') return false;
        if (el.disabled || el.readOnly) return false;
        var rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    }

    function pickUsernameField(container) {
        // 1. 显式标记为 username 的
        var explicit = container.querySelector(
            'input[autocomplete="username"], input[autocomplete="email"], ' +
            'input[name="user"], input[name="username"], input[name="email"], ' +
            'input[name="account"], input[name="userid"], input[name="login"], ' +
            'input[id="user"], input[id="username"], input[id="email"], ' +
            'input[id="account"], input[id="userid"], input[id="login"]'
        );
        if (explicit && isVisible(explicit)) return explicit;
        // 2. type=email
        var email = container.querySelector('input[type="email"]');
        if (email && isVisible(email)) return email;
        // 3. 任何可见的非 password/hidden/button 类 input
        var inputs = container.querySelectorAll(
            'input:not([type="password"]):not([type="hidden"]):not([type="submit"])' +
            ':not([type="button"]):not([type="checkbox"]):not([type="radio"]):not([type="file"])'
        );
        for (var i = 0; i < inputs.length; i++) {
            if (isVisible(inputs[i])) return inputs[i];
        }
        return null;
    }

    function pickPasswordField(container) {
        var pws = container.querySelectorAll('input[type="password"]');
        // 跳过隐藏的密码框，选第一个可见的
        for (var i = 0; i < pws.length; i++) {
            if (isVisible(pws[i])) return pws[i];
        }
        return null;
    }

    function report(u, p) {
        if (!u || !p) return;
        if (u === lastUser && p === lastPw && reported) return;
        lastUser = u; lastPw = p; reported = true;
        if (window.__naviPwDetector) window.__naviPwDetector.onCredentialsFound(u, p);
    }

    function scheduleDebouncedReport(user, pw) {
        if (!user || !pw) { reported = false; return; }
        // 凭据变化时重置 reported，允许再次上报
        if (user.value !== lastUser || pw.value !== lastPw) reported = false;
        if (debounceTimer) clearTimeout(debounceTimer);
        debounceTimer = setTimeout(function() {
            report(user.value, pw.value);
        }, 1200);
    }

    function attach(container) {
        var pw = pickPasswordField(container);
        if (!pw) return;
        var user = pickUsernameField(container);
        if (!user) return;

        if (!pw.__naviPwBound) {
            pw.__naviPwBound = true;
            pw.addEventListener('input', function() {
                reported = false;
                scheduleDebouncedReport(user, pw);
            });
            pw.addEventListener('change', function() {
                scheduleDebouncedReport(user, pw);
            });
            pw.addEventListener('blur', function() {
                if (pw.value && user.value) {
                    if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null; }
                    report(user.value, pw.value);
                }
            });
        }
        if (!user.__naviPwBound) {
            user.__naviPwBound = true;
            user.addEventListener('input', function() {
                reported = false;
                scheduleDebouncedReport(user, pw);
            });
            user.addEventListener('change', function() {
                scheduleDebouncedReport(user, pw);
            });
        }

        // 绑定 form submit
        var form = pw.closest('form');
        if (form && !form.__naviPwForm) {
            form.__naviPwForm = true;
            form.addEventListener('submit', function() {
                if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null; }
                report(user.value, pw.value);
            });
        }
        // 绑定 submit 按钮 / 任何 button[type=submit] / input[type=submit]
        var btns = container.querySelectorAll(
            'button[type="submit"], input[type="submit"], button:not([type])'
        );
        for (var i = 0; i < btns.length; i++) {
            var b = btns[i];
            if (b.__naviPwBtn) continue;
            b.__naviPwBtn = true;
            b.addEventListener('click', function() {
                if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null; }
                report(user.value, pw.value);
            });
        }
    }

    attach(document);

    // SPA 页面可能动态插入 form，监听 DOM 变化重新检测
    var mo = new MutationObserver(function() { attach(document); });
    mo.observe(document.documentElement, { childList: true, subtree: true });

    // 暴露手动触发接口，供原生菜单“保存此网站密码”调用
    window.__naviExtractCredentials = function() {
        var pw = pickPasswordField(document);
        if (!pw) return null;
        var user = pickUsernameField(document);
        return { username: user ? user.value : '', password: pw.value };
    };
})();
"""

/**
 * 自动填充 JS。改进点：
 * - 通过 React/Vue 兼容的方式触发 input 事件（使用原生 setter + InputEvent + change）。
 * - 兼容 type=email / type=text 的用户名框。
 * - 多个密码框时只填第一个可见的（避免改密页污染）。
 */
fun buildAutofillJs(username: String, password: String): String = """
(function() {
    function isVisible(el) {
        if (!el) return false;
        if (el.offsetParent === null && getComputedStyle(el).position !== 'fixed') return false;
        if (el.disabled || el.readOnly) return false;
        var rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    }
    function setVal(el, val) {
        var proto = window.HTMLInputElement.prototype;
        var setter = Object.getOwnPropertyDescriptor(proto, 'value');
        if (setter && setter.set) {
            setter.set.call(el, val);
        } else {
            el.value = val;
        }
        // React 16+ 监听 InputEvent，Vue 监听 input 事件
        try { el.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: val })); }
        catch (e) { el.dispatchEvent(new Event('input', { bubbles: true })); }
        el.dispatchEvent(new Event('change', { bubbles: true }));
        // 某些框架（如 Angular）需要 blur 才能更新 model
        el.dispatchEvent(new Event('blur', { bubbles: true }));
    }
    var pws = document.querySelectorAll('input[type="password"]');
    var pw = null;
    for (var i = 0; i < pws.length; i++) { if (isVisible(pws[i])) { pw = pws[i]; break; } }
    if (!pw) return;

    // 用户名框：优先显式标记，其次 type=email，最后任一可见文本框
    function pickUser() {
        var explicit = document.querySelector(
            'input[autocomplete="username"], input[autocomplete="email"], ' +
            'input[name="user"], input[name="username"], input[name="email"], ' +
            'input[name="account"], input[name="userid"], input[name="login"]'
        );
        if (explicit && isVisible(explicit)) return explicit;
        var em = document.querySelector('input[type="email"]');
        if (em && isVisible(em)) return em;
        var inputs = document.querySelectorAll(
            'input:not([type="password"]):not([type="hidden"]):not([type="submit"])' +
            ':not([type="button"]):not([type="checkbox"]):not([type="radio"]):not([type="file"])'
        );
        for (var j = 0; j < inputs.length; j++) {
            if (isVisible(inputs[j])) return inputs[j];
        }
        return null;
    }
    var user = pickUser();
    if (user) setVal(user, ${username.json()});
    setVal(pw, ${password.json()});
    if (pw.focus) pw.focus();
})();
"""

private fun String.json() = "\"${this.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")}\""
