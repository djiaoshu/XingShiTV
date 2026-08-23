package com.xingshi.tv;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

final class MgtvLiveResolver {
    private static final String TAG = "MgtvLiveResolver";
    private static final String DID = "did";
    private static final String CACHE_URL_PREFIX = "cached_url_";
    private static final String CACHE_TIME_PREFIX = "cached_at_";
    private static final long CACHE_TTL_MS = 5L * 60L * 1000L;
    private static final String CAMERA_LIST_API = "https://pwlp.bz.mgtv.com/v1/camera/list";
    private static final String LIVE_SOURCE_API = "https://pwlp.bz.mgtv.com/v1/live/source";
    private static final String APP_VERSION = "imgotv-pch5-9.0.4-1";
    private static final String SOURCE_SIGN_SALT = "LMFwh1k1m@pvt#Pt";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";

    private final SharedPreferences preferences;

    MgtvLiveResolver(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    String resolve(Channel channel) throws IOException {
        if (channel.mgtvActivityId == null || channel.mgtvActivityId.length() == 0) {
            throw new IOException("Missing MGTV activityId for " + channel.name);
        }
        Log.i(TAG, "Resolving MGTV channel name=" + channel.name
                + " streamId=" + channel.streamId
                + " activityId=" + channel.mgtvActivityId
                + " cameraId=" + nullToEmpty(channel.mgtvCameraId));
        String key = channel.streamId + "_" + nullToEmpty(channel.mgtvCameraId);
        long now = System.currentTimeMillis();
        String cachedUrl = preferences.getString(CACHE_URL_PREFIX + key, null);
        long cachedAt = preferences.getLong(CACHE_TIME_PREFIX + key, 0L);
        long cacheAgeMs = cachedAt > 0L ? now - cachedAt : -1L;
        if (cachedUrl != null && cachedUrl.length() > 0
                && cachedAt > 0L && cacheAgeMs >= 0L && cacheAgeMs < CACHE_TTL_MS) {
            Log.i(TAG, "MGTV cache hit channel=" + channel.name
                    + " key=" + key
                    + " cachedAt=" + cachedAt
                    + " ageMs=" + cacheAgeMs
                    + " ttlMs=" + CACHE_TTL_MS
                    + " url=" + cachedUrl);
            return cachedUrl;
        }
        if (cachedUrl != null && cachedUrl.length() > 0) {
            Log.i(TAG, "MGTV cache expired channel=" + channel.name
                    + " key=" + key
                    + " cachedAt=" + cachedAt
                    + " ageMs=" + cacheAgeMs
                    + " ttlMs=" + CACHE_TTL_MS);
        } else {
            Log.i(TAG, "MGTV cache miss channel=" + channel.name
                    + " key=" + key
                    + " ttlMs=" + CACHE_TTL_MS);
        }
        Log.i(TAG, "MGTV requesting fresh live/source channel=" + channel.name
                + " activityId=" + channel.mgtvActivityId
                + " cameraId=" + nullToEmpty(channel.mgtvCameraId));

        String cameraId = channel.mgtvCameraId;
        if (cameraId == null || cameraId.length() == 0) {
            cameraId = resolveCameraId(channel.mgtvActivityId);
        }
        String url = resolveLiveSource(channel.mgtvActivityId, cameraId);
        long cacheWriteTime = System.currentTimeMillis();
        preferences.edit()
                .putString(CACHE_URL_PREFIX + key, url)
                .putLong(CACHE_TIME_PREFIX + key, cacheWriteTime)
                .apply();
        Log.i(TAG, "MGTV cache updated channel=" + channel.name
                + " key=" + key
                + " cachedAt=" + cacheWriteTime
                + " ttlMs=" + CACHE_TTL_MS
                + " url=" + url);
        Log.i(TAG, "Resolved MGTV final url for " + channel.name + ": " + url);
        return url;
    }

    private String resolveCameraId(String activityId) throws IOException {
        TreeMap<String, String> params = new TreeMap<String, String>();
        params.put("uid", "");
        params.put("token", "");
        params.put("platform", "4");
        params.put("appVersion", "PCweb_1.0");
        params.put("clientKey", "pcweb");
        params.put("activityId", activityId);
        Log.i(TAG, "MGTV camera/list request activityId=" + activityId
                + " params=" + params);
        JSONObject body = getJson(CAMERA_LIST_API, params);
        int code = body.optInt("code", -1);
        String msg = body.optString("msg");
        Log.i(TAG, "MGTV camera/list response activityId=" + activityId
                + " code=" + code + " msg=" + msg);
        if (code != 0) {
            throw new IOException("MGTV camera/list failed: " + msg);
        }
        JSONObject data = body.optJSONObject("data");
        JSONArray cameras = data == null ? null : data.optJSONArray("cameras");
        Log.i(TAG, "MGTV camera/list cameras activityId=" + activityId
                + " count=" + (cameras == null ? 0 : cameras.length()));
        if (cameras == null || cameras.length() == 0) {
            throw new IOException("MGTV camera/list returned no cameras");
        }
        JSONObject camera = cameras.optJSONObject(0);
        String cameraId = camera == null ? null : camera.optString("cameraId", null);
        Log.i(TAG, "MGTV camera/list selected activityId=" + activityId
                + " cameraId=" + nullToEmpty(cameraId)
                + " camera=" + summarizeJson(camera));
        if (cameraId == null || cameraId.length() == 0) {
            throw new IOException("MGTV camera/list returned empty cameraId");
        }
        return cameraId;
    }

    private String resolveLiveSource(String activityId, String cameraId) throws IOException {
        String did = getOrCreateDid();
        TreeMap<String, String> params = new TreeMap<String, String>();
        params.put("cameraId", cameraId);
        params.put("activityId", activityId);
        params.put("platform", "4");
        params.put("appVersion", APP_VERSION);
        params.put("clientKey", "pcweb");
        params.put("auth_mode", "1");
        params.put("local_definition", "");
        params.put("init_definition", "2");
        params.put("did", did);
        params.put("uid", "");
        params.put("token", "");
        params.put("_t", String.valueOf(System.currentTimeMillis()));
        params.put("deviceId", did);

        String sign = sign(params);
        params.put("_support", "10000000");
        params.put("sign", sign);
        Log.i(TAG, "MGTV live/source request activityId=" + activityId
                + " cameraId=" + cameraId
                + " params=" + params);

        JSONObject body = getJson(LIVE_SOURCE_API, params);
        int code = body.optInt("code", -1);
        String msg = body.optString("msg");
        Log.i(TAG, "MGTV live/source response activityId=" + activityId
                + " cameraId=" + cameraId
                + " code=" + code + " msg=" + msg);
        if (code != 0) {
            throw new IOException("MGTV live/source failed: code="
                    + code + " msg=" + msg);
        }
        JSONObject data = body.optJSONObject("data");
        JSONArray sources = data == null ? null : data.optJSONArray("sources");
        Log.i(TAG, "MGTV live/source data activityId=" + activityId
                + " cameraId=" + cameraId
                + " data=" + summarizeJson(data)
                + " sourcesCount=" + (sources == null ? 0 : sources.length()));
        if (sources == null || sources.length() == 0) {
            throw new IOException("MGTV live/source returned no sources");
        }
        String fallback = null;
        String fallbackSummary = null;
        for (int index = 0; index < sources.length(); index++) {
            JSONObject source = sources.optJSONObject(index);
            if (source == null) {
                continue;
            }
            String url = source.optString("url", "");
            Log.i(TAG, "MGTV live/source candidate activityId=" + activityId
                    + " cameraId=" + cameraId
                    + " index=" + index
                    + " definition=" + source.optString("definition")
                    + " format=" + source.optString("format")
                    + " protocol=" + source.optString("protocol")
                    + " urlType=" + classifyUrl(url)
                    + " url=" + url
                    + " source=" + summarizeJson(source));
            if (url.length() == 0) {
                continue;
            }
            if (fallback == null) {
                fallback = url;
                fallbackSummary = summarizeJson(source);
            }
            String format = source.optString("format", "");
            if (format.toLowerCase(Locale.US).contains("m3u8")
                    || url.toLowerCase(Locale.US).contains(".m3u8")) {
                Log.i(TAG, "Resolved MGTV HLS activityId=" + activityId
                        + " cameraId=" + cameraId + " definition="
                        + source.optString("definition")
                        + " format=" + source.optString("format")
                        + " url=" + url);
                debugProbePlaylist(activityId, cameraId, url);
                return url;
            }
        }
        if (fallback != null) {
            Log.w(TAG, "MGTV live/source returned no explicit m3u8; using fallback"
                    + " activityId=" + activityId
                    + " cameraId=" + cameraId
                    + " urlType=" + classifyUrl(fallback)
                    + " url=" + fallback
                    + " source=" + fallbackSummary);
            debugProbePlaylist(activityId, cameraId, fallback);
            return fallback;
        }
        throw new IOException("MGTV live/source returned empty source URLs");
    }

    private JSONObject getJson(String endpoint, TreeMap<String, String> params)
            throws IOException {
        String url = endpoint + "?" + query(params);
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://www.mgtv.com/live");
        try {
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = input == null ? "" : new String(readFully(input), "UTF-8");
            Log.i(TAG, "MGTV HTTP response endpoint=" + endpoint
                    + " status=" + status + " length=" + body.length()
                    + " bodyPreview=" + preview(body));
            if (status < 200 || status >= 300) {
                throw new IOException("MGTV HTTP " + status + ": " + body);
            }
            return new JSONObject(body);
        } catch (JSONException error) {
            throw new IOException("Unable to parse MGTV response", error);
        } finally {
            connection.disconnect();
        }
    }

    private void debugProbePlaylist(String activityId, String cameraId, String url) {
        if (url == null || url.length() == 0) {
            Log.w(TAG, "MGTV playlist probe skipped empty url activityId=" + activityId
                    + " cameraId=" + cameraId);
            return;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Referer", "https://www.mgtv.com/live");
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = input == null ? "" : new String(readLimited(input, 512), "UTF-8");
            Log.i(TAG, "MGTV playlist probe activityId=" + activityId
                    + " cameraId=" + cameraId
                    + " status=" + status
                    + " contentType=" + connection.getContentType()
                    + " startsWithExtM3U=" + body.startsWith("#EXTM3U")
                    + " preview=" + preview(body));
        } catch (IOException error) {
            Log.w(TAG, "MGTV playlist probe failed activityId=" + activityId
                    + " cameraId=" + cameraId
                    + " url=" + url
                    + " error=" + error.getMessage(), error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String getOrCreateDid() {
        String did = preferences.getString(DID, null);
        if (did != null && did.length() > 0) {
            return did;
        }
        did = UUID.randomUUID().toString();
        preferences.edit().putString(DID, did).apply();
        return did;
    }

    private static String sign(TreeMap<String, String> params) throws IOException {
        StringBuilder body = new StringBuilder();
        for (String key : params.keySet()) {
            String value = params.get(key);
            if (value != null) {
                body.append(key).append(value);
            }
        }
        return md5(SOURCE_SIGN_SALT + body.toString() + SOURCE_SIGN_SALT)
                .toUpperCase(Locale.US);
    }

    private static String query(TreeMap<String, String> params) throws IOException {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String key : params.keySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(encode(key)).append('=').append(encode(nullToEmpty(params.get(key))));
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
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("MD5 is unavailable", error);
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String classifyUrl(String url) {
        if (url == null || url.length() == 0) {
            return "empty";
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) {
            return "hls-m3u8";
        }
        if (lower.startsWith("rtmp://")) {
            return "rtmp";
        }
        if (lower.contains(".flv")) {
            return "flv";
        }
        if (lower.contains(".mp4")) {
            return "mp4";
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return "http";
        }
        return "unknown";
    }

    private static String summarizeJson(JSONObject object) {
        if (object == null) {
            return "null";
        }
        return preview(object.toString());
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ');
        if (compact.length() <= 800) {
            return compact;
        }
        return compact.substring(0, 800) + "...";
    }

    private static byte[] readFully(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
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

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(512, limit));
            byte[] buffer = new byte[256];
            while (output.size() < limit) {
                int count = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
                if (count == -1) {
                    break;
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}

