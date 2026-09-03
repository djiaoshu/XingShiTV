package com.xingshi.tv;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;

final class ChannelCatalog {
    static final int SOURCE_CCTV_WEB = 0;
    static final int SOURCE_YSP_CCTV = 1;
    static final int SOURCE_YSP_SATELLITE = 2;
    static final int SOURCE_CUSTOM = 3;
    static final int SOURCE_MGTV = 4;
    static final int SOURCE_WEBVIEW = 5;
    static final int SOURCE_JSTV = 6;
    static final int SOURCE_KANKANEWS = 7;
    static final int SOURCE_GDTV = 8;
    static final String FULLSCREEN_MGTV = "MGTV";
    static final String FULLSCREEN_YANGSHIPIN = "YANGSHIPIN";

    private static final String TAG = "ChannelCatalog";
    private static final String CONFIG_ASSET = "channel_catalog.json";
    private static final String STREAM_BASE =
            "https://ldocctvwbcdbyte.volcfcdn.com/ldocctvwbcd/";
    private static final String BITRATE_RANGE = "?b=200-4000";

    private static final Channel[] FALLBACK_CCTV_CHANNELS = new Channel[] {
            new Channel("13", "CCTV-13 新闻", "cctv13",
                    streamUrl("cctv13"), "600001811", "2029797201")
    };
    private static final Group[] FALLBACK_GROUPS = new Group[] {
            new Group("fallback_cctv", "央视网 · 央视频道",
                    SOURCE_CCTV_WEB, FALLBACK_CCTV_CHANNELS)
    };

    static volatile Channel[] CCTV_CHANNELS = FALLBACK_CCTV_CHANNELS;
    static volatile Channel[] CHANNELS = CCTV_CHANNELS;
    private static volatile Group[] builtInGroups = FALLBACK_GROUPS;
    static volatile Group[] GROUPS = builtInGroups;
    private static boolean initialized;
    private static Group[] customGroups = new Group[0];

    private ChannelCatalog() {
    }

    static synchronized void initialize(Context context) {
        if (initialized) {
            return;
        }
        try {
            Group[] loaded = loadFromAssets(context);
            if (loaded.length == 0) {
                throw new IOException("No channel groups in " + CONFIG_ASSET);
            }
            builtInGroups = loaded;
            CCTV_CHANNELS = findChannelsBySource(loaded, SOURCE_CCTV_WEB);
            if (CCTV_CHANNELS.length == 0) {
                CCTV_CHANNELS = loaded[0].channels;
            }
            CHANNELS = CCTV_CHANNELS;
            Log.i(TAG, "Loaded channel config groups=" + loaded.length
                    + " channels=" + countChannels(loaded));
        } catch (IOException error) {
            builtInGroups = FALLBACK_GROUPS;
            CCTV_CHANNELS = FALLBACK_CCTV_CHANNELS;
            CHANNELS = CCTV_CHANNELS;
            Log.e(TAG, "Failed to load " + CONFIG_ASSET
                    + ", using minimal fallback", error);
        }
        initialized = true;
        rebuildGroups();
    }

    static synchronized void setCustomGroups(Group[] groups) {
        customGroups = groups == null ? new Group[0] : groups;
        rebuildGroups();
    }

    private static void rebuildGroups() {
        if (customGroups.length == 0) {
            GROUPS = builtInGroups;
            return;
        }
        Group[] groups = new Group[builtInGroups.length + customGroups.length];
        System.arraycopy(builtInGroups, 0, groups, 0, builtInGroups.length);
        System.arraycopy(customGroups, 0, groups, builtInGroups.length, customGroups.length);
        GROUPS = groups;
    }

    static int wrapGroupIndex(int index) {
        int size = GROUPS.length;
        return (index % size + size) % size;
    }

    static int wrapIndex(int index) {
        return wrapIndex(CHANNELS, index);
    }

    static int wrapIndex(Channel[] channels, int index) {
        int size = channels.length;
        return (index % size + size) % size;
    }

    static int indexOfNumber(String number) {
        return indexOfNumber(CHANNELS, number);
    }

    static int indexOfNumber(Channel[] channels, String number) {
        for (int index = 0; index < channels.length; index++) {
            if (channels[index].number.equals(number)) {
                return index;
            }
        }
        return 0;
    }

