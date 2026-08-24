# 星视TV项目总结

更新时间：2026-08-24

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
- 新增 `SOURCE_JSTV = 6`，用于江苏广电 JSTV 原生直播解析。
- 频道组和频道列表已迁移到 `app/src/main/assets/channel_catalog.json`，新增已有 sourceType 下的频道时优先只改配置。

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

当前原生 JSTV 江苏地方频道：31 个稳定频道。

- 江苏卫视
- 江苏城市
- 江苏公共新闻
- 江苏综艺
- 江苏影视
- 江苏体育休闲
- 江苏教育
- 江苏国际
- 优漫卡通
- 江苏卫视4K超高清
- 南京新闻综合
- 无锡新闻综合
- 常州新闻综合
- 南通新闻综合
- 连云港新闻综合
- 徐州-1
- 盐城1套
- 淮安综合
- 宿迁综合
- 泰州新闻综合
- 泰兴综合
- 宜兴新闻综合
- 洪泽1套
- 贾汪新闻综合
- 邳州综合
- 泗阳综合
- 铜山新闻综合
- 响水综合
- 兴化新闻综合
- 新沂新闻综合
- 盱眙综合

当前配置化统计：

- 内置频道组：6 个
- 内置频道总数：125 个
- 央视网频道：20 个
- 央视频央视频道：27 个
- 央视频卫视频道：33 个
- 湖南 MGTV 原生频道：6 个
- 江苏 JSTV 原生频道：31 个
- WebView 备用频道：8 个

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
  - 接入 `SOURCE_JSTV`，通过 `JstvLiveResolver` 解析签名 m3u8 后复用原生播放链。
  - 传递 `webUrl / webExtra / fullscreenType / managementUrl` 到 `WebPlayerActivity`。
  - 保留原生播放、MGTV Resolver、央视频/央视网解析流程。

- `app/src/main/java/com/xingshi/tv/Channel.java`
  - 增加 `webUrl`、`webExtra`、`fullscreenType` 字段。
  - 增加 `jstvChannelId`、`jstvEn`、`jstvStreamName`、`jstvPath` 字段。
  - 保留原有 `url / urls / streamId / yangshipin / mgtv` 字段。

- `app/src/main/java/com/xingshi/tv/ChannelCatalog.java`
  - 频道配置加载器。
  - 保留 `SOURCE_WEBVIEW`、`SOURCE_JSTV`、`FULLSCREEN_MGTV`、`FULLSCREEN_YANGSHIPIN` 等常量。
  - 从 `assets/channel_catalog.json` 读取内置频道组和频道列表。
  - 配置读取失败时回退到最小 CCTV fallback，避免 App 直接崩溃。
  - 新增已有 sourceType 的普通频道时不应修改此文件。

- `app/src/main/assets/channel_catalog.json`
  - 内置频道组和频道配置。
  - 当前迁移 6 个频道组、125 个频道。
  - JSON 只保存频道数据和播放参数，不保存 Resolver 签名算法或解析规则。

- `app/src/main/java/com/xingshi/tv/JstvLiveResolver.java`
  - JSTV 江苏广电原生 m3u8 解析。
  - 根据 `streamName` 生成 `txTime / txSecret` 签名 URL。
  - 支持 `applive/`、`live/`、`4klive/` 等不同频道 path。
  - 每次切台重新生成签名，不做长时间缓存。

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
  - `versionCode 5`
  - `versionName '1.1.0'`

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
- HlsProxyServer、MgtvLiveResolver、JstvLiveResolver、IJK 播放器链路

如果必须修改，应先保留当前 APK 和日志基准，逐项验证：

- MGTV 经视频道自动选台
- MGTV 自动全屏
- 央视频湖南卫视自动全屏
- OK / 点击画面打开频道菜单
- 菜单外触摸不穿透
- 返回提示和管理页入口

## 七、后续规划

- WebView 启动加载页继续优化：视觉样式、频道LOGO、动画、异常状态提示。
- 扩展更多原生 Resolver 和网页直播频道：CCTV网页源、地方卫视网页源、其他地方台。
- 继续优化 UI 成品感：频道菜单、管理页、加载页、退出提示。
- 版本发布整理：签名、版本号、发布包命名、变更记录。
- 管理页蒙层问题暂缓，后续如继续处理，建议从独立 Activity / Window 背景 / 模拟器 WebView 渲染差异方向排查。

## 八、新增频道规则

以后新增频道时：

1. 先判断属于已有 sourceType 还是新平台。
2. 如果属于已有 sourceType，只修改 `app/src/main/assets/channel_catalog.json`。
3. 只有新平台需要新解析逻辑时，才新增 SOURCE 和 Resolver。
4. 不再在 Java 中硬编码普通频道列表。
