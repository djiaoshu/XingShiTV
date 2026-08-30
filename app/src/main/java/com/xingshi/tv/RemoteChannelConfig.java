package com.xingshi.tv;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

final class RemoteChannelConfig {
    static final String CLOUDFLARE_CONFIG_URL = "https://config.tianya1234.eu.org/channels.json";
    static final String CONFIG_URL = CLOUDFLARE_CONFIG_URL;

    private static final String TAG = "RemoteChannelConfig";
    private static final String CACHE_FILE_NAME = "remote_channels_cache.json";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int MAX_CONFIG_BYTES = 256 * 1024;

    private RemoteChannelConfig() {
    }

    static ChannelCatalog.Group[] loadCached(Context context) throws IOException {
        File file = cacheFile(context);
        if (!file.exists() || file.length() <= 0L) {
            throw new IOException("remote config cache missing");
        }
        try {
            ChannelCatalog.Group[] groups = parse(new String(readFile(file), "UTF-8"));
            Log.i(TAG, "RemoteConfig: loaded from cache");
            return groups;
        } catch (JSONException error) {
            throw new IOException("invalid cached remote channel config", error);
        }
    }

    static ChannelCatalog.Group[] downloadAndCache(Context context) throws IOException {
        IOException lastError = null;
        try {
            String json = downloadJson(CLOUDFLARE_CONFIG_URL);
            ChannelCatalog.Group[] groups = parse(json);
            saveCache(context, json);
            Log.i(TAG, "RemoteConfig: Cloudflare success");
            Log.i(TAG, "RemoteConfig: cache updated");
            return groups;
        } catch (IOException error) {
            lastError = error;
            Log.w(TAG, "RemoteConfig: Cloudflare failed " + safeMessage(error));
        } catch (JSONException error) {
            lastError = new IOException("invalid Cloudflare remote channel config", error);
            Log.w(TAG, "RemoteConfig: Cloudflare failed " + safeMessage(lastError));
        }
        throw lastError == null ? new IOException("remote config unavailable") : lastError;
    }

    static ChannelCatalog.Group[] download() throws IOException {
        try {
            return parse(downloadJson(CLOUDFLARE_CONFIG_URL));
        } catch (JSONException error) {
            throw new IOException("invalid remote channel config", error);
        }
    }

    static ChannelCatalog.Group[] parse(String json) throws JSONException, IOException {
        return parse(json, "remote_hk_tw");
    }

    static ChannelCatalog.Group[] parse(String json, String groupId)
            throws JSONException, IOException {
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version", -1);
        JSONArray groupItems = root.optJSONArray("groups");
        if (groupItems != null && groupItems.length() > 0) {
            return parseMultiGroupConfig(root, groupId);
        }
        if (version == 1) {
            return new ChannelCatalog.Group[] {
                    parseGroup(root, groupId, 0)
            };
        }
        throw new IOException("unsupported remote channel config");
    }

    private static ChannelCatalog.Group[] parseMultiGroupConfig(JSONObject root,
            String groupIdPrefix) throws JSONException, IOException {
        JSONArray groupItems = root.optJSONArray("groups");
        if (groupItems == null || groupItems.length() == 0) {
            throw new IOException("remote config contains no groups");
        }
        ArrayList<ChannelCatalog.Group> groups = new ArrayList<ChannelCatalog.Group>();
        for (int index = 0; index < groupItems.length(); index++) {
            JSONObject item = groupItems.optJSONObject(index);
            if (item == null) {
                Log.w(TAG, "Skip invalid remote group index=" + index);
                continue;
            }
            try {
                groups.add(parseGroup(item, groupIdFor(groupIdPrefix, item, index), index));
            } catch (IOException error) {
                Log.w(TAG, "Skip invalid remote group index=" + index
                        + " reason=" + safeMessage(error));
            } catch (JSONException error) {
                Log.w(TAG, "Skip invalid remote group index=" + index
                        + " reason=" + safeMessage(error));
            }
        }
        if (groups.isEmpty()) {
            throw new IOException("remote config contains no playable groups");
        }
        Log.i(TAG, "Loaded remote config groups=" + groups.size());
        return groups.toArray(new ChannelCatalog.Group[groups.size()]);
    }

    private static ChannelCatalog.Group parseGroup(JSONObject root, String groupId,
            int groupIndex) throws JSONException, IOException {
        String groupName = root.optString("group", "").trim();
        if (groupName.length() == 0) {
            groupName = root.optString("name", "").trim();
        }
        JSONArray channelItems = root.optJSONArray("channels");
        if (groupName.length() == 0 || channelItems == null || channelItems.length() == 0) {
            throw new IOException("invalid remote group");
        }
        ArrayList<Channel> channels = new ArrayList<Channel>();
        int sourceCount = 0;
        for (int index = 0; index < channelItems.length(); index++) {
            JSONObject item = channelItems.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String name = item.optString("name", "").trim();
            JSONArray sources = item.optJSONArray("sources");
            if (name.length() == 0 || sources == null || sources.length() == 0) {
                Log.w(TAG, "Skip invalid remote channel index=" + index);
                continue;
            }
            Channel channel = null;
            for (int sourceIndex = 0; sourceIndex < sources.length(); sourceIndex++) {
                JSONObject source = sources.optJSONObject(sourceIndex);
                if (source == null) {
                    continue;
                }
                String streamUrl = source.optString("url", "").trim();
                if (!isStreamUrl(streamUrl)) {
                    continue;
                }
                String sourceName = source.optString("name",
                        "线路 " + (sourceIndex + 1)).trim();
                if (channel == null) {
                    channel = Channel.directSource(String.valueOf(channels.size() + 1),
                            name, "remote_" + groupIndex + "_" + index, streamUrl, sourceName);
                } else {
                    channel = channel.withAdditionalSource(sourceName, streamUrl);
                }
                sourceCount++;
            }
            if (channel != null) {
                channels.add(channel);
            }
        }
        if (channels.isEmpty()) {
            throw new IOException("remote config contains no playable channels");
        }
        Log.i(TAG, "Loaded remote config group=" + groupName
                + " channels=" + channels.size()
                + " sources=" + sourceCount);
        return new ChannelCatalog.Group(groupId, groupName,
                ChannelCatalog.SOURCE_CUSTOM,
                channels.toArray(new Channel[channels.size()]));
    }

    private static String groupIdFor(String prefix, JSONObject item, int index) {
        String id = item.optString("id", "").trim();
        if (id.length() == 0) {
            return prefix + "_" + (index + 1);
        }
        return prefix + "_" + safeId(id, index);
    }

    private static String safeId(String value, int index) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                builder.append(c);
            }
        }
        if (builder.length() == 0) {
            return String.valueOf(index + 1);
        }
        return builder.toString();
    }

    private static String downloadJson(String configUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(configUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "XingShiTV/1.0");
            connection.setRequestProperty("Accept", "application/json,*/*");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            byte[] bytes = readAll(connection.getInputStream());
            return new String(bytes, "UTF-8");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void saveCache(Context context, String json) throws IOException {
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(cacheFile(context));
            output.write(json.getBytes("UTF-8"));
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }

    private static File cacheFile(Context context) {
        return new File(context.getFilesDir(), CACHE_FILE_NAME);
    }

    private static byte[] readFile(File file) throws IOException {
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            return readAll(input);
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_CONFIG_BYTES) {
                    throw new IOException("remote config too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private static boolean isStreamUrl(String text) {
        return text.startsWith("http://") || text.startsWith("https://")
                || text.startsWith("rtmp://");
    }
}