    static int defaultChannelIndex(Group group) {
        if (group.source == SOURCE_CUSTOM) {
            return 0;
        }
        if (group.source == SOURCE_YSP_SATELLITE) {
            return 0;
        }
        if (group.source == SOURCE_YSP_CCTV) {
            return indexOfPid(group.channels, "600001811");
        }
        return indexOfNumber(group.channels, "13");
    }

    private static int indexOfPid(Channel[] channels, String pid) {
        for (int index = 0; index < channels.length; index++) {
            if (pid.equals(channels[index].yangshipinPid)) {
                return index;
            }
        }
        return 0;
    }

    static String preferHighBitrate(String url) {
        if (url == null) {
            return null;
        }
        if (url.contains("b=200-2100")) {
            return url.replace("b=200-2100", "b=200-4000");
        }
        if (url.indexOf('?') >= 0) {
            return url;
        }
        return url + BITRATE_RANGE;
    }

    private static Group[] loadFromAssets(Context context) throws IOException {
        String json = readAsset(context, CONFIG_ASSET);
        try {
            JSONObject root = new JSONObject(json);
            JSONArray groupItems = root.optJSONArray("groups");
            JSONArray channelItems = root.optJSONArray("channels");
            if (groupItems == null || channelItems == null) {
                throw new IOException("Missing groups or channels array");
            }
            LinkedHashMap<String, GroupBuilder> builders = readGroups(groupItems);
            readChannels(channelItems, builders);
            ArrayList<GroupBuilder> ordered = new ArrayList<GroupBuilder>(builders.values());
            Collections.sort(ordered, new Comparator<GroupBuilder>() {
                @Override
                public int compare(GroupBuilder left, GroupBuilder right) {
                    return left.order - right.order;
                }
            });
            ArrayList<Group> groups = new ArrayList<Group>();
            for (GroupBuilder builder : ordered) {
                if (builder.channels.isEmpty()) {
                    Log.w(TAG, "Skip empty channel group id=" + builder.id);
                    continue;
                }
                Collections.sort(builder.channels, new Comparator<ChannelEntry>() {
                    @Override
                    public int compare(ChannelEntry left, ChannelEntry right) {
                        return left.order - right.order;
                    }
                });
                Channel[] channels = new Channel[builder.channels.size()];
                for (int index = 0; index < builder.channels.size(); index++) {
                    channels[index] = builder.channels.get(index).channel;
                }
                groups.add(new Group(builder.id, builder.name, builder.source, channels));
            }
            return groups.toArray(new Group[groups.size()]);
        } catch (JSONException error) {
            throw new IOException("Invalid " + CONFIG_ASSET, error);
        }
    }

    private static LinkedHashMap<String, GroupBuilder> readGroups(JSONArray groupItems)
            throws JSONException {
        LinkedHashMap<String, GroupBuilder> builders = new LinkedHashMap<String, GroupBuilder>();
        for (int index = 0; index < groupItems.length(); index++) {
            JSONObject item = groupItems.getJSONObject(index);
            String id = item.optString("id", "");
            String name = item.optString("name", "");
            int source = sourceFromName(item.optString("sourceType", ""));
            if (id.length() == 0 || name.length() == 0 || source < 0) {
                Log.w(TAG, "Skip invalid group index=" + index);
                continue;
            }
            builders.put(id, new GroupBuilder(id, name, source,
                    item.optInt("order", index + 1)));
        }
        return builders;
    }

    private static void readChannels(JSONArray channelItems,
            LinkedHashMap<String, GroupBuilder> builders) throws JSONException {
        for (int index = 0; index < channelItems.length(); index++) {
            JSONObject item = channelItems.getJSONObject(index);
            String groupId = item.optString("groupId", "");
            GroupBuilder group = builders.get(groupId);
            if (group == null) {
                Log.w(TAG, "Skip channel with unknown groupId=" + groupId);
                continue;
            }
            int source = sourceFromName(item.optString("sourceType", ""));
            if (source < 0) {
                source = group.source;
            }
            Channel channel = buildChannel(item, source, index);
            if (channel == null) {
                continue;
            }
            group.channels.add(new ChannelEntry(channel, item.optInt("order", index + 1)));
        }
    }

