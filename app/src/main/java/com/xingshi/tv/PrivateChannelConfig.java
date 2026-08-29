package com.xingshi.tv;

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class PrivateChannelConfig {
    static final String GROUP_ID = "private_channels";
    static final String GROUP_NAME = "私密频道";

    private static final String TAG = "PrivateChannelConfig";
    private static final String CONFIG_URL = "http://168.138.204.101/channels.json";
    private static final String USERNAME = "xingshi";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int MAX_CONFIG_BYTES = 256 * 1024;

    private PrivateChannelConfig() {
    }

    static ChannelCatalog.Group[] download(String password) throws IOException {
        String json = downloadJson(password);
        try {
            ChannelCatalog.Group[] groups = RemoteChannelConfig.parse(json, GROUP_ID);
            Log.i(TAG, "private config loaded groups=" + groups.length);
            return groups;
        } catch (JSONException error) {
            throw new IOException("invalid private channel config", error);
        }
    }

    private static String downloadJson(String password) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(CONFIG_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "XingShiTV/1.0");
            connection.setRequestProperty("Accept", "application/json,*/*");
            connection.setRequestProperty("Authorization", basicAuth(password));
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new UnauthorizedException();
            }
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

    private static String basicAuth(String password) throws IOException {
        String credentials = USERNAME + ":" + (password == null ? "" : password);
        return "Basic " + Base64.encodeToString(credentials.getBytes("UTF-8"),
                Base64.NO_WRAP);
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
                    throw new IOException("private config too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    static final class UnauthorizedException extends IOException {
        UnauthorizedException() {
            super("HTTP 401");
        }
    }
}
