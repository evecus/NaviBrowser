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
 * 视频嗅探 JS 桥接：页面内扫描到的视频资源 URL 通过 onVideoFound 回调到原生。
 */
class VideoScannerJs(
    private val onVideoFound: (url: String, mimeType: String) -> Unit
) {
    @JavascriptInterface
    fun onVideoFound(url: String, mimeType: String) {
        if (url.isNotBlank()) onVideoFound(url, mimeType)
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
 * 3. 用户名检测策略：优先 autocomplete=username / type=email / name 与 id 含 user/email/account/login。
 * 4. 跳过改密场景：同一 form 内出现 type=new-password 或多个 password 字段（注册/改密页）时不再自动捕获，
 *    避免把“新密码”误存为登录密码。手动保存仍可用。
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

    // 判断某容器是否为“登录场景”：仅一个可见 password 框且无 new-password。
    // 多 password 框（注册/改密）或出现 new-password 时跳过自动捕获。
    function isLoginForm(container) {
        var pws = container.querySelectorAll('input[type="password"]');
        var visibleCount = 0;
        for (var i = 0; i < pws.length; i++) { if (isVisible(pws[i])) visibleCount++; }
        if (visibleCount === 0) return false;
        if (visibleCount > 1) return false;  // 多密码框：注册/改密
        var hasNew = container.querySelector(
            'input[autocomplete*="new"], input[autocomplete*="new-password"], ' +
            'input[name*="new"], input[id*="new"], input[name*="confirm"], input[id*="confirm"]');
        if (hasNew) return false;
        return true;
    }

    function pickUsernameField(container) {
        // 1. 显式标记为 username 的
        var explicit = container.querySelector(
            'input[autocomplete="username"], input[autocomplete="email"], ' +
            'input[autocomplete*="user"], input[autocomplete*="email"], ' +
            'input[name="user"], input[name="username"], input[name="email"], ' +
            'input[name="account"], input[name="userid"], input[name="login"], ' +
            'input[name="mail"], input[id="user"], input[id="username"], ' +
            'input[id="email"], input[id="account"], input[id="userid"], ' +
            'input[id="login"], input[id="mail"]'
        );
        if (explicit && isVisible(explicit)) return explicit;
        // 2. type=email
        var email = container.querySelector('input[type="email"]');
        if (email && isVisible(email)) return email;
        // 3. name/id 模糊匹配 user/email/account/login
        var fuzzy = container.querySelectorAll(
            'input[name], input[id]'
        );
        for (var i = 0; i < fuzzy.length; i++) {
            var el = fuzzy[i];
            if (!isVisible(el)) continue;
            var t = (el.name + ' ' + el.id + ' ' + (el.placeholder || '')).toLowerCase();
            if (/\b(user|email|account|login|mail|userid|username)\b/.test(t)) return el;
        }
        // 4. 任何可见的非 password/hidden/button 类 input
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
        if (!isLoginForm(container)) return;
        var pw = pickPasswordField(container);
        if (!pw) return;
        var user = pickUsernameField(container);

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
                if (pw.value && user && user.value) {
                    if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null; }
                    report(user.value, pw.value);
                }
            });
        }
        if (user && !user.__naviPwBound) {
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
                report(user ? user.value : '', pw.value);
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
                report(user ? user.value : '', pw.value);
            });
        }
    }

    attach(document);

    // SPA 页面可能动态插入 form，监听 DOM 变化重新检测
    var mo = new MutationObserver(function() { attach(document); });
    mo.observe(document.documentElement, { childList: true, subtree: true });

    // 暴露手动触发接口，供原生菜单“保存此网站密码”调用
    window.__naviExtractCredentials = function() {
        // 手动场景放宽：即便多密码框也允许提取第一个（用户可改）
        var pw = pickPasswordField(document);
        if (!pw) return null;
        var user = pickUsernameField(document);
        return { username: user ? user.value : '', password: pw.value };
    };
    // 暴露“是否检测到可见的 password 字段”，供自动填充判断页面是否需要填充
    window.__naviHasPasswordField = function() {
        return !!pickPasswordField(document);
    };
})();
"""

/**
 * 自动填充 JS。改进点：
 * - 通过 React/Vue 兼容的方式触发 input 事件（使用原生 setter + InputEvent + change）。
 * - 兼容 type=email / type=text 的用户名框。
 * - 多个密码框时只填第一个可见的（避免改密页污染）。
 * - 用户名检测与检测脚本保持一致（模糊匹配 name/id/placeholder）。
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

    // 用户名框：优先显式标记，其次 type=email，再次模糊匹配，最后任一可见文本框
    function pickUser() {
        var explicit = document.querySelector(
            'input[autocomplete="username"], input[autocomplete="email"], ' +
            'input[autocomplete*="user"], input[autocomplete*="email"], ' +
            'input[name="user"], input[name="username"], input[name="email"], ' +
            'input[name="account"], input[name="userid"], input[name="login"], ' +
            'input[name="mail"], input[id="user"], input[id="username"], ' +
            'input[id="email"], input[id="account"], input[id="userid"], ' +
            'input[id="login"], input[id="mail"]'
        );
        if (explicit && isVisible(explicit)) return explicit;
        var em = document.querySelector('input[type="email"]');
        if (em && isVisible(em)) return em;
        var fuzzy = document.querySelectorAll('input[name], input[id]');
        for (var k = 0; k < fuzzy.length; k++) {
            var el = fuzzy[k];
            if (!isVisible(el)) continue;
            var t = (el.name + ' ' + el.id + ' ' + (el.placeholder || '')).toLowerCase();
            if (/\b(user|email|account|login|mail|userid|username)\b/.test(t)) return el;
        }
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

/**
 * 视频嗅探 JS（document-start 注入）。
 *
 * 策略：
 * 1. 扫描 DOM 中的 <video>/<source>/<embed>/<object>/<iframe>，上报其 src（含 data-src）。
 * 2. hook URL.createObjectURL，捕获 blob: 视频 URL（标记为 video/blob）。
 * 3. hook HTMLMediaElement.prototype.play 与 source.src setter，捕获动态设置的媒体源。
 * 4. MutationObserver 监听后续插入的媒体元素，避免遗漏 SPA 动态加载。
 * 所有命中去重后通过 window.__naviVideoScanner.onVideoFound(url, mime) 回调原生。
 */
const val VIDEO_SNIFFER_JS = """
(function() {
    if (window.__naviVideoInjected) return;
    window.__naviVideoInjected = true;

    var seen = {};
    function report(url, mime) {
        if (!url || typeof url !== 'string') return;
        if (url.indexOf('blob:') === 0) {
            // blob 无法下载，但仍可上报供播放尝试
        } else if (!/^https?:/i.test(url)) {
            return;
        }
        if (seen[url]) return;
        seen[url] = true;
        try { if (window.__naviVideoScanner) window.__naviVideoScanner.onVideoFound(url, mime || ''); } catch(e){}
    }

    function scanNode(root) {
        if (!root || !root.querySelectorAll) return;
        var nodes = root.querySelectorAll('video, source, embed, object, iframe');
        for (var i = 0; i < nodes.length; i++) {
            var el = nodes[i];
            var src = el.src || el.getAttribute('data-src') || el.getAttribute('data') || '';
            if (src) report(src, el.tagName.toLowerCase());
            var sources = el.querySelectorAll ? el.querySelectorAll('source') : [];
            for (var j = 0; j < sources.length; j++) {
                var s = sources[j].src || sources[j].getAttribute('data-src') || '';
                if (s) report(s, 'source');
            }
        }
    }

    function scanAll() {
        try { scanNode(document); } catch(e){}
    }

    // 1. 初始扫描（document-start 时 body 可能尚未就绪，稍后补扫）
    scanAll();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', scanAll, false);
    }

    // 2. 监听 DOM 变化
    try {
        var mo = new MutationObserver(function(muts) {
            for (var i = 0; i < muts.length; i++) {
                var added = muts[i].addedNodes;
                for (var j = 0; j < added.length; j++) {
                    var n = added[j];
                    if (n && n.nodeType === 1) scanNode(n);
                }
            }
        });
        mo.observe(document.documentElement || document, { childList: true, subtree: true });
    } catch(e){}

    // 3. hook URL.createObjectURL 捕获 blob 媒体
    try {
        var origCreate = URL.createObjectURL;
        URL.createObjectURL = function(obj) {
            var url = origCreate.apply(this, arguments);
            try {
                if (obj && (obj instanceof Blob || obj instanceof File)) {
                    // 仅对体积较大或 video/* 类型上报
                    var type = obj.type || '';
                    if (type.indexOf('video/') === 0 || type.indexOf('application/') === 0) {
                        report(url, type || 'video/blob');
                    } else if (obj.size && obj.size > 1024 * 512) {
                        report(url, type || 'video/blob');
                    }
                }
            } catch(e){}
            return url;
        };
    } catch(e){}

    // 4. hook HTMLMediaElement.play / .src setter 捕获动态媒体源
    try {
        var proto = window.HTMLMediaElement.prototype;
        var desc = Object.getOwnPropertyDescriptor(proto, 'src');
        if (desc && desc.set) {
            Object.defineProperty(proto, 'src', {
                set: function(v) { report(v, 'media'); return desc.set.call(this, v); },
                get: function() { return desc.get.call(this); },
                configurable: true
            });
        }
        var origPlay = proto.play;
        proto.play = function() {
            try { if (this.currentSrc) report(this.currentSrc, 'media'); } catch(e){}
            return origPlay.apply(this, arguments);
        };
    } catch(e){}
})();
"""
