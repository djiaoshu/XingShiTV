package com.xingshi.tv;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class JstvLiveResolver {
    private static final String TAG = "JstvLiveResolver";
    private static final String STREAM_HOST =
            "https://litchi-play-encrypted-site.jstv.com/";
    private static final String SECRET = "wrf2yJaCwC8HX3cfJz8P";
    static final String STREAM_REFERER = "https://live.jstv.com/";
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    /*
     * JSTV validates txTime against server time. Some TV boxes/emulators keep a
     * drifting local clock; a 3-minute window can create immediately expired
     * URLs and make every JSTV channel fail with playlist HTTP 403.
     */
    private static final int TX_TIME_OFFSET_SECONDS = 24 * 60 * 60;

    String resolve(Channel channel) throws IOException {
        if (channel.jstvStreamName == null || channel.jstvStreamName.length() == 0) {
            throw new IOException("Missing JSTV stream for " + channel.name);
        }
        Log.i(TAG, "JSTV resolve channel=" + channel.name
                + " channelId=" + nullToEmpty(channel.jstvChannelId)
                + " en=" + nullToEmpty(channel.jstvEn)
                + " stream=" + channel.jstvStreamName
                + " path=" + nullToEmpty(channel.jstvPath));

        String streamName = channel.jstvStreamName;
        String path = channel.jstvPath;
        if (path == null || path.length() == 0) {
            throw new IOException("Missing JSTV path for " + channel.name);
        }
        String baseUrl = STREAM_HOST + stripLeadingSlash(path);
        String txTime = Long.toHexString(System.currentTimeMillis() / 1000L
                + TX_TIME_OFFSET_SECONDS);
        String txSecret = md5(SECRET + streamName + txTime);
        String signedUrl = baseUrl + "?txSecret=" + txSecret + "&txTime=" + txTime;

        Log.i(TAG, "JSTV signed url generated channel=" + channel.name
                + " stream=" + streamName
                + " txTime=" + txTime);
        debugProbePlaylist(channel, signedUrl);
        Log.i(TAG, "JSTV resolve success channel=" + channel.name
                + " stream=" + streamName);
        return signedUrl;
    }

    private void debugProbePlaylist(Channel channel, String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            applyHeaders(connection);
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = input == null ? "" : new String(readLimited(input, 512), "UTF-8");
            Log.i(TAG, "JSTV playlist probe channel=" + channel.name
                    + " stream=" + channel.jstvStreamName
                    + " status=" + status
                    + " contentType=" + connection.getContentType()
                    + " startsWithExtM3U=" + body.startsWith("#EXTM3U")
                    + " preview=" + preview(body));
        } catch (IOException error) {
            Log.w(TAG, "JSTV playlist probe failed channel=" + channel.name
                    + " stream=" + channel.jstvStreamName
                    + " error=" + error.getMessage(), error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void applyHeaders(HttpURLConnection connection) {
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", STREAM_REFERER);
        connection.setRequestProperty("Accept", "*/*");
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stripLeadingSlash(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') {
            index++;
        }
        return value.substring(index);
    }

    private static String preview(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ');
        if (compact.length() <= 300) {
            return compact;
        }
        return compact.substring(0, 300) + "...";
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
