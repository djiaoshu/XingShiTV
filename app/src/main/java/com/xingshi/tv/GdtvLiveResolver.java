package com.xingshi.tv;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.IOException;

final class GdtvLiveResolver {
    interface Callback {
        void onSuccess(String streamUrl, long elapsedMs);

        void onError(IOException error, long elapsedMs);
    }

    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    static final String STREAM_REFERER = "https://www.gdtv.cn/";

    private static final String TAG = "GdtvLiveResolver";
    private static final long RESOLVE_TIMEOUT_MS = 30000L;

    private final Activity activity;
    private final FrameLayout root;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int requestSeq;
    private WebView activeWebView;

    GdtvLiveResolver(Activity activity, FrameLayout root) {
        this.activity = activity;
        this.root = root;
    }

    void resolve(final Channel channel, final Callback callback) {
        if (channel.gdtvChannelId == null || channel.gdtvChannelId.length() == 0) {
            callback.onError(new IOException("Missing GDTv channelId for " + channel.name), 0L);
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                startResolveOnMainThread(channel, callback);
            }
        });
    }

    void cancel() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                requestSeq++;
                cleanupWebView();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startResolveOnMainThread(final Channel channel, final Callback callback) {
        requestSeq++;
        final int seq = requestSeq;
        final long startedAt = SystemClock.elapsedRealtime();
        cleanupWebView();

        final String pageUrl = "https://www.gdtv.cn/tvChannelDetail/" + channel.gdtvChannelId;
        Log.i(TAG, "GDTV resolve start channel=" + channel.name
                + " channelId=" + channel.gdtvChannelId
                + " stream=" + nullToEmpty(channel.gdtvStreamName)
                + " pageHost=www.gdtv.cn");
        Log.i("GDTV_METRIC", "initial resolve start channel=" + channel.name
                + " t=" + startedAt);

        final WebView webView = new WebView(activity);
        activeWebView = webView;
        webView.setVisibility(View.INVISIBLE);
        webView.setAlpha(0f);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUserAgentString(USER_AGENT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                maybeComplete(seq, channel, callback, startedAt, url);
                return null;
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                Uri uri = request == null ? null : request.getUrl();
                maybeComplete(seq, channel, callback, startedAt,
                        uri == null ? null : uri.toString());
                return null;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "GDTV page finished channel=" + channel.name
                        + " host=" + hostOf(url));
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, 1);
        root.addView(webView, params);
        webView.loadUrl(pageUrl);

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (seq != requestSeq) {
                    return;
                }
                long elapsed = SystemClock.elapsedRealtime() - startedAt;
                cleanupWebView();
                callback.onError(new IOException("GDTv m3u8 resolve timeout"), elapsed);
            }
        }, RESOLVE_TIMEOUT_MS);
    }

    private void maybeComplete(final int seq, final Channel channel, final Callback callback,
            final long startedAt, String url) {
        if (url == null || !isGdtvM3u8(url)) {
            return;
        }
        String stream = channel.gdtvStreamName;
        if (stream != null && stream.length() > 0 && !url.contains("/" + stream + ".m3u8")) {
            return;
        }
        final String streamUrl = url;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                complete(seq, channel, callback, startedAt, streamUrl);
            }
        });
    }

    private void complete(int seq, Channel channel, Callback callback,
            long startedAt, String streamUrl) {
        if (seq != requestSeq) {
            return;
        }
        requestSeq++;
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        Log.i(TAG, "GDTV resolve success channel=" + channel.name
                + " stream=" + nullToEmpty(channel.gdtvStreamName)
                + " elapsedMs=" + elapsed
                + " host=" + hostOf(streamUrl)
                + " path=" + pathOf(streamUrl));
        Log.i("GDTV_METRIC", "initial resolve success channel=" + channel.name
                + " elapsedMs=" + elapsed);
        cleanupWebView();
        callback.onSuccess(streamUrl, elapsed);
    }

    private void cleanupWebView() {
        if (activeWebView == null) {
            return;
        }
        WebView webView = activeWebView;
        activeWebView = null;
        try {
            root.removeView(webView);
        } catch (RuntimeException ignored) {
            // The view may already be detached during Activity teardown.
        }
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.clearHistory();
        webView.removeAllViews();
        webView.destroy();
    }

    private static boolean isGdtvM3u8(String url) {
        String lower = url.toLowerCase();
        return lower.contains(".m3u8")
                && (lower.contains("itouchtv.cn") || lower.contains("gdtv.cn"));
    }

    static boolean isGdtvUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains(".gdtv.cn") || lower.contains("//gdtv.cn")
                || lower.contains(".itouchtv.cn") || lower.contains("//itouchtv.cn");
    }

    private static String hostOf(String url) {
        try {
            return Uri.parse(url).getHost();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private static String pathOf(String url) {
        try {
            return Uri.parse(url).getPath();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
