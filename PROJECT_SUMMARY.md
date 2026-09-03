# 星视TV项目总结

更新时间：2026-09-03

## 一、项目定位

- 项目名称：星视TV
- 原项目：NativeWasmTv
- 原项目地址：https://github.com/buhanzhe/NativeWasmTv
- 当前定位：Android TV 直播播放器，基于 NativeWasmTv 二次开发。

星视TV保留 NativeWasmTv 原有 IJK 原生播放能力，并在此基础上增加配置化频道、网页直播备用播放、远程频道加载、多线路频道和多种直播源格式兼容。

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
- 保留 IJK 播放器、HlsProxyServer、央视网/央视频解析链路。
- `SOURCE_MGTV = 4` 继续使用 `MgtvLiveResolver` + 原生播放器链路。
- `SOURCE_WEBVIEW = 5` 用于 WebView 网页直播备用播放。
- `SOURCE_JSTV = 6` 用于江苏广电 JSTV 原生直播解析。
- `SOURCE_CUSTOM` 支持多线路名称、远程频道和动态直播源类型探测。
- 频道组和频道列表已迁移到 `app/src/main/assets/channel_catalog.json`，新增已有 sourceType 下的频道时优先只改配置。

### SOURCE_CUSTOM 兼容

- `.m3u8`：继续走 `HlsProxyServer -> IJK`。
- `.ts`：直接交给 IJK。
- `.flv`：直接交给 IJK。
- unknown / PHP / 无扩展名 / 动态跳转 URL：由 `CustomStreamTypeDetector` 轻量探测为 HLS / FLV / MPEG-TS 后自动分流。
- 明确后缀的 `.m3u8/.ts/.flv` 不做额外探测，避免影响起播速度。

### 远程频道

- 已支持附加远程频道配置加载。
- 远程「港台频道」已接入，当前保留 19 个频道、23 条线路。
- 远程公开频道配置支持 AES-256-GCM 密文响应和密文缓存，同时兼容旧明文配置。
- 支持远程多线路频道，同一频道下保留多个 `sources`，由用户自行切换线路。
- 远程配置异常时不影响内置频道显示和播放。
- 支持私密频道入口和密码验证；密码只在当前 App 会话内使用，不写入源码、配置文件或日志。

### WebView直播

- WebView 播放 Activity：`WebPlayerActivity.java`。
- WebView 使用 PC User-Agent，解决部分网页直播跳移动端或播放器异常的问题。
- WebView 频道使用 `webUrl / webExtra / fullscreenType` 配置。
- 广东频道使用 `fullscreenType=GDTV_PC`，通过 GDtV PC 页面播放器 DOM 全屏化并在 `video playing` 后隐藏 Loading。
- MGTV 频道统一入口：`https://www.mgtv.com/live/`
- MGTV 通过 `webExtra=分类名 频道名` 自动选台。
- 「网页播放备用」已调整为「湖南地方频道（备用）」。

当前湖南 WebView 备用频道：湖南卫视网页、湖南经视网页、湖南都市网页、湖南娱乐网页、湖南电影网页、湖南电视剧网页、金鹰卡通网页、金鹰纪实网页。

当前原生 MGTV 湖南地方频道：湖南都市、湖南经视、湖南娱乐、金鹰卡通、金鹰纪实、湖南电影。

当前原生 JSTV 江苏地方频道：31 个稳定频道。

### 频道统计

- 内置频道组：8 个
- 内置频道总数：147 个
- 央视网频道：20 个
- 央视频央视频道：27 个
- 央视频卫视频道：33 个
- 湖南 MGTV 原生频道：6 个
- 江苏 JSTV 原生频道：31 个
- 湖南 WebView 备用频道：8 个
- 广东频道：17 个

说明：广东频道已恢复，统一采用 GDtV PC 页面 WebView 播放方式，排除购物频道「南方购物」。

### 全屏

