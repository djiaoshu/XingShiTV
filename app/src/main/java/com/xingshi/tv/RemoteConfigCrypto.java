package com.xingshi.tv;

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class RemoteConfigCrypto {
    private static final String TAG = "RemoteConfigCrypto";
    private static final String ALG = "A256GCM";
    private static final int GCM_TAG_BITS = 128;

    private static final byte[] KEY = new byte[] {
            (byte) 0x7a, (byte) 0x31, (byte) 0xd4, (byte) 0x8e,
            (byte) 0x05, (byte) 0xb9, (byte) 0x42, (byte) 0xe1,
            (byte) 0x6c, (byte) 0x90, (byte) 0xfa, (byte) 0x23,
            (byte) 0x11, (byte) 0xcd, (byte) 0x77, (byte) 0x58,
            (byte) 0x9f, (byte) 0x04, (byte) 0xa6, (byte) 0xbe,
            (byte) 0x36, (byte) 0x62, (byte) 0x19, (byte) 0xc8,
            (byte) 0xed, (byte) 0x73, (byte) 0x2a, (byte) 0x0f,
            (byte) 0x84, (byte) 0x55, (byte) 0xb1, (byte) 0x6d
    };

    private RemoteConfigCrypto() {
    }

    static String decryptIfNeeded(String response) throws IOException {
        if (!looksLikeEncryptedConfig(response)) {
            return response;
        }
        try {
            JSONObject root = new JSONObject(response);
            String alg = root.optString("alg", "");
            if (!ALG.equals(alg)) {
                throw new IOException("unsupported encrypted config algorithm");
            }
            byte[] iv = Base64.decode(root.optString("iv", ""), Base64.DEFAULT);
            byte[] cipherText = Base64.decode(root.optString("data", ""), Base64.DEFAULT);
            if (iv.length == 0 || cipherText.length == 0) {
                throw new IOException("invalid encrypted config payload");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            Log.i(TAG, "RemoteConfigCrypto decrypt success");
            return new String(plain, "UTF-8");
        } catch (IOException error) {
            Log.w(TAG, "RemoteConfigCrypto decrypt failed " + safeMessage(error));
            throw error;
        } catch (Exception error) {
            Log.w(TAG, "RemoteConfigCrypto decrypt failed " + safeMessage(error));
            throw new IOException("encrypted remote config decrypt failed", error);
        }
    }

    private static boolean looksLikeEncryptedConfig(String response) throws IOException {
        if (response == null) {
            throw new IOException("empty remote config");
        }
        String text = response.trim();
        if (text.length() == 0) {
            throw new IOException("empty remote config");
        }
        if (!text.startsWith("{")) {
            return false;
        }
        try {
            JSONObject root = new JSONObject(text);
            return ALG.equals(root.optString("alg", ""))
                    && root.has("iv") && root.has("data");
        } catch (JSONException error) {
            return false;
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
