package com.xingshi.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

final class KankanewsContext {
    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    static final String STREAM_REFERER = "https://live.kankanews.com/";
    static final String PAGE_REFERER = "https://live.kankanews.com/huikan";

    private static final String PREFERENCES = "kankanews_context";
    private static final String CLIENT_ID = "client_id";
    private static final String NANOID_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz-";
    private static final String KAPI_NONCE_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile String cachedClientId;
    private static volatile String debugClientIdOverride;
    private static volatile ApiHeaderOverride debugApiHeaderOverride;
    private static volatile boolean debugDiagnosticsEnabled;

    private KankanewsContext() {
    }

    static String getUserAgent() {
        return USER_AGENT;
    }

    static String getUserAgentHash() {
        return safeHash(USER_AGENT);
    }

    static String getClientId(Context context) {
        if (debugClientIdOverride != null && isValidNanoId(debugClientIdOverride)) {
            return debugClientIdOverride;
        }
        if (cachedClientId != null && isValidNanoId(cachedClientId)) {
            return cachedClientId;
        }
        synchronized (KankanewsContext.class) {
            if (cachedClientId != null && isValidNanoId(cachedClientId)) {
                return cachedClientId;
            }
            if (context != null) {
                SharedPreferences preferences = context.getApplicationContext()
                        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
                String stored = preferences.getString(CLIENT_ID, null);
                if (isValidNanoId(stored)) {
                    cachedClientId = stored;
                    return cachedClientId;
                }
                cachedClientId = randomNanoId(21);
                preferences.edit().putString(CLIENT_ID, cachedClientId).apply();
                return cachedClientId;
            }
            cachedClientId = randomNanoId(21);
            return cachedClientId;
        }
    }

    static String getCurrentClientIdForLog() {
        return getClientId(null);
    }

    static void setDebugClientIdOverride(String value) {
        if (value != null && isValidNanoId(value)) {
            debugClientIdOverride = value;
        } else {
            debugClientIdOverride = null;
        }
    }

    static boolean hasDebugClientIdOverride() {
        return debugClientIdOverride != null && isValidNanoId(debugClientIdOverride);
    }

    static void setDebugApiHeaderOverride(String nonce, String timestamp, String sign) {
        if (nonce != null && nonce.length() > 0
                && timestamp != null && timestamp.length() > 0
                && sign != null && sign.length() > 0) {
            debugApiHeaderOverride = new ApiHeaderOverride(nonce, timestamp, sign);
        } else {
            debugApiHeaderOverride = null;
        }
    }

    static ApiHeaderOverride getDebugApiHeaderOverride() {
        return debugApiHeaderOverride;
    }

    static boolean hasDebugApiHeaderOverride() {
        return debugApiHeaderOverride != null;
    }

    static void setDebugDiagnosticsEnabled(boolean enabled) {
        debugDiagnosticsEnabled = enabled;
    }

    static boolean isDebugDiagnosticsEnabled() {
        return debugDiagnosticsEnabled;
    }

    static String getClientIdHash(Context context) {
        return safeHash(getClientId(context));
    }

    static int getClientIdLength(Context context) {
        return getClientId(context).length();
    }

    static boolean isCurrentClientIdValid(Context context) {
        return isValidNanoId(getClientId(context));
    }

    static String randomNanoId(int size) {
        StringBuilder builder = new StringBuilder(size);
        for (int index = 0; index < size; index++) {
            builder.append(NANOID_ALPHABET.charAt(RANDOM.nextInt(NANOID_ALPHABET.length())));
        }
        return builder.toString();
    }

    static String randomKapiNonce() {
        // Kankanews KAPI nonce must match the web frontend: 8 chars,
        // lowercase alphanumeric only. Using NanoID's full alphabet can still
        // return KAPI code=1000, but produces CDN credentials that fail with
        // HTTP 403. This is a confirmed protocol rule from PC/Android replay tests.
        StringBuilder builder = new StringBuilder(8);
        for (int index = 0; index < 8; index++) {
            builder.append(KAPI_NONCE_ALPHABET.charAt(RANDOM.nextInt(
                    KAPI_NONCE_ALPHABET.length())));
        }
        return builder.toString();
    }

    static boolean isValidNanoId(String value) {
        if (value == null || value.length() != 21) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (NANOID_ALPHABET.indexOf(value.charAt(index)) < 0) {
                return false;
            }
        }
        return true;
    }

    static String safeHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 4 && index < bytes.length; index++) {
                int number = bytes[index] & 0xff;
                if (number < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(number));
            }
            return builder.toString().toLowerCase(Locale.US);
        } catch (NoSuchAlgorithmException error) {
            return "unknown";
        } catch (IOException error) {
            return "unknown";
        }
    }

    static final class ApiHeaderOverride {
        final String nonce;
        final String timestamp;
        final String sign;

        ApiHeaderOverride(String nonce, String timestamp, String sign) {
            this.nonce = nonce;
            this.timestamp = timestamp;
            this.sign = sign;
        }
    }
}