    private static Channel buildChannel(JSONObject item, int source, int index) {
        String number = item.optString("number", String.valueOf(index + 1));
        String name = item.optString("name", "");
        if (name.length() == 0) {
            Log.w(TAG, "Skip unnamed channel index=" + index);
            return null;
        }
        String streamId = item.optString("streamId", null);
        String url = item.optString("url", null);
        String yangshipinPid = item.optString("yangshipinPid", null);
        String yangshipinStreamId = item.optString("yangshipinStreamId", null);
        String activityId = item.optString("activityId", null);
        String cameraId = item.optString("cameraId", null);
        String jstvChannelId = item.optString("channelId", null);
        String jstvEn = item.optString("en", null);
        String jstvStreamName = item.optString("stream", null);
        String jstvPath = item.optString("path", null);
        String kankanewsChannelId = item.optString("channelId", null);
        String kankanewsStreamName = item.optString("stream", null);
        String gdtvChannelId = item.optString("channelId", null);
        String gdtvStreamName = item.optString("stream", null);
        String webUrl = item.optString("webUrl", null);
        String webExtra = item.optString("webExtra", null);
        String fullscreenType = item.optString("fullscreenType", null);
        String epgId = item.optString("epgId", null);
        String epgSource = item.optString("epgSource", null);

        if (source == SOURCE_CCTV_WEB) {
            if (streamId == null || streamId.length() == 0) {
                Log.w(TAG, "Skip CCTV channel without streamId name=" + name);
                return null;
            }
            if (url == null || url.length() == 0) {
                url = streamUrl(streamId);
            }
        } else if (source == SOURCE_YSP_CCTV || source == SOURCE_YSP_SATELLITE) {
            if (yangshipinPid == null || yangshipinPid.length() == 0) {
                Log.w(TAG, "Skip Yangshipin channel without pid name=" + name);
                return null;
            }
            streamId = "ysp_" + yangshipinPid;
        } else if (source == SOURCE_MGTV) {
            if (activityId == null || activityId.length() == 0) {
                Log.w(TAG, "Skip MGTV channel without activityId name=" + name);
                return null;
            }
            streamId = "mgtv_" + activityId;
        } else if (source == SOURCE_JSTV) {
            if (jstvEn == null || jstvEn.length() == 0
                    || jstvStreamName == null || jstvStreamName.length() == 0
                    || jstvPath == null || jstvPath.length() == 0) {
                Log.w(TAG, "Skip JSTV channel with incomplete config name=" + name);
                return null;
            }
            streamId = "jstv_" + jstvEn;
        } else if (source == SOURCE_KANKANEWS) {
            if (kankanewsChannelId == null || kankanewsChannelId.length() == 0) {
                Log.w(TAG, "Skip Kankanews channel without channelId name=" + name);
                return null;
            }
            streamId = "kankanews_" + kankanewsChannelId;
        } else if (source == SOURCE_GDTV) {
            if (gdtvChannelId == null || gdtvChannelId.length() == 0) {
                Log.w(TAG, "Skip GDTv channel without channelId name=" + name);
                return null;
            }
            streamId = "gdtv_" + gdtvChannelId;
        } else if (source == SOURCE_WEBVIEW) {
            if (webUrl == null || webUrl.length() == 0) {
                Log.w(TAG, "Skip WebView channel without webUrl name=" + name);
                return null;
            }
            streamId = "webview_" + number;
        }

        Channel channel = new Channel(number, name, streamId, url,
                yangshipinPid, yangshipinStreamId, activityId, cameraId,
                jstvChannelId, jstvEn, jstvStreamName, jstvPath,
                kankanewsChannelId, kankanewsStreamName,
                gdtvChannelId, gdtvStreamName,
                webUrl, webExtra, fullscreenType);
        if ((epgId == null || epgId.trim().length() == 0)
                && (epgSource == null || epgSource.trim().length() == 0)) {
            return channel;
        }
        return channel.withEpg(epgId, epgSource);
    }

