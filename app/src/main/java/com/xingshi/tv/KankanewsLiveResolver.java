package com.xingshi.tv;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class KankanewsLiveResolver {
    static final String USER_AGENT = KankanewsContext.USER_AGENT;
    static final String STREAM_REFERER = KankanewsContext.STREAM_REFERER;

    private static final String TAG = "KankanewsLiveResolver";
    private static final String DEBUG_TAG = "KANKAN";
    private static final String CTX_TAG = "KANKAN_CTX";
    private static final String API_BASE = "https://kapi.kankanews.com";
    private static final String API_VERSION = "v1";
    private static final String WEB_VERSION = "2.42.21";
    private static final String SIGN_SALT = "28c8edde3d61a0411511d3b1866f0636";
    private static final String PUBLIC_KEY_BASE64 =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDP5hzPUW5RFeE2xBT1ERB3hHZI"
                    + "Votn/qatWhgc1eZof09qKjElFN6Nma461ZAwGpX4aezKP8Adh4WJj4u2O54xCXDt"
                    + "wzKRqZO2oNZkuNmF2Va8kLgiEQAAcxYc8JgTN+uQQNpsep4n/o1sArTJooZIF17E"
                    + "tSqSgXDcJ7yDj5rc7wIDAQAB";

    private final Context context;
    private RSAPublicKey publicKey;

    KankanewsLiveResolver() {
        this(null);
    }

    KankanewsLiveResolver(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    String resolve(Channel channel) throws IOException {
        if (channel.kankanewsChannelId == null || channel.kankanewsChannelId.length() == 0) {
            throw new IOException("Missing Kankanews channelId for " + channel.name);
        }
        String channelId = channel.kankanewsChannelId;
        String uuid = getResolverClientId();
        Log.i(TAG, "Kankanews resolve channel=" + channel.name
                + " channelId=" + channelId
                + " stream=" + nullToEmpty(channel.kankanewsStreamName));
        Log.i(DEBUG_TAG, "resolver start channel=" + channel.name
                + " channelId=" + channelId
                + " stream=" + nullToEmpty(channel.kankanewsStreamName));
        Log.i(DEBUG_TAG, "uuid valid=" + isValidNanoId(uuid)
                + " length=" + uuid.length()
                + " uaHash=" + safeHash(USER_AGENT));
        if (KankanewsContext.isDebugDiagnosticsEnabled()) {
            Log.i(CTX_TAG, "resolver api uaHash=" + KankanewsContext.getUserAgentHash()
                    + " uuidHash=" + KankanewsContext.getClientIdHash(context)
                    + " uuidValid=" + KankanewsContext.isCurrentClientIdValid(context)
                    + " uuidLength=" + KankanewsContext.getClientIdLength(context));
        }

        JSONObject body = getJson("/content/pc/tv/channel/detail", channelId);
        String code = body.optString("code", "");
        Log.i(DEBUG_TAG, "api business code=" + code
                + " message=" + body.optString("message", ""));
        if (!"1000".equals(code)) {
            throw new IOException("Kankanews API code=" + code
                    + " message=" + body.optString("message", ""));
        }
        String liveAddress;
        try {
            liveAddress = findString(body, "live_address");
        } catch (JSONException error) {
            throw new IOException("Unable to read Kankanews live_address", error);
        }
        if (liveAddress == null || liveAddress.length() == 0) {
            throw new IOException("Missing Kankanews live_address for " + channel.name);
        }
        Log.i(DEBUG_TAG, "live_address encrypted present=true length=" + liveAddress.length());
        String streamUrl = decryptLiveAddress(liveAddress);
        Log.i(DEBUG_TAG, "decrypt success=" + (streamUrl != null && streamUrl.length() > 0));
        if (streamUrl == null || streamUrl.length() == 0) {
            throw new IOException("Unable to decrypt Kankanews live_address for " + channel.name);
        }
        if (!streamUrl.contains(".m3u8")) {
            throw new IOException("Kankanews decrypted URL is not HLS for " + channel.name);
        }
        Log.i(DEBUG_TAG, "stream host=" + hostOf(streamUrl)
                + " path=" + pathOf(streamUrl)
                + " isHttp=" + isHttpUrl(streamUrl)
                + " isM3u8=" + streamUrl.contains(".m3u8"));
        probeStream(channel.name, streamUrl);
        Log.i(TAG, "Kankanews resolve success channel=" + channel.name
                + " channelId=" + channelId
                + " host=" + hostOf(streamUrl)
                + " stream=" + nullToEmpty(channel.kankanewsStreamName));
        Log.i(DEBUG_TAG, "resolve success channel=" + channel.name
                + " host=" + hostOf(streamUrl));
        return streamUrl;
    }

    private JSONObject getJson(String path, String channelId) throws IOException {
        String url = API_BASE + path + "?channel_id=" + channelId;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(9000);
            connection.setUseCaches(false);
            applyApiHeaders(connection, channelId);
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = input == null ? "" : new String(readFully(input), "UTF-8");
            Log.i(DEBUG_TAG, "api http=" + status
                    + " path=" + path
                    + " bodyBytes=" + body.length()
                    + " uaHash=" + safeHash(USER_AGENT));
            if (status < 200 || status >= 300) {
                throw new IOException("Kankanews API HTTP " + status);
            }
            return new JSONObject(body);
        } catch (JSONException error) {
            throw new IOException("Invalid Kankanews API JSON", error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void applyApiHeaders(HttpURLConnection connection, String channelId)
            throws IOException {
        Map<String, String> signParams = new LinkedHashMap<String, String>();
        KankanewsContext.ApiHeaderOverride override =
                KankanewsContext.getDebugApiHeaderOverride();
        signParams.put("channel_id", channelId);
        signParams.put("platform", "pc");
        signParams.put("version", WEB_VERSION);
        signParams.put("nonce", override != null ? override.nonce : randomNonce());
        signParams.put("timestamp", override != null
                ? override.timestamp : String.valueOf(System.currentTimeMillis() / 1000L));
        signParams.put("Api-Version", API_VERSION);
        SignSnapshot signSnapshot = signSnapshot(signParams);
        String sign = override != null ? override.sign : signSnapshot.sign;

        connection.setRequestProperty("platform", "pc");
        connection.setRequestProperty("version", WEB_VERSION);
        connection.setRequestProperty("nonce", signParams.get("nonce"));
        connection.setRequestProperty("timestamp", signParams.get("timestamp"));
        connection.setRequestProperty("Api-Version", API_VERSION);
        connection.setRequestProperty("sign", sign);
        connection.setRequestProperty("M-Uuid", getResolverClientId());
        connection.setRequestProperty("User-Agent", KankanewsContext.getUserAgent());
        connection.setRequestProperty("Referer", KankanewsContext.PAGE_REFERER);
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        if (KankanewsContext.isDebugDiagnosticsEnabled()) {
            Log.i(CTX_TAG, "resolver kapi request method=GET"
                    + " host=" + URI.create(API_BASE).getHost()
                    + " path=/content/pc/tv/channel/detail"
                    + " queryKeys=channel_id"
                    + " headerKeys=Accept,Api-Version,M-Uuid,Referer,User-Agent,nonce,platform,sign,timestamp,version"
                    + " platform=pc"
                    + " version=" + WEB_VERSION
                    + " apiVersion=" + API_VERSION
                    + " nonceLength=" + signParams.get("nonce").length()
                    + " nonceHash=" + KankanewsContext.safeHash(signParams.get("nonce"))
                    + " timestamp=" + signParams.get("timestamp")
                    + " signLength=" + sign.length()
                    + " signHash=" + KankanewsContext.safeHash(sign)
                    + " computedSignHash=" + KankanewsContext.safeHash(signSnapshot.sign)
                    + " apiOverride=" + KankanewsContext.hasDebugApiHeaderOverride()
                    + " uuidHash=" + KankanewsContext.getClientIdHash(context)
                    + " uuidOverride=" + KankanewsContext.hasDebugClientIdOverride()
                    + " uaHash=" + KankanewsContext.getUserAgentHash()
                    + " acceptHash=" + KankanewsContext.safeHash(
                            "application/json, text/plain, */*")
                    + " refererHash=" + KankanewsContext.safeHash(KankanewsContext.PAGE_REFERER));
            Log.i(CTX_TAG, "resolver sign canonical keys=" + signSnapshot.keys
                    + " canonicalHash=" + signSnapshot.canonicalHash
                    + " canonicalWithSaltHash=" + signSnapshot.canonicalWithSaltHash
                    + " firstMd5Hash=" + KankanewsContext.safeHash(signSnapshot.firstMd5)
                    + " finalSignHash=" + KankanewsContext.safeHash(signSnapshot.sign));
        }
    }

    private void probeStream(String channelName, String playlistUrl) {
        try {
            ProbeResult playlist = probeUrl(playlistUrl, false);
            Log.i(DEBUG_TAG, "playlist http=" + playlist.status
                    + " channel=" + channelName
                    + " host=" + hostOf(playlistUrl)
                    + " contentType=" + nullToEmpty(playlist.contentType)
                    + " bytes=" + playlist.body.length);
            if (playlist.status < 200 || playlist.status >= 300 || playlist.body.length == 0) {
                return;
            }
            String first = firstMediaUrl(playlistUrl, new String(playlist.body, "UTF-8"));
            if (first == null || first.length() == 0) {
                Log.i(DEBUG_TAG, "playlist first media missing channel=" + channelName);
                return;
            }
            ProbeResult firstProbe = probeUrl(first, true);
            if (isPlaylist(first, firstProbe.contentType)) {
                Log.i(DEBUG_TAG, "variant playlist http=" + firstProbe.status
                        + " channel=" + channelName
                        + " host=" + hostOf(first)
                        + " path=" + pathOf(first)
                        + " bytes=" + firstProbe.body.length);
                if (firstProbe.status >= 200 && firstProbe.status < 300
                        && firstProbe.body.length > 0) {
                    String segment = firstMediaUrl(first, new String(firstProbe.body, "UTF-8"));
                    probeSegment(channelName, segment);
                }
            } else {
                Log.i(DEBUG_TAG, "first media http=" + firstProbe.status
                        + " channel=" + channelName
                        + " host=" + hostOf(first)
                        + " path=" + pathOf(first)
                        + " contentType=" + nullToEmpty(firstProbe.contentType)
                        + " bytes=" + firstProbe.body.length);
            }
        } catch (Exception error) {
            Log.w(DEBUG_TAG, "stream probe failed channel=" + channelName
                    + " error=" + error.getClass().getSimpleName()
                    + ":" + error.getMessage());
        }
    }

    private void probeSegment(String channelName, String segmentUrl) {
        if (segmentUrl == null || segmentUrl.length() == 0) {
            Log.i(DEBUG_TAG, "segment missing channel=" + channelName);
            return;
        }
        try {
            ProbeResult segment = probeUrl(segmentUrl, true);
            Log.i(DEBUG_TAG, "segment http=" + segment.status
                    + " channel=" + channelName
                    + " host=" + hostOf(segmentUrl)
                    + " path=" + pathOf(segmentUrl)
                    + " contentType=" + nullToEmpty(segment.contentType)
                    + " bytes=" + segment.body.length);
        } catch (Exception error) {
            Log.w(DEBUG_TAG, "segment probe failed channel=" + channelName
                    + " error=" + error.getClass().getSimpleName()
                    + ":" + error.getMessage());
        }
    }

    private ProbeResult probeUrl(String url, boolean range) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(9000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", KankanewsContext.getUserAgent());
            connection.setRequestProperty("M-Uuid", getResolverClientId());
            connection.setRequestProperty("Referer", STREAM_REFERER);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Connection", "keep-alive");
            if (KankanewsContext.isDebugDiagnosticsEnabled()) {
                Log.i(CTX_TAG, "resolver playlist uaHash=" + KankanewsContext.getUserAgentHash()
                        + " uuidHash=" + KankanewsContext.getClientIdHash(context)
                        + " uuidValid=" + KankanewsContext.isCurrentClientIdValid(context)
                        + " uuidLength=" + KankanewsContext.getClientIdLength(context)
                        + " host=" + hostOf(url)
                        + " path=" + pathOf(url));
            }
            if (range) {
                connection.setRequestProperty("Range", "bytes=0-4095");
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            byte[] body = input == null ? new byte[0] : readAtMost(input, 8192);
            return new ProbeResult(status, connection.getContentType(), body);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String firstMediaUrl(String playlistUrl, String playlist) {
        URI base = URI.create(playlistUrl);
        String[] lines = playlist.split("\\r?\\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 0 && !trimmed.startsWith("#")) {
                return base.resolve(trimmed).toString();
            }
        }
        return null;
    }

    private static boolean isPlaylist(String url, String contentType) {
        String lowerUrl = url == null ? "" : url.toLowerCase(Locale.US);
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        return lowerUrl.contains(".m3u8") || lowerType.contains("mpegurl");
    }

    private String decryptLiveAddress(String encrypted) throws IOException {
        byte[] cipherBytes;
        try {
            cipherBytes = Base64.decode(encrypted, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid Kankanews live_address base64", error);
        }
        RSAPublicKey key = publicKey();
        int blockSize = (key.getModulus().bitLength() + 7) / 8;
        if (blockSize <= 0 || cipherBytes.length % blockSize != 0) {
            throw new IOException("Unexpected Kankanews RSA block size");
        }
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        for (int offset = 0; offset < cipherBytes.length; offset += blockSize) {
            byte[] block = new byte[blockSize];
            System.arraycopy(cipherBytes, offset, block, 0, blockSize);
            byte[] decrypted = rsaPublicDecryptBlock(block, key, blockSize);
            byte[] unpadded = unpadPkcs1(decrypted);
            plain.write(unpadded, 0, unpadded.length);
        }
        return new String(plain.toByteArray(), "UTF-8");
    }

    private RSAPublicKey publicKey() throws IOException {
        if (publicKey != null) {
            return publicKey;
        }
        try {
            byte[] encoded = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
            publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
            return publicKey;
        } catch (Exception error) {
            throw new IOException("Unable to load Kankanews public key", error);
        }
    }

    private static byte[] rsaPublicDecryptBlock(byte[] block, RSAPublicKey key, int blockSize) {
        BigInteger input = new BigInteger(1, block);
        BigInteger output = input.modPow(key.getPublicExponent(), key.getModulus());
        byte[] bytes = output.toByteArray();
        if (bytes.length == blockSize) {
            return bytes;
        }
        byte[] normalized = new byte[blockSize];
        if (bytes.length > blockSize) {
            System.arraycopy(bytes, bytes.length - blockSize, normalized, 0, blockSize);
        } else {
            System.arraycopy(bytes, 0, normalized, blockSize - bytes.length, bytes.length);
        }
        return normalized;
    }

    private static byte[] unpadPkcs1(byte[] block) throws IOException {
        if (block.length < 11 || block[0] != 0 || (block[1] != 1 && block[1] != 2)) {
            throw new IOException("Invalid Kankanews RSA padding");
        }
        int index = 2;
        while (index < block.length && block[index] != 0) {
            index++;
        }
        if (index >= block.length - 1) {
            throw new IOException("Invalid Kankanews RSA padding separator");
        }
        int length = block.length - index - 1;
        byte[] result = new byte[length];
        System.arraycopy(block, index + 1, result, 0, length);
        return result;
    }

    private static String findString(Object value, String key) throws JSONException {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has(key) && !object.isNull(key)) {
                return object.optString(key, null);
            }
            JSONArray names = object.names();
            if (names == null) {
                return null;
            }
            for (int index = 0; index < names.length(); index++) {
                String nestedKey = names.getString(index);
                String found = findString(object.get(nestedKey), key);
                if (found != null && found.length() > 0) {
                    return found;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                String found = findString(array.get(index), key);
                if (found != null && found.length() > 0) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String sign(Map<String, String> params) throws IOException {
        return signSnapshot(params).sign;
    }

    private static SignSnapshot signSnapshot(Map<String, String> params) throws IOException {
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);
        StringBuilder builder = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (value != null) {
                builder.append(key).append('=').append(value).append('&');
            }
        }
        String canonical = builder.toString();
        String canonicalWithSalt = canonical + SIGN_SALT;
        String firstMd5 = md5(canonicalWithSalt);
        String finalSign = md5(firstMd5);
        return new SignSnapshot(joinKeys(keys), KankanewsContext.safeHash(canonical),
                KankanewsContext.safeHash(canonicalWithSalt), firstMd5, finalSign);
    }

    private static String joinKeys(List<String> keys) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(keys.get(index));
        }
        return builder.toString();
    }

    private static String md5(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                int number = item & 0xff;
                if (number < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(number));
            }
            return builder.toString().toLowerCase(Locale.US);
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("MD5 is unavailable", error);
        }
    }

    private String getResolverClientId() {
        return KankanewsContext.getClientId(context);
    }

    static String debugClientIdHash() {
        return KankanewsContext.getClientIdHash(null);
    }

    static int debugClientIdLength() {
        return KankanewsContext.getClientIdLength(null);
    }

    private static String randomNonce() {
        return KankanewsContext.randomKapiNonce();
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    private static String pathOf(String url) {
        try {
            return URI.create(url).getPath();
        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    private static boolean isHttpUrl(String url) {
        try {
            String scheme = URI.create(url).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static boolean isValidNanoId(String value) {
        return KankanewsContext.isValidNanoId(value);
    }

    private static String safeHash(String value) {
        return KankanewsContext.safeHash(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static byte[] readAtMost(InputStream input, int maxBytes) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int remaining = maxBytes;
            while (remaining > 0) {
                int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count == -1) {
                    break;
                }
                output.write(buffer, 0, count);
                remaining -= count;
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static byte[] readFully(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static final class ProbeResult {
        final int status;
        final String contentType;
        final byte[] body;

        ProbeResult(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private static final class SignSnapshot {
        final String keys;
        final String canonicalHash;
        final String canonicalWithSaltHash;
        final String firstMd5;
        final String sign;

        SignSnapshot(String keys, String canonicalHash, String canonicalWithSaltHash,
                String firstMd5, String sign) {
            this.keys = keys;
            this.canonicalHash = canonicalHash;
            this.canonicalWithSaltHash = canonicalWithSaltHash;
            this.firstMd5 = firstMd5;
            this.sign = sign;
        }
    }
}
