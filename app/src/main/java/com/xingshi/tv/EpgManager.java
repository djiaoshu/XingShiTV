package com.xingshi.tv;

import android.content.Context;
import android.util.Log;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

final class EpgManager {
    static final String SOURCE_112114 = "112114";
    static final String SOURCE_EPGPW_HK = "EPGPW_HK";
    static final String SOURCE_EPGPW_TW = "EPGPW_TW";

    private static final String TAG = "EpgManager";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 12000;
    private static final int MAX_COMPRESSED_BYTES = 4 * 1024 * 1024;
    private static final long REFRESH_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    private static final String TRACE_TAG = "EPG_TRACE";

    private static final SourceConfig[] SOURCES = new SourceConfig[] {
            new SourceConfig(SOURCE_112114,
                    "http://168.138.204.101/epg/pp.xml.gz", "epg_112114.xml.gz",
                    "epg_seed_112114.xml"),
            new SourceConfig(SOURCE_EPGPW_HK,
                    "https://epg.pw/xmltv/epg_HK.xml.gz", "epg_epgpw_hk.xml.gz",
                    null),
            new SourceConfig(SOURCE_EPGPW_TW,
                    "https://epg.pw/xmltv/epg_TW.xml.gz", "epg_epgpw_tw.xml.gz",
                    null)
    };

    interface Listener {
        void onEpgChanged();
    }

    private final Context context;
    private final Listener listener;
    private final Object lock = new Object();
    private final HashMap<String, ArrayList<Programme>> programmes =
            new HashMap<String, ArrayList<Programme>>();
    private final HashSet<String> forcedRefreshSources = new HashSet<String>();
    private volatile boolean ready;

    EpgManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                loadCachedSources();
                refreshSourcesIfNeeded();
            }
        }, "epg-manager").start();
    }

    boolean isReady() {
        return ready;
    }

    ProgramState currentState(Channel channel, long nowMs) {
        EpgRef ref = resolveRef(channel);
        if (ref == null) {
            traceQuery("currentState", channel, null, -1, nowMs, null, null);
            return ProgramState.empty();
        }
        ArrayList<Programme> list;
        synchronized (lock) {
            ArrayList<Programme> cached = programmes.get(key(ref.source, ref.id));
            if (cached == null || cached.isEmpty()) {
                traceQuery("currentState", channel, ref, 0, nowMs, null, null);
                return ProgramState.empty();
            }
            list = new ArrayList<Programme>(cached);
        }
        Programme current = null;
        Programme next = null;
        for (int index = 0; index < list.size(); index++) {
            Programme item = list.get(index);
            if (item.startMs <= nowMs && item.stopMs > nowMs) {
                current = item;
                if (index + 1 < list.size()) {
                    next = list.get(index + 1);
                }
                break;
            }
            if (item.startMs > nowMs) {
                next = item;
                break;
            }
        }
        traceQuery("currentState", channel, ref, list.size(), nowMs, current, next);
        return current == null && next == null
                ? ProgramState.empty() : new ProgramState(current, next);
    }

    Programme[] programsForToday(Channel channel, long nowMs) {
        EpgRef ref = resolveRef(channel);
        if (ref == null) {
            traceQuery("today", channel, null, -1, nowMs, null, null);
            return new Programme[0];
        }
        ArrayList<Programme> list;
        synchronized (lock) {
            ArrayList<Programme> cached = programmes.get(key(ref.source, ref.id));
            if (cached == null || cached.isEmpty()) {
                traceQuery("today", channel, ref, 0, nowMs, null, null);
                requestForceRefreshForMissingChannel(ref);
                return new Programme[0];
            }
            list = new ArrayList<Programme>(cached);
        }
        long startOfDay = startOfDay(nowMs);
        long endOfDay = startOfDay + 24L * 60L * 60L * 1000L;
        ArrayList<Programme> today = new ArrayList<Programme>();
        for (Programme item : list) {
            if (item.stopMs > startOfDay && item.startMs < endOfDay) {
                today.add(item);
            }
        }
        Programme current = null;
        for (Programme item : today) {
            if (item.startMs <= nowMs && item.stopMs > nowMs) {
                current = item;
                break;
            }
        }
        traceQuery("today", channel, ref, today.size(), nowMs, current, null);
        if (today.isEmpty()) {
            requestForceRefreshForMissingChannel(ref);
        }
        return today.toArray(new Programme[today.size()]);
    }

    int currentProgramIndex(EpgManager.Programme[] items, long nowMs) {
        if (items == null) {
            return -1;
        }
        for (int index = 0; index < items.length; index++) {
            Programme item = items[index];
            if (item.startMs <= nowMs && item.stopMs > nowMs) {
                return index;
            }
        }
        return -1;
    }

    private static long startOfDay(long nowMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void loadCachedSources() {
        boolean changed = false;
        for (SourceConfig source : SOURCES) {
            File file = cacheFile(source);
            Log.i(TRACE_TAG, "cache path source=" + source.id
                    + " path=" + file.getAbsolutePath()
                    + " exists=" + file.isFile()
                    + " bytes=" + (file.isFile() ? file.length() : 0L));
            if (!file.isFile() || file.length() <= 0L) {
                if (loadBundledSeed(source)) {
                    changed = true;
                }
                continue;
            }
            try {
                HashMap<String, ArrayList<Programme>> parsed =
                        parse(source.id, new FileInputStream(file));
                traceParsedSource(source.id, "cache", parsed);
                applySource(source.id, parsed);
                Log.i(TAG, "EPG loaded from cache source=" + source.id);
                changed = true;
            } catch (Exception error) {
                Log.w(TAG, "EPG cache ignored source=" + source.id
                        + " reason=" + safeMessage(error));
            }
        }
        if (changed) {
            ready = true;
            notifyChanged();
        }
    }

    private boolean loadBundledSeed(SourceConfig source) {
        if (source.assetName == null) {
            return false;
        }
        try {
            HashMap<String, ArrayList<Programme>> parsed = parse(source.id,
                    context.getAssets().open(source.assetName));
            traceParsedSource(source.id, "asset:" + source.assetName, parsed);
            applySource(source.id, parsed);
            Log.i(TAG, "EPG loaded from bundled seed source=" + source.id);
            return true;
        } catch (Exception error) {
            Log.w(TAG, "EPG bundled seed ignored source=" + source.id
                    + " reason=" + safeMessage(error));
            return false;
        }
    }

    private void refreshSourcesIfNeeded() {
        for (SourceConfig source : SOURCES) {
            File file = cacheFile(source);
            if (!shouldRefresh(source, file, System.currentTimeMillis())) {
                continue;
            }
            try {
                byte[] compressed = download(source.url);
                HashMap<String, ArrayList<Programme>> parsed =
                        parse(source.id, new ByteArrayInputStream(compressed));
                traceParsedSource(source.id, "remote", parsed);
                saveCache(source, compressed);
                applySource(source.id, parsed);
                ready = true;
                Log.i(TAG, "EPG refreshed source=" + source.id
                        + " channels=" + parsed.size());
                notifyChanged();
            } catch (Exception error) {
                Log.w(TAG, "EPG refresh failed source=" + source.id
                        + " reason=" + safeMessage(error));
            }
        }
    }

    private boolean shouldRefresh(SourceConfig source, File file, long nowMs) {
        if (!file.isFile()
                || nowMs - file.lastModified() >= REFRESH_INTERVAL_MS) {
            return true;
        }
        return false;
    }

    private void requestForceRefreshForMissingChannel(EpgRef ref) {
        final SourceConfig source = findSource(ref.source);
        if (source == null) {
            return;
        }
        synchronized (lock) {
            if (forcedRefreshSources.contains(ref.source)) {
                Log.i(TRACE_TAG, "force refresh skipped source=" + ref.source
                        + " reason=already_attempted");
                return;
            }
            forcedRefreshSources.add(ref.source);
        }
        Log.i(TRACE_TAG, "channel epg missing today channel=" + ref.id
                + " source=" + ref.source);
        Log.i(TRACE_TAG, "force refresh source=" + ref.source
                + " reason=channel_today_empty");
        new Thread(new Runnable() {
            @Override
            public void run() {
                refreshSource(source);
            }
        }, "epg-force-refresh-" + ref.source).start();
    }

    private void refreshSource(SourceConfig source) {
        try {
            byte[] compressed = download(source.url);
            HashMap<String, ArrayList<Programme>> parsed =
                    parse(source.id, new ByteArrayInputStream(compressed));
            traceParsedSource(source.id, "remote", parsed);
            saveCache(source, compressed);
            applySource(source.id, parsed);
            ready = true;
            Log.i(TAG, "EPG refreshed source=" + source.id
                    + " channels=" + parsed.size());
            notifyChanged();
        } catch (Exception error) {
            Log.w(TAG, "EPG refresh failed source=" + source.id
                    + " reason=" + safeMessage(error));
        }
    }

    private static SourceConfig findSource(String sourceId) {
        for (SourceConfig source : SOURCES) {
            if (source.id.equals(sourceId)) {
                return source;
            }
        }
        return null;
    }

    private void applySource(String sourceId,
            HashMap<String, ArrayList<Programme>> parsed) {
        synchronized (lock) {
            ArrayList<String> removeKeys = new ArrayList<String>();
            for (String item : programmes.keySet()) {
                if (item.startsWith(sourceId + "\n")) {
                    removeKeys.add(item);
                }
            }
            for (String item : removeKeys) {
                programmes.remove(item);
            }
            for (String channelId : parsed.keySet()) {
                ArrayList<Programme> list = parsed.get(channelId);
                Collections.sort(list, new Comparator<Programme>() {
                    @Override
                    public int compare(Programme left, Programme right) {
                        if (left.startMs == right.startMs) {
                            return 0;
                        }
                        return left.startMs < right.startMs ? -1 : 1;
                    }
                });
                programmes.put(key(sourceId, channelId), list);
            }
        }
    }

    private HashMap<String, ArrayList<Programme>> parse(String sourceId, InputStream input)
            throws IOException, SAXException, ParserConfigurationException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        GZIPInputStream gzip = null;
        InputStream xmlInput = buffered;
        try {
            buffered.mark(2);
            int first = buffered.read();
            int second = buffered.read();
            buffered.reset();
            boolean gzipFormat = first == 0x1f && second == 0x8b;
            if (gzipFormat) {
                gzip = new GZIPInputStream(buffered);
                xmlInput = gzip;
            }
            Log.i(TRACE_TAG, "parse source=" + sourceId
                    + " detected format=" + (gzipFormat ? "gzip" : "xml"));
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            SAXParser parser = factory.newSAXParser();
            ProgrammeHandler handler = new ProgrammeHandler();
            InputSource source = new InputSource(xmlInput);
            source.setEncoding("UTF-8");
            parser.parse(source, handler);
            return handler.items;
        } finally {
            if (gzip != null) {
                gzip.close();
            } else {
                buffered.close();
            }
        }
    }

    private byte[] download(String urlText) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlText);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "XingShiTV/1.0");
            connection.setRequestProperty("Accept", "application/gzip,*/*");
            int status = connection.getResponseCode();
            Log.i(TRACE_TAG, "download url=" + urlText + " http=" + status);
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            byte[] bytes = readAll(connection.getInputStream());
            Log.i(TRACE_TAG, "download url=" + urlText + " bytes=" + bytes.length);
            return bytes;
        } finally {
            if (connection != null) {
                connection.disconnect();
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
                if (total > MAX_COMPRESSED_BYTES) {
                    throw new IOException("EPG file too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private void saveCache(SourceConfig source, byte[] bytes) throws IOException {
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(cacheFile(source));
            output.write(bytes);
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }

    private File cacheFile(SourceConfig source) {
        return new File(context.getFilesDir(), source.cacheName);
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onEpgChanged();
        }
    }

    private static EpgRef resolveRef(Channel channel) {
        if (channel == null) {
            return null;
        }
        if (channel.epgId != null && channel.epgSource != null) {
            return new EpgRef(channel.epgSource, channel.epgId);
        }
        return explicitRemoteRef(channel.name);
    }

    private static EpgRef explicitRemoteRef(String channelName) {
        if ("TVB Plus".equals(channelName)) {
            return new EpgRef(SOURCE_EPGPW_HK, "368361");
        }
        if ("公视".equals(channelName)) {
            return new EpgRef(SOURCE_EPGPW_TW, "457215");
        }
        if ("港台电视31".equals(channelName)) {
            return new EpgRef(SOURCE_EPGPW_HK, "368550");
        }
        if ("港台电视32".equals(channelName)) {
            return new EpgRef(SOURCE_EPGPW_HK, "368551");
        }
        if ("翡翠台".equals(channelName)) {
            return new EpgRef(SOURCE_EPGPW_HK, "368366");
        }
        return null;
    }

    private static String key(String sourceId, String channelId) {
        return sourceId + "\n" + channelId;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private static void traceParsedSource(String sourceId, String origin,
            HashMap<String, ArrayList<Programme>> parsed) {
        Log.i(TRACE_TAG, "parsed source=" + sourceId + " origin=" + origin
                + " channels=" + parsed.size()
                + " cctv1=" + count(parsed, "CCTV1")
                + " hunan=" + count(parsed, "湖南卫视")
                + " jiangsu=" + count(parsed, "江苏卫视")
                + " guangdong=" + count(parsed, "广东卫视"));
        traceFirst("parsed cctv1", parsed.get("CCTV1"));
        traceFirst("parsed hunan", parsed.get("湖南卫视"));
        traceFirst("parsed jiangsu", parsed.get("江苏卫视"));
        traceFirst("parsed guangdong", parsed.get("广东卫视"));
    }

    private static int count(HashMap<String, ArrayList<Programme>> parsed, String channelId) {
        ArrayList<Programme> items = parsed.get(channelId);
        return items == null ? 0 : items.size();
    }

    private static void traceQuery(String stage, Channel channel, EpgRef ref,
            int count, long nowMs, Programme current, Programme next) {
        if (channel == null || !isTraceChannel(channel.name)) {
            return;
        }
        Log.i(TRACE_TAG, stage + " channel=" + channel.name
                + " epgSource=" + (ref == null ? "" : ref.source)
                + " epgId=" + (ref == null ? "" : ref.id)
                + " count=" + count
                + " now=" + formatTime(nowMs)
                + " current=" + formatProgram(current)
                + " next=" + formatProgram(next));
    }

    private static boolean isTraceChannel(String name) {
        return "CCTV-1 综合".equals(name)
                || "湖南卫视".equals(name) || "江苏卫视".equals(name)
                || "广东卫视".equals(name);
    }

    private static void traceFirst(String label, ArrayList<Programme> items) {
        if (items == null || items.isEmpty()) {
            Log.i(TRACE_TAG, label + " first=none");
            return;
        }
        Log.i(TRACE_TAG, label + " first=" + formatProgram(items.get(0))
                + " last=" + formatProgram(items.get(items.size() - 1)));
    }

    private static String formatProgram(Programme item) {
        if (item == null) {
            return "none";
        }
        return formatTime(item.startMs) + "-" + formatTime(item.stopMs)
                + " " + item.title;
    }

    private static String formatTime(long timeMs) {
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(timeMs));
    }

    static final class ProgramState {
        final Programme current;
        final Programme next;

        ProgramState(Programme current, Programme next) {
            this.current = current;
            this.next = next;
        }

        static ProgramState empty() {
            return new ProgramState(null, null);
        }

        boolean hasAny() {
            return current != null || next != null;
        }
    }

    static final class Programme {
        final String title;
        final long startMs;
        final long stopMs;

        Programme(String title, long startMs, long stopMs) {
            this.title = title;
            this.startMs = startMs;
            this.stopMs = stopMs;
        }

        int progressPercent(long nowMs) {
            long duration = stopMs - startMs;
            if (duration <= 0L) {
                return 0;
            }
            long elapsed = Math.max(0L, Math.min(duration, nowMs - startMs));
            return (int) Math.min(100L, elapsed * 100L / duration);
        }
    }

    private static final class SourceConfig {
        final String id;
        final String url;
        final String cacheName;
        final String assetName;

        SourceConfig(String id, String url, String cacheName, String assetName) {
            this.id = id;
            this.url = url;
            this.cacheName = cacheName;
            this.assetName = assetName;
        }
    }

    private static final class EpgRef {
        final String source;
        final String id;

        EpgRef(String source, String id) {
            this.source = source;
            this.id = id;
        }
    }

    private static final class ProgrammeHandler extends DefaultHandler {
        final HashMap<String, ArrayList<Programme>> items =
                new HashMap<String, ArrayList<Programme>>();
        private final SimpleDateFormat dateFormat =
                new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        private String channelId;
        private long startMs;
        private long stopMs;
        private StringBuilder title;
        private boolean inTitle;

        ProgrammeHandler() {
            dateFormat.setTimeZone(TimeZone.getDefault());
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            if ("programme".equals(qName)) {
                channelId = attributes.getValue("channel");
                startMs = parseTime(attributes.getValue("start"));
                stopMs = parseTime(attributes.getValue("stop"));
                title = new StringBuilder();
                inTitle = false;
            } else if ("title".equals(qName) && channelId != null) {
                inTitle = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inTitle && title != null) {
                title.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ("title".equals(qName)) {
                inTitle = false;
            } else if ("programme".equals(qName)) {
                if (channelId != null && startMs > 0L && stopMs > startMs) {
                    String name = title == null ? "" : title.toString().trim();
                    if (name.length() > 0) {
                        ArrayList<Programme> list = items.get(channelId);
                        if (list == null) {
                            list = new ArrayList<Programme>();
                            items.put(channelId, list);
                        }
                        list.add(new Programme(name, startMs, stopMs));
                    }
                }
                channelId = null;
                title = null;
                inTitle = false;
            }
        }

        private long parseTime(String value) {
            if (value == null) {
                return 0L;
            }
            String trimmed = value.trim();
            if (trimmed.length() < 20) {
                return 0L;
            }
            try {
                return dateFormat.parse(trimmed.substring(0, 20)).getTime();
            } catch (ParseException error) {
                return 0L;
            }
        }
    }
}