- 保留 `WebChromeClient.onShowCustomView()` / `onHideCustomView()` 的 HTML5 全屏容器逻辑。
- MGTV 自动全屏：视频播放后定位播放器区域，由 Native MotionEvent 模拟真实双击触发。
- 央视频自动全屏：等待播放稳定后，对播放器容器执行 Native MotionEvent 双击。
- WebView 播放、选台和自动全屏逻辑保持稳定。

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
- “关于星视TV”已加入星视TV项目主页、NativeWasmTv 来源说明和原项目链接。

## 四、关键技术修改

- `app/src/main/java/com/xingshi/tv/MainActivity.java`
  - 接入 `SOURCE_WEBVIEW`、`SOURCE_JSTV` 和远程 `SOURCE_CUSTOM` 频道。
  - 支持远程频道加载、私密频道入口、多线路频道切换和 SOURCE_CUSTOM 动态类型分流。
  - 保留原生播放、MGTV Resolver、JSTV Resolver、央视频/央视网解析流程。

- `app/src/main/java/com/xingshi/tv/Channel.java`
  - 增加 `webUrl`、`webExtra`、`fullscreenType` 字段。
  - 增加 JSTV 相关播放参数字段。
  - 增加多线路显示名称 `sourceNames` 支持。
  - 保留原有 `url / urls / streamId / yangshipin / mgtv` 字段。

- `app/src/main/java/com/xingshi/tv/ChannelCatalog.java`
  - 频道配置加载器。
  - 从 `assets/channel_catalog.json` 读取内置频道组和频道列表。
  - 配置读取失败时回退到最小 CCTV fallback，避免 App 直接崩溃。
  - 新增已有 sourceType 的普通频道时不应修改此文件。

- `app/src/main/assets/channel_catalog.json`
  - 内置频道组和频道配置。
  - 当前内置 8 个频道组、147 个频道。
  - JSON 只保存频道数据和播放参数，不保存 Resolver 签名算法或解析规则。

- `app/src/main/java/com/xingshi/tv/RemoteChannelConfig.java`
  - 远程公开频道配置加载、校验、缓存和解析。
  - 将远程多线路频道映射为 `SOURCE_CUSTOM`。
  - 支持 AES-256-GCM 密文响应，解密并校验成功后才更新本地密文缓存。
  - 继续兼容旧明文配置和 `groups[]` 动态多频道组格式。
  - 远程失败时不影响本地内置频道。

- `app/src/main/java/com/xingshi/tv/RemoteConfigCrypto.java`
  - 远程公开频道配置密文包装识别和 AES-256-GCM 解密。
  - 不在日志中输出密钥、明文频道 JSON 或直播 URL。

- `app/src/main/java/com/xingshi/tv/PrivateChannelConfig.java`
  - 私密频道配置加载和校验。
  - 密码由用户运行时输入，仅保存在当前会话内。
  - 不在日志中输出密码或认证内容。

- `app/src/main/java/com/xingshi/tv/CustomStreamTypeDetector.java`
  - 对 unknown / 动态 URL 做轻量媒体类型探测。
  - 支持 HLS、FLV、MPEG-TS 判定。
  - 使用短超时和内存缓存，避免重复探测同一 URL。

- `app/src/main/java/com/xingshi/tv/WebPlayerActivity.java`
  - WebView 播放主入口。
  - MGTV `webExtra` 自动选台。
  - 视频检测 JS。
  - MGTV / Yangshipin 自动全屏策略。
  - WebChromeClient CustomView 全屏容器。
  - WebView 播放时频道菜单、返回提示、触摸拦截、Loading Overlay。

- `app/src/main/java/com/xingshi/tv/JstvLiveResolver.java`
  - JSTV 江苏广电原生 m3u8 解析。
  - 支持不同频道 path。
  - 每次切台重新生成签名，不做长时间缓存。