    private static int sourceFromName(String value) {
        if ("CCTV_WEB".equals(value)) {
            return SOURCE_CCTV_WEB;
        }
        if ("YSP_CCTV".equals(value)) {
            return SOURCE_YSP_CCTV;
        }
        if ("YSP_SATELLITE".equals(value)) {
            return SOURCE_YSP_SATELLITE;
        }
        if ("MGTV".equals(value)) {
            return SOURCE_MGTV;
        }
        if ("WEBVIEW".equals(value)) {
            return SOURCE_WEBVIEW;
        }
        if ("JSTV".equals(value)) {
            return SOURCE_JSTV;
        }
        if ("KANKANEWS".equals(value)) {
            return SOURCE_KANKANEWS;
        }
        if ("GDTV".equals(value)) {
            return SOURCE_GDTV;
        }
        if ("CUSTOM".equals(value)) {
            return SOURCE_CUSTOM;
        }
        return -1;
    }

    private static String readAsset(Context context, String name) throws IOException {
        InputStream input = context.getAssets().open(name);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        } finally {
            input.close();
        }
    }

    private static Channel[] findChannelsBySource(Group[] groups, int source) {
        for (Group group : groups) {
            if (group.source == source) {
                return group.channels;
            }
        }
        return new Channel[0];
    }

    private static int countChannels(Group[] groups) {
        int count = 0;
        for (Group group : groups) {
            count += group.channels.length;
        }
        return count;
    }

    private static String streamUrl(String streamId) {
        if ("cctv1".equals(streamId)) {
            return "https://ldncctvwbcdcnc.v.wscdns.com/ldncctvwbcd/"
                    + "cdrmldcctv1_1/index.m3u8" + BITRATE_RANGE;
        }
        if ("cctv3".equals(streamId)) {
            return "https://ldocctvwbcdks.v.kcdnvip.com/ldocctvwbcd/"
                    + "cdrmldcctv3_1/index.m3u8" + BITRATE_RANGE;
        }
        if ("cctv5".equals(streamId)) {
            return "https://ldcctvwbcdks.v.kcdnvip.com/ldcctvwbcd/"
                    + "cdrmldcctv5_1/index.m3u8" + BITRATE_RANGE;
        }
        if ("cctv5plus".equals(streamId)) {
            return "https://ldcctvwbcdtxy.liveplay.myqcloud.com/ldcctvwbcd/"
                    + "cdrmldcctv5plus_1/index.m3u8" + BITRATE_RANGE;
        }
        if ("cctv6".equals(streamId)) {
            return "https://ldocctvwbcdbd.a.bdydns.com/ldocctvwbcd/"
                    + "cdrmldcctv6_1/index.m3u8" + BITRATE_RANGE;
        }
        if ("cctv8".equals(streamId)) {
            return "https://ldocctvwbcdks.v.kcdnvip.com/ldocctvwbcd/"
                    + "cdrmldcctv8_1/index.m3u8" + BITRATE_RANGE;
        }
        if ("cctv13".equals(streamId)) {
            return "https://ldncctvwbcdbd.a.bdydns.com/ldncctvwbcd/"
                    + "cdrmldcctv13_1/index.m3u8" + BITRATE_RANGE;
        }
        if ("cctv16".equals(streamId)) {
            return "https://ldcctvwbcdks.v.kcdnvip.com/ldcctvwbcd/"
                    + "cdrmldcctv16_1/index.m3u8" + BITRATE_RANGE;
        }
        return STREAM_BASE + "cdrmld" + streamId + "_1/index.m3u8" + BITRATE_RANGE;
    }

    private static final class GroupBuilder {
        final String id;
        final String name;
        final int source;
        final int order;
        final ArrayList<ChannelEntry> channels = new ArrayList<ChannelEntry>();

        GroupBuilder(String id, String name, int source, int order) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.order = order;
        }
    }

    private static final class ChannelEntry {
        final Channel channel;
        final int order;

        ChannelEntry(Channel channel, int order) {
            this.channel = channel;
            this.order = order;
        }
    }

    static final class Group {
        final String id;
        final String title;
        final int source;
        final Channel[] channels;

        Group(String title, int source, Channel[] channels) {
            this(null, title, source, channels);
        }

        Group(String id, String title, int source, Channel[] channels) {
            this.id = id;
            this.title = title;
            this.source = source;
            this.channels = channels;
        }
    }
}
