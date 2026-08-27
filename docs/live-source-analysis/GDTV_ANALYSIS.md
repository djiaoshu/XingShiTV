# GDTV / 荔枝网直播源分析

本文记录星视TV对广东广播电视台网页直播的技术分析过程。分析对象包括 PC 页面和移动端页面：

- PC: `https://www.gdtv.cn/tvChannelDetail/43`
- Mobile: `https://m.gdtv.cn/tvChannelDetail/45`

最终接入决策：正式频道采用 Mobile WebView，原生 Resolver 暂停正式使用并保留为研究/备用。

## 1. 页面与播放器结构

PC 页面是一个前端 SPA 页面，入口页面只有基础 DOM，实际频道列表、频道详情、播放器配置都由 JS bundle 加载。

移动端 `m.gdtv.cn` 同样是前端 SPA 页面，资源来自 `sitecdn.itouchtv.cn/sitecdn/m/spa/`。移动端页面不是裸 m3u8，也不是简单 iframe，它同样依赖接口、签名请求头和 TCDN 播放地址。

两端播放链路都以 HLS 为主：

```text
页面路由 tvChannelDetail/{id}
↓
频道列表 / 频道详情 API
↓
TCDN getParam
↓
返回带 t_token 的 m3u8
↓
video.js / HLS 播放
```

PC 页面使用 `video.js` 与 HLS 相关前端库；移动端也通过网页播放器加载同类 HLS 地址。

## 2. PC 与 Mobile API 对比

### PC 页面

示例页面：

```text
https://www.gdtv.cn/tvChannelDetail/45
```

关键接口：

```text
GET https://gdtv-api.gdtv.cn/api/tv/v2/tvChannel?category=0
GET https://tcdn-api.itouchtv.cn/getParam
GET https://gdtv-api.gdtv.cn/api/tv/v2/tvChannel/45?tvChannelPk=45&node=...
GET https://gdtv-api.gdtv.cn/api/tv/v2/tvMenu?tvChannelPk=45&beginAt=...&endAt=...
```

关键请求头：

```text
x-itouchtv-client: WEB_PC
x-itouchtv-device-id: WEB_<uuid>
x-itouchtv-ca-timestamp: <milliseconds>
x-itouchtv-ca-key: <public key id>
x-itouchtv-ca-signature: <signature>
Referer: https://www.gdtv.cn/
User-Agent: PC Chrome UA
```

### Mobile 页面

示例页面：

```text
https://m.gdtv.cn/tvChannelDetail/45
```

关键接口：

```text
GET https://gdtv-api.gdtv.cn/api/tv/v2/tvChannel?category=0
GET https://gdtv-api.gdtv.cn/api/tv/v2/tvMenu?tvChannelPk=45&beginAt=...&endAt=...
GET https://gdtv-api.gdtv.cn/api/tvColumn/v1/tvColumn/45
GET https://tcdn-api.itouchtv.cn/getParam
GET https://gdtv-api.gdtv.cn/api/tv/v2/tvChannel/45?node=...
```

关键请求头：

```text
x-itouchtv-client: WEB_M
x-itouchtv-device-id: WEBM_<uuid>
x-itouchtv-ca-timestamp: <milliseconds>
x-itouchtv-ca-key: <public key id>
x-itouchtv-ca-signature: <signature>
Referer: https://m.gdtv.cn/
User-Agent: Mobile Chrome UA
```

结论：

- 移动端仍使用 `gdtv-api.gdtv.cn`。
- 移动端仍需要 `x-itouchtv-*` 签名头。
- 移动端没有绕过 TCDN，也没有提供长期固定 m3u8。
- 移动端 client/device-id/Referer 与 PC 不同：`WEB_M`、`WEBM_`、`https://m.gdtv.cn/`。

## 3. m3u8 与 token 特征

PC 与 Mobile 对同一频道 `45 广东新闻` 最终都获得同类播放地址：

```text
https://tcdn.itouchtv.cn/live/xwpd.m3u8?t_token=<redacted>
```

其中：

