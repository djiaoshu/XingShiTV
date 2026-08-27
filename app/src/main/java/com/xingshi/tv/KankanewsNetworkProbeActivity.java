package com.xingshi.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.util.Base64;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KankanewsNetworkProbeActivity extends Activity {
    private static final String TAG = "KANKAN_NET";
    private static final String CTX_TAG = "KANKAN_CTX";
    private static final String PROBE_PAGE = "https://live.kankanews.com/__xingshitv_probe__";
    private static final String DEFAULT_CHANNEL_ID = "1";
    private static final String URL_FILE = "kankanews_probe_url.txt";
    private static final String UUID_FILE = "kankanews_probe_uuid.txt";
    private WebView webView;
    private String streamUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TlsCompat.install();
        runProbe();
    }

    private void runProbe() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String channelId = getIntent().getStringExtra("channel_id");
                if (channelId == null || channelId.length() == 0) {
                    channelId = DEFAULT_CHANNEL_ID;
                }
                try {
                    String label = getIntent().getStringExtra("sample_label");
                    if (label == null || label.length() == 0) {
                        label = "ANDROID_URL";
                    }
                    KankanewsContext.setDebugDiagnosticsEnabled(true);
                    applyUuidOverride();
                    applyApiHeaderOverride();
                    writePrivateText(UUID_FILE,
                            KankanewsContext.getClientId(KankanewsNetworkProbeActivity.this));
                    Log.i(TAG, "probe start label=" + label + " channelId=" + channelId);
                    logAndroidNetworkInfo();
                    streamUrl = readStreamUrlExtra();
                    if (streamUrl == null || streamUrl.length() == 0) {
                        Channel channel = new Channel("999", "KankanewsProbe", "", null,
                                null, null, null, null, null, null, null, null,
                                channelId, "", null, null, null);
                        KankanewsLiveResolver resolver =
                                new KankanewsLiveResolver(KankanewsNetworkProbeActivity.this);
                        streamUrl = resolver.resolve(channel);
                        writePrivateText(URL_FILE, streamUrl);
                        Log.i(TAG, "resolved stream label=" + label
                                + " host=" + hostOf(streamUrl)
                                + " path=" + pathOf(streamUrl)
                                + " uuidHash=" + KankanewsContext.getClientIdHash(
                                        KankanewsNetworkProbeActivity.this)
                                + " uuidLength=" + KankanewsContext.getClientIdLength(
                                        KankanewsNetworkProbeActivity.this)
                                + " uaHash=" + KankanewsContext.getUserAgentHash());
                    } else {
                        Log.i(TAG, "using external stream label=" + label
                                + " host=" + hostOf(streamUrl)
                                + " path=" + pathOf(streamUrl)
                                + " uuidHash=" + KankanewsContext.getClientIdHash(
                                        KankanewsNetworkProbeActivity.this)
                                + " uuidLength=" + KankanewsContext.getClientIdLength(
                                        KankanewsNetworkProbeActivity.this)
                                + " uaHash=" + KankanewsContext.getUserAgentHash());
                    }
                    probeHttpUrlConnection(streamUrl);
                    probeOkHttpIfAvailable(streamUrl);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            probeWebView(streamUrl);
                        }
                    });
                } catch (Exception error) {
                    Log.e(TAG, "probe failed stage=resolve error="
                            + error.getClass().getSimpleName() + ":" + error.getMessage(), error);
                }
            }
        }, "kankanews-network-probe").start();
    }

    private String readStreamUrlExtra() {
        String encoded = getIntent().getStringExtra("stream_url_b64");
        if (encoded == null || encoded.length() == 0) {
            return null;
        }
        try {
            return new String(Base64.decode(encoded, Base64.DEFAULT), "UTF-8");
        } catch (Exception error) {
            Log.w(TAG, "unable to decode external stream url error="
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
            return null;
        }
    }

    private void applyUuidOverride() {
        String encoded = getIntent().getStringExtra("uuid_b64");
        if (encoded == null || encoded.length() == 0) {
            KankanewsContext.setDebugClientIdOverride(null);
            return;
        }
        try {
            String uuid = new String(Base64.decode(encoded, Base64.DEFAULT), "UTF-8");
            KankanewsContext.setDebugClientIdOverride(uuid);
            Log.i(TAG, "uuid override applied valid="
                    + KankanewsContext.isValidNanoId(uuid)
                    + " length=" + uuid.length()
                    + " uuidHash=" + KankanewsContext.safeHash(uuid));
        } catch (Exception error) {
            KankanewsContext.setDebugClientIdOverride(null);
            Log.w(TAG, "uuid override failed error="
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
        }
    }

    private void applyApiHeaderOverride() {
        String nonce = readStringExtra("nonce_b64");
        String timestamp = readStringExtra("timestamp_b64");
        String sign = readStringExtra("sign_b64");
        if (nonce == null || timestamp == null || sign == null) {
            KankanewsContext.setDebugApiHeaderOverride(null, null, null);
            return;
        }
        KankanewsContext.setDebugApiHeaderOverride(nonce, timestamp, sign);
        Log.i(TAG, "api header override applied nonceLength=" + nonce.length()
                + " nonceHash=" + KankanewsContext.safeHash(nonce)
                + " timestamp=" + timestamp
                + " signLength=" + sign.length()
                + " signHash=" + KankanewsContext.safeHash(sign));
    }

    private String readStringExtra(String name) {
        String encoded = getIntent().getStringExtra(name);
        if (encoded == null || encoded.length() == 0) {
            return null;
        }
        try {
            return new String(Base64.decode(encoded, Base64.DEFAULT), "UTF-8");
        } catch (Exception error) {
            Log.w(TAG, "unable to decode extra=" + name + " error="
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
            return null;
        }
    }

    private void writePrivateText(String name, String value) {
        FileOutputStream output = null;
        try {
            File file = new File(getFilesDir(), name);
            output = new FileOutputStream(file, false);
            output.write(value.getBytes("UTF-8"));
            if (URL_FILE.equals(name)) {
                Log.i(TAG, "android url stored file=" + URL_FILE
                        + " host=" + hostOf(value)
                        + " path=" + pathOf(value));
            } else if (UUID_FILE.equals(name)) {
                Log.i(TAG, "android uuid stored file=" + UUID_FILE
                        + " uuidHash=" + KankanewsContext.safeHash(value)
                        + " uuidLength=" + value.length()
                        + " uuidValid=" + KankanewsContext.isValidNanoId(value));
            }
        } catch (Exception error) {
            Log.w(TAG, "android private file store failed name=" + name + " error="
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void logAndroidNetworkInfo() {
        logAndroidExit("ipv4", new String[] {
                "https://api4.ipify.org?format=json",
                "https://ipv4.icanhazip.com",
                "http://ipv4.icanhazip.com",
                "https://4.ident.me",
                "http://4.ident.me"
        });
        try {
            ProbeResult ipv6 = httpGet("https://api6.ipify.org?format=json", false);
            Log.i(TAG, "android exit ipv6 http=" + ipv6.status
                    + " ipHash=" + KankanewsContext.safeHash(extractIp(ipv6.body)));
        } catch (Exception error) {
            Log.i(TAG, "android exit ipv6 error="
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName("volc-stream.kksmg.com");
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < addresses.length; index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(KankanewsContext.safeHash(addresses[index].getHostAddress()));
            }
            Log.i(TAG, "android dns host=volc-stream.kksmg.com count=" + addresses.length
                    + " hashes=" + builder.toString());
        } catch (Exception error) {
            Log.i(TAG, "android dns error="
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
        }
    }

    private void logAndroidExit(String kind, String[] urls) {
        String lastError = "";
        for (String url : urls) {
            try {
                ProbeResult result = httpGet(url, false);
                String ip = extractIp(result.body);
                if (result.status >= 200 && result.status < 300 && ip.length() > 0) {
                    Log.i(TAG, "android exit " + kind
                            + " http=" + result.status
                            + " ipHash=" + KankanewsContext.safeHash(ip)
                            + " provider=" + hostOf(url));
                    return;
                }
                lastError = "HTTP " + result.status + " provider=" + hostOf(url);
            } catch (Exception error) {
                lastError = error.getClass().getSimpleName() + ":" + error.getMessage()
                        + " provider=" + hostOf(url);
            }
        }
        Log.i(TAG, "android exit " + kind + " error=" + lastError);
    }

    private void probeHttpUrlConnection(String url) {
        try {
            ProbeResult playlist = httpGet(url, false);
            Log.i(TAG, "httpurlconnection playlist http=" + playlist.status
                    + " host=" + hostOf(url)
                    + " contentType=" + empty(playlist.contentType)
                    + " bytes=" + playlist.body.length
                    + " uaHash=" + KankanewsContext.getUserAgentHash());
            Log.i(CTX_TAG, "probe http uaHash=" + KankanewsContext.getUserAgentHash()
                    + " uuidHash=" + KankanewsContext.getClientIdHash(this)
                    + " uuidValid=" + KankanewsContext.isCurrentClientIdValid(this)
                    + " uuidLength=" + KankanewsContext.getClientIdLength(this)
                    + " host=" + hostOf(url)
                    + " path=" + pathOf(url));
            String first = firstMediaUrl(url, playlist.body);
            if (first != null && first.length() > 0) {
                ProbeResult segment = httpGet(first, true);
                Log.i(TAG, "httpurlconnection first http=" + segment.status
                        + " kind=" + kindOf(first, segment.contentType)
                        + " host=" + hostOf(first)
                        + " path=" + pathOf(first)
                        + " contentType=" + empty(segment.contentType)
                        + " bytes=" + segment.body.length);
            }
        } catch (Exception error) {
            Log.w(TAG, "httpurlconnection failed error="
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
        }
    }

    private ProbeResult httpGet(String url, boolean range) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", KankanewsContext.getUserAgent());
            connection.setRequestProperty("M-Uuid", KankanewsContext.getClientId(this));
            connection.setRequestProperty("Referer", KankanewsLiveResolver.STREAM_REFERER);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Connection", "keep-alive");
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

    private void probeOkHttpIfAvailable(String url) {
        try {
            Class<?> clientClass = Class.forName("okhttp3.OkHttpClient");
            Class<?> requestBuilderClass = Class.forName("okhttp3.Request$Builder");
            Object client = clientClass.newInstance();
            Object builder = requestBuilderClass.newInstance();
            invoke(builder, "url", new Class<?>[] { String.class }, new Object[] { url });
            invoke(builder, "header",
                    new Class<?>[] { String.class, String.class },
                    new Object[] { "User-Agent", KankanewsContext.getUserAgent() });
            invoke(builder, "header",
                    new Class<?>[] { String.class, String.class },
                    new Object[] { "M-Uuid", KankanewsContext.getClientId(this) });
            invoke(builder, "header",
                    new Class<?>[] { String.class, String.class },
                    new Object[] { "Referer", KankanewsLiveResolver.STREAM_REFERER });
            invoke(builder, "header",
                    new Class<?>[] { String.class, String.class },
                    new Object[] { "Accept", "*/*" });
            Object request = invoke(builder, "build", new Class<?>[0], new Object[0]);
            Object call = invoke(client, "newCall",
                    new Class<?>[] { Class.forName("okhttp3.Request") },
                    new Object[] { request });
            Object response = invoke(call, "execute", new Class<?>[0], new Object[0]);
            int code = ((Integer) invoke(response, "code", new Class<?>[0], new Object[0]))
                    .intValue();
            Log.i(TAG, "okhttp playlist http=" + code
                    + " host=" + hostOf(url)
                    + " uaHash=" + KankanewsContext.getUserAgentHash());
            Log.i(CTX_TAG, "probe okhttp uaHash=" + KankanewsContext.getUserAgentHash()
                    + " uuidHash=" + KankanewsContext.getClientIdHash(this)
                    + " uuidValid=" + KankanewsContext.isCurrentClientIdValid(this)
                    + " uuidLength=" + KankanewsContext.getClientIdLength(this)
                    + " host=" + hostOf(url)
                    + " path=" + pathOf(url));
            invoke(response, "close", new Class<?>[0], new Object[0]);
        } catch (ClassNotFoundException error) {
            Log.i(TAG, "okhttp unavailable no okhttp3 dependency");
        } catch (Exception error) {
            Log.w(TAG, "okhttp probe failed error="
                    + error.getClass().getSimpleName() + ":" + error.getMessage());
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object[] args)
            throws Exception {
        Method method = target.getClass().getMethod(name, types);
        return method.invoke(target, args);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void probeWebView(final String url) {
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setUserAgentString(KankanewsContext.getUserAgent());
        webView.addJavascriptInterface(new ProbeBridge(), "ProbeBridge");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    Log.i(TAG, "webview console " + sanitizeConsoleMessage(
                            consoleMessage.message()));
                }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                    WebResourceRequest request) {
                if (request != null && PROBE_PAGE.equals(request.getUrl().toString())) {
                    return probePageResponse(url);
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String pageUrl, Bitmap favicon) {
                Log.i(TAG, "webview page started url=" + safeUrl(pageUrl));
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                Log.i(TAG, "webview page finished url=" + safeUrl(pageUrl));
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                    WebResourceResponse errorResponse) {
                String requestUrl = request == null || request.getUrl() == null
                        ? "" : request.getUrl().toString();
                int status = errorResponse == null ? -1 : errorResponse.getStatusCode();
                Log.i(TAG, "webview http error status=" + status
                        + " host=" + hostOf(requestUrl)
                        + " path=" + pathOf(requestUrl));
            }
        });
        Log.i(TAG, "webview uaHash=" + KankanewsContext.safeHash(settings.getUserAgentString())
                + " loading probe page");
        Log.i(CTX_TAG, "probe webview uaHash="
                + KankanewsContext.safeHash(settings.getUserAgentString())
                + " uuidHash=" + KankanewsContext.getClientIdHash(this)
                + " uuidValid=" + KankanewsContext.isCurrentClientIdValid(this)
                + " uuidLength=" + KankanewsContext.getClientIdLength(this));
        webView.loadUrl(PROBE_PAGE);
    }

    private WebResourceResponse probePageResponse(String targetUrl) {
        String uuid = KankanewsContext.getClientId(this);
        String html = "<!doctype html><html><body><script>"
                + "var target='" + js(targetUrl) + "';"
                + "var muuid='" + js(uuid) + "';"
                + "console.log('probe page start host=" + js(hostOf(targetUrl)) + "');"
                + "fetch(target,{cache:'no-store',credentials:'omit',headers:{'M-Uuid':muuid}})"
                + ".then(function(r){console.log('fetch status='+r.status+' type='+r.type);"
                + "return r.text().then(function(t){ProbeBridge.report('fetch',r.status,r.type,t.length);});})"
                + ".catch(function(e){console.log('fetch error='+e);ProbeBridge.reportError('fetch',''+e);});"
                + "var xhr=new XMLHttpRequest();"
                + "xhr.onreadystatechange=function(){if(xhr.readyState===4){"
                + "console.log('xhr status='+xhr.status);"
                + "ProbeBridge.report('xhr',xhr.status,'',xhr.responseText.length);}};"
                + "try{xhr.open('GET',target,true);xhr.setRequestHeader('M-Uuid',muuid);xhr.send();}"
                + "catch(e){console.log('xhr error='+e);ProbeBridge.reportError('xhr',''+e);}"
                + "</script></body></html>";
        return new WebResourceResponse("text/html", "UTF-8",
                new ByteArrayInputStream(html.getBytes()));
    }

    public final class ProbeBridge {
        @JavascriptInterface
        public void report(String client, int status, String type, int bytes) {
            Log.i(TAG, "webview " + client
                    + " status=" + status
                    + " type=" + empty(type)
                    + " bytes=" + bytes
                    + " host=" + hostOf(streamUrl)
                    + " uaHash=" + KankanewsContext.getUserAgentHash());
        }

        @JavascriptInterface
        public void reportError(String client, String error) {
            Log.i(TAG, "webview " + client + " error=" + error
                    + " host=" + hostOf(streamUrl)
                    + " uaHash=" + KankanewsContext.getUserAgentHash());
        }
    }

    private static String firstMediaUrl(String playlistUrl, byte[] body) {
        URI base = URI.create(playlistUrl);
        String[] lines;
        try {
            lines = new String(body, "UTF-8").split("\\r?\\n", -1);
        } catch (IOException error) {
            return null;
        }
        for (String line : lines) {
            String item = line.trim();
            if (item.length() > 0 && !item.startsWith("#")) {
                return base.resolve(item).toString();
            }
        }
        return null;
    }

    private static String kindOf(String url, String contentType) {
        String lowerUrl = url == null ? "" : url.toLowerCase();
        String lowerType = contentType == null ? "" : contentType.toLowerCase();
        if (lowerUrl.contains(".m3u8") || lowerType.contains("mpegurl")) {
            return "playlist";
        }
        if (lowerUrl.contains(".ts") || lowerType.contains("mp2t")) {
            return "segment";
        }
        return "unknown";
    }

    private static String extractIp(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            String text = new String(body, "UTF-8");
            int start = text.indexOf("\"ip\"");
            if (start < 0) {
                return text.trim();
            }
            start = text.indexOf(':', start);
            if (start < 0) {
                return "";
            }
            int firstQuote = text.indexOf('"', start);
            int secondQuote = firstQuote < 0 ? -1 : text.indexOf('"', firstQuote + 1);
            return firstQuote < 0 || secondQuote < 0 ? "" : text.substring(firstQuote + 1, secondQuote);
        } catch (IOException error) {
            return "";
        }
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private static String pathOf(String url) {
        try {
            return URI.create(url).getPath();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private static String safeUrl(String url) {
        if (url == null) {
            return "";
        }
        return hostOf(url) + pathOf(url);
    }

    private static String sanitizeConsoleMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceAll(
                "https://volc-stream\\.kksmg\\.com/[^'\\s]+",
                "https://volc-stream.kksmg.com/<redacted>");
    }

    private static String js(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String safeHash(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 4 && index < bytes.length; index++) {
                int number = bytes[index] & 0xff;
                if (number < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(number));
            }
            return builder.toString();
        } catch (Exception error) {
            return "unknown";
        }
    }

    private static String empty(String value) {
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
}
