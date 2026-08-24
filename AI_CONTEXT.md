# AI_CONTEXT

更新时间：2026-08-24

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
- versionCode：5
- versionName：1.1.0

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

### 更新系统

星视TV已彻底断开 NativeWasmTv 原项目更新检查；后续更新系统只能使用星视TV自己的发布源。

已移除/禁用的原项目更新链路：

- `MainActivity` 启动时不再创建 `AutoUpdater`，不再调用 `checkForUpdates()`。
- 已删除旧 `AutoUpdater.java`，不再访问 `https://github.com/buhanzhe/NativeWasmTv/raw/refs/heads/master/version-iptv.json`。
- 已删除旧 `ApkFileProvider.java` 和 Manifest 中的 APK 安装 provider。
- 已移除 `REQUEST_INSTALL_PACKAGES` 权限。
- 已移除 `app/build.gradle` 中生成原项目 GitHub 更新 manifest 的 `generateVersionFile` 任务。

暂时不要自行接入新的更新系统。后续如果要做更新，只能使用 XingShiTV 自己的 GitHub Release 或星视TV自有发布源。

### 原生播放

原 NativeWasmTv 的原生播放链路保留，包括：

- IJK 播放器
- HlsProxyServer
- 央视网 / 央视频相关原生解析
- MgtvLiveResolver
- JstvLiveResolver

不要为了 WebView 功能重写原生播放链路。

### Source 类型

位于 `ChannelCatalog.java`：

- `SOURCE_CCTV_WEB = 0`
- `SOURCE_YSP_CCTV = 1`
- `SOURCE_YSP_SATELLITE = 2`
- `SOURCE_CUSTOM = 3`
- `SOURCE_MGTV = 4`
- `SOURCE_WEBVIEW = 5`
- `SOURCE_JSTV = 6`

`SOURCE_WEBVIEW` 是新增网页播放备用类型，只影响 WebView 播放，不应影响其他 source。

### Channel 数据结构

位于 `Channel.java`。当前关键字段：

- `url / urls`
- `yangshipinPid`
- `yangshipinStreamId`
- `mgtvActivityId`
- `mgtvCameraId`
- `jstvChannelId`
- `jstvEn`
- `jstvStreamName`
- `jstvPath`
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

频道数据已经配置化。

核心文件：

- `app/src/main/assets/channel_catalog.json`
- `app/src/main/java/com/xingshi/tv/ChannelCatalog.java`

`channel_catalog.json` 保存内置频道组和频道数据；`ChannelCatalog.java` 负责读取 JSON、转换 `sourceType`、构建 `Group / Channel`，并在配置读取失败时回退到最小 CCTV 兜底分组。

内置频道组：

- `央视网 · 央视频道`
- `央视频 · 央视频道`
- `央视频 · 卫视频道`
- `湖南地方频道`
- `江苏地方频道`
- `网页播放备用`

当前配置迁移结果：

- 内置频道组：6 个
- 内置频道总数：125 个
- 央视网频道：20 个
- 央视频央视频道：27 个
- 央视频卫视频道：33 个
- 湖南 MGTV 原生频道：6 个
- 江苏 JSTV 原生频道：31 个
- WebView 备用频道：8 个

新增已有 `sourceType` 下的普通频道时，应优先只修改 `channel_catalog.json`，不再修改 `MainActivity`、Resolver 或 IJK 播放链路。

### 湖南地方频道，SOURCE_MGTV

原生 MGTV Resolver 频道：

- 湖南都市
- 湖南经视
- 湖南娱乐
- 金鹰卡通
- 金鹰纪实
- 湖南电影

### 江苏地方频道，SOURCE_JSTV

原生 JSTV Resolver 当前稳定频道数量：31 个。

频道：

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

播放链路：

`JSTV频道 -> JstvLiveResolver -> 签名m3u8 -> HlsProxyServer -> IJK`

JSTV 不实现动态频道列表、WebToken、Bearer token、EPG 或 WebView 播放。每次切台按 JSTV 前端规则重新生成 `txTime / txSecret`，不要长时间缓存签名 URL。

JSTV 频道配置支持不同相对 path：

- `applive/{stream}.m3u8`
- `live/{stream}.m3u8`
- `4klive/{stream}.m3u8`