- `xwpd` 是广东新闻频道的 stream 标识。
- `t_token` 是短期鉴权参数。
- 不应保存完整 `t_token`。
- 不应把抓包得到的 m3u8 固定写入 `channel_catalog.json`。

## 4. PC / Mobile 交叉重放

本轮对同一频道 `45 广东新闻` 分别捕获 PC 与 Mobile 新鲜 m3u8，并使用 DIRECT 网络重放。

短时间交叉重放结果：

| 测试项 | playlist | 首个分片 |
| --- | --- | --- |
| PC URL + PC headers | 200 | 200 |
| PC URL + Mobile headers | 200 | 200 |
| Mobile URL + Mobile headers | 200 | 200 |
| Mobile URL + PC headers | 200 | 200 |

结论：

- 刚生成的 m3u8 在短时间内不明显绑定 PC/Mobile UA。
- 刚生成的 m3u8 在短时间内不明显绑定 PC/Mobile Referer。
- 真正风险在于 token 生命周期和浏览器/CDN上下文，而不是“PC URL 只能 PC 用、Mobile URL 只能 Mobile 用”。

## 5. 固定 URL 的 5 分钟 DIRECT 观察

### PC 新鲜 URL

对 PC 页面捕获的新鲜 m3u8 做 5 分钟 DIRECT 重放：

```text
0-50s: playlist 200, segment 200
约 66s 起: playlist 403
之后持续 403
```

### Mobile 新鲜 URL

对 Mobile 页面现抓的新鲜 m3u8 做 5 分钟 DIRECT 重放：

```text
0-99s: playlist 200, segment 200
约 115s 起: playlist 403
之后持续 403
```

结论：

- PC 与 Mobile 都存在短效 token。
- Mobile 这次样本有效时间略长，但没有从根本上解决过期问题。
- 固定 URL 不能作为星视TV内置频道地址。

## 6. 网页连续播放观察

### PC 网页 5 分钟观察

PC 官方页面持续 5 分钟：

```text
api requests: 12
getParam: 2
detail: 1
m3u8 requests: 98
m3u8 responses: 98
m3u8 HTTP: 200 x 98
unique m3u8 urls: 1
unique token hashes: 1
```

现象：

- PC 官方网页 5 分钟内反复请求同一个 m3u8 URL。
- 浏览器内请求全部 200。
- 同一个 URL 被 curl DIRECT 重放时，约 1 分钟后变为 403。

推断：

- 播放稳定性可能不仅由 URL 字符串决定。
- 可能存在浏览器连接状态、HTTP 客户端指纹、CDN上下文或请求时序差异。
- Resolver 不能只做到“拿到 m3u8”，还必须验证 Android/Java/HlsProxyServer 能长期刷新 playlist。

### Mobile 网页 5 分钟观察

移动端官方页面 5 分钟抓包：

```text
api requests: 8
getParam: 2
detail: 2
m3u8 requests: 2
m3u8 responses: 2
m3u8 HTTP: 200 x 1, 403 x 1
unique m3u8 urls: 2
unique token hashes: 2
```

现象：

- 移动端页面会重新获取 detail / TCDN 参数。
- 抓包中出现过一个 403 和一个 200。
- 移动端行为不比 PC 更简单。

## 7. 频道可收录数量

`tvChannel?category=0` 可发现 18 路电视直播：

