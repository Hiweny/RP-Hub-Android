# RP Hub · Android APK

RP Hub（[在线地址](https://sta1n156.github.io/RP-Hub/)）的高性能 Android 壳应用。

APK **直接加载在线站点**，所以网页一更新，App 内容自动同步；同时在外壳层做了针对
手机端的全面优化，弥补浏览器/普通 WebView 的卡顿与功能缺失。

## ✨ 做了哪些优化

### 1. 全屏沉浸式显示
- Edge-to-edge：内容铺满整屏，状态栏 / 导航栏透明并隐藏，**顶部和底部没有白线**。
- 支持刘海 / 挖孔屏（cutout `shortEdges`），从屏幕边缘滑动可临时唤出系统栏。
- 窗口与 WebView 背景色随主题切换（浅色 `#f9fafb` / 深色 `#1e1e1e`），**消除启动白闪**。

### 2. 深色模式跟随系统
- 通过注入脚本 hook 页面主题逻辑：系统切换深色 / 浅色时，App 与网页同步切换。
- 强制关闭 WebView 的「算法深色化」，避免与网页自身的深色主题冲突（双重变暗）。

### 3. 流畅度优化（重点）
- **移除毛玻璃 `backdrop-filter`**：移动 GPU 上最昂贵的特效，降级为等效半透明填充。
- 消除点击灰色高亮、隐藏滚动条、关闭过度滚动回弹。
- WebView 关闭边缘发光、提升渲染器优先级（`RENDERER_PRIORITY_IMPORTANT`），减少重载卡顿。
- 关闭 SafeBrowsing 网络检查，减少导航往返延迟。
- `largeHeap` + 硬件加速，长对话列表滚动更稳。

### 4. 文件上传 / 下载（关键）
- **上传**：接管 `onShowFileChooser`，唤起系统**文件管理器**选择角色卡（`.png/.json/.jsonl`）等。
- **下载**：网页用 `blob:` 导出角色卡 / 聊天记录，Android WebView 原生**不支持** blob 下载
  → 注入桥接脚本拦截 `a[download]`，把字节经 `JavascriptInterface` 分块回传给原生，
  写入系统「下载」目录；普通 http(s) 下载交给系统 `DownloadManager`。
- 兼容 Android 10+（MediaStore，免存储权限）与旧版本（公开下载目录 + 运行时权限）。

## 🔧 构建方式

完全由 GitHub Actions 云端构建，无需本地环境。推送到 `main` 分支即自动出包并发布 Release。

## 📦 下载

- 固定链接（始终为最新版）：
  `https://github.com/Hiweny/RP-Hub-Android/releases/latest/download/RP-Hub.apk`

## ⚠️ 说明
- 应用为酒馆前端壳，需要网络访问在线站点。
- 已放开明文流量（`usesCleartextTraffic`），以支持连接自建的 `http://` 本地 / 局域网 API。
