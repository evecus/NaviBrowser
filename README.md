# NaviBrowser

[![Build APK](https://github.com/YOUR_USERNAME/NaviBrowser/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/NaviBrowser/actions/workflows/build.yml)

基于 Android WebView (Chromium) 的轻量级浏览器，Kotlin 编写。

## ✅ 无需 Android Studio，直接上传到 GitHub 即可构建

CI 会自动生成 `gradle-wrapper.jar`，你只需要把代码上传到 GitHub，Actions 自动出 APK。

---

## 功能特性

- 🌐 **多标签页** — 列表式，支持无限标签
- 🔍 **搜索引擎快捷切换** — 底部栏一键切换（必应/谷歌/百度/搜狗/Yandex/DuckDuckGo）
- 🔑 **密码管理器** — AES-256-GCM + Android Keystore 加密，自动检测并填充
- ⬇️ **内置下载器** — 后台下载，通知栏进度
- 🏠 **主页快捷方式** — 自定义图标网格
- 🕵️ **无痕模式** — 不记录历史/Cookie/密码
- ⭐ **书签** / 📜 **历史记录**

---

## 上传到 GitHub（纯网页操作，无需任何本地工具）

### 方法一：GitHub 网页上传 ZIP

1. 打开 [github.com](https://github.com) 并登录
2. 点击右上角 **+** → **New repository**
3. 仓库名填 `NaviBrowser`，选 Public，**不要**勾选任何初始化选项，点 **Create**
4. 在空仓库页面，点击 **uploading an existing file**
5. 把 ZIP 解压后的所有文件拖进去（**注意**：要上传文件夹内的内容，不是 ZIP 本身）
6. 滚到底部，点 **Commit changes**
7. 进入 **Actions** 标签，等待 3~5 分钟构建完成
8. 点击构建记录 → 底部 **Artifacts** → 下载 APK

### 方法二：用 Git 命令行上传

```bash
cd NaviBrowser
git init
git add .
git commit -m "feat: initial commit"
git remote add origin https://github.com/你的用户名/NaviBrowser.git
git push -u origin main
```

---

## 下载 APK

- **每次推送**：Actions → 构建记录 → Artifacts（需登录 GitHub）
- **正式发布**：打 tag 后自动出现在 Releases 页面（无需登录即可下载）

```bash
# 发布正式版
git tag v1.0.0
git push origin v1.0.0
```

---

## 配置签名（可选）

不配置也能用，直接安装 unsigned APK 即可。如需签名：

在仓库 **Settings → Secrets and variables → Actions → New repository secret** 添加：

| Secret | 说明 |
|--------|------|
| `KEYSTORE_BASE64` | keystore 文件的 Base64 编码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_PASSWORD` | 密钥密码 |

生成 keystore（需要 Java）：
```bash
keytool -genkey -v -keystore navi.jks -alias navibrowser \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -i navi.jks | tr -d '\n'   # 输出填入 KEYSTORE_BASE64
```

---

## 技术栈

Kotlin · MVVM · Room · OkHttp · Glide · Android Keystore · AES-256-GCM · API 24+
