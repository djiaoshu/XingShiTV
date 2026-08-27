# 看看新闻直播源分析归档

分析对象：[https://live.kankanews.com/huikan](https://live.kankanews.com/huikan)

本文用于归档看看新闻直播页从网页分析到 DIRECT 成功播放的完整技术过程，后续遇到“API 能成功返回播放地址，但 HLS/CDN 请求 403”的直播站点时优先参考。文档只保留长期复用的算法、字段、接口结构和脱敏示例，不保存短期 JWT、Cookie、个人 IP、本机代理地址或当前有效播放 token。

## 1. 页面与播放器结构

看看新闻回看/直播页是 Nuxt/Vue SPA。初始 HTML 主要负责加载 Nuxt 运行时和页面 chunk，直播频道、播放器配置和播放地址不会以明文 m3u8 形式直接出现在 HTML 中。

关键前端资源形态：

- Nuxt 页面入口：`/_knews_nuxt/js/pages/huikan/index.v2.42.21.js`
- 直播页业务 chunk：`/_knews_nuxt/js/pages/huikan/index/pages/index.v2.42.21.js`
- 播放器：xgplayer + HLS

页面播放流程大致为：

```text
live.kankanews.com/huikan
-> Nuxt/Vue 页面加载
-> 调用频道列表 API
-> 点击/默认选择频道
-> 调用频道详情 API
-> 获得加密 live_address
-> 前端 RSA 分段解密
-> xgplayer 加载鉴权 HLS
```

因此，不能把网页 URL 直接当成播放源，也不能只靠 HTML 静态扫描获得明文 m3u8。正确方向是还原前端 API 签名、解密和 CDN 鉴权上下文，再交给 IJK 原生播放。

## 2. 频道 API

API 基础域名：

```text
https://kapi.kankanews.com
```

核心接口：

```text
GET /content/pc/tv/channels
GET /content/pc/tv/channel/detail?channel_id={channelId}
```

其中：

- `channels` 返回页面可展示频道列表。
- `channel/detail` 返回单频道详情，包含 `live_address`、版权限制状态、频道/节目状态等字段。

请求需要携带 KAPI 签名相关字段：

```text
platform=pc
version=2.42.21
nonce=<8位随机字符串>
timestamp=<Unix秒>
Api-Version=v1
sign=<double-md5签名>
M-Uuid=<21位nanoid>
```

签名生成逻辑：

1. 将业务参数和公共参数放入同一个参数表，例如 `channel_id`、`platform`、`version`、`nonce`、`timestamp`。
2. 按 key 字典序排序。
3. 拼接为 `key=value&key=value`。
4. 追加前端固定 salt。
5. 对拼接结果做 MD5，再对第一次 MD5 结果做第二次 MD5。
6. 结果使用小写 hex，放入 `sign`。

脱敏伪代码：

```text
signed_base = join(sorted(params), "&") + KAPI_SIGN_SALT
sign = md5(md5(signed_base))
```

注意：`Api-Version` 是请求头，`platform/version/nonce/timestamp/sign` 也按前端实际请求放入请求头。不同接口是否把业务参数放入 URL query，应保持和前端一致。

## 3. live_address 解密

`channel/detail` 返回的播放信息不是明文 URL，而是加密后的 `live_address`。

典型结构：

```json
{
  "code": "1000",
  "data": {
    "channel": {
      "id": 1,
      "name": "东方卫视",
      "live_address": "<encrypted-live-address>"
    }
  }
}
```

前端解密特征：

- 前端模块内置 RSA public key。
- `live_address` 先 base64 解码为字节流。
- 按 128 字节一段处理。
- 每段通过 RSA public key 做解密/验签式还原。
- 对 PKCS#1 v1.5 padding 去填充。
- 拼接后得到明文 HLS URL。

实现 Resolver 时可使用 Java RSA 公钥运算，或手动使用 `RSAPublicKey` 的 modulus/exponent 做 `modPow` 后进行 PKCS#1 v1.5 unpad。要注意分段处理，不能把完整密文一次性解密。

解密后的 URL 类型包括：

```text
https://volc-stream.kksmg.com/live/{stream}/index.m3u8?token=<jwt>&volcSecret=<server-sign>&volcTime=<server-time>
https://tencent-stream.kksmg.com/live/{stream}.m3u8?token=<jwt>&txSecret=<server-sign>&txTime=<server-time>
https://ws-channels.kksmg.com/live/{stream}/playlist.m3u8?token=<jwt>
```

`volcSecret/volcTime`、`txSecret/txTime`、JWT 均由服务端生成，客户端不要自行伪造或在文档中归档当前有效值。

## 4. 第一次失败过程

第一轮还原时已经可以做到：

- KAPI 请求成功，返回 `code=1000`。
- `live_address` RSA 分段解密成功。
- 得到看起来完整的 m3u8/CDN URL。

但在 DIRECT 环境下请求 m3u8 仍返回 403。

排查过的因素包括：

- Cookie：不是主要原因。
- Referer/Origin：需要保持合理网页来源，但单独补齐不能解决 403。
- TLS/浏览器常见 header：不是根因。
- 普通 UUID：不能替代前端生成的设备 ID。
- 空 `M-Uuid` 或随意字符串：API 可能仍返回地址，但 CDN 不放行。

这个阶段的重要结论是：**API 能返回 m3u8 并不代表播放鉴权已经通过。**

## 5. 403 根因

最终根因是 KAPI `nonce` 生成规则，而不是 Android TLS、IJK、HlsProxyServer 或 `M-Uuid` 本身。

看看新闻前端使用的是 21 位 nanoid 风格设备标识。该值参与服务端下发 token 的上下文绑定，是播放鉴权的必要条件。缺失、空值、普通 UUID 或随意字符串时，`channel/detail` 仍可能返回加密播放地址，但解密后的 CDN m3u8 请求会 403。

`M-Uuid` 有效格式特征：

```text
长度：21
字符集：数字、大写字母、小写字母、下划线、短横线
示例：<21-char-nanoid>
```

后续 PC / Android 交叉重放证明：同一个合法 `M-Uuid` 在 PC KAPI 实现下可生成可播放 URL，但 Android 旧 KAPI 实现仍会生成 403 URL。因此本轮 Android 403 的最终根因是 `nonce` 字符集错误。

KAPI `nonce` 的正式规则：

```text
长度：8
字符集：abcdefghijklmnopqrstuvwxyz0123456789
```

当 `M-Uuid` 与 `nonce` 均符合前端规则后：

- `channel/detail` 返回正常。
- 解密 m3u8 请求 HTTP 200。
- TS/fMP4 分片请求 HTTP 200。
- DIRECT 持续播放测试通过。

## 6. UA 绑定

解密后的 JWT 中存在 `ua_hash`。该值会随 User-Agent 变化。

因此，Resolver 获取播放地址时使用的 User-Agent，必须和 `HlsProxyServer` 后续请求 playlist/TS 分片使用的 User-Agent 保持一致。否则可能出现：

```text
API成功
live_address解密成功
m3u8 URL看似完整
playlist或分片403
```

当前建议固定使用 PC User-Agent：

```text
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36
```

同时保持：

```text
Referer: https://live.kankanews.com/
Accept: */*
```

## 7. IP/token 鉴权特征

JWT 中可观察到以下 claims：

```text
app
domain
exp
iat
nonce
platform
stream_name
ua_hash
user_id
user_ip
uuid
version
```

关键判断：

- `user_ip` 表明 token 与服务端识别到的出口 IP 存在绑定。
- `iat/exp` 表明 token 有有效期，不能长期缓存。
- `stream_name` 表明 token 与频道流名绑定。
- `uuid` 对应 `M-Uuid`。
- `ua_hash` 对应请求 User-Agent。
- `volcSecret/volcTime`、`txSecret/txTime` 等 CDN 参数由服务端生成。

客户端 Resolver 应负责生成有效 `M-Uuid`、KAPI 签名并获取服务端下发的播放 URL；不应本地伪造 JWT、CDN secret 或服务端时间签名。

## 8. DIRECT 验证方法

直播流验证阶段必须禁用 V2Ray/系统代理，避免把“代理可播”误判为“中国大陆 DIRECT 可播”。

PowerShell 环境检查：

```powershell
Get-ChildItem Env: | Where-Object Name -Match 'proxy'
```

curl 验证：

```powershell
curl.exe --noproxy "*" "<m3u8-url>"
```

Python 验证：

```python
session.trust_env = False
```

或使用 `urllib.request.ProxyHandler({})` 禁止继承环境代理。

验证步骤：

1. 频道详情 API 成功。
2. RSA 解密得到 m3u8。
3. DIRECT 请求 playlist HTTP 200。
4. 解析 master/variant playlist。
5. DIRECT 请求 TS/fMP4 分片 HTTP 200。
6. 每隔约 8 秒刷新 playlist 并拉取最新分片。
7. 持续至少 90 秒。
8. 记录成功次数、失败次数、HTTP 状态、超时、断流。

本轮判断标准：8 个测试目标中，技术链路能完成 `API -> 解密 -> playlist 200 -> 分片 200 -> 90秒连续刷新` 的记为技术可接入；存在网页端版权屏蔽的频道即使技术链路可还原，也不作为优先上线对象。

## 9. 频道测试结果

| 频道 | 技术状态 | 版权/上线状态 |
| --- | --- | --- |
| 东方卫视 | `channel_id=1`，`stream_name=dfws4k`，DIRECT playlist/分片/90秒持续测试通过 | 推荐优先接入 |
| 第一财经 | `channel_id=5`，`stream_name=dycj`，DIRECT playlist/分片/90秒持续测试通过 | 推荐优先接入 |
| 都市频道 | `channel_id=4`，`stream_name=ylpd`，DIRECT playlist/分片/90秒持续测试通过 | 推荐优先接入 |
| 魔都眼 | `channel_id=11`，`stream_name=shanghaieye`，DIRECT playlist/分片/90秒持续测试通过 | 推荐优先接入 |
| 新纪实 | `channel_id=12`，`stream_name=xjspd`，DIRECT playlist/分片/90秒持续测试通过 | 推荐优先接入 |
| 五星体育 | `channel_id=10`，技术链路测试通过 | 体育赛事版权风险较高，建议候选/谨慎上线 |
| 哈哈炫动 | `channel_id=9`，技术链路曾通过 | 当前节目存在 `is_shield=1` 风险，暂缓 |
| 新闻综合 | `channel_id=2`，接口可分析 | 网页端明确出现“版权受限，此时段不提供电视网络转播服务”，从当前候选排除 |

后续上线时，应再次检查 `channel/detail` 的版权限制字段和网页端实际状态。

## 10. 最终星视TV接入建议

推荐新增独立原生播放类型：

```text
SOURCE_KANKANEWS
-> KankanewsLiveResolver
-> 21位 nanoid M-Uuid
-> KAPI签名
-> RSA分段解密 live_address
-> 鉴权 m3u8
-> HlsProxyServer
-> IJK
```

不要优先走 `SOURCE_WEBVIEW`。看看新闻的网页播放依赖 xgplayer 和 Nuxt 页面状态，但真实 HLS 已可稳定还原，原生 IJK 播放更符合星视TV当前架构。

接入注意：

- 每次切台重新请求 `channel/detail`，不要长期缓存完整 m3u8。
- `M-Uuid` 可在客户端稳定保存，也可至少在进程内保持一致。
- Resolver 与 HlsProxyServer 必须使用同一 User-Agent。
- HlsProxyServer 对 `kksmg.com` 站点添加独立请求头分支，避免影响 CCTV、央视频、MGTV、JSTV。

## 11. 经验总结

**API 能返回 m3u8 并不代表播放鉴权已经通过。**

以后遇到“API 成功、m3u8 地址也成功解析、但 CDN 403”的网站，优先检查：

- 设备 ID / UUID / nanoid。
- UA hash。
- IP 绑定。
- session 上下文。
- token/JWT claims。
- API 请求与 CDN 请求之间的上下文绑定。
- Resolver 请求 header 与播放器/代理请求 header 是否一致。

这类问题不要过早判定为“只能 WebView”。应先还原前端上下文，再做 DIRECT playlist 和分片验证。

## 12. 安全要求

归档和提交时必须遵守：

- 不保存当前短期有效 JWT/token。
- 不保存 Cookie。
- 不保存个人 IP。
- 不保存本机代理地址。
- 不保存本机绝对路径。
- 可以保存算法、字段名、接口结构和脱敏示例。
- 可以记录频道 ID、stream name、公开 API endpoint 和公开网页资源路径。

原始抓包 JSON、短期测试 URL、临时日志继续放在项目外测试目录，不提交到 GitHub。

## 13. 真机诊断复盘，2026-08-25

星视TV真机诊断 APK 曾对 5 个 Kankanews 上海频道进行逐层日志验证，早期失败表现为：

```text
M-Uuid valid=true length=21
-> KAPI HTTP 200
-> business code=1000
-> RSA decrypt success
-> 获得鉴权 m3u8
-> Resolver 直接请求 playlist HTTP 403
-> HlsProxyServer 请求同一 playlist HTTP 403
-> IJK prepared=false / -10000
```

这说明失败发生在 IJK 和 HlsProxyServer 之前：Android Resolver 获取到的鉴权 m3u8 已经无法通过 CDN playlist 校验。

### 13.1 交叉重放结论

PC DIRECT 与 Android 夜神交叉重放证明：

```text
PC 生成有效 URL:
PC playlist 200 / TS 206
Android playlist 200 / TS 206

旧 Android 生成 URL:
PC playlist 403
Android playlist 403
```

因此已排除：

- Android 无法访问 Kankanews CDN。
- Android `HttpURLConnection` 不能拉取有效 playlist/TS。
- IJK 公共参数导致首个失败。
- HlsProxyServer 公共代理行为导致首个失败。
- PC / Android 出口 IP 或 DNS/CDN 节点差异。

问题跟随“Android KAPI 生成的鉴权 URL”移动。

### 13.2 UUID/KAPI 矩阵

进一步做 `M-Uuid` 与 KAPI 实现 2x2 实验：

| M-Uuid 来源 | KAPI 实现 | PC 重放 | Android 重放 |
| --- | --- | --- | --- |
| PC UUID | PC | playlist 200 / TS 206 | playlist 200 / TS 206 |
| PC UUID | Android 旧实现 | playlist 403 | playlist 403 |
| Android UUID | PC | playlist 200 / TS 206 | playlist 200 / TS 206 |
| Android UUID | Android 旧实现 | playlist 403 | playlist 403 |

结论：

- 合法 21 位 nanoid `M-Uuid` 是必要条件。
- `JWT uuid` 与请求 header 中的 `M-Uuid` 直接一致。
- 但 `M-Uuid` 本身不是本轮 Android 403 的根因。

### 13.3 最终根因：KAPI nonce 规则

最终确认根因是 Android 端 KAPI `nonce` 生成规则错误。

已确认规则：

```text
KAPI nonce:
长度 = 8
字符集 = a-z + 0-9
```

旧 Android 实现错误地复用了 nanoid 字符集，可能出现：

```text
A-Z
a-z
0-9
_
-
```

错误 nonce 的迷惑性很强：它不一定导致 KAPI 报错，而是可能出现：

```text
KAPI HTTP 200
business code=1000
RSA 解密成功
得到完整鉴权 URL
-> CDN playlist HTTP 403
```

修正为小写字母数字 8 位 nonce 后，Android 连续 3 次重新取流验证均成功：

| 次数 | KAPI | playlist | 首个 TS |
| --- | --- | --- | --- |
| 1 | HTTP 200 / code=1000 | 200 | 206 |
| 2 | HTTP 200 / code=1000 | 200 | 206 |
| 3 | HTTP 200 / code=1000 | 200 | 206 |

### 13.4 正式播放验证

修正 nonce 后，星视TV正式播放链验证通过：

```text
频道
-> KankanewsLiveResolver
-> 21 位 nanoid M-Uuid
-> 8 位 a-z0-9 nonce
-> KAPI 签名
-> RSA 分段解密 live_address
-> 鉴权 m3u8
-> HlsProxyServer
-> IJK
```

已验证第一批上海频道：

- 东方卫视
- 第一财经
- 都市频道
- 魔都眼
- 新纪实

5 个频道均已进入 `player prepared`，并在 60 到 90 秒播放窗口内持续拉取 playlist/segment，未出现 `-10000/0`。切台顺序 `东方卫视 -> 第一财经 -> 都市频道 -> 魔都眼 -> 新纪实 -> 东方卫视` 也验证通过。

### 13.5 经验总结

本案例最重要的长期经验：

**API 能返回 m3u8 并不代表播放鉴权已经通过。**

以后遇到：

```text
API HTTP 200
业务 code 成功
解密/解析出 m3u8
CDN playlist 403
```

不要只检查播放器、代理和 CDN 请求头，也要重点检查 API 阶段参与签名或服务端风控的动态字段格式，例如：

- nonce 字符集和长度。
- timestamp 精度和时钟偏差。
- `M-Uuid` / 设备 ID。
- UA hash。
- token/JWT claims。
- API 请求与 CDN 请求之间的上下文绑定。

Kankanews 当前推荐继续采用 `SOURCE_KANKANEWS -> KankanewsLiveResolver -> HlsProxyServer -> IJK`，不应优先改为 WebView。