签名算法保持不变：`txSecret = md5(secret + streamName + txTime)`。JSON 中的 `stream` 用于签名，`path` 会写入 `Channel.jstvPath` 用于拼接播放 URL，Resolver 不再猜测固定 `/applive/` 路径。

### 配置文件结构

`channel_catalog.json` 顶层结构：

- `version`
- `groups`
- `channels`

频道组字段：

- `id`
- `name`
- `sourceType`
- `order`

频道通用字段：

- `groupId`
- `number`
- `name`
- `sourceType`
- `order`

常用 `sourceType` 参数：

- `CCTV_WEB`：`streamId`，可选 `yangshipinPid / yangshipinStreamId`
- `YSP_CCTV / YSP_SATELLITE`：`yangshipinPid / yangshipinStreamId`
- `MGTV`：`activityId / cameraId`
- `JSTV`：`channelId / en / stream / path`
- `WEBVIEW`：`webUrl / webExtra / fullscreenType`

JSON 只保存频道数据和播放参数，不保存签名算法、鉴权算法或解析规则。

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
  - 频道配置加载器。
  - 保留 source 常量和默认频道索引逻辑。
  - 从 `assets/channel_catalog.json` 读取内置频道组和频道列表。
  - 配置读取失败时使用最小 CCTV fallback，避免 App 直接崩溃。
  - 新增已有 sourceType 的普通频道时不应修改此文件。

- `app/src/main/assets/channel_catalog.json`
  - 内置频道组和频道配置。
  - 当前迁移 6 个内置频道组、125 个内置频道。
  - 以后新增已有 sourceType 下的普通频道，优先只改此文件。

- `app/src/main/java/com/xingshi/tv/MgtvLiveResolver.java`
  - 原生 MGTV m3u8 解析。

- `app/src/main/java/com/xingshi/tv/JstvLiveResolver.java`
  - 原生 JSTV m3u8 签名解析。
  - 支持 JSTV 频道配置不同 path，签名算法保持不变。

- `app/src/main/java/com/xingshi/tv/HlsProxyServer.java`
  - HLS 代理。曾处理过 MGTV CDN 302/403 相关调试，并为 JSTV 增加独立 Referer / User-Agent 请求头。

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
- JstvLiveResolver

如果必须修改，请先备份当前 APK，并用日志验证：

- `WEBVIEW_TEST`
- `MGTV_TEST`
- `CHANNEL_TEST`
- `PLAYER_TEST`
- `HLS_PROXY`
- `JstvLiveResolver`

## 新增直播站点标准分析流程

目的：以后用户只需要提供一个直播页面 URL，例如 `https://example.com/live/`，Codex 就应该自动按照下面的流程分析，不需要用户再次说明应该采用哪种播放方式。

### 一、基本原则

星视TV新增直播站点时：

**优先解析真实直播流并使用 IJK 原生播放器，WebView 只作为无法稳定解析真实直播流时的兜底方案。**

不要看到网页直播地址就默认增加 `SOURCE_WEBVIEW`。

### 二、当前已有播放体系

- `SOURCE_CCTV_WEB = 0`
  - 央视网/CCTV直播解析
  - `resolveFallbackUrl()`
  - HLS -> `HlsProxyServer` -> IJK
- `SOURCE_YSP_CCTV = 1`
  - 央视频央视频道
  - `resolveYangshipinUrl()`
  - HLS -> `HlsProxyServer` -> IJK
- `SOURCE_YSP_SATELLITE = 2`
  - 央视频卫视频道
  - `resolveYangshipinUrl()`
  - HLS -> `HlsProxyServer` -> IJK
- `SOURCE_MGTV = 4`
  - 湖南地方频道
  - `MgtvLiveResolver`
  - 解析真实 m3u8
  - `HlsProxyServer` -> IJK
- `SOURCE_WEBVIEW = 5`
  - 网页播放备用
  - `WebPlayerActivity`
  - 仅用于无法稳定提取真实直播流的情况
- `SOURCE_JSTV = 6`
  - 江苏地方频道
  - `JstvLiveResolver`
  - 固定频道映射 + 签名 m3u8
  - `HlsProxyServer` -> IJK

### 三、收到新直播页面后的分析顺序

用户只提供直播页面 URL 时，自动执行以下分析。

#### 1. 查找直接直播流

检查：

- HTML
- 页面 JS
- Network/API
- 播放器配置

搜索：

