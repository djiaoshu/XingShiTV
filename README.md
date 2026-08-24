# 星视TV (XingShiTV)

星视TV是一款 Android TV 网络电视应用。

当前版本：星视TV v1.1.0

本项目基于开源项目 NativeWasmTv 二次开发。

原项目：[https://github.com/buhanzhe/NativeWasmTv](https://github.com/buhanzhe/NativeWasmTv)

感谢原作者开源分享。

## 主要功能

- Android TV 遥控器操作
- 多频道直播播放
- 自定义直播源管理
- CCTV / 央视频 / MGTV / JSTV 原生直播支持
- WebView 网页直播备用播放
- MGTV 网页直播适配
- 央视频网页直播适配
- 自动全屏处理
- Native WebView 播放器交互优化
- 网页频道覆盖菜单
- 直播源在线管理
- 软件内提供星视TV GitHub 项目主页入口

## v1.1.0 更新重点

- 新增「江苏地方频道」频道组。
- 新增 JSTV 原生直播解析。
- 江苏地区扩展至 31 个稳定频道。
- 新增江苏卫视4K超高清、南京新闻综合、无锡新闻综合、常州新闻综合、南通新闻综合、连云港新闻综合等频道。
- 频道目录改为 JSON 配置驱动，内置频道统一由 `app/src/main/assets/channel_catalog.json` 管理。
- 彻底移除 NativeWasmTv 原项目自动更新检查，不再访问原项目版本接口。
- 直播源管理页新增星视TV GitHub 项目主页入口。

## 当前内置频道

当前配置共 6 个频道组、125 个内置频道：

- CCTV：20
- 央视频央视频道：27
- 央视频卫视频道：33
- 湖南 MGTV：6
- 江苏 JSTV：31
- WebView备用：8

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
├── AI_CONTEXT.md
├── app
├── gradle
├── build.gradle
└── settings.gradle
```

说明：

- `README.md`：面向普通用户和开发者的项目介绍。
- `PROJECT_SUMMARY.md`：当前阶段开发总结。
- `AI_CONTEXT.md`：给 AI / Codex 后续继续开发使用的上下文。
- `app/src/main/assets/channel_catalog.json`：内置频道组和频道配置。
- `app/src/main/java/com/xingshi/tv`：主要 Java 源码。
- `app/src/main/res/raw/control.html`：直播源管理页面。

## 构建说明

当前项目沿用旧版 Android 构建环境：

- JDK 8
- Gradle 4.4
- Android Gradle Plugin 3.1.4
- Android SDK Platform android-27
- Android Build Tools 27.0.3

创建本地 SDK 配置：

```properties
sdk.dir=C\:\\Android\\Sdk
```

保存为 `local.properties`。该文件只用于本机环境，不应提交到 Git。

编译 Debug APK：

```powershell
.\gradlew.bat assembleDebug
```

## 开发文档

- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)
- [AI_CONTEXT.md](AI_CONTEXT.md)

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