| 频道 | pk | stream | 技术状态 | 收录建议 |
| --- | ---: | --- | --- | --- |
| 广东卫视 | 43 | gdws | m3u8/分片可访问，需动态 token | 候选 |
| 广东珠江 | 44 | gdzj | m3u8/分片可访问，需动态 token | 候选 |
| 广东新闻 | 45 | xwpd | m3u8/分片可访问，需动态 token | 候选 |
| 广东民生 | 48 | gdgg | m3u8/分片可访问，需动态 token | 候选 |
| 广东体育 | 47 | gdty | m3u8/分片可访问，需动态 token | 候选 |
| 大湾区卫视 | 51 | nfws | m3u8/分片可访问，需动态 token | 候选 |
| 大湾区卫视（海外版） | 46 | gdgj | m3u8/分片可访问，需动态 token | 候选 |
| 广东影视 | 53 | gdys | m3u8/分片可访问，需动态 token | 候选 |
| 4K超高清 | 16 | gdzy | m3u8/分片可访问，需动态 token | 候选 |
| 广东少儿 | 54 | gdse | m3u8/分片可访问，需动态 token | 候选 |
| 嘉佳卡通 | 66 | jjkt | m3u8/分片可访问，需动态 token | 候选 |
| 南方购物 | 42 | nfgw | 技术可播 | 排除，购物频道 |
| 岭南戏曲 | 15 | lnxq | m3u8/分片可访问，需动态 token | 候选 |
| 广东移动 | 74 | ydpd | m3u8/分片可访问，需动态 token | 候选 |
| 广东台经典剧 | 100 | lizhi | m3u8/分片可访问，需动态 token | 候选 |
| 纪录片 | 94 | jilupian | m3u8/分片可访问，需动态 token | 候选 |
| 健康 | 99 | health | m3u8/分片可访问，需动态 token | 候选 |
| GRTN生活 | 102 | life | m3u8/分片可访问，需动态 token | 候选 |

按星视TV海外/站点筛选规则，购物频道提前排除，因此本轮可进入下一阶段的候选频道为：

```text
17 路
```

这些频道还不是“可直接上线稳定频道”，需要 Resolver 与 Android 播放链进一步验证。

## 8. PC 页面方案 vs Mobile 页面方案

| 项目 | PC 页面方案 | Mobile 页面方案 |
| --- | --- | --- |
| 页面入口 | `www.gdtv.cn/tvChannelDetail/{id}` | `m.gdtv.cn/tvChannelDetail/{id}` |
| API host | `gdtv-api.gdtv.cn` | `gdtv-api.gdtv.cn` |
| TCDN API | `tcdn-api.itouchtv.cn/getParam` | `tcdn-api.itouchtv.cn/getParam` |
| client | `WEB_PC` | `WEB_M` |
| device-id | `WEB_<uuid>` | `WEBM_<uuid>` |
| Referer | `https://www.gdtv.cn/` | `https://m.gdtv.cn/` |
| 签名复杂度 | 高，需要复现 `x-itouchtv-*` | 高，同样需要复现 `x-itouchtv-*` |
| m3u8形式 | `tcdn.itouchtv.cn/live/{stream}.m3u8?t_token=...` | 基本相同 |
| token寿命 | 本轮约 1 分钟后 curl replay 403 | 本轮约 2 分钟后 curl replay 403 |
| 网页连续播放 | PC浏览器 5分钟内 m3u8 98/98 HTTP 200 | 移动端抓包中出现一次403和一次200 |
| Android兼容性推断 | 需要专项真机诊断 | 不一定更简单，仍需专项真机诊断 |
| 推荐优先级 | 优先研究 | 暂不优先 |

## 9. 星视TV接入建议

当前不建议直接正式开发 `GdtvLiveResolver` 批量上线。

推荐后续路线：

```text
SOURCE_GDTV
↓
GdtvLiveResolver
↓
生成/持久化 WEB_PC device-id
↓
复现 x-itouchtv-* API签名
↓
getParam
↓
tvChannel/detail
↓
动态 m3u8
↓
GDTv专用 playlist/segment 请求头
↓
HlsProxyServer
↓
IJK
```

关键注意：

- 不要把固定 m3u8 写进频道配置。
- 不要保存短效 `t_token`。
- 不要因为移动端页面存在就默认 `SOURCE_WEBVIEW`。
- 移动端仍有同样签名体系和短效 token，不能明显降低 Resolver 难度。
- PC 官方网页 5分钟播放稳定，但 curl replay 同 URL 约1分钟后 403，说明后续必须做 Android 三栈验证。
- 如果 Android Java/OkHttp/HlsProxyServer 长时间 playlist 刷新仍 403，再评估 WebView 兜底。

当前推荐：

