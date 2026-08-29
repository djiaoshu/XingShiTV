package com.xingshi.tv;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class CustomStreamTypeDetector {
    enum MediaType {
        HLS,
        FLV,
        MPEG_TS,
        UNKNOWN
    }

    static final class Result {
        final MediaType type;
        final String finalUrl;
        final String contentType;
        final int httpStatus;
        final long elapsedMs;
        final boolean fromCache;

        Result(MediaType type, String finalUrl, String contentType, int httpStatus,
                long elapsedMs, boolean fromCache) {
            this.type = type;
            this.finalUrl = finalUrl;
            this.contentType = contentType;
            this.httpStatus = httpStatus;
            this.elapsedMs = elapsedMs;
            this.fromCache = fromCache;
        }
    }

    private static final String TAG = "PLAYER_TEST";
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int READ_TIMEOUT_MS = 1500;
    private static final int TOTAL_TIMEOUT_MS = 3200;
    private static final int PROBE_BYTES = 2048;
    private static final ConcurrentHashMap<String, Result> CACHE =
            new ConcurrentHashMap<String, Result>();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "custom-stream-probe");
            thread.setDaemon(true);
            return thread;
        }
    });

    private CustomStreamTypeDetector() {
    }

    static Result detect(String streamUrl) {
        Result cached = CACHE.get(streamUrl);
        if (cached != null) {
            return new Result(cached.type, cached.finalUrl, cached.contentType,
                    cached.httpStatus, 0L, true);
        }

        long startedAt = System.currentTimeMillis();
        final String probeUrl = streamUrl;
        Future<Result> future = EXECUTOR.submit(new Callable<Result>() {
            @Override
            public Result call() {
                return detectInternal(probeUrl);
            }
        });
        try {
            return future.get(TOTAL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            long elapsed = System.currentTimeMillis() - startedAt;
            Result result = new Result(MediaType.UNKNOWN, null, null, -1, elapsed, false);
            CACHE.put(streamUrl, result);
            Log.w(TAG, "custom probe timeout elapsedMs=" + elapsed);
            return result;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            long elapsed = System.currentTimeMillis() - startedAt;
            Result result = new Result(MediaType.UNKNOWN, null, null, -1, elapsed, false);
            CACHE.put(streamUrl, result);
            Log.w(TAG, "custom probe interrupted elapsedMs=" + elapsed);
            return result;
        } catch (ExecutionException error) {
            long elapsed = System.currentTimeMillis() - startedAt;
            Result result = new Result(MediaType.UNKNOWN, null, null, -1, elapsed, false);
            CACHE.put(streamUrl, result);
            Log.w(TAG, "custom probe failed elapsedMs=" + elapsed
                    + " error=" + safeMessage(error));
            return result;
        }
    }

    private static Result detectInternal(String streamUrl) {
        long startedAt = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(streamUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "XingShiTV/1.0");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Range", "bytes=0-" + (PROBE_BYTES - 1));
            int status = connection.getResponseCode();
            String finalUrl = connection.getURL().toString();
            String contentType = connection.getContentType();
            byte[] header = readSmallHeader(connection);
            long elapsed = System.currentTimeMillis() - startedAt;
            MediaType type = detectType(finalUrl, contentType, header);
            Result result = new Result(type, finalUrl, contentType, status, elapsed, false);
            CACHE.put(streamUrl, result);
            Log.i(TAG, "custom probe result type=" + type
                    + " http=" + status
                    + " elapsedMs=" + elapsed
                    + " finalHost=" + MainActivity.hostOf(finalUrl)
                    + " contentType=" + safeContentType(contentType));
            return result;
        } catch (IOException error) {
            long elapsed = System.currentTimeMillis() - startedAt;
            Result result = new Result(MediaType.UNKNOWN, null, null, -1, elapsed, false);
            CACHE.put(streamUrl, result);
            Log.w(TAG, "custom probe failed elapsedMs=" + elapsed
                    + " error=" + safeMessage(error));
            return result;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static MediaType detectType(String finalUrl, String contentType, byte[] header) {
        String lowerUrl = finalUrl == null ? "" : finalUrl.toLowerCase(Locale.US);
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.US);
        if (lowerUrl.contains(".m3u8") || lowerType.contains("mpegurl")) {
            return MediaType.HLS;
        }
        if (lowerUrl.contains(".flv") || lowerType.contains("x-flv")
                || lowerType.contains("video/flv") || startsWithFlv(header)) {
            return MediaType.FLV;
        }
        if (lowerUrl.contains(".ts") || lowerType.contains("mp2t") || looksLikeTs(header)) {
            return MediaType.MPEG_TS;
        }
        return MediaType.UNKNOWN;
    }

    private static byte[] readSmallHeader(HttpURLConnection connection) throws IOException {
        InputStream input = null;
        try {
            input = connection.getInputStream();
            byte[] buffer = new byte[PROBE_BYTES];
            int offset = 0;
            int count;
            while (offset < buffer.length
                    && (count = input.read(buffer, offset, buffer.length - offset)) > 0) {
                offset += count;
                if (offset >= 3 && startsWithFlv(buffer)) {
                    break;
                }
                if (offset >= 564 && looksLikeTs(buffer)) {
                    break;
                }
            }
            if (offset == buffer.length) {
                return buffer;
            }
            byte[] result = new byte[offset];
            System.arraycopy(buffer, 0, result, 0, offset);
            return result;
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    private static boolean startsWithFlv(byte[] data) {
        return data != null && data.length >= 3
                && data[0] == 'F' && data[1] == 'L' && data[2] == 'V';
    }

    private static boolean looksLikeTs(byte[] data) {
        if (data == null || data.length < 188) {
            return false;
        }
        if ((data[0] & 0xff) != 0x47) {
            return false;
        }
        return data.length < 376 || (data[188] & 0xff) == 0x47
                || data.length < 564 || (data[376] & 0xff) == 0x47;
    }

    private static String safeContentType(String contentType) {
        return contentType == null ? "" : contentType;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
