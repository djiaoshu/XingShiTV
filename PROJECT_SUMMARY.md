# 星视TV项目总结

更新时间：2026-08-23

## 一、项目定位

- 项目名称：星视TV
- 原项目：NativeWasmTv
- 原项目地址：https://github.com/buhanzhe/NativeWasmTv
- 当前定位：Android TV直播播放器，基于 NativeWasmTv 二次开发。

星视TV保留 NativeWasmTv 原有原生播放能力，并在此基础上增加 WebView 网页直播备用播放能力，重点适配 MGTV 和央视频网页直播场景。

## 二、品牌信息

- App名称：星视TV
- applicationId：com.xingshi.tv
- Manifest package：com.xingshi.tv
- 原包名：xiao.bu.tv，当前 Java 包路径已迁移到 `com.xingshi.tv`
- App图标：已替换 `mipmap-* / ic_launcher.png`
- Android TV Banner：已替换 `res/drawable/tv_banner.png`
- 桌面显示名称：`res/values/strings.xml` 中 `app_name=星视TV`

## 三、已完成功能

### 播放

- 保留原 NativeWasmTv 原生播放逻辑。
- 保留 IJK 播放器、HlsProxyServer、央视频/央视网相关原生解析链路。
- 新增 `SOURCE_WEBVIEW = 5`，用于 WebView 网页直播备用播放。
- `SOURCE_MGTV = 4` 仍走 `MgtvLiveResolver` + 原生播放器链路。

### WebView直播

- 新增 WebView 播放 Activity：`WebPlayerActivity.java`。
- WebView 使用 PC User-Agent，解决 MGTV 页面跳移动端的问题。
- WebView 频道使用 `webUrl / webExtra / fullscreenType` 配置。
- MGTV 频道统一入口：`https://www.mgtv.com/live/`
- MGTV 通过 `webExtra=分类名 频道名` 自动选台。

当前 WebView 备用播放频道：

- 湖南卫视网页：央视频，`fullscreenType=YANGSHIPIN`
- 湖南经视网页：MGTV，`webExtra=热门 湖南经视`
- 湖南都市网页：MGTV，`webExtra=热门 湖南都市`
- 湖南娱乐网页：MGTV，`webExtra=热门 湖南娱乐`
- 湖南电影网页：MGTV，`webExtra=热门 湖南电影`
- 湖南电视剧网页：MGTV，`webExtra=热门 湖南电视剧`
- 金鹰卡通网页：MGTV，`webExtra=热门 金鹰卡通`
- 金鹰纪实网页：MGTV，`webExtra=热门 金鹰纪实`

当前原生 MGTV 湖南地方频道：

- 湖南都市
- 湖南经视
- 湖南娱乐
- 金鹰卡通
- 金鹰纪实
- 湖南电影

### 全屏

- 保留 `WebChromeClient.onShowCustomView()` / `onHideCustomView()` 的 HTML5 全屏容器逻辑。
- MGTV 自动全屏：视频播放后定位 `mango-kerne-layer / kernel-container` 等播放器区域，由 Native MotionEvent 模拟真实双击触发。
- 央视频自动全屏：等待播放稳定后，对 `video-con / c-container` 等播放器容器执行 Native MotionEvent 双击。
- 已验证：MGTV WebView 播放、选台和自动全屏可用；央视频网页播放和自动全屏可用。

### 交互

- WebView 播放中 OK / Enter 打开频道选择覆盖层。
- WebView 播放中点击画面打开频道选择覆盖层，避免点击穿透导致网页播放器暂停。
- 频道菜单显示期间，菜单外区域触摸事件被消费，不传递给 WebView/video。
- WebView 播放中返回键先显示返回提示。
- 返回提示确认后进入直播源管理页。
- WebView 播放和频道菜单覆盖层可共存，频道选择时再切换频道。

### 管理

- 直播源管理页面：`res/raw/control.html`
- 页面标题已改为“星视TV · 直播源管理”。
- 支持 M3U / M3U8 / TXT 列表地址输入。
- “关于星视TV”已加入 NativeWasmTv 来源说明和原项目链接。
- 管理页目前仍有一个已知显示问题：从“网页播放备用”路径进入时，部分环境下页面看起来有一层浅色蒙层；从其他频道组入口进入正常。该问题暂时不影响核心直播播放。

### WebView加载页