```text
推荐：带鉴权 Resolver + IJK
```

但在正式接入前，必须先完成：

1. 从官方前端 signer chunks / fallback 中准确提取 `x-itouchtv-ca-signature` 生成逻辑。
2. 做 Android 端 GDTv NetworkProbe，比较 Java、OkHttp、WebView 对同一新鲜 m3u8 的 playlist/segment 行为。
3. 验证 HlsProxyServer 在 5-10 分钟内持续刷新 playlist 是否仍为 200。
4. 如果 playlist 过期，需要设计 GDTv 专用 URL 刷新机制，不要破坏其他源。

## 10. 测试资料

本轮测试资料保存在项目测试目录：

```text
tests/live-source/Gdtv/
```

主要文件：

```text
m-gdtv-45.html
gdtv-pc-mobile-records.json
gdtv-pc-mobile-summary.json
gdtv-pc-mobile-replay-validation.json
gdtv-mobile-fresh-5min.json
gdtv-pc-fresh-records.json
gdtv-mobile-fresh-records.json
probe-gdtv-pc-mobile.cjs
probe-gdtv-single.cjs
validate_gdtv_pc_mobile_replay.py
observe_gdtv_fresh.py
summarize_gdtv_pc_mobile.cjs
```

测试资料可能包含短期失效 URL，仅用于本地技术分析，不应提交敏感 token 到公开文档。

## 11. 经验总结

GDTv 的核心经验与 Kankanews 类似：

```text
API能返回m3u8，不代表原生播放链已经稳定。
```

特别是：

- m3u8 有短效 `t_token`。
- 浏览器内连续请求同一个 URL 可能持续 200。
- curl/Java 重放同一个 URL 可能很快 403。
- 后续必须把“获取播放地址”和“长期刷新playlist/segment”作为两个独立验证点。

因此，GDTv 不能按普通固定 HLS 源处理，应作为带鉴权和上下文要求的独立站点接入。

## 12. 当前接入决策更新

后续真机测试确认，GDTv Mobile WebView 方案可正常播放广东卫视，并能通过页面播放器按钮触发 `WebChromeClient.onShowCustomView()` 建立 Android 全屏。正式频道配置采用：

```text
SOURCE_WEBVIEW
Mobile GDtV page
fullscreenType=GDTV
https://m.gdtv.cn/tvChannelDetail/{pk}
```

原生 `GdtvLiveResolver -> HlsProxyServer -> IJK` 已证明技术上可行，但 GDtV m3u8 使用约 132 秒短效 token，token 过期后的重新解析和播放器恢复会造成约 5 秒可感知中断。因此原生方案暂停正式使用，仅保留为后续技术研究备用；本阶段不继续优化 GDTv 原生 token 刷新、后台预取或 IJK/HLS 公共链路。

已加入正式「广东频道」组的候选频道共 17 路：广东卫视、广东珠江、广东新闻、广东民生、广东体育、大湾区卫视、大湾区卫视（海外版）、广东影视、4K超高清、广东少儿、嘉佳卡通、岭南戏曲、广东移动、广东台经典剧、纪录片、健康、GRTN生活。

明确排除：南方购物。

GDTv WebView 已验证：

- 正常起播。
- 官方网页自行维护 token，连续播放稳定。
- `prism-fullscreen-btn -> Native MotionEvent -> onShowCustomView()` 全屏成功。
- Back 键逻辑正常。

闪屏最终处理：

- 不采用固定延迟。
- 不采用频道8的 `realTouchPlayer()` 双击播放器层方案。
- 保留 `prism-fullscreen-btn -> Native MotionEvent -> onShowCustomView()` 链路。
- `fullscreenType=GDTV` 时，只有同时满足 `fullscreenchange [object HTMLDivElement]`、`onShowCustomView()` 后再次收到 `video playing`、`customView != null`，才隐藏 Loading Overlay。

该方案已由实际安装测试确认，可避免 `Loading 100% -> 普通 GDtV 页面/播放器短暂露出 -> CustomView 全屏` 的闪屏。
