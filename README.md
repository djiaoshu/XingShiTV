# 星视TV (XingShiTV)

星视TV是一款 Android TV 网络电视应用。

当前版本：星视TV v1.3.2

本项目基于开源项目 NativeWasmTv 二次开发。

原项目：[https://github.com/buhanzhe/NativeWasmTv](https://github.com/buhanzhe/NativeWasmTv)

感谢原作者开源分享。

## 主要功能

- Android TV 遥控器操作
- 多频道直播播放
- 自定义直播源管理
- CCTV / 央视频 / MGTV / JSTV / 看看新闻原生直播支持
- 远程频道配置加载
- 远程多线路频道支持
- HLS / TS / FLV / 动态直播源自动分流
- WebView 网页直播备用播放
- MGTV 网页直播适配
- 央视频网页直播适配
- 自动全屏处理
- Native WebView 播放器交互优化
- 网页频道覆盖菜单
- 直播源在线管理
- 软件内提供星视TV GitHub 项目主页入口

## v1.3.2 更新重点

- 恢复广东频道，新增 17 路 GDtV PC 页面播放。
- 优化广东频道 WebView 播放兼容性。
- 远程频道配置新增 AES-256-GCM 加密支持。
- 远程配置本地缓存改为密文缓存。
- 保持旧明文配置兼容。
- 保持远程动态多频道组兼容。

## v1.3.1 更新重点

- 默认启动进入 CCTV-1 综合。
- 多线路播放失败时自动切换备用线路。
- 多线路频道优先尝试最近一次成功线路。
- 当前频道全部线路失败后，自动尝试同频道组下一个频道。
- 远程频道配置支持动态多频道组。
- 继续兼容旧版单组远程配置。

## v1.3.0 更新重点

- 新增远程港台频道支持。
- 新增远程多线路频道支持。
- 增强 HLS / TS / FLV / 动态直播源播放兼容。
- `.m3u8` 继续使用 `HlsProxyServer -> IJK`。
- `.ts` / `.flv` 改为直接交给 IJK 播放。
- 无明确后缀、PHP、动态跳转等直播源会先进行轻量类型探测，再自动分流。
- 新增私密频道入口及密码验证。
- 广东频道因源站状态变化暂时下线，后续版本改用新的 WebView 适配恢复。
- 湖南 WebView 频道调整为「湖南地方频道（备用）」。
- 优化远程频道异常容错。

## v1.2.0 更新重点

- 新增「广东频道」频道组。
- 广东频道正式采用 GDtV Mobile WebView 播放方案。
- 新增 17 路广东频道，排除购物频道「南方购物」。
- GDtV 支持 `prism-fullscreen-btn -> Native MotionEvent -> WebChromeClient.onShowCustomView()` 全屏。
- 修复进入 GDtV 全屏前短暂露出普通网页/播放器画面的闪屏问题。
- 优化 GDtV WebView Back 键交互。
- 保留 `GdtvLiveResolver` 作为实验/备用方案，正式频道不走原生 GDtV 方案。

## v1.1.0 更新重点

- 新增「江苏地方频道」频道组。
- 新增 JSTV 原生直播解析。
- 江苏地区扩展至 31 个稳定频道。
- 频道目录改为 JSON 配置驱动，内置频道统一由 `app/src/main/assets/channel_catalog.json` 管理。
- 彻底移除 NativeWasmTv 原项目自动更新检查，不再访问原项目版本接口。
- 直播源管理页新增星视TV GitHub 项目主页入口。

## 当前内置频道

当前内置配置共 8 个频道组、147 个频道：

- CCTV：20
- 央视频央视频道：27
- 央视频卫视频道：33
- 湖南 MGTV：6
- 江苏 JSTV：31
- 上海看看新闻：5
- 湖南 WebView 备用：8
- 广东频道：17

远程频道为附加频道源，加载失败时不影响内置频道。

## 技术说明

原生频道继续复用：

- HlsProxyServer
- ijkplayer
- CCTV / 央视频解析链
- MgtvLiveResolver
- JstvLiveResolver

JSTV：

- 通过频道配置中的 `stream / path` 生成签名 m3u8。
- 支持 `applive/`、`live/`、`4klive/` 等不同直播路径。
- 播放链路为 `JstvLiveResolver -> HlsProxyServer -> IJK`。

自定义/远程直播源：

- HLS/m3u8 使用 `HlsProxyServer -> IJK`。
- TS / FLV 使用 IJK Direct。
- 无明确类型的动态地址先进行轻量类型探测，再自动分流。
- 多线路频道会保留全部线路，由用户自行切换。

WebView频道采用独立适配策略。

MGTV:

- 网页频道自动选择
- Native 触摸模拟全屏

央视频:

- 稳定播放检测
- Native 触摸模拟全屏

其他技术点：

- WebView 播放使用 PC User-Agent
- 频道菜单以覆盖层方式显示
- 菜单显示期间拦截底层 WebView 触摸事件
- 管理页支持 M3U / M3U8 / TXT 直播源地址输入
- 已有 sourceType 下新增频道时，原则上只需修改 `channel_catalog.json`

## 项目结构

```text
XingShiTV
├── README.md
├── PROJECT_SUMMARY.md
├── app
├── gradle
├── build.gradle
└── settings.gradle
```

说明：

- `README.md`：面向普通用户和开发者的项目介绍。
- `PROJECT_SUMMARY.md`：当前阶段开发总结。
- `app/src/main/assets/channel_catalog.json`：内置频道组和频道配置。
- `app/src/main/java/com/xingshi/tv`：主要 Java 源码。
- `app/src/main/res/raw/control.html`：直播源管理页面。
- `AI_CONTEXT.md`：本地 AI/Codex 上下文文件，不提交到 GitHub。

## 构建说明

当前项目沿用旧版 Android 构建环境：

- JDK 8
- Gradle 4.4
- Android Gradle Plugin 3.1.4
- Android SDK Platform android-27
- Android Build Tools 27.0.3

推荐使用项目本地脚本编译 Debug APK：

```powershell
.\build-debug.ps1
```

本机 SDK/JDK 路径写入本地配置文件，不应提交到 Git。

## 开发文档

- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)

## 后续计划

- 增加更多 WebView 网页频道
- 增加更多地区原生直播频道
- 优化 UI
- 增加更多直播源
- 设计星视TV自己的版本更新系统

## 项目主页

[https://github.com/djiaoshu/XingShiTV](https://github.com/djiaoshu/XingShiTV)

欢迎 Star 项目、反馈问题和关注后续版本。

## 致谢

本项目基于 NativeWasmTv 二次开发。

感谢 NativeWasmTv 原作者开源分享，也感谢相关播放器和 WebView 直播项目提供的技术参考。