- 已实现基础 Loading Overlay。
- 进度阶段：
  - WebView创建：10%
  - 开始加载网页：30%
  - 网页加载完成：50%
  - 找到播放器：70%
  - video playing：90%
  - 进入全屏：100%

## 四、关键技术修改

- `app/src/main/java/com/xingshi/tv/WebPlayerActivity.java`
  - WebView 播放主入口。
  - PC User-Agent。
  - MGTV `webExtra` 自动选台。
  - 视频检测 JS。
  - MGTV / Yangshipin 自动全屏策略。
  - WebChromeClient CustomView 全屏容器。
  - WebView 播放时频道菜单、返回提示、触摸拦截、Loading Overlay。

- `app/src/main/java/com/xingshi/tv/MainActivity.java`
  - 接入 `SOURCE_WEBVIEW`。
  - 传递 `webUrl / webExtra / fullscreenType / managementUrl` 到 `WebPlayerActivity`。
  - 保留原生播放、MGTV Resolver、央视频/央视网解析流程。

- `app/src/main/java/com/xingshi/tv/Channel.java`
  - 增加 `webUrl`、`webExtra`、`fullscreenType` 字段。
  - 保留原有 `url / urls / streamId / yangshipin / mgtv` 字段。

- `app/src/main/java/com/xingshi/tv/ChannelCatalog.java`
  - 增加 `SOURCE_WEBVIEW`。
  - 增加 `FULLSCREEN_MGTV`、`FULLSCREEN_YANGSHIPIN`。
  - 增加湖南地方频道和 WebView 备用频道配置。

- `app/src/main/res/raw/control.html`
  - 直播源管理页面。
  - 品牌文案改为星视TV。
  - 增加 NativeWasmTv 来源说明。

- `app/src/main/res/layout/view_channel_list_panel.xml`
  - WebView 播放中的频道选择覆盖层复用布局。

- `app/src/main/res/layout/view_back_navigation_prompt.xml`
  - WebView 播放返回确认提示布局。

- `app/src/main/AndroidManifest.xml`
  - 包名 `com.xingshi.tv`。
  - 注册 `ManagementActivity` 和 `WebPlayerActivity`。
  - 设置图标、TV Banner、横屏和硬件加速。

- `app/build.gradle`
  - `applicationId 'com.xingshi.tv'`
  - `targetSdkVersion 28`
  - `versionCode 4`
  - `versionName '1.3.0'`

## 五、当前稳定版本状态

- 当前标记稳定APK：`<release-dir>/星视TV-management-clean-debug.apk`
- 最近编译验证APK：`<release-dir>/星视TV-management-activity-debug.apk`
- package/applicationId：`com.xingshi.tv`
- App名称：星视TV
- targetSdk：28
- 编译状态：BUILD SUCCESSFUL

说明：`星视TV-management-clean-debug.apk` 是当前阶段标记的稳定测试版本；`星视TV-management-activity-debug.apk` 是后续尝试修复管理页蒙层时生成的实验包。

## 六、注意事项

以下部分已经通过多轮实机/模拟器日志验证，后续不要随意改动：

- MGTV fullscreen strategy
- Yangshipin fullscreen strategy
- WebChromeClient CustomView 全屏框架
- Native MotionEvent 全屏事件
- WebView JS 视频检测和 MGTV `webExtra` 自动选台逻辑
- WebView 播放时频道菜单触摸拦截逻辑
- WebView overlay 层级
- HlsProxyServer、MgtvLiveResolver、IJK 播放器链路

如果必须修改，应先保留当前 APK 和日志基准，逐项验证：

- MGTV 经视频道自动选台
- MGTV 自动全屏
- 央视频湖南卫视自动全屏
- OK / 点击画面打开频道菜单
- 菜单外触摸不穿透
- 返回提示和管理页入口

## 七、后续规划

- WebView 启动加载页继续优化：视觉样式、频道LOGO、动画、异常状态提示。
- 扩展更多网页直播频道：CCTV网页源、地方卫视网页源、其他地方台。
- 继续优化 UI 成品感：频道菜单、管理页、加载页、退出提示。
- 版本发布整理：签名、版本号、发布包命名、变更记录。
- 管理页蒙层问题暂缓，后续如继续处理，建议从独立 Activity / Window 背景 / 模拟器 WebView 渲染差异方向排查。
