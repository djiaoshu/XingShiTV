# AI_CONTEXT

更新时间：2026-08-23

本文档用于后续 AI / Codex 接手星视TV项目时快速理解当前状态。请优先阅读本文，再阅读 `PROJECT_SUMMARY.md`。

## 项目基本信息

- 项目名称：星视TV
- 原项目：NativeWasmTv
- 原项目地址：https://github.com/buhanzhe/NativeWasmTv
- 当前定位：Android TV直播播放器，基于 NativeWasmTv 二次开发。
- 源码目录：`<project-root>`
- 当前包名 / applicationId：`com.xingshi.tv`
- App名称：星视TV
- targetSdk：28
- versionCode：4
- versionName：1.3.0

## 构建环境

- JDK8：`<jdk8-path>`
- Gradle：4.4
- Android Gradle Plugin：3.1.4
- Android SDK：`<android-sdk-path>`
- 需要组件：
  - `platforms;android-27`
  - `build-tools;27.0.3`
  - `platform-tools`

注意：旧 Gradle / AGP 在包含非 ASCII 字符的路径下可能出现编码或 R.java 相关问题。此前稳定做法是复制到 ASCII 临时目录编译，再把 APK 拷回发布目录。

## 当前稳定版本

- 当前标记稳定APK：`<release-dir>/星视TV-management-clean-debug.apk`
- 最近编译验证APK：`<release-dir>/星视TV-management-activity-debug.apk`
- 编译状态：BUILD SUCCESSFUL

说明：`management-clean` 是当前阶段标记的稳定测试包；`management-activity` 是管理页蒙层问题尝试修复后的实验包。用户已决定暂时不继续处理蒙层问题。

## 核心功能状态

### 原生播放

原 NativeWasmTv 的原生播放链路保留，包括：

- IJK 播放器
- HlsProxyServer
- 央视网 / 央视频相关原生解析
- MgtvLiveResolver

不要为了 WebView 功能重写原生播放链路。

### Source 类型

位于 `ChannelCatalog.java`：

- `SOURCE_CCTV_WEB = 0`
- `SOURCE_YSP_CCTV = 1`
- `SOURCE_YSP_SATELLITE = 2`
- `SOURCE_CUSTOM = 3`
- `SOURCE_MGTV = 4`
- `SOURCE_WEBVIEW = 5`

`SOURCE_WEBVIEW` 是新增网页播放备用类型，只影响 WebView 播放，不应影响其他 source。

### Channel 数据结构

位于 `Channel.java`。当前关键字段：

- `url / urls`
- `yangshipinPid`
- `yangshipinStreamId`
- `mgtvActivityId`
- `mgtvCameraId`
- `webUrl`
- `webExtra`
- `fullscreenType`

WebView 频道使用：

- `webUrl`：网页播放地址
- `webExtra`：MGTV 自动选台参数，例如 `热门 湖南经视`
- `fullscreenType`：`MGTV` 或 `YANGSHIPIN`

## WebView 播放

核心文件：`app/src/main/java/com/xingshi/tv/WebPlayerActivity.java`

已实现：

- 系统 Android WebView 播放
- PC User-Agent，避免 MGTV 跳转移动端
- WebViewClient URL 日志
- WebChromeClient CustomView 全屏容器
- JavaScript 视频检测
- MGTV 自动选台
- MGTV 自动全屏
- Yangshipin 自动全屏
- Loading Overlay
- OK / 点击画面打开频道菜单
- 菜单显示时触摸拦截，避免事件穿透到底层 WebView
- 返回提示

不要恢复以下历史尝试：

- `KEYCODE_F` 全屏
- `video.requestFullscreen()`
- 点击 MGTV `fullscreenBtn`
- JS `dispatchEvent('dblclick')`

最终验证有效的是 Native MotionEvent：

- MGTV：定位 `mango-kerne-layer / kernel-container`，真实双击。
- Yangshipin：等待播放稳定，定位 `video-con / c-container`，真实双击。

## 当前频道配置

核心文件：`ChannelCatalog.java`

内置频道组：

- `央视网 · 央视频道`
- `央视频 · 央视频道`
- `央视频 · 卫视频道`
- `湖南地方频道`
- `网页播放备用`

### 湖南地方频道，SOURCE_MGTV

原生 MGTV Resolver 频道：

- 湖南都市
- 湖南经视
- 湖南娱乐
- 金鹰卡通
- 金鹰纪实
- 湖南电影

### 网页播放备用，SOURCE_WEBVIEW

WebView 频道：