- `app/src/main/res/raw/control.html`
  - 直播源管理页面。
  - 品牌文案改为星视TV。
  - 增加星视TV项目主页和 NativeWasmTv 来源说明。

- `app/build.gradle`
  - `applicationId 'com.xingshi.tv'`
  - `targetSdkVersion 28`
  - `versionCode 11`
  - `versionName '1.3.4'`

## v1.3.4 稳定节点

- 完善央视、卫视节目单。
- 完善港台频道节目单。
- 优化 EPG 缓存与自动刷新机制。
- 修复跨日后节目单可能无法及时更新的问题。
- 提升节目单加载稳定性。

## v1.3.3 稳定节点

- 新增统一输入动作层，优化遥控器与键盘操作一致性。
- 新增 `KEYCODE_CHANNEL_UP/DOWN` 遥控器频道键支持。
- 优化 Android TV / 电视盒子私密频道密码输入。
- 新增 IJK 主播放页触屏上下滑动换台、左右滑动换线路。
- 新增 IJK 主播放页鼠标左键打开菜单、滚轮换台支持。
- 优化 IJK 与 WebView 的频道菜单按键处理。
- 保持广东频道 GDTV_PC 与原有播放逻辑兼容。

## v1.3.2 稳定节点

- 恢复广东频道，新增 17 路 GDtV PC 页面播放。
- 优化广东频道 WebView 播放兼容性。
- 远程频道配置新增 AES-256-GCM 加密支持。
- 远程配置本地缓存改为密文缓存。
- 保持旧明文配置兼容。
- 保持远程动态多频道组兼容。

## v1.3.1 稳定节点

- 默认启动进入 `CCTV-1 综合`。
- `SOURCE_CUSTOM` 多线路失败后自动尝试备用线路。
- 多线路频道记录最近一次成功 source index，下次优先尝试。
- 当前频道全部线路失败后，只在同频道组内自动尝试下一个频道，最多一轮。
- 远程频道配置支持根节点 `groups[]` 动态多频道组，同时兼容旧版 `group + channels[]` 单组格式。
## 五、当前稳定版本状态

- 当前发布版本：`v1.3.4`
- Release APK：`XingShiTV-v1.3.4.apk`
- package/applicationId：`com.xingshi.tv`
- App名称：星视TV
- targetSdk：28
- 编译状态：BUILD SUCCESSFUL

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
- SOURCE_CUSTOM 的 `.m3u8 -> HlsProxyServer`、`.ts/.flv -> IJK Direct` 和动态探测分流规则

如果必须修改，应先保留当前 APK 和日志基准，逐项验证：MGTV 经视频道自动选台、MGTV 自动全屏、央视频湖南卫视自动全屏、WebView 菜单交互、SOURCE_CUSTOM 的 m3u8/TS/FLV/动态 URL 播放路径。

## 七、后续规划

- 继续筛选更稳定的远程频道源。
- 继续优化远程频道异常提示和线路可用性标记。
- WebView 启动加载页继续优化：视觉样式、频道LOGO、动画、异常状态提示。
- 扩展更多原生 Resolver 和网页直播频道。
- 继续优化 UI 成品感：频道菜单、管理页、加载页、退出提示。

## 八、新增频道规则

以后新增频道时：

1. 先判断属于已有 sourceType 还是新平台。
2. 如果属于已有 sourceType，只修改频道配置或远程配置。
3. 只有新平台需要新解析逻辑时，才新增 SOURCE 和 Resolver。
4. 不再在 Java 中硬编码普通频道列表。

## 九、开发原则补充

- Existing Solution First：新需求或故障处理前，先检查项目内部成熟实现，优先复用后再做最小差异适配。
- IJK Quick Check First：新直播源先做 DIRECT playlist/segment 快检和 Android/IJK 快速起播判断；短效 token、复杂签名、频繁续签的网站要尽快比较 WebView，不为“理论可用 IJK”投入过度深测。
