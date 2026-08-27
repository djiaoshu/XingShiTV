# 星视TV (XingShiTV)

星视TV是一款 Android TV 网络电视应用。

当前版本：星视TV v1.2.0

本项目基于开源项目 NativeWasmTv 二次开发。

原项目：[https://github.com/buhanzhe/NativeWasmTv](https://github.com/buhanzhe/NativeWasmTv)

感谢原作者开源分享。

## 主要功能

- Android TV 遥控器操作
- 多频道直播播放
- 自定义直播源管理
- CCTV / 央视频 / MGTV / JSTV / 看看新闻原生直播支持
- WebView 网页直播备用播放
- MGTV 网页直播适配
- 央视频网页直播适配
- GDtV 广东频道 Mobile WebView 适配
- 自动全屏处理
- Native WebView 播放器交互优化
- 网页频道覆盖菜单
- 直播源在线管理
- 软件内提供星视TV GitHub 项目主页入口

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
- 新增江苏卫视4K超高清、南京新闻综合、无锡新闻综合、常州新闻综合、南通新闻综合、连云港新闻综合等频道。
- 频道目录改为 JSON 配置驱动，内置频道统一由 `app/src/main/assets/channel_catalog.json` 管理。
- 彻底移除 NativeWasmTv 原项目自动更新检查，不再访问原项目版本接口。
- 直播源管理页新增星视TV GitHub 项目主页入口。

## 当前内置频道

当前配置共 8 个频道组、147 个内置频道：

- CCTV：20
- 央视频央视频道：27
- 央视频卫视频道：33
- 湖南 MGTV：6
- 江苏 JSTV：31
- 上海看看新闻：5
- 广东 GDtV：17
- WebView备用：8

## 技术说明

JSTV 播放链路：

`JstvLiveResolver -> HlsProxyServer -> IJK`

WebView频道采用独立适配策略。

MGTV:

- 网页频道自动选择
- Native 触摸模拟全屏

央视频:

- 稳定播放检测
- Native 触摸模拟全屏

GDtV:

- Mobile WebView 播放
- 官方网页自行维护短效 token
- Native 触摸点击网页全屏按钮
- CustomView 建立并确认全屏播放后释放 Loading

频道配置：

- 内置频道由 `app/src/main/assets/channel_catalog.json` 管理。
- 已有 sourceType 下新增频道时，原则上只需修改频道配置。

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

## 项目主页

[https://github.com/djiaoshu/XingShiTV](https://github.com/djiaoshu/XingShiTV)

欢迎 Star 项目、反馈问题和关注后续版本。

## 后续计划

- 增加更多 WebView 网页频道
- 增加更多地区原生直播频道
- 优化 UI
- 增加更多直播源
- 设计星视TV自己的版本更新系统
