# 星视TV (XingShiTV)

星视TV是一款 Android TV 网络电视应用。

本项目基于开源项目 NativeWasmTv 二次开发。

原项目：[https://github.com/buhanzhe/NativeWasmTv](https://github.com/buhanzhe/NativeWasmTv)

感谢原作者开源分享。

当前版本：星视TV v1.0 beta

## 主要功能

- Android TV遥控器操作
- 多频道直播播放
- 自定义直播源管理
- WebView网页直播支持
- MGTV网页直播适配
- 央视频网页直播适配
- 自动全屏处理
- Native WebView播放器交互优化
- 网页频道覆盖菜单
- 直播源在线管理

## 当前频道能力

星视TV保留原 NativeWasmTv 的原生播放能力，并新增 WebView 网页播放备用模式。

当前已适配的 WebView 网页直播：

- 湖南卫视网页，央视频播放器
- 湖南经视网页，MGTV播放器
- 湖南都市网页，MGTV播放器
- 湖南娱乐网页，MGTV播放器
- 湖南电影网页，MGTV播放器
- 湖南电视剧网页，MGTV播放器
- 金鹰卡通网页，MGTV播放器
- 金鹰纪实网页，MGTV播放器

## 技术说明

WebView频道采用独立适配策略。

MGTV:

- 网页频道自动选择
- Native触摸模拟全屏

央视频:

- 稳定播放检测
- Native触摸模拟全屏

其他技术点：

- 原生播放链路继续使用 ijkplayer
- WebView播放使用 PC User-Agent
- 频道菜单以覆盖层方式显示
- 菜单显示期间拦截底层 WebView 触摸事件
- 管理页支持 M3U / M3U8 / TXT 直播源地址输入

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

- 增加更多WEBVIEW网页频道
- 优化UI
- 增加更多直播源

## 致谢

本项目基于 NativeWasmTv 二次开发。

感谢 NativeWasmTv 原作者开源分享，也感谢相关播放器和 WebView 直播项目提供的技术参考。
