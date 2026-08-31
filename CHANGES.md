# NaviBrowser 修改说明

## 变更文件列表

### 新文件（需添加）
| 文件 | 说明 |
|------|------|
| `app/src/main/java/com/navibrowser/ui/settings/SettingsActivity.kt` | 设置独立页面（替换 BottomSheet） |
| `app/src/main/java/com/navibrowser/ui/download/DownloadManagerActivity.kt` | 下载管理独立页面 |
| `app/src/main/java/com/navibrowser/ui/download/DownloadListAdapter.kt` | 下载列表适配器（含重试/删除） |
| `app/src/main/java/com/navibrowser/ui/password/PasswordManagerActivity.kt` | 密码管理独立页面 |
| `app/src/main/java/com/navibrowser/ui/password/PasswordListAdapter.kt` | 密码列表适配器 |
| `app/src/main/res/layout/activity_settings.xml` | 设置页面布局 |
| `app/src/main/res/layout/activity_download_manager.xml` | 下载管理页面布局 |
| `app/src/main/res/layout/activity_password_manager.xml` | 密码管理页面布局 |
| `app/src/main/res/drawable/ic_navi_logo.xml` | 主页罗盘 Logo（矢量） |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 应用图标前景层（矢量罗盘） |
| `app/src/main/res/drawable/ic_launcher_background.xml` | 应用图标背景（深蓝） |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 自适应图标配置 |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | 自适应圆形图标配置 |

### 修改文件（覆盖原文件）
| 文件 | 修改内容 |
|------|---------|
| `app/src/main/AndroidManifest.xml` | 注册 3 个新 Activity，移除 foregroundServiceType |
| `app/src/main/java/com/navibrowser/ui/browser/BrowserActivity.kt` | 一键唤出输入法；地址栏智能显示；标签页按钮无数字；设置/下载/密码改为独立页面；菜单移除密码入口 |
| `app/src/main/java/com/navibrowser/util/UrlUtils.kt` | 智能识别 IP/域名直接访问 vs 搜索词；getAddressBarText() 搜索页显示搜索词 |
| `app/src/main/java/com/navibrowser/ui/download/DownloadService.kt` | 改用系统 DownloadManager（解决下载失败问题） |
| `app/src/main/res/layout/fragment_home.xml` | 主页用新 NaviBrowser 罗盘 Logo + 品牌名 |
| `app/src/main/res/layout/menu_bottom_sheet.xml` | 移除菜单中的密码管理器入口 |
| `app/src/main/res/layout/activity_browser.xml` | 标签页按钮 text 改为空字符串（不显示数字） |
| `app/src/main/res/layout/item_download.xml` | 添加重试、删除按钮 |

---

## 核心修改说明

### 1. 图标设计
- 采用**罗盘/导航仪**主题，深蓝 `#1A5CCC` 背景
- 白色北指针 + 浅蓝南指针，体现导航定位
- 使用 Android 自适应图标（`ic_launcher_foreground.xml` + `ic_launcher_background.xml`）
- 主页同步使用 `ic_navi_logo.xml` 矢量 Logo

### 2. 一键唤出输入法
- `tvTitle.setOnClickListener` 内调用 `showKeyboardImmediately()`
- 使用 `InputMethodManager.SHOW_FORCED` 强制弹出，无需二次点击

### 3. 智能 URL 识别
`UrlUtils.processInput()` 规则（优先级从高到低）：
1. 已有 `http://` 或 `https://` → 直接使用
2. IPv4 格式（`x.x.x.x`、`x.x.x.x:port`）→ 补 `http://`
3. 域名格式（含点、合法字符）→ 补 `https://`
4. 其余 → 调用搜索引擎

地址栏显示逻辑（`UrlUtils.getAddressBarText()`）：
- 搜索页面 → 显示搜索词
- 直接访问的 IP/网址 → 显示完整 URL
- 聚焦编辑时 → 显示原始完整 URL（方便修改）

### 4. 标签页按钮去数字
`updateTabCount()` 将 `btnTabs.text = ""` 而非数字

### 5. 独立页面
- 设置、下载管理、密码管理均改为 `AppCompatActivity`（全屏）
- 菜单中密码管理器入口移至 **设置 → 密码管理器**

### 6. 下载修复
原 OkHttp 下载失败原因：目标站检查 User-Agent/Cookie/HTTPS 重定向
新方案使用**系统 DownloadManager**：
- 自动处理重定向、Cookie、HTTPS
- 后台可靠执行，有系统通知
- 下载失败时可在下载管理页点击"重试"