- `.m3u8`
- `master.m3u8`
- `index.m3u8`
- HLS
- stream URL

如果存在稳定可直接使用的 m3u8，优先使用：

`直接直播地址 -> HlsProxyServer -> IJK`

#### 2. 查找播放接口

如果页面没有直接暴露 m3u8，分析页面调用的：

- play API
- live API
- stream API
- channel API
- JSON接口

查找：

- channelId
- cid
- pid
- code
- streamId
- playId

判断能否通过频道标识调用接口获得真实 m3u8。

如果可以，优先建立独立 Resolver，例如：

`JstvLiveResolver`

播放链路：

`频道ID -> Resolver -> m3u8 -> HlsProxyServer -> IJK`

#### 3. 分析鉴权

如果 m3u8 或播放接口需要：

- token
- sign
- timestamp
- vsecret
- auth
- Referer
- User-Agent
- Cookie
- Origin

分析其生成方式和有效期。

如果可以稳定生成，建立：

`带鉴权 Resolver -> HlsProxyServer -> IJK`

不要因为存在动态参数就直接改用 WebView。

#### 4. 判断是否需要 HlsProxyServer 特殊处理

检查：

- m3u8 请求头
- ts/m4s 分片请求头
- Referer
- Origin
- User-Agent
- Cookie
- 重定向
- HTTPS

确认现有 `HlsProxyServer` 是否可以直接处理。

如果需要扩展，请优先设计站点独立配置，不要破坏其他已经稳定的频道。

#### 5. 最后判断 WebView

只有满足以下情况之一时才考虑 `SOURCE_WEBVIEW`：

- 无法稳定获得真实直播流
- 播放地址完全依赖页面 JS 状态
- 存在无法复现的浏览器会话
- 播放器强依赖网页环境
- 原生 IJK 无法播放

WebView 属于最后兜底方案。

### 四、输出格式

每次分析新的直播页面后，先输出：

- 网站/平台
- 可发现频道
- 频道唯一标识
- 播放接口
- 真实直播流格式
- 是否动态地址
- 是否需要 token/sign
- 是否需要特殊请求头
- 是否可以 IJK 原生播放
- 推荐播放方式
- 推荐 Resolver 名称
- 实现难度
- 稳定性判断

最终明确给出：

`推荐：直接 IJK`

或：

`推荐：Resolver + IJK`

或：

`推荐：带鉴权 Resolver + IJK`

或：

`推荐：SOURCE_WEBVIEW`

### 五、开发保护规则

分析新站点和新增频道时，不得为了新站点随意修改已经验证稳定的：

- `MgtvLiveResolver`
- MGTV原生播放链
- Yangshipin解析链
- CCTV解析链
- `HlsProxyServer` 公共行为
- MGTV WebView fullscreen strategy
- Yangshipin WebView fullscreen strategy
- `WebChromeClient`
- Native MotionEvent 全屏逻辑

如果新网站需要特殊行为，优先增加：

- 新 SOURCE 类型
- 新 Resolver
- 站点独立配置

不要把站点特例硬塞进已有 Resolver。

### 六、后续默认行为

以后用户如果只说：

`分析这个直播地址：https://xxxx`

或者：

`这个直播页面能不能加到星视TV`

不需要再次询问采用哪种方式。

直接按照本章节流程分析，并先给出最合适的播放方案。

在用户确认实施之前，以分析为主，不主动破坏现有稳定播放代码。

## 后续规划

- WebView Loading Overlay 继续优化：视觉效果、频道LOGO、动画、加载失败提示。
- 继续扩展网页直播频道：CCTV网页源、地方卫视网页源、更多地方台。
- 整理发布版本：正式签名、版本号策略、APK命名、更新日志。
- UI 成品化：频道菜单、管理页、退出提示、关于页面。
- 管理页蒙层问题暂缓，后续如重启该问题，优先比较 `ManagementActivity` 独立入口和 `WebPlayerActivity` 返回入口的 Window / Activity / WebView 渲染差异。

## 新增频道默认规则

以后新增频道时：

1. 先判断属于已有 sourceType 还是新平台。
2. 如果属于已有 sourceType，只修改 `app/src/main/assets/channel_catalog.json`。
3. 只有新平台需要新解析逻辑时，才新增 SOURCE 和 Resolver。
4. 不再在 Java 中硬编码普通频道列表。
