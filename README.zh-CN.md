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

## 技术说明

WebView频道采用独立适配策略。

MGTV:

- 网页频道自动选择
- Native触摸模拟全屏

央视频:

- 稳定播放检测
- Native触摸模拟全屏

## 构建环境

- JDK 8
- Gradle 4.4
- Android Gradle Plugin 3.1.4
- Android SDK Platform android-27
- Android Build Tools 27.0.3

创建 `local.properties`：

```properties
sdk.dir=C\:\\Android\\Sdk
```

编译：

```powershell
.\gradlew.bat assembleDebug
```

## 文档

- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)
- [AI_CONTEXT.md](AI_CONTEXT.md)

## 后续计划

- 增加更多WEBVIEW网页频道
- 优化UI
- 增加更多直播源