- 湖南卫视网页：`https://www.yangshipin.cn/tv/home?pid=600002475`，`fullscreenType=YANGSHIPIN`
- 湖南经视网页：`https://www.mgtv.com/live/`，`webExtra=热门 湖南经视`，`fullscreenType=MGTV`
- 湖南都市网页：`https://www.mgtv.com/live/`，`webExtra=热门 湖南都市`，`fullscreenType=MGTV`
- 湖南娱乐网页：`https://www.mgtv.com/live/`，`webExtra=热门 湖南娱乐`，`fullscreenType=MGTV`
- 湖南电影网页：`https://www.mgtv.com/live/`，`webExtra=热门 湖南电影`，`fullscreenType=MGTV`
- 湖南电视剧网页：`https://www.mgtv.com/live/`，`webExtra=热门 湖南电视剧`，`fullscreenType=MGTV`
- 金鹰卡通网页：`https://www.mgtv.com/live/`，`webExtra=热门 金鹰卡通`，`fullscreenType=MGTV`
- 金鹰纪实网页：`https://www.mgtv.com/live/`，`webExtra=热门 金鹰纪实`，`fullscreenType=MGTV`

## 直播源管理页面

文件：`app/src/main/res/raw/control.html`

当前状态：

- 标题：星视TV · 直播源管理
- 输入项：M3U / M3U8 / TXT 列表地址
- 关于区块：关于星视TV
- 已加入 NativeWasmTv 二次开发说明和原项目链接。

已知问题：

- 从“网页播放备用”路径进入管理页时，部分环境下仍可能看起来有一层浅色蒙层。
- 从其他频道组路径进入管理页显示正常。
- 用户已决定暂时不继续修复该问题。

## 品牌修改

已完成：

- App名称：星视TV
- applicationId：`com.xingshi.tv`
- Manifest package：`com.xingshi.tv`
- Java 包路径：`com/xingshi/tv`
- launcher icon：`mipmap-* / ic_launcher.png`
- TV Banner：`res/drawable/tv_banner.png`

不要再引用旧包名 `xiao.bu.tv` 作为当前包名。

## 重要文件

- `app/src/main/java/com/xingshi/tv/WebPlayerActivity.java`
  - WebView 播放、自动选台、自动全屏、频道菜单覆盖层、返回提示、Loading Overlay。

- `app/src/main/java/com/xingshi/tv/MainActivity.java`
  - 原主播放 Activity，负责不同 source 的播放入口。

- `app/src/main/java/com/xingshi/tv/Channel.java`
  - 频道数据结构。

- `app/src/main/java/com/xingshi/tv/ChannelCatalog.java`
  - 内置频道和 source 配置。

- `app/src/main/java/com/xingshi/tv/MgtvLiveResolver.java`
  - 原生 MGTV m3u8 解析。

- `app/src/main/java/com/xingshi/tv/HlsProxyServer.java`
  - HLS 代理。曾处理过 MGTV CDN 302/403 相关调试。

- `app/src/main/res/raw/control.html`
  - 直播源管理页面。

- `app/src/main/res/layout/view_channel_list_panel.xml`
  - WebView 播放中的频道菜单覆盖层。

- `app/src/main/res/layout/view_back_navigation_prompt.xml`
  - WebView 返回提示。

## 不要随意修改

以下逻辑已经过大量调试并稳定：

- MGTV fullscreen strategy
- Yangshipin fullscreen strategy
- WebChromeClient CustomView 全屏逻辑
- Native MotionEvent 全屏触发
- MGTV `webExtra` 自动选台
- PC User-Agent
- WebView 播放时频道菜单触摸拦截
- 返回提示焦点处理
- 原生 IJK 播放链路
- HlsProxyServer
- MgtvLiveResolver

如果必须修改，请先备份当前 APK，并用日志验证：

- `WEBVIEW_TEST`
- `MGTV_TEST`
- `CHANNEL_TEST`
- `PLAYER_TEST`
- `HLS_PROXY`

## 后续规划

- WebView Loading Overlay 继续优化：视觉效果、频道LOGO、动画、加载失败提示。
- 继续扩展网页直播频道：CCTV网页源、地方卫视网页源、更多地方台。
- 整理发布版本：正式签名、版本号策略、APK命名、更新日志。
- UI 成品化：频道菜单、管理页、退出提示、关于页面。
- 管理页蒙层问题暂缓，后续如重启该问题，优先比较 `ManagementActivity` 独立入口和 `WebPlayerActivity` 返回入口的 Window / Activity / WebView 渲染差异。
