package com.xingshi.tv;

import com.bu.cc.tv.NativeCmgDecryptor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public final class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_WEB_PLAYER = 1001;
    private static final String PREFERENCES = "tv_player";
    private static final String LAST_GROUP_INDEX = "last_group_index";
    private static final String LAST_CHANNEL_INDEX = "last_channel_index";
    private static final String LAST_SOURCE_PREFERENCES = "last_success_sources";
    private static final String STATE_GROUP_INDEX = "state_group_index";
    private static final String STATE_CHANNEL_INDEX = "state_channel_index";
    private static final String TAG_AUTO_RECOVERY = "AUTO_RECOVERY";
    private static final String REVERSE_UP_DOWN = "reverse_up_down";
    private static final String GITHUB_URL = "https://github.com/djiaoshu/XingShiTV";
    private static final int FIRST_LAUNCH_GROUP_INDEX = 0;
    private static final int FIRST_LAUNCH_CHANNEL_INDEX = 0;
    private static final long CHANNEL_BAR_TIMEOUT_MS = 3000L;
    private static final long PANEL_TIMEOUT_MS = 5000L;
    private static final long BACK_PROMPT_TIMEOUT_MS = 5000L;
    private static final long EXIT_CONFIRM_TIMEOUT_MS = BACK_PROMPT_TIMEOUT_MS;
    private static final long CHANNEL_PREFETCH_DELAY_MS = 1500L;
    private static final long CCTV_BUFFERING_RECOVERY_MS = 8000L;
    private static final long CCTV_VIDEO_STALL_RECOVERY_MS = 8000L;
    private static final long CUSTOM_SOURCE_TIMEOUT_MS = 5000L;
    private static final long NUMERIC_CHANNEL_TIMEOUT_MS = 1200L;
    private static final long GDTV_PROXY_MONITOR_INTERVAL_MS = 1000L;
    private static final float TOUCH_SWIPE_MIN_DISTANCE_DP = 72f;
    private static final float TOUCH_SWIPE_AXIS_RATIO = 1.45f;
    private static final float MOUSE_CLICK_MAX_MOVE_DP = 16f;
    private static final long MOUSE_SCROLL_THROTTLE_MS = 350L;

    private final Runnable hideChannelBar = new Runnable() {
        @Override
        public void run() {
            channelBar.setVisibility(View.GONE);
        }
    };
    private final Runnable hideChannelList = new Runnable() {
        @Override
        public void run() {
            closeChannelList();
        }
    };
    private final Runnable hideBackPrompt = new Runnable() {
        @Override
        public void run() {
            backPrompt.setVisibility(View.GONE);
            lastBackPressedAt = 0L;
            root.requestFocus();
        }
    };
    private final Runnable commitNumericChannel = new Runnable() {
        @Override
        public void run() {
            commitNumericChannel();
        }
    };
    private final SimpleDateFormat channelListClockFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final Runnable updateChannelListClock = new Runnable() {
        @Override
        public void run() {
            if (channelListPanel.getVisibility() != View.VISIBLE) {
                return;
            }
            channelListClock.setText(channelListClockFormat.format(new Date()));
            long now = System.currentTimeMillis();
            channelListClock.postDelayed(this, 1000L - now % 1000L);
        }
    };

    private View root;
    private View channelBar;
    private View loadingPanel;
    private View channelListPanel;
    private TextView channelListTitle;
    private TextView channelListClock;
    private TextView channelName;
    private TextView statusText;
    private TextView videoInfo;
    private TextView loadingChannel;
    private TextView loadingStatus;
    private TextView numericChannelOverlay;
    private TextView managementUrl;
    private TextView epgPanelTitle;
    private TextView epgEmpty;
    private ListView groupList;
    private ListView channelList;
    private ListView epgProgramList;
    private ChannelListAdapter groupAdapter;
    private ChannelListAdapter channelAdapter;
    private EpgProgramAdapter epgProgramAdapter;
    private LiveUrlResolver liveUrlResolver;
    private MgtvLiveResolver mgtvLiveResolver;
    private JstvLiveResolver jstvLiveResolver;
    private KankanewsLiveResolver kankanewsLiveResolver;
    private GdtvLiveResolver gdtvLiveResolver;
    private YangshipinWebResolver yangshipinResolver;
    private DirectVideoView videoView;
    private Surface videoSurface;
    private HlsProxyServer proxy;
    private boolean proxyStatefulCmgSource;
    private boolean lowResourceDevice;
    private File cmgDebugDir;
    private IjkMediaPlayer player;
    private boolean prepared;
    private volatile int playRequestId;
    private int playerStartRetryCount;
    private int bufferingEventId;
    private int currentGroupIndex;
    private int currentChannelIndex;
    private int currentSourceIndex;
    private int triedCustomSources;
    private int browsingGroupIndex;
    private int videoWidth;
    private int videoHeight;
    private int videoSarNum = 1;
    private int videoSarDen = 1;
    private long lastBackPressedAt;
    private long bufferingStartedAt;
    private long lastPlaybackProgressAt;
    private long lastPlaybackPosition = -1L;
    private String currentPlaybackStatus = "";
    private boolean buffering;
    private boolean bufferingStatusVisible;
    private boolean playbackProgressObserved;
    private int stallRecoveryRequestId = -1;
    private int gdtvConsecutiveRefreshFailures;
    private long gdtvInitialResolveStartedAt;
    private long gdtvRefreshStartedAt;
    private long gdtvRecoverStartedAt;
    private boolean gdtvRefreshInProgress;
    private QrCodeView managementQr;
    private View managementPanel;
    private View backPrompt;
    private Button backPromptOk;
    private PlaylistManager playlistManager;
    private ChannelCatalog.Group[] playlistGroups = new ChannelCatalog.Group[0];
    private ChannelCatalog.Group[] remoteGroups = new ChannelCatalog.Group[0];
    private ChannelCatalog.Group[] privateGroups = new ChannelCatalog.Group[0];
    private LocalControlServer controlServer;
    private volatile boolean reverseUpDown;
    private boolean remoteInputMode;
    private String privateSessionPassword;
    private EpgManager epgManager;
    private SharedPreferences lastSourcePreferences;
    private boolean autoRecoveryActive;
    private int autoRecoveryGroupIndex = -1;
    private int autoRecoveryStartChannelIndex = -1;
    private final HashSet<String> autoRecoveryTriedSources = new HashSet<String>();
    private boolean privateChannelLoading;
    private String numericChannelInput = "";
    private float touchStartX;
    private float touchStartY;
    private boolean touchGestureTracking;
    private float mouseStartX;
    private float mouseStartY;
    private boolean mousePrimaryTracking;
    private long lastMouseScrollAt;

    private final Runnable updateVideoInfo = new Runnable() {
        @Override
        public void run() {
            refreshVideoInfo();
            if (player != null) {
                videoInfo.postDelayed(this, 1000L);
            }
        }
    };
    private final Runnable updateEpgStatus = new Runnable() {
        @Override
        public void run() {
            refreshCurrentEpgStatus();
            if (player != null || channelBar.getVisibility() == View.VISIBLE) {
                channelBar.postDelayed(this, 60000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TlsCompat.install();
        configureResourceProfile();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applySystemUiVisibility();
        setContentView(R.layout.activity_main);
        ChannelCatalog.initialize(this);

        root = findViewById(R.id.root);
        channelBar = findViewById(R.id.channel_bar);
        loadingPanel = findViewById(R.id.loading_panel);
        channelListPanel = findViewById(R.id.channel_list_panel);
        channelListTitle = (TextView) findViewById(R.id.channel_list_title);
        channelListClock = (TextView) findViewById(R.id.channel_list_clock);
        channelName = (TextView) findViewById(R.id.channel_name);
        statusText = (TextView) findViewById(R.id.status_text);
        videoInfo = (TextView) findViewById(R.id.video_info);
        loadingChannel = (TextView) findViewById(R.id.loading_channel);
        loadingStatus = (TextView) findViewById(R.id.loading_status);
        numericChannelOverlay = (TextView) findViewById(R.id.numeric_channel_overlay);
        managementUrl = (TextView) findViewById(R.id.management_url);
        epgPanelTitle = (TextView) findViewById(R.id.epg_panel_title);
        epgEmpty = (TextView) findViewById(R.id.epg_empty);
        managementQr = (QrCodeView) findViewById(R.id.management_qr);
        managementPanel = findViewById(R.id.management_panel);
        backPrompt = findViewById(R.id.back_navigation_prompt);
        backPromptOk = (Button) findViewById(R.id.back_prompt_ok);
        groupList = (ListView) findViewById(R.id.channel_group_list);
        channelList = (ListView) findViewById(R.id.channel_list);
        epgProgramList = (ListView) findViewById(R.id.epg_program_list);
        groupAdapter = new ChannelListAdapter(this);
        channelAdapter = new ChannelListAdapter(this);
        epgProgramAdapter = new EpgProgramAdapter(this);
        final SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        reverseUpDown = preferences.getBoolean(REVERSE_UP_DOWN, false);
        lastSourcePreferences = getSharedPreferences(LAST_SOURCE_PREFERENCES, MODE_PRIVATE);
        remoteInputMode = hasTelevisionUi();
        playlistManager = new PlaylistManager(this);
        playlistGroups = playlistManager.loadCached();
        try {
            remoteGroups = RemoteChannelConfig.loadCached(this);
        } catch (Exception error) {
            remoteGroups = new ChannelCatalog.Group[0];
            Log.i(TAG, "RemoteConfig: no usable cache " + error.getMessage());
        }
        rebuildCustomGroups();
        loadRemoteChannelConfig();
        epgManager = new EpgManager(this, new EpgManager.Listener() {
            @Override
            public void onEpgChanged() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshCurrentEpgStatus();
                        refreshSelectedEpgPanel();
                    }
                });
            }
        });
        epgManager.start();
        liveUrlResolver = new LiveUrlResolver(getSharedPreferences("live_url_resolver", MODE_PRIVATE));
        mgtvLiveResolver = new MgtvLiveResolver(
                getSharedPreferences("mgtv_live_resolver", MODE_PRIVATE));
        jstvLiveResolver = new JstvLiveResolver();
        kankanewsLiveResolver = new KankanewsLiveResolver(this);
        gdtvLiveResolver = new GdtvLiveResolver(this, (FrameLayout) root);
        yangshipinResolver = new YangshipinWebResolver(this, (FrameLayout) root,
                getIntent().getBooleanExtra("cmg_keep_web_trace", false));
        groupList.setAdapter(groupAdapter);
        channelList.setAdapter(channelAdapter);
        epgProgramList.setAdapter(epgProgramAdapter);
        groupList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showChannelMenu(position);
            }
        });
        groupList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (channelListPanel.getVisibility() == View.VISIBLE
                        && position != browsingGroupIndex) {
                    showChannelMenu(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        channelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switchBrowsingChannel(position);
            }
        });
        channelList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateEpgPanelForSelectedChannel(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        videoView = (DirectVideoView) findViewById(R.id.video_surface);
        View.OnClickListener openChannelsOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openChannelList();
            }
        };
        root.setOnClickListener(openChannelsOnClick);
        videoView.setOnClickListener(openChannelsOnClick);
        backPromptOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmBackPrompt();
            }
        });
        videoView.setSurfaceCallback(new DirectVideoView.SurfaceCallback() {
            @Override
            public void onVideoSurfaceCreated(Surface surface) {
                videoSurface = surface;
                if (player != null) {
                    player.setSurface(surface);
                }
            }

            @Override
            public void onVideoSurfaceDestroyed(Surface surface) {
                if (player != null && videoSurface == surface) {
                    player.setSurface(null);
                }
                if (videoSurface == surface) {
                    videoSurface = null;
                }
            }
        });
        root.requestFocus();
        maybeProbeCmgRuntime();
        if (getIntent().hasExtra("cmg_compare")) {
            return;
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_GROUP_INDEX)
                && savedInstanceState.containsKey(STATE_CHANNEL_INDEX)) {
            currentGroupIndex = ChannelCatalog.wrapGroupIndex(
                    savedInstanceState.getInt(STATE_GROUP_INDEX, FIRST_LAUNCH_GROUP_INDEX));
            currentChannelIndex = ChannelCatalog.wrapIndex(currentGroup().channels,
                    savedInstanceState.getInt(STATE_CHANNEL_INDEX, FIRST_LAUNCH_CHANNEL_INDEX));
        } else {
            currentGroupIndex = FIRST_LAUNCH_GROUP_INDEX;
            currentChannelIndex = FIRST_LAUNCH_CHANNEL_INDEX;
        }
        browsingGroupIndex = currentGroupIndex;
        showChannelMenu(currentGroupIndex);

        try {
            cmgDebugDir = getIntent().getBooleanExtra("cmg_debug_dump", false)
                    ? getExternalFilesDir("cmg-debug") : null;
            switchChannel(currentChannelIndex);
        } catch (Exception error) {
            Log.e(TAG, "Unable to start player", error);
            showChannelBar(currentChannel().name,
                    "启动失败: " + error.getMessage());
        }
        startManagementServer();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_GROUP_INDEX, currentGroupIndex);
        outState.putInt(STATE_CHANNEL_INDEX, currentChannelIndex);
        super.onSaveInstanceState(outState);
    }
    private boolean hasTelevisionUi() {
        UiModeManager manager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return (manager != null && manager.getCurrentModeType()
                == Configuration.UI_MODE_TYPE_TELEVISION)
                || getPackageManager().hasSystemFeature("android.software.leanback");
    }

    private void confirmBackPrompt() {
        backPrompt.removeCallbacks(hideBackPrompt);
        backPrompt.setVisibility(View.GONE);
        lastBackPressedAt = 0L;
        openManagement();
    }

    private void openManagement() {
        clearNumericChannelInput();
        if (remoteInputMode) {
            openManagementPanel();
        } else {
            openManagementPage();
        }
    }

    private void openManagementPanel() {
        closeChannelList();
        backPrompt.removeCallbacks(hideBackPrompt);
        backPrompt.setVisibility(View.GONE);
        lastBackPressedAt = 0L;
        refreshManagementAddress();
        managementPanel.setVisibility(View.VISIBLE);
        managementPanel.bringToFront();
        root.requestFocus();
    }

    private void closeManagementPanel() {
        managementPanel.setVisibility(View.GONE);
        root.requestFocus();
    }

    private void openManagementPage() {
        if (controlServer == null || controlServer.getPort() == 0) {
            Toast.makeText(this, "管理服务尚未启动", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(this, ManagementActivity.class)
                    .putExtra(ManagementActivity.EXTRA_URL, controlServer.getLoopbackUrl()));
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法打开管理网页", Toast.LENGTH_SHORT).show();
        }
    }

    private void startManagementServer() {
        try {
            InputStream input = getResources().openRawResource(R.raw.control);
            byte[] html;
            try {
                html = readStream(input);
            } finally {
                input.close();
            }
            controlServer = new LocalControlServer(html, new LocalControlServer.Listener() {
                @Override
                public String stateJson() {
                    return buildControlState();
                }

                @Override
                public String control(JSONObject request) throws Exception {
                    return handleWebControl(request);
                }

                @Override
                public String settings(JSONObject request) throws Exception {
                    return handleWebSettings(request);
                }
            });
            controlServer.start();
            refreshManagementAddress();
        } catch (IOException error) {
            Log.e(TAG, "Unable to start management server", error);
            managementUrl.setText("局域网管理服务启动失败");
            managementQr.setText(null);
        }
    }

    private static byte[] readStream(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String safeStreamUrlForLog(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll(
                "(?i)([?&](?:token|txSecret|txTime|volcSecret|volcTime|wsSecret|wsTime|sign|auth|vsecret|jwt)=)[^&]+",
                "$1<redacted>");
    }

    static String hostOf(String url) {
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

    private static String streamType(String url) {
        if (url == null) {
            return "unknown";
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) {
            return "hls";
        }
        if (lower.contains(".flv")) {
            return "flv";
        }
        if (lower.contains(".ts")) {
            return "ts";
        }
        return "unknown";
    }

    private static boolean shouldUseDirectCustomSource(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) {
            return false;
        }
        if (lower.contains(".ts") || lower.contains(".flv")) {
            return true;
        }
        return isKnownCustomStreamRedirect(url);
    }

    private static boolean isKnownCustomStreamRedirect(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String path = uri.getPath();
            return host != null && "m.061899.xyz".equalsIgnoreCase(host)
                    && path != null && path.startsWith("/mg/");
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static String customProbeSummary(CustomStreamTypeDetector.Result result) {
        if (result == null) {
            return "";
        }
        return " detectedType=" + result.type
                + " probeHttp=" + result.httpStatus
                + " probeMs=" + result.elapsedMs
                + " probeCache=" + result.fromCache
                + " finalHost=" + hostOf(result.finalUrl);
    }

    private void refreshManagementAddress() {
        if (controlServer == null) {
            return;
        }
        String url = controlServer.getLanUrl();
        if (url == null) {
            managementUrl.setText("未检测到局域网 IPv4 地址");
            managementQr.setText(null);
        } else {
            managementUrl.setText(url);
            managementQr.setText(url);
        }
    }

    private String buildControlState() {
        try {
            ChannelCatalog.Group[] groups = ChannelCatalog.GROUPS;
            int groupIndex = Math.max(0, Math.min(currentGroupIndex, groups.length - 1));
            ChannelCatalog.Group group = groups[groupIndex];
            int channelIndex = ChannelCatalog.wrapIndex(group.channels, currentChannelIndex);
            Channel channel = group.channels[channelIndex];
            JSONObject root = new JSONObject();
            root.put("ok", true);
            root.put("githubUrl", GITHUB_URL);
            JSONObject current = new JSONObject();
            current.put("groupIndex", groupIndex);
            current.put("channelIndex", channelIndex);
            current.put("group", group.title);
            current.put("name", channel.name);
            current.put("sourceIndex", group.source == ChannelCatalog.SOURCE_CUSTOM
                    ? currentSourceIndex : 0);
            current.put("sourceCount", Math.max(1, channel.sourceCount()));
            root.put("current", current);
            JSONArray jsonGroups = new JSONArray();
            for (int groupPosition = 0; groupPosition < groups.length; groupPosition++) {
                JSONObject jsonGroup = new JSONObject();
                jsonGroup.put("name", groups[groupPosition].title);
                JSONArray channels = new JSONArray();
                for (Channel item : groups[groupPosition].channels) {
                    channels.put(new JSONObject().put("name", item.name)
                            .put("sourceCount", Math.max(1, item.sourceCount())));
                }
                jsonGroup.put("channels", channels);
                jsonGroups.put(jsonGroup);
            }
            root.put("groups", jsonGroups);
            root.put("settings", new JSONObject()
                    .put("reverseKeys", reverseUpDown)
                    .put("playlistUrl", playlistManager.getPlaylistUrl())
                    .put("recommendedPlaylistUrl", PlaylistManager.RECOMMENDED_URL));
            return root.toString();
        } catch (JSONException error) {
            return "{\"ok\":false,\"message\":\"状态生成失败\"}";
        }
    }

    private String handleWebControl(JSONObject request) throws JSONException {
        final String action = request.optString("action", "");
        final int requestedGroup = request.optInt("group", -1);
        final int requestedChannel = request.optInt("channel", -1);
        if (!"next".equals(action) && !"previous".equals(action)
                && !"toggle".equals(action) && !"play".equals(action)) {
            throw new JSONException("未知的控制指令");
        }
        if ("play".equals(action)) {
            ChannelCatalog.Group[] groups = ChannelCatalog.GROUPS;
            if (requestedGroup < 0 || requestedGroup >= groups.length
                    || requestedChannel < 0
                    || requestedChannel >= groups[requestedGroup].channels.length) {
                throw new JSONException("频道不存在");
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ("next".equals(action)) {
                    switchRelative(1);
                } else if ("previous".equals(action)) {
                    switchRelative(-1);
                } else if ("toggle".equals(action)) {
                    togglePlayback();
                } else {
                    ChannelCatalog.Group[] groups = ChannelCatalog.GROUPS;
                    if (requestedGroup < 0 || requestedGroup >= groups.length
                            || requestedChannel < 0
                            || requestedChannel >= groups[requestedGroup].channels.length) {
                        return;
                    }
                    currentGroupIndex = requestedGroup;
                    browsingGroupIndex = requestedGroup;
                    switchChannel(requestedChannel);
                    closeChannelList();
                }
            }
        });
        return new JSONObject().put("ok", true).toString();
    }

    private String handleWebSettings(JSONObject request) throws Exception {
        if (request.has("reverseKeys")) {
            reverseUpDown = request.optBoolean("reverseKeys", false);
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean(REVERSE_UP_DOWN, reverseUpDown).apply();
        }
        String message = "设置已保存";
        if (request.has("playlistUrl")) {
            final ChannelCatalog.Group[] customGroups = playlistManager.downloadAndSave(
                    request.optString("playlistUrl", ""));
            applyPlaylistGroups(customGroups);
            int channelCount = 0;
            for (ChannelCatalog.Group group : customGroups) {
                channelCount += group.channels.length;
            }
            message = customGroups.length == 0 ? "已移除在线频道"
                    : "已加载 " + customGroups.length + " 个分组、" + channelCount + " 个频道";
        }
        return new JSONObject().put("ok", true).put("message", message).toString();
    }

    private void applyPlaylistGroups(final ChannelCatalog.Group[] customGroups)
            throws InterruptedException {
        playlistGroups = customGroups == null ? new ChannelCatalog.Group[0] : customGroups;
        applyCombinedCustomGroups();
    }

    private void applyCombinedCustomGroups()
            throws InterruptedException {
        final CountDownLatch applied = new CountDownLatch(1);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean wasCustom = currentGroupIndex < ChannelCatalog.GROUPS.length
                        && currentGroup().source == ChannelCatalog.SOURCE_CUSTOM;
                ChannelCatalog.setCustomGroups(combineCustomGroups());
                if (currentGroupIndex >= ChannelCatalog.GROUPS.length) {
                    currentGroupIndex = 0;
                    currentChannelIndex = ChannelCatalog.defaultChannelIndex(currentGroup());
                    browsingGroupIndex = currentGroupIndex;
                    switchChannel(currentChannelIndex);
                } else if (wasCustom) {
                    currentChannelIndex = ChannelCatalog.wrapIndex(
                            currentGroup().channels, currentChannelIndex);
                    switchChannel(currentChannelIndex);
                }
                if (channelListPanel.getVisibility() == View.VISIBLE) {
                    showChannelMenu(currentGroupIndex);
                }
                applied.countDown();
            }
        });
        applied.await(5L, TimeUnit.SECONDS);
    }

    private void rebuildCustomGroups() {
        ChannelCatalog.setCustomGroups(combineCustomGroups());
    }

    private ChannelCatalog.Group[] combineCustomGroups() {
        int playlistCount = playlistGroups == null ? 0 : playlistGroups.length;
        int remoteCount = remoteGroups == null ? 0 : remoteGroups.length;
        ChannelCatalog.Group[] privateList = privateGroups != null && privateGroups.length > 0
                ? privateGroups : new ChannelCatalog.Group[] { privatePlaceholderGroup() };
        int privateCount = privateList.length;
        ChannelCatalog.Group[] groups =
                new ChannelCatalog.Group[playlistCount + remoteCount + privateCount];
        if (playlistCount > 0) {
            System.arraycopy(playlistGroups, 0, groups, 0, playlistCount);
        }
        if (remoteCount > 0) {
            System.arraycopy(remoteGroups, 0, groups, playlistCount, remoteCount);
        }
        if (privateCount > 0) {
            System.arraycopy(privateList, 0, groups, playlistCount + remoteCount, privateCount);
        }
        return groups;
    }

    private ChannelCatalog.Group privatePlaceholderGroup() {
        Channel channel = Channel.directSource("0", "输入密码加载私密频道",
                "private_unlock", "about:blank", "输入密码");
        return new ChannelCatalog.Group(PrivateChannelConfig.GROUP_ID,
                PrivateChannelConfig.GROUP_NAME, ChannelCatalog.SOURCE_CUSTOM,
                new Channel[] { channel });
    }

    private boolean isPrivateGroup(ChannelCatalog.Group group) {
        return group != null && PrivateChannelConfig.GROUP_ID.equals(group.id);
    }

    private boolean isPrivateGroupLoaded() {
        return privateGroups != null && privateGroups.length > 0
                && privateGroups[0].channels.length > 0
                && !"private_unlock".equals(privateGroups[0].channels[0].streamId);
    }

    private boolean isPrivatePlaceholder(ChannelCatalog.Group group) {
        return isPrivateGroup(group) && !isPrivateGroupLoaded();
    }

    private void loadRemoteChannelConfig() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ChannelCatalog.Group[] loaded = RemoteChannelConfig.downloadAndCache(MainActivity.this);
                    int channelCount = 0;
                    int sourceCount = 0;
                    for (ChannelCatalog.Group group : loaded) {
                        channelCount += group.channels.length;
                        for (Channel channel : group.channels) {
                            sourceCount += channel.sourceCount();
                        }
                    }
                    Log.i(TAG, "Remote channel config loaded groups=" + loaded.length
                            + " channels=" + channelCount
                            + " sources=" + sourceCount);
                    remoteGroups = loaded;
                    applyCombinedCustomGroups();
                } catch (Exception error) {
                    Log.w(TAG, "Remote channel config ignored: " + error.getMessage());
                    if (remoteGroups != null && remoteGroups.length > 0) {
                        Log.i(TAG, "RemoteConfig: using cached config");
                    }
                }
            }
        }, "remote-channel-config").start();
    }

    private void maybeProbeCmgRuntime() {
        if (getIntent().getExtras() != null) {
            Log.i(TAG, "Intent extras: " + getIntent().getExtras());
        }
        if (getIntent().hasExtra("cmg_compare")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    runCmgCompareProbe();
                }
            }, "cmg-compare-probe").start();
            return;
        }
        if (getIntent().hasExtra("cmg_replay")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    runCmgReplayProbe();
                }
            }, "cmg-replay-probe").start();
            return;
        }
        if (!getIntent().hasExtra("cmg_probe")) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "CMG native probe start");
                    Log.i(TAG, "CMG native probe: " + NativeCmgDecryptor.probeRuntime());
                } catch (Throwable error) {
                    Log.e(TAG, "CMG native probe failed", error);
                }
            }
        }, "cmg-native-probe").start();
    }

    private void runCmgCompareProbe() {
        try {
            String playerTag = getIntent().getStringExtra("cmg_tag");
            if (playerTag == null) {
                playerTag = "1780652630064";
            }
            String updateTagText = getIntent().getStringExtra("cmg_update_tag");
            if (updateTagText == null) {
                updateTagText = "0";
            }
            int updateTag = (int) Long.parseLong(updateTagText, 16);
            Log.i(TAG, "CMG compare configure ok="
                    + NativeCmgDecryptor.configureRuntimeForProbe(playerTag, updateTag)
                    + " tag=" + playerTag + " updateTag=" + updateTagText);

            File dir = getExternalFilesDir(null);
            if (dir == null) {
                throw new IOException("External files dir unavailable");
            }
            String beforeName = getIntent().getStringExtra("cmg_before");
            if (beforeName == null) {
                beforeName = "official-cmg-nal-1-before.b64";
            }
            String afterName = getIntent().getStringExtra("cmg_after");
            if (afterName == null) {
                afterName = "official-cmg-nal-1-after.b64";
            }
            byte[] before = readBase64File(new File(dir, beforeName));
            byte[] officialAfter = readBase64File(new File(dir, afterName));
            String warmupName = getIntent().getStringExtra("cmg_warm_before");
            if (warmupName == null) {
                warmupName = "official-cmg-nal-0-before.b64";
            }
            File warmupFile = new File(dir, warmupName);
            if (warmupFile.exists()) {
                String warmupTagText = getIntent().getStringExtra("cmg_warm_update_tag");
                if (warmupTagText == null) {
                    warmupTagText = "6c34b9ae";
                }
                int warmupTag = (int) Long.parseLong(warmupTagText, 16);
                byte[] warmup = readBase64File(warmupFile);
                NativeCmgDecryptor.setUpdateTagForProbe(warmupTag);
                byte[] warmupAfter = NativeCmgDecryptor.decodeNalForProbe(warmup, true, true);
                Log.i(TAG, "CMG compare warmup tag=" + warmupTagText
                        + " len=" + warmup.length
                        + " out=" + (warmupAfter == null ? -1 : warmupAfter.length)
                        + " diff=" + (warmupAfter == null ? -1 : diffCount(warmup, warmupAfter)));
                NativeCmgDecryptor.setUpdateTagForProbe(updateTag);
            }
            int preStep = getIntent().getIntExtra("cmg_pre_step", -1);
            if (preStep >= 0 && preStep <= 8) {
                byte[] preStepAfter = NativeCmgDecryptor.decodeNalSingleStepForProbe(
                        before, true, preStep);
                Log.i(TAG, "CMG compare pre-step-" + preStep
                        + " out=" + (preStepAfter == null ? -1 : preStepAfter.length)
                        + " diff=" + (preStepAfter == null ? -1 : diffCount(before, preStepAfter)));
            }
            byte[] nativeAfter = NativeCmgDecryptor.decodeNalForProbe(before, true, true);
            if (nativeAfter == null) {
                Log.e(TAG, "CMG compare native output is null");
                return;
            }
            logByteCompare("official", before, officialAfter);
            logByteCompare("native", before, nativeAfter);
            logByteCompare("native-vs-official", officialAfter, nativeAfter);
            for (int step = 0; step <= 8; step++) {
                byte[] stepAfter = NativeCmgDecryptor.decodeNalSingleStepForProbe(
                        before, true, step);
                if (stepAfter == null) {
                    Log.e(TAG, "CMG compare native-step-" + step + " output is null");
                    continue;
                }
                logByteCompare("native-step-" + step, before, stepAfter);
            }
        } catch (Throwable error) {
            Log.e(TAG, "CMG compare failed", error);
        }
    }

    private void runCmgReplayProbe() {
        try {
            String playerTag = getIntent().getStringExtra("cmg_tag");
            if (playerTag == null) {
                playerTag = "player_container_player";
            }
            boolean forceOfficialTags = !getIntent().hasExtra("cmg_replay_no_force");
            boolean callNativeActive = !getIntent().hasExtra("cmg_replay_no_active");
            int targetIndex = getIntent().getIntExtra("cmg_replay_target", 71);
            Log.i(TAG, "CMG replay configure ok="
                    + NativeCmgDecryptor.configureRuntimeForProbe(playerTag, 0)
                    + " tag=" + playerTag
                    + " forceOfficialTags=" + forceOfficialTags
                    + " callNativeActive=" + callNativeActive
                    + " target=" + targetIndex);

            File dir = getExternalFilesDir(null);
            if (dir == null) {
                throw new IOException("External files dir unavailable");
            }
            String manifestName = getIntent().getStringExtra("cmg_replay_manifest");
            if (manifestName == null) {
                manifestName = "cmg-replay-manifest.txt";
            }
            String[] lines = new String(readFile(new File(dir, manifestName)), "UTF-8")
                    .split("\\r?\\n");
            int firstActiveMismatch = -1;
            int firstDecodeMismatch = -1;
            int decodedCount = 0;
            int activeCount = 0;
            for (String line : lines) {
                if (line == null || line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", -1);
                if (parts.length < 7) {
                    Log.w(TAG, "CMG replay skip malformed line: " + line);
                    continue;
                }
                int index = Integer.parseInt(parts[0]);
                int nalType = Integer.parseInt(parts[1]);
                String officialTagText = parts[2];
                boolean decoded = "1".equals(parts[3]);
                String beforeName = parts[4];
                String expectedName = parts[5];
                int officialDiff = Integer.parseInt(parts[6]);

                int nativeTag = 0;
                if (callNativeActive) {
                    nativeTag = NativeCmgDecryptor.updateSessionForProbe();
                    activeCount++;
                    int officialTag = parseHexUpdateTag(officialTagText);
                    if (firstActiveMismatch < 0 && officialTag != 0 && nativeTag != officialTag) {
                        firstActiveMismatch = index;
                        Log.i(TAG, "CMG replay first active mismatch index=" + index
                                + " type=" + nalType
                                + " nativeTag=" + String.format(Locale.US, "%08x", nativeTag)
                                + " officialTag=" + officialTagText);
                    }
                }
                if (!decoded) {
                    if (index <= 8 || index == targetIndex || firstActiveMismatch == index) {
                        Log.i(TAG, "CMG replay active-only index=" + index
                                + " type=" + nalType
                                + " nativeTag=" + String.format(Locale.US, "%08x", nativeTag)
                                + " officialTag=" + officialTagText);
                    }
                    continue;
                }

                byte[] before = readBase64File(new File(dir, beforeName));
                byte[] expected = readBase64File(new File(dir, expectedName));
                if (forceOfficialTags) {
                    NativeCmgDecryptor.setUpdateTagForProbe(parseHexUpdateTag(officialTagText));
                }
                byte[] actual = NativeCmgDecryptor.decodeNalForProbe(before, true, true);
                decodedCount++;
                if (actual == null) {
                    Log.e(TAG, "CMG replay native null index=" + index + " type=" + nalType);
                    if (firstDecodeMismatch < 0) {
                        firstDecodeMismatch = index;
                    }
                    continue;
                }
                int expectedDiff = diffCount(before, expected);
                int actualDiff = diffCount(before, actual);
                int nativeVsOfficial = diffCount(expected, actual);
                int firstNativeVsOfficial = firstDiff(expected, actual);
                if (nativeVsOfficial != 0 && firstDecodeMismatch < 0) {
                    firstDecodeMismatch = index;
                    Log.i(TAG, "CMG replay first decode mismatch index=" + index
                            + " type=" + nalType
                            + " officialTag=" + officialTagText
                            + " nativeTag=" + String.format(Locale.US, "%08x", nativeTag)
                            + " officialDiff=" + expectedDiff
                            + " actualDiff=" + actualDiff
                            + " nativeVsOfficial=" + nativeVsOfficial
                            + " firstDiff=" + firstNativeVsOfficial
                            + " expectedHead64=" + headHex(expected, 64)
                            + " actualHead64=" + headHex(actual, 64));
                }
                if (index <= 8 || index == targetIndex || nativeVsOfficial != 0) {
                    Log.i(TAG, "CMG replay step index=" + index
                            + " type=" + nalType
                            + " officialTag=" + officialTagText
                            + " nativeTag=" + String.format(Locale.US, "%08x", nativeTag)
                            + " officialDiff=" + officialDiff
                            + " expectedDiff=" + expectedDiff
                            + " actualDiff=" + actualDiff
                            + " nativeVsOfficial=" + nativeVsOfficial);
                }
            }
            Log.i(TAG, "CMG replay summary activeCount=" + activeCount
                    + " decodedCount=" + decodedCount
                    + " firstActiveMismatch=" + firstActiveMismatch
                    + " firstDecodeMismatch=" + firstDecodeMismatch);
        } catch (Throwable error) {
            Log.e(TAG, "CMG replay failed", error);
        }
    }

    private static byte[] readBase64File(File file) throws IOException {
        String text = new String(readFile(file), "US-ASCII");
        return Base64.decode(text.trim(), Base64.DEFAULT);
    }

    private static byte[] readFile(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length());
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static void logByteCompare(String label, byte[] expectedBase, byte[] actual) {
        Log.i(TAG, "CMG compare " + label
                + " baseLen=" + expectedBase.length
                + " actualLen=" + actual.length
                + " firstDiff=" + firstDiff(expectedBase, actual)
                + " diffCount=" + diffCount(expectedBase, actual)
                + " baseSha256=" + sha256Hex(expectedBase)
                + " actualSha256=" + sha256Hex(actual)
                + " actualHead64=" + headHex(actual, 64));
    }

    private static int firstDiff(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int index = 0; index < length; index++) {
            if (left[index] != right[index]) {
                return index;
            }
        }
        return left.length == right.length ? -1 : length;
    }

    private static int diffCount(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        int count = Math.abs(left.length - right.length);
        for (int index = 0; index < length; index++) {
            if (left[index] != right[index]) {
                count++;
            }
        }
        return count;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(data), digest.getDigestLength());
        } catch (NoSuchAlgorithmException error) {
            return "sha256-unavailable";
        }
    }

    private static String headHex(byte[] data, int maxLength) {
        int length = Math.min(data.length, maxLength);
        byte[] head = new byte[length];
        System.arraycopy(data, 0, head, 0, length);
        return hex(head, length);
    }

    private static String hex(byte[] data, int length) {
        char[] chars = new char[length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int index = 0; index < length; index++) {
            int value = data[index] & 0xff;
            chars[index * 2] = digits[value >>> 4];
            chars[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(chars);
    }

    private void applySystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private ChannelCatalog.Group currentGroup() {
        return ChannelCatalog.GROUPS[currentGroupIndex];
    }

    private Channel currentChannel() {
        return currentGroup().channels[currentChannelIndex];
    }

    private void switchChannel(int index) {
        switchChannel(index, false);
    }

    private void switchChannel(int index, boolean automaticRecovery) {
        clearNumericChannelInput();
        if (!automaticRecovery) {
            cancelAutoRecovery("user");
        }
        ChannelCatalog.Group group = currentGroup();
        int channelIndex = ChannelCatalog.wrapIndex(group.channels, index);
        currentSourceIndex = initialSourceIndex(group, group.channels[channelIndex]);
        triedCustomSources = 1;
        startChannel(channelIndex);
    }

    private void startChannel(int index) {
        final ChannelCatalog.Group group = currentGroup();
        currentChannelIndex = ChannelCatalog.wrapIndex(group.channels, index);
        final Channel channel = group.channels[currentChannelIndex];
        if (isPrivatePlaceholder(group)) {
            Log.i(TAG, "Private channel entry selected");
            showPrivatePasswordDialog();
            return;
        }
        if (autoRecoveryActive) {
            markAutoRecoveryAttempt(group, currentChannelIndex, currentSourceIndex);
        }
        Log.i("CHANNEL_TEST", "switch channel name=" + channel.name
                + " source=" + group.source
                + " url=" + safeStreamUrlForLog(channel.sourceUrl(currentSourceIndex)));
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putInt(LAST_GROUP_INDEX, currentGroupIndex)
                .putInt(LAST_CHANNEL_INDEX, currentChannelIndex)
                .apply();
        if (channelListPanel.getVisibility() == View.VISIBLE) {
            groupAdapter.setSelectedIndex(currentGroupIndex);
            channelAdapter.setSelectedIndex(currentChannelIndex);
            groupList.setSelection(currentGroupIndex);
            channelList.setSelection(currentChannelIndex);
        }
        final int requestId = ++playRequestId;
        playerStartRetryCount = 0;
        if (gdtvLiveResolver != null) {
            gdtvLiveResolver.cancel();
        }
        releasePlayer();
        resetVideoLayout();
        showLoading(channel.name, group.source == ChannelCatalog.SOURCE_CUSTOM
                ? customSourceStatus("正在连接") : "正在准备直播");
        try {
            resetProxyForChannelSwitch();
        } catch (IOException error) {
            Log.e(TAG, "Unable to reset proxy for channel switch", error);
            hideLoading();
            showChannelBar(channel.name, "切换失败: " + error.getMessage());
            return;
        }
        if (group.source == ChannelCatalog.SOURCE_CCTV_WEB
                || group.source == ChannelCatalog.SOURCE_CUSTOM) {
            resolveFallbackUrl(channel, requestId);
            return;
        }
        if (group.source == ChannelCatalog.SOURCE_MGTV) {
            Log.i("CHANNEL_TEST", "SOURCE_MGTV start name=" + channel.name
                    + " requestId=" + requestId);
            resolveMgtvUrl(channel, requestId);
            return;
        }
        if (group.source == ChannelCatalog.SOURCE_JSTV) {
            Log.i("CHANNEL_TEST", "SOURCE_JSTV start name=" + channel.name
                    + " requestId=" + requestId);
            resolveJstvUrl(channel, requestId);
            return;
        }
        if (group.source == ChannelCatalog.SOURCE_KANKANEWS) {
            Log.i("CHANNEL_TEST", "SOURCE_KANKANEWS start name=" + channel.name
                    + " requestId=" + requestId);
            resolveKankanewsUrl(channel, requestId);
            return;
        }
        if (group.source == ChannelCatalog.SOURCE_GDTV) {
            Log.i("CHANNEL_TEST", "SOURCE_GDTV start name=" + channel.name
                    + " requestId=" + requestId);
            resetGdtvMetrics();
            resolveGdtvUrl(channel, requestId, false);
            return;
        }
        if (group.source == ChannelCatalog.SOURCE_WEBVIEW) {
            startWebPlayer(channel, channel.webUrl);
            return;
        }
        resolveYangshipinUrl(channel, requestId);
    }

    private void startWebPlayer(Channel channel, String webUrl) {
        hideLoading();
        showChannelBar(channel.name, "网页播放");
        Log.i("WEBVIEW_TEST", "name=" + channel.name
                + " url=" + channel.url
                + " webUrl=" + channel.webUrl
                + " webExtra=" + channel.webExtra
                + " fullscreenType=" + channel.fullscreenType);
        Log.i("WEBVIEW_TEST", "打开网页: url=" + webUrl);
        startActivityForResult(new Intent(this, WebPlayerActivity.class)
                .putExtra(WebPlayerActivity.EXTRA_URL, webUrl)
                .putExtra(WebPlayerActivity.EXTRA_EXTRA, channel.webExtra)
                .putExtra(WebPlayerActivity.EXTRA_FULLSCREEN_TYPE, channel.fullscreenType)
                .putExtra(WebPlayerActivity.EXTRA_GROUP_INDEX, currentGroupIndex)
                .putExtra(WebPlayerActivity.EXTRA_CHANNEL_INDEX, currentChannelIndex)
                .putExtra(WebPlayerActivity.EXTRA_MANAGEMENT_URL,
                        controlServer == null ? null : controlServer.getLoopbackUrl()),
                REQUEST_WEB_PLAYER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WEB_PLAYER
                && resultCode == WebPlayerActivity.RESULT_OPEN_CHANNEL_MENU) {
            Log.i("WEBVIEW_TEST", "WebPlayer requested original management menu");
            openManagement();
            return;
        }
        if (requestCode == REQUEST_WEB_PLAYER
                && resultCode == WebPlayerActivity.RESULT_SWITCH_CHANNEL
                && data != null) {
            int groupIndex = data.getIntExtra(WebPlayerActivity.EXTRA_GROUP_INDEX, currentGroupIndex);
            int channelIndex = data.getIntExtra(WebPlayerActivity.EXTRA_CHANNEL_INDEX,
                    currentChannelIndex);
            groupIndex = ChannelCatalog.wrapGroupIndex(groupIndex);
            channelIndex = ChannelCatalog.wrapIndex(ChannelCatalog.GROUPS[groupIndex].channels,
                    channelIndex);
            Log.i("WEBVIEW_TEST", "WebPlayer switch channel group=" + groupIndex
                    + " channel=" + channelIndex);
            currentGroupIndex = groupIndex;
            switchChannel(channelIndex);
            return;
        }
        if (requestCode == REQUEST_WEB_PLAYER
                && resultCode == WebPlayerActivity.RESULT_EXIT_APP) {
            Log.i("WEBVIEW_TEST", "WebPlayer requested app exit");
            finish();
        }
    }

    private String customSourceStatus(String prefix) {
        Channel channel = currentChannel();
        int count = Math.max(1, channel.sourceCount());
        String sourceName = channel.sourceName(currentSourceIndex);
        if (sourceName == null || sourceName.length() == 0) {
            sourceName = "线路 " + (currentSourceIndex + 1);
        }
        return prefix + sourceName + " " + (currentSourceIndex + 1) + "/" + count;
    }

    private boolean switchCustomSource(int offset, boolean automatic, String reason) {
        if (currentGroup().source != ChannelCatalog.SOURCE_CUSTOM) {
            return false;
        }
        if (automatic) {
            return handleAutoRecoverySourceFailure(reason);
        }
        cancelAutoRecovery("user");
        clearNumericChannelInput();
        Channel channel = currentChannel();
        int count = channel.sourceCount();
        if (count <= 1) {
            showChannelBar(channel.name, "当前频道只有一条线路");
            return true;
        }
        currentSourceIndex = (currentSourceIndex + offset) % count;
        if (currentSourceIndex < 0) {
            currentSourceIndex += count;
        }
        triedCustomSources = 1;
        startChannel(currentChannelIndex);
        String sourceName = channel.sourceName(currentSourceIndex);
        if (sourceName == null || sourceName.length() == 0) {
            sourceName = "线路 " + (currentSourceIndex + 1);
        }
        showChannelBar(channel.name, "已切换至" + sourceName
                + " " + (currentSourceIndex + 1) + "/" + count);
        return true;
    }

    private int initialSourceIndex(ChannelCatalog.Group group, Channel channel) {
        int count = channel.sourceCount();
        if (count <= 1) {
            return 0;
        }
        int index = lastSourcePreferences == null ? 0
                : lastSourcePreferences.getInt(lastSourceKey(group, channel), 0);
        if (index < 0 || index >= count) {
            return 0;
        }
        Log.i("LAST_SOURCE", "restored channel=" + channel.name + " source=" + index);
        return index;
    }

    private void saveLastSuccessSource(ChannelCatalog.Group group, Channel channel) {
        if (channel.sourceCount() <= 1 || currentSourceIndex < 0
                || currentSourceIndex >= channel.sourceCount()) {
            return;
        }
        if (lastSourcePreferences == null) {
            lastSourcePreferences = getSharedPreferences(LAST_SOURCE_PREFERENCES, MODE_PRIVATE);
        }
        lastSourcePreferences.edit()
                .putInt(lastSourceKey(group, channel), currentSourceIndex)
                .apply();
        Log.i("LAST_SOURCE", "saved channel=" + channel.name + " source="
                + currentSourceIndex);
    }

    private static String lastSourceKey(ChannelCatalog.Group group, Channel channel) {
        String groupId = group.id == null || group.id.length() == 0 ? group.title : group.id;
        return groupId + "|" + channel.number + "|" + channel.name;
    }

    private void cancelAutoRecovery(String reason) {
        if (!autoRecoveryActive) {
            return;
        }
        Log.i(TAG_AUTO_RECOVERY, "cancelled by " + reason);
        autoRecoveryActive = false;
        autoRecoveryGroupIndex = -1;
        autoRecoveryStartChannelIndex = -1;
        autoRecoveryTriedSources.clear();
    }

    private void ensureAutoRecoveryStarted(String reason) {
        if (autoRecoveryActive) {
            return;
        }
        autoRecoveryActive = true;
        autoRecoveryGroupIndex = currentGroupIndex;
        autoRecoveryStartChannelIndex = currentChannelIndex;
        autoRecoveryTriedSources.clear();
        Log.i(TAG_AUTO_RECOVERY, "start channel=" + currentChannel().name
                + " reason=" + reason);
    }

    private void markAutoRecoveryAttempt(ChannelCatalog.Group group, int channelIndex,
            int sourceIndex) {
        autoRecoveryTriedSources.add(autoRecoverySourceKey(group, channelIndex, sourceIndex));
    }

    private boolean hasAutoRecoveryAttempted(ChannelCatalog.Group group, int channelIndex,
            int sourceIndex) {
        return autoRecoveryTriedSources.contains(
                autoRecoverySourceKey(group, channelIndex, sourceIndex));
    }

    private static String autoRecoverySourceKey(ChannelCatalog.Group group, int channelIndex,
            int sourceIndex) {
        String groupId = group.id == null || group.id.length() == 0 ? group.title : group.id;
        return groupId + "|" + channelIndex + "|" + sourceIndex;
    }

    private boolean handleAutoRecoverySourceFailure(String reason) {
        ensureAutoRecoveryStarted(reason);
        ChannelCatalog.Group group = currentGroup();
        Channel channel = currentChannel();
        markAutoRecoveryAttempt(group, currentChannelIndex, currentSourceIndex);
        Log.i(TAG_AUTO_RECOVERY, "source failed channel=" + channel.name
                + " source=" + currentSourceIndex + " reason=" + reason);
        int count = Math.max(1, channel.sourceCount());
        for (int offset = 1; offset < count; offset++) {
            int nextSource = (currentSourceIndex + offset) % count;
            if (hasAutoRecoveryAttempted(group, currentChannelIndex, nextSource)) {
                continue;
            }
            currentSourceIndex = nextSource;
            triedCustomSources++;
            Log.i(TAG_AUTO_RECOVERY, "try source=" + currentSourceIndex);
            startChannel(currentChannelIndex);
            showChannelBar(channel.name, reason + "，自动切换至"
                    + channel.sourceName(currentSourceIndex) + " "
                    + (currentSourceIndex + 1) + "/" + count);
            return true;
        }
        Log.i(TAG_AUTO_RECOVERY, "channel exhausted channel=" + channel.name);
        return tryNextAutoRecoveryChannel(reason);
    }

    private boolean tryNextAutoRecoveryChannel(String reason) {
        ensureAutoRecoveryStarted(reason);
        ChannelCatalog.Group group = currentGroup();
        int size = group.channels.length;
        for (int offset = 1; offset < size; offset++) {
            int nextChannel = ChannelCatalog.wrapIndex(group.channels,
                    currentChannelIndex + offset);
            if (nextChannel == autoRecoveryStartChannelIndex) {
                break;
            }
            Channel candidate = group.channels[nextChannel];
            int sourceIndex = initialSourceIndex(group, candidate);
            if (hasAutoRecoveryAttempted(group, nextChannel, sourceIndex)) {
                continue;
            }
            Log.i(TAG_AUTO_RECOVERY, "next channel=" + candidate.name);
            switchChannel(nextChannel, true);
            return true;
        }
        Log.i(TAG_AUTO_RECOVERY, "all channels exhausted group=" + group.title);
        autoRecoveryActive = false;
        autoRecoveryGroupIndex = -1;
        autoRecoveryStartChannelIndex = -1;
        autoRecoveryTriedSources.clear();
        hideLoading();
        showChannelBar(currentChannel().name, "当前频道组全部线路不可用");
        return true;
    }

    private void handlePlaybackFailure(String reason) {
        if (currentGroup().source == ChannelCatalog.SOURCE_CUSTOM) {
            handleAutoRecoverySourceFailure(reason);
            return;
        }
        ensureAutoRecoveryStarted(reason);
        ChannelCatalog.Group group = currentGroup();
        Channel channel = currentChannel();
        markAutoRecoveryAttempt(group, currentChannelIndex, 0);
        Log.i(TAG_AUTO_RECOVERY, "channel exhausted channel=" + channel.name);
        tryNextAutoRecoveryChannel(reason);
    }

    private void onPlaybackReady(Channel channel) {
        if (currentGroup().channels[currentChannelIndex] != channel) {
            return;
        }
        saveLastSuccessSource(currentGroup(), channel);
        if (autoRecoveryActive) {
            Log.i(TAG_AUTO_RECOVERY, "success channel=" + channel.name
                    + " source=" + currentSourceIndex);
            cancelAutoRecovery("success");
        }
    }

    private void configureResourceProfile() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        int memoryClassMb = manager == null ? 0 : manager.getMemoryClass();
        int largeMemoryClassMb = manager == null ? 0 : manager.getLargeMemoryClass();
        boolean systemLowRam = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && manager != null && manager.isLowRamDevice();
        lowResourceDevice = Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT
                || systemLowRam
                || (memoryClassMb > 0 && memoryClassMb <= 64);
        Log.i(TAG, "Resource profile low=" + lowResourceDevice
                + " memoryClassMb=" + memoryClassMb
                + " largeMemoryClassMb=" + largeMemoryClassMb
                + " heapLimitMb=" + (Runtime.getRuntime().maxMemory() / (1024L * 1024L)));
    }

    private void resetProxyForChannelSwitch() throws IOException {
        boolean statefulCmgSource = currentGroup().source != ChannelCatalog.SOURCE_CCTV_WEB
                && currentGroup().source != ChannelCatalog.SOURCE_CUSTOM
                && currentGroup().source != ChannelCatalog.SOURCE_MGTV;
        HlsProxyServer.resetCmgSessionForChannelSwitch();
        HlsProxyServer previous = proxy;
        proxy = null;
        if (previous != null) {
            // A CCTV proxy may still have prefetched segments queued for decryption.
            // Closing it first cancels the old stateful H5E session before the new one starts.
            previous.close();
        }
        HlsProxyServer next = new HlsProxyServer(
                cmgDebugDir, statefulCmgSource, lowResourceDevice);
        next.start();
        proxy = next;
        proxyStatefulCmgSource = statefulCmgSource;
    }

    private void resolveYangshipinUrl(final Channel channel, final int requestId) {
        if (channel.yangshipinPid == null) {
            resolveFallbackUrl(channel, requestId);
            return;
        }
        updateLoadingStatus("正在获取央视频线路");
        showChannelBar(channel.name, "正在解析央视频源");
        yangshipinResolver.resolve(requestId, channel, new YangshipinWebResolver.Callback() {
            @Override
            public void onResolved(int resolvedRequestId, String url,
                    String cmgTag, String cmgInitialUpdateTag, String cmgUpdateTag,
                    int cmgUpdateWarmupCount, long cmgInitTimeMs,
                    long cmgUpdateBaseTimeMs, String cmgUpdateTrace,
                    String cmgNativeTrace) {
                if (resolvedRequestId != playRequestId) {
                    return;
                }
                if (cmgTag != null && cmgTag.length() > 0) {
                    int initialUpdateTag = parseHexUpdateTag(cmgInitialUpdateTag);
                    int updateTag = parseHexUpdateTag(cmgUpdateTag);
                    HlsProxyServer.configureCmgDebugContext(cmgTag,
                            cmgInitialUpdateTag, cmgUpdateTag,
                            cmgInitTimeMs, cmgUpdateBaseTimeMs, cmgUpdateTrace);
                    HlsProxyServer.configureCmgUpdateTags(initialUpdateTag, updateTag);
                    NativeCmgDecryptor.configureLocationForProbe(
                            "https://www.yangshipin.cn/tv/home?pid=" + channel.yangshipinPid);
                    boolean configured = NativeCmgDecryptor.configureRuntimeForProbe(cmgTag, 0);
                    CmgWarmupResult warmup = configured
                            ? warmupCmgUpdateSession(cmgUpdateWarmupCount,
                                    cmgInitTimeMs, cmgUpdateBaseTimeMs, cmgUpdateTrace,
                                    cmgNativeTrace, initialUpdateTag, updateTag)
                            : CmgWarmupResult.empty();
                    HlsProxyServer.configureCmgRuntimeClock(
                            cmgUpdateBaseTimeMs > 0L ? cmgUpdateBaseTimeMs : cmgInitTimeMs,
                            warmup.clockOffsetMs);
                    Log.i(TAG, "Configured CMG runtime from Yangshipin tag="
                            + cmgTag + " initialTag=" + cmgInitialUpdateTag
                            + " updateTag=" + cmgUpdateTag + " ok=" + configured
                            + " warmup=" + warmup.count + "/" + cmgUpdateWarmupCount
                            + " initTime=" + cmgInitTimeMs
                            + " clockOffsetMs=" + warmup.clockOffsetMs
                            + " traceLen=" + (cmgUpdateTrace == null ? 0 : cmgUpdateTrace.length()));
                }
                startResolvedPlayer(channel, url);
            }

            @Override
            public void onFailed(int resolvedRequestId, String reason) {
                if (resolvedRequestId != playRequestId) {
                    return;
                }
                if (channel.url != null) {
                    Log.w(TAG, "Falling back to VDN for " + channel.name + ": " + reason);
                    resolveFallbackUrl(channel, requestId);
                } else {
                    Log.w(TAG, "YSP resolve failed for " + channel.name + ": " + reason);
                    hideLoading();
                    showChannelBar(channel.name, "央视频源解析失败: " + reason);
                }
            }
        });
    }

    private static int parseHexUpdateTag(String text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        try {
            return (int) Long.parseLong(text, 16);
        } catch (NumberFormatException error) {
            Log.w(TAG, "Invalid CMG update tag: " + text);
            return 0;
        }
    }

    private static CmgWarmupResult warmupCmgUpdateSession(int requestedCount, long initTimeMs,
            long baseTimeMs, String trace, String nativeTrace, int targetInitTag,
            int targetUpdateTag) {
        int count = Math.max(0, Math.min(requestedCount, 96));
        String[] entries = trace == null || trace.length() == 0
                ? new String[0] : trace.split(";");
        if (count == 0 && targetInitTag == 0 && targetUpdateTag == 0
                && entries.length == 0
                && (nativeTrace == null || nativeTrace.length() == 0)) {
            return CmgWarmupResult.empty();
        }
        int clockOffsetMs = 0;
        if (initTimeMs > 0L) {
            int matchedOffset = initializeCmgAtOfficialInitTag(initTimeMs, targetInitTag);
            clockOffsetMs = matchedOffset;
            Log.i(TAG, "CMG native traced InitPlayer time=" + initTimeMs
                    + " offset=" + matchedOffset
                    + " initResult=" + String.format(Locale.US, "%08x",
                    NativeCmgDecryptor.getPlayerInitResultForProbe()));
        }
        if (nativeTrace != null && nativeTrace.length() > 0) {
            int replayTag = NativeCmgDecryptor.replayOfficialTraceForProbe(
                    nativeTrace, trace, baseTimeMs, clockOffsetMs);
            Log.i(TAG, "CMG native official trace replay tag="
                    + String.format(Locale.US, "%08x", replayTag)
                    + " target=" + String.format(Locale.US, "%08x", targetUpdateTag)
                    + " traceLen=" + nativeTrace.length());
            if (replayTag != 0) {
                NativeCmgDecryptor.clearClockForProbe();
                return new CmgWarmupResult(count, clockOffsetMs);
            }
        }
        if (baseTimeMs > 0L && entries.length > 0) {
            int tracedCount = Math.min(count, entries.length);
            int firstMismatch = -1;
            int lastTag = 0;
            for (int index = 0; index < tracedCount; index++) {
                String[] parts = entries[index].split(",", -1);
                long deltaMs = parsePositiveLong(parts.length > 0 ? parts[0] : "");
                String officialTagText = parts.length > 1 ? parts[1] : "";
                NativeCmgDecryptor.setClockForProbe(baseTimeMs + deltaMs + clockOffsetMs);
                lastTag = NativeCmgDecryptor.updateSessionForProbe();
                int officialTag = parseHexUpdateTag(officialTagText);
                if (firstMismatch < 0 && officialTag != 0 && lastTag != officialTag) {
                    firstMismatch = index;
                    Log.i(TAG, "CMG traced warmup first tag mismatch index=" + index
                            + " nativeTag=" + String.format(Locale.US, "%08x", lastTag)
                            + " officialTag=" + officialTagText
                            + " deltaMs=" + deltaMs);
                }
            }
            NativeCmgDecryptor.clearClockForProbe();
            Log.i(TAG, "CMG native traced UpdatePlayer warmup count=" + tracedCount
                    + "/" + count + " lastTag=" + String.format(Locale.US, "%08x", lastTag)
                    + " firstMismatch=" + firstMismatch
                    + " baseTimeMs=" + baseTimeMs);
            return new CmgWarmupResult(tracedCount, clockOffsetMs);
        }
        int lastTag = 0;
        for (int index = 0; index < count; index++) {
            lastTag = NativeCmgDecryptor.updateSessionForProbe();
        }
        NativeCmgDecryptor.clearClockForProbe();
        if (count > 0) {
            Log.i(TAG, "CMG native UpdatePlayer warmup count=" + count
                    + " lastTag=" + String.format(Locale.US, "%08x", lastTag));
        }
        return new CmgWarmupResult(count, clockOffsetMs);
    }

    private static final class CmgWarmupResult {
        final int count;
        final int clockOffsetMs;

        CmgWarmupResult(int count, int clockOffsetMs) {
            this.count = count;
            this.clockOffsetMs = clockOffsetMs;
        }

        static CmgWarmupResult empty() {
            return new CmgWarmupResult(0, 0);
        }
    }

    private static int initializeCmgAtOfficialInitTag(long initTimeMs, int targetInitTag) {
        int bestOffset = 0;
        int bestResult = 0;
        int[] offsets = new int[121];
        offsets[0] = 0;
        int count = 1;
        for (int offset = 1; offset <= 60; offset++) {
            offsets[count++] = offset;
            offsets[count++] = -offset;
        }
        for (int index = 0; index < count; index++) {
            int offset = offsets[index];
            NativeCmgDecryptor.resetRuntimeForProbe();
            NativeCmgDecryptor.setClockForProbe(initTimeMs + offset);
            if (!NativeCmgDecryptor.initializeRuntimeForProbe()) {
                continue;
            }
            int result = NativeCmgDecryptor.getPlayerInitResultForProbe();
            if (index == 0) {
                bestResult = result;
            }
            if (targetInitTag != 0 && result == targetInitTag) {
                Log.i(TAG, "CMG native InitPlayer matched official tag="
                        + String.format(Locale.US, "%08x", targetInitTag)
                        + " offsetMs=" + offset);
                return offset;
            }
            bestOffset = offset;
        }
        NativeCmgDecryptor.resetRuntimeForProbe();
        NativeCmgDecryptor.setClockForProbe(initTimeMs);
        NativeCmgDecryptor.initializeRuntimeForProbe();
        Log.w(TAG, "CMG native InitPlayer did not match official tag target="
                + String.format(Locale.US, "%08x", targetInitTag)
                + " first=" + String.format(Locale.US, "%08x", bestResult)
                + " searchedOffsetMs=" + bestOffset);
        return 0;
    }

    private static void waitForCmgUpdateTag(int currentTag, int targetTag) {
        if (targetTag == 0 || currentTag == targetTag) {
            return;
        }
        long deadline = android.os.SystemClock.elapsedRealtime() + 1500L;
        int lastTag = currentTag;
        int attempts = 0;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            attempts++;
            lastTag = NativeCmgDecryptor.updateSessionForProbe();
            if (lastTag == targetTag) {
                Log.i(TAG, "CMG native reached official updateTag="
                        + String.format(Locale.US, "%08x", targetTag)
                        + " attempts=" + attempts);
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Log.w(TAG, "CMG native did not reach official updateTag target="
                + String.format(Locale.US, "%08x", targetTag)
                + " last=" + String.format(Locale.US, "%08x", lastTag)
                + " attempts=" + attempts);
    }

    private static long parsePositiveLong(String text) {
        if (text == null || text.length() == 0) {
            return 0L;
        }
        try {
            long value = Long.parseLong(text);
            return Math.max(0L, value);
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private void resolveFallbackUrl(final Channel channel, final int requestId) {
        final boolean directCustomSource = currentGroup().source == ChannelCatalog.SOURCE_CUSTOM;
        final String configuredUrl = directCustomSource
                ? channel.sourceUrl(currentSourceIndex) : channel.url;
        if (configuredUrl == null) {
            hideLoading();
            showChannelBar(channel.name, "没有可用的备用源");
            return;
        }
        updateLoadingStatus(directCustomSource
                ? customSourceStatus("正在连接") : "正在获取高清线路");
        showChannelBar(channel.name, directCustomSource
                ? customSourceStatus("正在连接") : "正在解析备用源");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String streamUrl = configuredUrl;
                if (!directCustomSource) {
                    try {
                        streamUrl = liveUrlResolver.resolve(channel);
                    } catch (IOException error) {
                        Log.w(TAG, "Falling back to static HLS for " + channel.name, error);
                    }
                }
                final String resolvedUrl = streamUrl;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (requestId != playRequestId) {
                            return;
                        }
                        startResolvedPlayer(channel, resolvedUrl);
                    }
                });
            }
        }, "live-url-resolve").start();
    }

    private void resolveMgtvUrl(final Channel channel, final int requestId) {
        updateLoadingStatus("正在获取芒果TV线路");
        showChannelBar(channel.name, "正在解析芒果TV源");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String streamUrl = mgtvLiveResolver.resolve(channel);
                    Log.i("MGTV_TEST", "resolve callback success url=" + streamUrl);
                    Log.i("CHANNEL_TEST", "Mgtv resolve success name=" + channel.name
                            + " requestId=" + requestId
                            + " url=" + streamUrl);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != playRequestId) {
                                return;
                            }
                            Log.i("MGTV_TEST", "before startResolvedPlayer url=" + streamUrl);
                            startResolvedPlayer(channel, streamUrl);
                        }
                    });
                } catch (final IOException error) {
                    Log.e("MGTV_TEST", "resolve callback failed error=" + error);
                    Log.w("CHANNEL_TEST", "Mgtv resolve failed name=" + channel.name
                            + " requestId=" + requestId
                            + " error=" + error.getMessage(), error);
                    Log.w(TAG, "MGTV resolve failed for " + channel.name, error);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != playRequestId) {
                                return;
                            }
                            hideLoading();
                            showChannelBar(channel.name,
                                    "芒果TV源解析失败: " + error.getMessage());
                        }
                    });
                }
            }
        }, "mgtv-live-resolve").start();
    }

    private void resolveJstvUrl(final Channel channel, final int requestId) {
        updateLoadingStatus("正在获取江苏广电线路");
        showChannelBar(channel.name, "正在解析江苏广电源");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String streamUrl = jstvLiveResolver.resolve(channel);
                    Log.i("CHANNEL_TEST", "JSTV resolve success name=" + channel.name
                            + " requestId=" + requestId);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != playRequestId) {
                                return;
                            }
                            startResolvedPlayer(channel, streamUrl);
                        }
                    });
                } catch (final IOException error) {
                    Log.w("CHANNEL_TEST", "JSTV resolve failed name=" + channel.name
                            + " requestId=" + requestId
                            + " error=" + error.getMessage(), error);
                    Log.w(TAG, "JSTV resolve failed for " + channel.name, error);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != playRequestId) {
                                return;
                            }
                            hideLoading();
                            showChannelBar(channel.name,
                                    "江苏广电源解析失败: " + error.getMessage());
                        }
                    });
                }
            }
        }, "jstv-live-resolve").start();
    }

    private void resolveKankanewsUrl(final Channel channel, final int requestId) {
        updateLoadingStatus("正在获取看看新闻线路");
        showChannelBar(channel.name, "正在解析看看新闻源");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String streamUrl = kankanewsLiveResolver.resolve(channel);
                    Log.i("CHANNEL_TEST", "Kankanews resolve success name=" + channel.name
                            + " requestId=" + requestId);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != playRequestId) {
                                return;
                            }
                            startResolvedPlayer(channel, streamUrl);
                        }
                    });
                } catch (final IOException error) {
                    Log.w("CHANNEL_TEST", "Kankanews resolve failed name=" + channel.name
                            + " requestId=" + requestId
                            + " error=" + error.getMessage(), error);
                    Log.w(TAG, "Kankanews resolve failed for " + channel.name, error);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (requestId != playRequestId) {
                                return;
                            }
                            hideLoading();
                            showChannelBar(channel.name,
                                    "看看新闻源解析失败: " + error.getMessage());
                        }
                    });
                }
            }
        }, "kankanews-live-resolve").start();
    }

    private void resolveGdtvUrl(final Channel channel, final int requestId,
            final boolean refresh) {
        if (refresh) {
            updateLoadingStatus("正在刷新广东台线路");
            showChannelBar(channel.name, "广东台线路已过期，正在刷新");
        } else {
            updateLoadingStatus("正在获取广东台线路");
            showChannelBar(channel.name, "正在解析广东台源");
        }
        long now = SystemClock.elapsedRealtime();
        if (refresh) {
            gdtvRefreshStartedAt = now;
            Log.i("GDTV_METRIC", "refresh resolve start channel=" + channel.name
                    + " t=" + now);
        } else {
            gdtvInitialResolveStartedAt = now;
            Log.i("GDTV_METRIC", "initial resolve request channel=" + channel.name
                    + " t=" + now);
        }
        gdtvLiveResolver.resolve(channel, new GdtvLiveResolver.Callback() {
            @Override
            public void onSuccess(final String streamUrl, final long elapsedMs) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (requestId != playRequestId) {
                            return;
                        }
                        if (refresh) {
                            long refreshElapsed = SystemClock.elapsedRealtime()
                                    - gdtvRefreshStartedAt;
                            gdtvRecoverStartedAt = SystemClock.elapsedRealtime();
                            Log.i("GDTV_METRIC", "refresh resolve success channel="
                                    + channel.name
                                    + " resolverElapsedMs=" + elapsedMs
                                    + " totalResolveElapsedMs=" + refreshElapsed);
                        } else {
                            Log.i("GDTV_METRIC", "initial resolve completed channel="
                                    + channel.name
                                    + " resolverElapsedMs=" + elapsedMs);
                        }
                        Log.i("CHANNEL_TEST", "GDTV resolve success name=" + channel.name
                                + " requestId=" + requestId
                                + " refresh=" + refresh);
                        gdtvRefreshInProgress = false;
                        startResolvedPlayer(channel, streamUrl);
                    }
                });
            }

            @Override
            public void onError(final IOException error, final long elapsedMs) {
                Log.w("CHANNEL_TEST", "GDTV resolve failed name=" + channel.name
                        + " requestId=" + requestId
                        + " refresh=" + refresh
                        + " elapsedMs=" + elapsedMs
                        + " error=" + error.getMessage(), error);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (requestId != playRequestId) {
                            return;
                        }
                        gdtvRefreshInProgress = false;
                        hideLoading();
                        showChannelBar(channel.name,
                                "广东台源解析失败: " + error.getMessage());
                    }
                });
            }
        });
    }

    private void startResolvedPlayer(Channel channel, String streamUrl) {
        boolean kankanewsSource = currentGroup().source == ChannelCatalog.SOURCE_KANKANEWS;
        boolean gdtvSource = currentGroup().source == ChannelCatalog.SOURCE_GDTV;
        Log.i("PLAYER_TEST", "startResolvedPlayer channel="
                + channel.name
                + " url="
                + safeStreamUrlForLog(streamUrl));
        Log.i("CHANNEL_TEST", "startResolvedPlayer url=" + safeStreamUrlForLog(streamUrl)
                + " name=" + channel.name
                + " source=" + currentGroup().source);
        if (kankanewsSource) {
            Log.i("KANKAN", "before player channel=" + channel.name
                    + " streamHost=" + hostOf(streamUrl)
                    + " streamPath=" + pathOf(streamUrl)
                    + " streamType=" + streamType(streamUrl));
        }
        if (gdtvSource) {
            Log.i("GDTV_METRIC", "before player channel=" + channel.name
                    + " streamHost=" + hostOf(streamUrl)
                    + " streamPath=" + pathOf(streamUrl));
        }
        updateLoadingStatus("正在连接视频");
        try {
            startPlayer(channel, streamUrl);
        } catch (IOException error) {
            Log.e(TAG, "Unable to play " + channel.name, error);
            if (currentGroup().source == ChannelCatalog.SOURCE_CUSTOM) {
                handleAutoRecoverySourceFailure("线路连接失败");
                return;
            }
            hideLoading();
            showChannelBar(channel.name, "连接失败: " + error.getMessage());
        }
    }

    private void resetGdtvMetrics() {
        gdtvConsecutiveRefreshFailures = 0;
        gdtvInitialResolveStartedAt = 0L;
        gdtvRefreshStartedAt = 0L;
        gdtvRecoverStartedAt = 0L;
        gdtvRefreshInProgress = false;
    }

    private boolean shouldRefreshGdtvStream() {
        if (currentGroup().source != ChannelCatalog.SOURCE_GDTV) {
            return false;
        }
        if (gdtvConsecutiveRefreshFailures >= 3) {
            Log.w("GDTV_METRIC", "refresh skipped max consecutive failures count="
                    + gdtvConsecutiveRefreshFailures);
            return false;
        }
        if (proxy == null) {
            return !prepared;
        }
        int status = proxy.getLastGdtvUpstreamStatus();
        String kind = proxy.getLastGdtvRequestKind();
        return status == 403 || ("playlist".equals(kind) && status >= 400) || !prepared;
    }

    private void refreshGdtvStream(Channel channel, int requestId) {
        if (requestId != playRequestId || currentGroup().source != ChannelCatalog.SOURCE_GDTV) {
            return;
        }
        if (gdtvRefreshInProgress) {
            Log.i("GDTV_METRIC", "refresh skipped already in progress channel="
                    + channel.name);
            return;
        }
        gdtvConsecutiveRefreshFailures++;
        gdtvRefreshInProgress = true;
        Log.i("GDTV_METRIC", "refresh start channel=" + channel.name
                + " consecutiveFailureCount=" + gdtvConsecutiveRefreshFailures
                + " firstPlaylistSuccessElapsedMs="
                + (proxy == null ? -1L : proxy.getGdtvFirstPlaylistSuccessElapsedMs())
                + " first403ElapsedMs="
                + (proxy == null ? -1L : proxy.getGdtvFirstPlaylist403ElapsedMs()));
        resolveGdtvUrl(channel, requestId, true);
    }

    private void scheduleGdtvProxyMonitor(final Channel channel, final int requestId,
            final IMediaPlayer watchedPlayer) {
        channelBar.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (requestId != playRequestId || player != watchedPlayer
                        || currentGroup().source != ChannelCatalog.SOURCE_GDTV) {
                    return;
                }
                int lastStatus = proxy == null ? -1 : proxy.getLastGdtvUpstreamStatus();
                String lastKind = proxy == null ? "" : proxy.getLastGdtvRequestKind();
                boolean refreshNeeded = proxy == null ? !prepared
                        : lastStatus == 403 || ("playlist".equals(lastKind) && lastStatus >= 400)
                        || !prepared;
                if (!gdtvRefreshInProgress && refreshNeeded && shouldRefreshGdtvStream()) {
                    Log.w("GDTV_METRIC", "proxy monitor triggers refresh channel="
                            + channel.name
                            + " lastProxyStatus=" + lastStatus
                            + " lastProxyKind=" + lastKind
                            + " prepared=" + prepared);
                    refreshGdtvStream(channel, requestId);
                    return;
                }
                channelBar.postDelayed(this, GDTV_PROXY_MONITOR_INTERVAL_MS);
            }
        }, GDTV_PROXY_MONITOR_INTERVAL_MS);
    }

    private static long elapsedSince(long startedAt) {
        if (startedAt <= 0L) {
            return -1L;
        }
        return SystemClock.elapsedRealtime() - startedAt;
    }

    private void startPlayer(final Channel channel, final String streamUrl) throws IOException {
        releasePlayer();
        resetVideoLayout();
        IjkMediaPlayer.loadLibrariesOnce(null);

        final IjkMediaPlayer nextPlayer = new IjkMediaPlayer();
        player = nextPlayer;
        final boolean customSource = currentGroup().source == ChannelCatalog.SOURCE_CUSTOM;
        final boolean kankanewsSource = currentGroup().source == ChannelCatalog.SOURCE_KANKANEWS;
        final boolean gdtvSource = currentGroup().source == ChannelCatalog.SOURCE_GDTV;
        final int sourceRequestId = playRequestId;
        boolean softwareDecode = getIntent().getBooleanExtra("debug_software_decode", false);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec",
                softwareDecode ? 0 : 1);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "mediacodec-handle-resolution-change", 1);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "an", 0);
        nextPlayer.setVolume(1.0f, 1.0f);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);
        final boolean cctvSource = currentGroup().source == ChannelCatalog.SOURCE_CCTV_WEB;
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames",
                cctvSource ? 100 : 60);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 0);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync-av-start", 1);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 30000);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "first-high-water-mark-ms",
                cctvSource ? 5000 : 3500);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "next-high-water-mark-ms", 5000);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "last-high-water-mark-ms", 5000);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
        /* Every channel switch creates a localhost proxy on a new port. IJK 0.8.8
         * can retain an empty localhost DNS-cache entry from the closed proxy,
         * making the first connection to the new port fail spuriously. */
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 256 * 1024);
        nextPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "live_start_index",
                cctvSource ? -1 : -3);
        if (videoSurface != null) {
            nextPlayer.setSurface(videoSurface);
        }
        nextPlayer.setOnVideoSizeChangedListener(new IMediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(IMediaPlayer mediaPlayer, int width, int height,
                    int sarNum, int sarDen) {
                if (player != mediaPlayer) {
                    return;
                }
                updateVideoLayout(mediaPlayer);
            }
        });
        nextPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mediaPlayer) {
                if (player != mediaPlayer) {
                    return;
                }
                prepared = true;
                lastPlaybackProgressAt = SystemClock.elapsedRealtime();
                lastPlaybackPosition = -1L;
                playbackProgressObserved = false;
                updateVideoLayout(mediaPlayer);
                mediaPlayer.start();
                if (kankanewsSource) {
                    Log.i("KANKAN", "player prepared channel=" + channel.name
                            + " video=" + mediaPlayer.getVideoWidth()
                            + "x" + mediaPlayer.getVideoHeight());
                }
                if (gdtvSource) {
                    if (gdtvRecoverStartedAt > 0L) {
                        Log.i("GDTV_METRIC", "recover playback prepared channel="
                                + channel.name
                                + " elapsedMs=" + (SystemClock.elapsedRealtime()
                                - gdtvRecoverStartedAt)
                                + " visibleInterruption=unknown"
                                + " consecutiveFailureCountBeforeReset="
                                + gdtvConsecutiveRefreshFailures);
                        gdtvConsecutiveRefreshFailures = 0;
                        Log.i("GDTV_METRIC", "refresh consecutive failures reset channel="
                                + channel.name);
                        gdtvRecoverStartedAt = 0L;
                    } else {
                        Log.i("GDTV_METRIC", "player prepared channel=" + channel.name
                                + " elapsedSinceInitialResolveMs="
                                + elapsedSince(gdtvInitialResolveStartedAt));
                    }
                    scheduleGdtvProxyMonitor(channel, sourceRequestId, mediaPlayer);
                }
                scheduleVideoInfoRefresh();
                prefetchNearbyChannels(channel);
                onPlaybackReady(channel);
                hideLoading();
                showChannelBar(channel.name, customSource
                        ? customSourceStatus("直播播放中 · ") : "直播播放中");
            }
        });
        nextPlayer.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mediaPlayer, int what, int extra) {
                if (player != mediaPlayer) {
                    return false;
                }
                if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    onPlaybackReady(channel);
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    buffering = true;
                    bufferingStartedAt = SystemClock.elapsedRealtime();
                    final int eventId = ++bufferingEventId;
                    final int requestId = playRequestId;
                    final IjkMediaPlayer watchedPlayer = nextPlayer;
                    channelBar.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (buffering && eventId == bufferingEventId
                                    && requestId == playRequestId) {
                                bufferingStatusVisible = true;
                                showChannelBar(channel.name, "正在缓冲");
                            }
                        }
                    }, 400L);
                    if (cctvSource) {
                        channelBar.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (buffering && eventId == bufferingEventId
                                        && requestId == playRequestId
                                        && player == watchedPlayer) {
                                    recoverCctvPlayback(requestId, watchedPlayer,
                                            "buffering for " + CCTV_BUFFERING_RECOVERY_MS + "ms");
                                }
                            }
                        }, CCTV_BUFFERING_RECOVERY_MS);
                    }
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    long elapsed = buffering
                            ? SystemClock.elapsedRealtime() - bufferingStartedAt : 0L;
                    buffering = false;
                    bufferingEventId++;
                    if (bufferingStatusVisible) {
                        bufferingStatusVisible = false;
                        showChannelBar(channel.name, customSource
                                ? customSourceStatus("直播播放中 · ") : "直播播放中");
                    }
                    if (elapsed >= 250L) {
                        Log.i(TAG, "Buffering recovered channel=" + channel.name
                                + " elapsedMs=" + elapsed);
                    }
                }
                return false;
            }
        });
        nextPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer mediaPlayer, int what, int extra) {
                if (player == mediaPlayer) {
                    if (kankanewsSource) {
                        Log.e("KANKAN", "player error what=" + what
                                + " extra=" + extra
                                + " prepared=" + prepared
                                + " channel=" + channel.name);
                    }
                    if (gdtvSource && shouldRefreshGdtvStream()) {
                        final IMediaPlayer failedPlayer = mediaPlayer;
                        int lastGdtvStatus = proxy == null ? -1
                                : proxy.getLastGdtvUpstreamStatus();
                        String lastGdtvKind = proxy == null ? ""
                                : proxy.getLastGdtvRequestKind();
                        Log.w("GDTV_METRIC", "player error triggers refresh channel="
                                + channel.name
                                + " what=" + what
                                + " extra=" + extra
                                + " prepared=" + prepared
                                + " lastProxyStatus=" + lastGdtvStatus
                                + " lastProxyKind=" + lastGdtvKind
                                + " consecutiveFailureCount="
                                + gdtvConsecutiveRefreshFailures);
                        channelBar.post(new Runnable() {
                            @Override
                            public void run() {
                                if (sourceRequestId == playRequestId
                                        && player == failedPlayer) {
                                    refreshGdtvStream(channel, sourceRequestId);
                                }
                            }
                        });
                        return true;
                    }
                    if (customSource) {
                        final IMediaPlayer failedPlayer = mediaPlayer;
                        channelBar.post(new Runnable() {
                            @Override
                            public void run() {
                                if (sourceRequestId == playRequestId
                                        && player == failedPlayer) {
                                    handleAutoRecoverySourceFailure("线路播放失败");
                                }
                            }
                        });
                        return true;
                    }
                    if (playerStartRetryCount < 2) {
                        final int requestId = playRequestId;
                        final IMediaPlayer failedPlayer = mediaPlayer;
                        final int retry = ++playerStartRetryCount;
                        Log.w(TAG, "Player start failed; retrying local proxy request "
                                + retry + "/2 error=" + what + "/" + extra);
                        channelBar.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (requestId != playRequestId || player != failedPlayer) {
                                    return;
                                }
                                try {
                                    startPlayer(channel, streamUrl);
                                } catch (IOException error) {
                                    Log.e(TAG, "Unable to retry " + channel.name, error);
                                }
                            }
                        }, 500L);
                        return true;
                    }
                    hideLoading();
                    handlePlaybackFailure("播放错误: " + what + "/" + extra);
                }
                return true;
            }
        });
        CustomStreamTypeDetector.Result customProbe = null;
        boolean directCustomStream = customSource && shouldUseDirectCustomSource(streamUrl);
        if (customSource && "unknown".equals(streamType(streamUrl))) {
            customProbe = CustomStreamTypeDetector.detect(streamUrl);
            if (customProbe.type == CustomStreamTypeDetector.MediaType.HLS) {
                directCustomStream = false;
            } else if (customProbe.type == CustomStreamTypeDetector.MediaType.FLV
                    || customProbe.type == CustomStreamTypeDetector.MediaType.MPEG_TS) {
                directCustomStream = true;
            }
        }
        String playerUrl = directCustomStream ? streamUrl : proxy.proxyUrl(streamUrl);
        if (directCustomStream) {
            Log.i("PLAYER_TEST", "custom datasource type=direct"
                    + " originHost=" + hostOf(streamUrl)
                    + " originType=" + streamType(streamUrl)
                    + customProbeSummary(customProbe));
        } else if (customSource) {
            Log.i("PLAYER_TEST", "custom datasource type=local_proxy"
                    + " originHost=" + hostOf(streamUrl)
                    + " originType=" + streamType(streamUrl)
                    + customProbeSummary(customProbe));
        }
        if (kankanewsSource) {
            Log.i("KANKAN", "player datasource type=local_proxy"
                    + " proxyHost=127.0.0.1"
                    + " originHost=" + hostOf(streamUrl)
                    + " originType=" + streamType(streamUrl));
        }
        if (gdtvSource) {
            Log.i("GDTV_METRIC", "player datasource type=local_proxy"
                    + " proxyHost=127.0.0.1"
                    + " originHost=" + hostOf(streamUrl)
                    + " originType=" + streamType(streamUrl));
        }
        nextPlayer.setDataSource(playerUrl);
        nextPlayer.prepareAsync();
        if (customSource) {
            channelBar.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (sourceRequestId == playRequestId && player == nextPlayer && !prepared) {
                        handleAutoRecoverySourceFailure("连接超过 5 秒");
                    }
                }
            }, CUSTOM_SOURCE_TIMEOUT_MS);
        }
    }

    private void recoverCctvPlayback(int requestId, IMediaPlayer watchedPlayer, String reason) {
        if (requestId != playRequestId || player != watchedPlayer
                || currentGroup().source != ChannelCatalog.SOURCE_CCTV_WEB
                || stallRecoveryRequestId == requestId) {
            return;
        }
        stallRecoveryRequestId = requestId;
        Log.w(TAG, "Recovering stalled CCTV playback at live edge: " + reason);
        switchChannel(currentChannelIndex);
    }

    private void prefetchNearbyChannels(final Channel playingChannel) {
        final ChannelCatalog.Group group = currentGroup();
        if (group.source != ChannelCatalog.SOURCE_CCTV_WEB
                || group.channels[currentChannelIndex] != playingChannel) {
            return;
        }
        final Channel previous = group.channels[ChannelCatalog.wrapIndex(
                group.channels, currentChannelIndex - 1)];
        final Channel next = group.channels[ChannelCatalog.wrapIndex(
                group.channels, currentChannelIndex + 1)];
        final int requestId = playRequestId;
        channelBar.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (requestId != playRequestId) {
                    return;
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        prefetchChannel(next);
                    }
                }, "channel-url-prefetch-next").start();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        prefetchChannel(previous);
                    }
                }, "channel-url-prefetch-previous").start();
            }
        }, CHANNEL_PREFETCH_DELAY_MS);
    }

    private void prefetchChannel(Channel channel) {
        try {
            liveUrlResolver.resolve(channel);
        } catch (IOException error) {
            Log.d(TAG, "Unable to prefetch " + channel.streamId, error);
        }
    }

    private void switchRelative(int offset) {
        switchChannel(currentChannelIndex + offset);
    }

    private void enterNumericChannel(int digit) {
        cancelAutoRecovery("user");
        if (numericChannelInput.length() >= 3) {
            clearNumericChannelInput();
        }
        numericChannelInput += String.valueOf(digit);
        channelBar.removeCallbacks(commitNumericChannel);
        numericChannelOverlay.setText(numericChannelInput);
        numericChannelOverlay.setVisibility(View.VISIBLE);
        numericChannelOverlay.bringToFront();
        if (numericChannelInput.length() >= 3) {
            commitNumericChannel();
        } else {
            channelBar.postDelayed(commitNumericChannel, NUMERIC_CHANNEL_TIMEOUT_MS);
        }
    }

    private void commitNumericChannel() {
        if (numericChannelInput.length() == 0) {
            return;
        }
        String channelNumber = numericChannelInput;
        clearNumericChannelInput();
        Channel[] channels = currentGroup().channels;
        for (int index = 0; index < channels.length; index++) {
            if (channelNumber.equals(channels[index].number)) {
                switchChannel(index);
                return;
            }
        }
        showChannelBar(currentChannel().name, "没有频道号 " + channelNumber);
    }

    private void clearNumericChannelInput() {
        if (channelBar != null) {
            channelBar.removeCallbacks(commitNumericChannel);
        }
        numericChannelInput = "";
        if (numericChannelOverlay != null) {
            numericChannelOverlay.setVisibility(View.GONE);
        }
    }

    private void togglePlayback() {
        cancelAutoRecovery("user");
        Channel channel = currentChannel();
        if (player == null || !prepared) {
            switchChannel(currentChannelIndex);
        } else if (player.isPlaying()) {
            player.pause();
            showChannelBar(channel.name, "已暂停");
        } else {
            player.start();
            showChannelBar(channel.name, "直播播放中");
        }
    }

    private void switchBrowsingChannel(int position) {
        ChannelCatalog.Group group = ChannelCatalog.GROUPS[browsingGroupIndex];
        if (isPrivatePlaceholder(group)) {
            currentGroupIndex = browsingGroupIndex;
            currentChannelIndex = ChannelCatalog.wrapIndex(group.channels, position);
            showPrivatePasswordDialog();
            return;
        }
        currentGroupIndex = browsingGroupIndex;
        switchChannel(position);
        closeChannelList();
    }

    private void showPrivatePasswordDialog() {
        if (privateChannelLoading) {
            showChannelBar(PrivateChannelConfig.GROUP_NAME, "正在加载，请稍候");
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setSelectAllOnFocus(true);
        input.setHint("请输入密码");
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("私密频道")
                .setMessage("请输入访问密码")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                boolean done = actionId == EditorInfo.IME_ACTION_DONE;
                boolean enter = event != null && event.getAction() == KeyEvent.ACTION_DOWN
                        && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER);
                if (done || enter) {
                    submitPrivatePassword(dialog, input);
                    return true;
                }
                return false;
            }
        });
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus) {
                    showKeyboard(input);
                }
            }
        });
        input.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showKeyboard(input);
            }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialogInterface) {
                final AlertDialog shown = (AlertDialog) dialogInterface;
                shown.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                submitPrivatePassword(shown, input);
                            }
                        });
                input.requestFocus();
                input.post(new Runnable() {
                    @Override
                    public void run() {
                        showKeyboard(input);
                    }
                });
                shown.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.show();
    }

    private void submitPrivatePassword(AlertDialog dialog, EditText input) {
        if (privateChannelLoading) {
            return;
        }
        String password = input.getText().toString();
        if (password.length() == 0) {
            Toast.makeText(MainActivity.this, "请输入密码", Toast.LENGTH_SHORT).show();
            focusPrivatePasswordInput(input);
            return;
        }
        hideKeyboard(input);
        setPrivatePasswordDialogEnabled(dialog, false);
        loadPrivateChannels(password, dialog, input);
    }

    private void setPrivatePasswordDialogEnabled(AlertDialog dialog, boolean enabled) {
        if (dialog == null) {
            return;
        }
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) {
            positive.setEnabled(enabled);
        }
        if (negative != null) {
            negative.setEnabled(enabled);
        }
    }

    private void focusPrivatePasswordInput(final EditText input) {
        if (input == null) {
            return;
        }
        input.requestFocus();
        input.selectAll();
        input.post(new Runnable() {
            @Override
            public void run() {
                showKeyboard(input);
            }
        });
    }

    private void showKeyboard(EditText input) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard(EditText input) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void loadPrivateChannels(final String password) {
        loadPrivateChannels(password, null, null);
    }

    private void loadPrivateChannels(final String password, final AlertDialog dialog,
            final EditText input) {
        if (privateChannelLoading) {
            return;
        }
        privateChannelLoading = true;
        showLoading(PrivateChannelConfig.GROUP_NAME, "正在验证密码");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ChannelCatalog.Group[] loaded = PrivateChannelConfig.download(password);
                    privateSessionPassword = password;
                    privateGroups = loaded;
                    applyCombinedCustomGroups();
                    int channels = loaded.length == 0 ? 0 : loaded[0].channels.length;
                    int sources = countSources(loaded);
                    Log.i(TAG, "Private channels loaded channels=" + channels
                            + " sources=" + sources);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            privateChannelLoading = false;
                            int groupIndex = findPrivateGroupIndex();
                            if (groupIndex < 0) {
                                hideLoading();
                                setPrivatePasswordDialogEnabled(dialog, true);
                                focusPrivatePasswordInput(input);
                                showChannelBar(PrivateChannelConfig.GROUP_NAME,
                                        "私密频道加载失败");
                                return;
                            }
                            if (dialog != null && dialog.isShowing()) {
                                dialog.dismiss();
                            }
                            currentGroupIndex = groupIndex;
                            browsingGroupIndex = groupIndex;
                            currentChannelIndex = 0;
                            closeChannelList();
                            switchChannel(0);
                        }
                    });
                } catch (final PrivateChannelConfig.UnauthorizedException error) {
                    privateSessionPassword = null;
                    onPrivateChannelLoadFailed("密码错误", false, dialog, input);
                } catch (Exception error) {
                    privateSessionPassword = null;
                    Log.w(TAG, "Private channel load failed: " + error.getMessage());
                    onPrivateChannelLoadFailed("私密频道加载失败", true, dialog, input);
                }
            }
        }, "private-channel-config").start();
    }

    private void onPrivateChannelLoadFailed(final String message, final boolean longToast) {
        onPrivateChannelLoadFailed(message, longToast, null, null);
    }

    private void onPrivateChannelLoadFailed(final String message, final boolean longToast,
            final AlertDialog dialog, final EditText input) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                privateChannelLoading = false;
                hideLoading();
                setPrivatePasswordDialogEnabled(dialog, true);
                Toast.makeText(MainActivity.this, message,
                        longToast ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                showChannelBar(PrivateChannelConfig.GROUP_NAME, message);
                if (dialog != null && dialog.isShowing()) {
                    focusPrivatePasswordInput(input);
                }
            }
        });
    }

    private int findPrivateGroupIndex() {
        for (int index = 0; index < ChannelCatalog.GROUPS.length; index++) {
            if (isPrivateGroup(ChannelCatalog.GROUPS[index])) {
                return index;
            }
        }
        return -1;
    }

    private static int countSources(ChannelCatalog.Group[] groups) {
        int count = 0;
        if (groups == null) {
            return 0;
        }
        for (ChannelCatalog.Group group : groups) {
            for (Channel channel : group.channels) {
                count += channel.sourceCount();
            }
        }
        return count;
    }

    private void openChannelList() {
        cancelAutoRecovery("user");
        clearNumericChannelInput();
        lastBackPressedAt = 0L;
        backPrompt.removeCallbacks(hideBackPrompt);
        backPrompt.setVisibility(View.GONE);
        closeManagementPanel();
        channelListPanel.setVisibility(View.VISIBLE);
        showChannelMenu(currentGroupIndex);
        channelListClock.removeCallbacks(updateChannelListClock);
        updateChannelListClock.run();
        channelList.post(new Runnable() {
            @Override
            public void run() {
                channelList.setSelection(currentChannelIndex);
                channelList.requestFocusFromTouch();
                channelList.requestFocus();
            }
        });
        scheduleChannelListDismiss();
    }

    private void showChannelMenu(int groupIndex) {
        browsingGroupIndex = ChannelCatalog.wrapGroupIndex(groupIndex);
        ChannelCatalog.Group group = ChannelCatalog.GROUPS[browsingGroupIndex];
        int selectedIndex = browsingGroupIndex == currentGroupIndex
                ? currentChannelIndex : ChannelCatalog.defaultChannelIndex(group);
        channelListTitle.setText(getString(R.string.channel_panel_title,
                group.title, group.channels.length));
        groupAdapter.showGroups(ChannelCatalog.GROUPS, browsingGroupIndex);
        channelAdapter.showChannels(group.channels, selectedIndex);
        groupList.setSelection(browsingGroupIndex);
        channelList.setSelection(selectedIndex);
        updateEpgPanel(group, selectedIndex);
        scheduleChannelListDismiss();
    }

    private void refreshSelectedEpgPanel() {
        if (channelListPanel == null || channelListPanel.getVisibility() != View.VISIBLE) {
            return;
        }
        int position = channelList.getSelectedItemPosition();
        if (position == AdapterView.INVALID_POSITION) {
            position = browsingGroupIndex == currentGroupIndex
                    ? currentChannelIndex : ChannelCatalog.defaultChannelIndex(
                            ChannelCatalog.GROUPS[browsingGroupIndex]);
        }
        updateEpgPanelForSelectedChannel(position);
    }

    private void updateEpgPanelForSelectedChannel(int channelIndex) {
        if (channelListPanel == null || channelListPanel.getVisibility() != View.VISIBLE) {
            return;
        }
        ChannelCatalog.Group group = ChannelCatalog.GROUPS[browsingGroupIndex];
        updateEpgPanel(group, channelIndex);
    }

    private void updateEpgPanel(ChannelCatalog.Group group, int channelIndex) {
        if (group == null || group.channels.length == 0) {
            epgPanelTitle.setText("今日节目");
            epgProgramAdapter.setPrograms(new EpgManager.Programme[0], -1);
            epgProgramList.setVisibility(View.GONE);
            epgEmpty.setVisibility(View.VISIBLE);
            return;
        }
        int safeIndex = ChannelCatalog.wrapIndex(group.channels, channelIndex);
        Channel channel = group.channels[safeIndex];
        epgPanelTitle.setText(channel.name + " · 今日节目");
        EpgManager.Programme[] programmes = epgManager == null
                ? new EpgManager.Programme[0]
                : epgManager.programsForToday(channel, System.currentTimeMillis());
        int currentIndex = epgManager == null ? -1
                : epgManager.currentProgramIndex(programmes, System.currentTimeMillis());
        traceEpgUi("panel", channel, programmes.length, currentIndex);
        epgProgramAdapter.setPrograms(programmes, currentIndex);
        if (programmes.length == 0) {
            epgProgramList.setVisibility(View.GONE);
            epgEmpty.setVisibility(View.VISIBLE);
            return;
        }
        epgEmpty.setVisibility(View.GONE);
        epgProgramList.setVisibility(View.VISIBLE);
        final int focusIndex = currentIndex >= 0 ? currentIndex : 0;
        epgProgramList.post(new Runnable() {
            @Override
            public void run() {
                epgProgramList.setSelection(focusIndex);
            }
        });
    }

    private void closeChannelList() {
        channelListPanel.removeCallbacks(hideChannelList);
        channelListClock.removeCallbacks(updateChannelListClock);
        channelListPanel.setVisibility(View.GONE);
        root.requestFocus();
    }

    private void scheduleChannelListDismiss() {
        channelListPanel.removeCallbacks(hideChannelList);
        channelListPanel.postDelayed(hideChannelList, PANEL_TIMEOUT_MS);
    }

    private void showChannelBar(final String channel, final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                channelBar.removeCallbacks(hideChannelBar);
                channelName.setText(channel);
                currentPlaybackStatus = status;
                statusText.setText(statusWithEpg(status));
                channelBar.setVisibility(View.VISIBLE);
                channelBar.postDelayed(hideChannelBar, CHANNEL_BAR_TIMEOUT_MS);
                scheduleEpgStatusRefresh();
            }
        });
    }

    private void refreshCurrentEpgStatus() {
        if (statusText == null || currentPlaybackStatus == null) {
            return;
        }
        if (channelBar.getVisibility() == View.VISIBLE) {
            statusText.setText(statusWithEpg(currentPlaybackStatus));
        }
    }

    private void scheduleEpgStatusRefresh() {
        channelBar.removeCallbacks(updateEpgStatus);
        channelBar.postDelayed(updateEpgStatus, 60000L);
    }

    private String statusWithEpg(String status) {
        if (epgManager == null || !epgManager.isReady()) {
            return status;
        }
        EpgManager.ProgramState state =
                epgManager.currentState(currentChannel(), System.currentTimeMillis());
        if (state == null || !state.hasAny()) {
            traceEpgUi("status", currentChannel(), 0, -1);
            return status;
        }
        traceEpgUi("status", currentChannel(), 1, state.current == null ? -1 : 0);
        StringBuilder builder = new StringBuilder(status == null ? "" : status);
        long now = System.currentTimeMillis();
        if (state.current != null) {
            builder.append("  ·  正在: ").append(state.current.title)
                    .append(" ").append(state.current.progressPercent(now)).append("%");
        }
        if (state.next != null) {
            builder.append("  ·  下个: ").append(state.next.title);
        }
        return builder.toString();
    }

    private static void traceEpgUi(String stage, Channel channel, int count,
            int currentIndex) {
        if (channel == null || (!"CCTV-1 综合".equals(channel.name)
                && !"湖南卫视".equals(channel.name)
                && !"江苏卫视".equals(channel.name))) {
            return;
        }
        Log.i("EPG_TRACE", "ui " + stage + " channel=" + channel.name
                + " epgSource=" + channel.epgSource
                + " epgId=" + channel.epgId
                + " count=" + count
                + " currentIndex=" + currentIndex);
    }

    private void showLoading(final String channel, final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loadingPanel.animate().cancel();
                loadingChannel.setText(channel);
                loadingStatus.setText(status);
                if (loadingPanel.getVisibility() != View.VISIBLE) {
                    loadingPanel.setAlpha(0f);
                    loadingPanel.setScaleX(0.96f);
                    loadingPanel.setScaleY(0.96f);
                    loadingPanel.setVisibility(View.VISIBLE);
                    loadingPanel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(160L).start();
                }
            }
        });
    }

    private void updateLoadingStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loadingStatus.setText(status);
            }
        });
    }

    private void hideLoading() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loadingPanel.animate().cancel();
                loadingPanel.setVisibility(View.GONE);
            }
        });
    }

    private void releasePlayer() {
        prepared = false;
        buffering = false;
        bufferingStatusVisible = false;
        bufferingEventId++;
        playbackProgressObserved = false;
        lastPlaybackPosition = -1L;
        lastPlaybackProgressAt = 0L;
        if (videoInfo != null) {
            videoInfo.removeCallbacks(updateVideoInfo);
        }
        if (channelBar != null) {
            channelBar.removeCallbacks(updateEpgStatus);
        }
        if (player != null) {
            player.setSurface(null);
            player.release();
            player = null;
        }
    }

    private void resetVideoLayout() {
        videoWidth = 0;
        videoHeight = 0;
        videoSarNum = 1;
        videoSarDen = 1;
        videoView.setVideoSize(0, 0, 1, 1);
        refreshVideoInfo();
    }

    private void updateVideoLayout(IMediaPlayer mediaPlayer) {
        videoWidth = mediaPlayer.getVideoWidth();
        videoHeight = mediaPlayer.getVideoHeight();
        videoSarNum = mediaPlayer.getVideoSarNum();
        videoSarDen = mediaPlayer.getVideoSarDen();
        if (videoSarNum <= 0) {
            videoSarNum = 1;
        }
        if (videoSarDen <= 0) {
            videoSarDen = 1;
        }
        videoView.setVideoSize(videoWidth, videoHeight, videoSarNum, videoSarDen);
        refreshVideoInfo();
        Log.i(TAG, "Video source=" + videoWidth + "x" + videoHeight
                + " sar=" + videoSarNum + "/" + videoSarDen);
    }

    private void scheduleVideoInfoRefresh() {
        videoInfo.removeCallbacks(updateVideoInfo);
        videoInfo.post(updateVideoInfo);
    }

    @SuppressLint("SetTextI18n")
    private void refreshVideoInfo() {
        if (videoInfo == null) {
            return;
        }
        String resolution = videoWidth > 0 && videoHeight > 0
                ? videoWidth + "x" + videoHeight : "--";
        float outputFps = 0f;
        float decodeFps = 0f;
        if (player != null) {
            outputFps = player.getVideoOutputFramesPerSecond();
            decodeFps = player.getVideoDecodeFramesPerSecond();
        }
        if (prepared && currentGroup().source == ChannelCatalog.SOURCE_CCTV_WEB) {
            long now = SystemClock.elapsedRealtime();
            long playbackPosition = player.getCurrentPosition();
            if (playbackPosition > lastPlaybackPosition) {
                playbackProgressObserved = true;
                lastPlaybackPosition = playbackPosition;
                lastPlaybackProgressAt = now;
            } else if (playbackProgressObserved && !buffering && lastPlaybackProgressAt > 0L
                    && now - lastPlaybackProgressAt >= CCTV_VIDEO_STALL_RECOVERY_MS) {
                recoverCctvPlayback(playRequestId, player,
                        "playback clock stopped for "
                                + (now - lastPlaybackProgressAt) + "ms");
            }
        }
        String fps = outputFps > 0.01f
                ? String.format(Locale.US, "%.1f/%.1f", outputFps, decodeFps) : "--";
        videoInfo.setText("源: " + resolution + "  fps: " + fps);
    }

    private void moveChannelMenuSelection(int offset) {
        if (epgProgramList.hasFocus()) {
            int count = epgProgramAdapter.getCount();
            if (count <= 0) {
                return;
            }
            int position = epgProgramList.getSelectedItemPosition();
            if (position == AdapterView.INVALID_POSITION) {
                position = 0;
            }
            int nextPosition = Math.max(0, Math.min(count - 1, position + offset));
            epgProgramList.setSelection(nextPosition);
            return;
        }

        if (groupList.hasFocus()) {
            int position = groupList.getSelectedItemPosition();
            if (position == AdapterView.INVALID_POSITION) {
                position = browsingGroupIndex;
            }
            int nextPosition = Math.max(0, Math.min(
                    ChannelCatalog.GROUPS.length - 1, position + offset));
            if (nextPosition != browsingGroupIndex) {
                showChannelMenu(nextPosition);
            } else {
                groupList.setSelection(nextPosition);
            }
            return;
        }

        if (!channelList.hasFocus()) {
            channelList.requestFocus();
        }
        int position = channelList.getSelectedItemPosition();
        Channel[] channels = ChannelCatalog.GROUPS[browsingGroupIndex].channels;
        if (position == AdapterView.INVALID_POSITION) {
            position = browsingGroupIndex == currentGroupIndex
                    ? currentChannelIndex : ChannelCatalog.defaultChannelIndex(
                            ChannelCatalog.GROUPS[browsingGroupIndex]);
        }
        int nextPosition = Math.max(0, Math.min(channels.length - 1, position + offset));
        channelList.setSelection(nextPosition);
    }

    private static boolean isHandledRemoteKey(int keyCode) {
        return InputAction.isHandledKey(keyCode);
    }

    private void setRemoteInputMode(boolean remote) {
        if (remoteInputMode != remote) {
            remoteInputMode = remote;
            Log.i(TAG, "Input mode changed to " + (remote ? "remote" : "touch"));
        }
    }

    private static boolean isTouchInput(MotionEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN
                || (source & InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS;
    }

    private static boolean isMouseInput(MotionEvent event) {
        return (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isMouseInput(event)) {
            return dispatchMouseEvent(event);
        }
        if (!isTouchInput(event)) {
            return super.dispatchTouchEvent(event);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelAutoRecovery("user");
            setRemoteInputMode(false);
            touchStartX = event.getX();
            touchStartY = event.getY();
            touchGestureTracking = canHandlePlaybackTouchGesture();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            touchGestureTracking = false;
        } else if (action == MotionEvent.ACTION_UP) {
            if (touchGestureTracking) {
                InputAction inputAction = touchActionFromGesture(
                        event.getX() - touchStartX, event.getY() - touchStartY);
                touchGestureTracking = false;
                if (inputAction != null && performPlaybackTouchAction(inputAction)) {
                    return true;
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean dispatchMouseEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_SCROLL) {
            return handleMouseScroll(event) || super.dispatchTouchEvent(event);
        }
        if (action == MotionEvent.ACTION_BUTTON_PRESS
                || action == MotionEvent.ACTION_BUTTON_RELEASE) {
            Log.i(TAG, "MOUSE_BUTTON action=" + action
                    + " buttonState=" + event.getButtonState());
            if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                Log.i(TAG, "MOUSE_BUTTON secondary action=" + action);
            }
            return super.dispatchTouchEvent(event);
        }
        if (action == MotionEvent.ACTION_DOWN) {
            cancelAutoRecovery("user");
            setRemoteInputMode(false);
            mouseStartX = event.getX();
            mouseStartY = event.getY();
            mousePrimaryTracking = canHandlePlaybackTouchGesture();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mousePrimaryTracking = false;
        } else if (action == MotionEvent.ACTION_UP && mousePrimaryTracking) {
            mousePrimaryTracking = false;
            float maxMove = MOUSE_CLICK_MAX_MOVE_DP * getResources().getDisplayMetrics().density;
            float dx = event.getX() - mouseStartX;
            float dy = event.getY() - mouseStartY;
            if (Math.abs(dx) <= maxMove && Math.abs(dy) <= maxMove) {
                performPlaybackTouchAction(InputAction.OPEN_MENU);
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (isMouseInput(event)) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_SCROLL) {
                return handleMouseScroll(event) || super.dispatchGenericMotionEvent(event);
            }
            if (action == MotionEvent.ACTION_BUTTON_PRESS
                    || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                Log.i(TAG, "MOUSE_BUTTON action=" + action
                        + " buttonState=" + event.getButtonState());
                if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                    Log.i(TAG, "MOUSE_BUTTON secondary action=" + action);
                }
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean handleMouseScroll(MotionEvent event) {
        if (!canHandlePlaybackTouchGesture()) {
            return false;
        }
        float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        Log.i(TAG, "MOUSE_SCROLL vscroll=" + vscroll);
        if (vscroll == 0f) {
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastMouseScrollAt < MOUSE_SCROLL_THROTTLE_MS) {
            return true;
        }
        lastMouseScrollAt = now;
        cancelAutoRecovery("user");
        setRemoteInputMode(false);
        performPlaybackTouchAction(vscroll > 0f
                ? InputAction.CHANNEL_UP : InputAction.CHANNEL_DOWN);
        return true;
    }

    private boolean canHandlePlaybackTouchGesture() {
        return channelListPanel.getVisibility() != View.VISIBLE
                && managementPanel.getVisibility() != View.VISIBLE
                && backPrompt.getVisibility() != View.VISIBLE;
    }

    private InputAction touchActionFromGesture(float dx, float dy) {
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        float minDistance = TOUCH_SWIPE_MIN_DISTANCE_DP
                * getResources().getDisplayMetrics().density;
        if (absDx < minDistance && absDy < minDistance) {
            return InputAction.OPEN_MENU;
        }
        if (absDx >= absDy * TOUCH_SWIPE_AXIS_RATIO) {
            return dx < 0 ? InputAction.SOURCE_NEXT : InputAction.SOURCE_PREV;
        }
        if (absDy >= absDx * TOUCH_SWIPE_AXIS_RATIO) {
            return dy < 0 ? InputAction.CHANNEL_UP : InputAction.CHANNEL_DOWN;
        }
        return null;
    }

    private boolean performPlaybackTouchAction(InputAction action) {
        Log.i(TAG, "TOUCH_GESTURE action=" + action);
        switch (action) {
            case OPEN_MENU:
            case CONFIRM:
                openChannelList();
                return true;
            case CHANNEL_UP:
                switchRelative(reverseUpDown ? 1 : -1);
                return true;
            case CHANNEL_DOWN:
                switchRelative(reverseUpDown ? -1 : 1);
                return true;
            case SOURCE_PREV:
                switchCustomSource(-1, false, "touch");
                return true;
            case SOURCE_NEXT:
                switchCustomSource(1, false, "touch");
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        InputAction action = InputAction.fromKeyCode(keyCode);
        if (event.getAction() == KeyEvent.ACTION_DOWN && action != null) {
            setRemoteInputMode(true);
        }
        if (event.getAction() == KeyEvent.ACTION_UP && action != null) {
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getRepeatCount() > 0 && action != null
                && !action.allowsRepeatNavigation()) {
            return true;
        }

        if (backPrompt.getVisibility() == View.VISIBLE
                && (action == InputAction.CONFIRM
                || action == InputAction.OPEN_MANAGEMENT)) {
            confirmBackPrompt();
            return true;
        }
        if (backPrompt.getVisibility() == View.VISIBLE && action != InputAction.BACK) {
            return action != null || super.dispatchKeyEvent(event);
        }

        if (managementPanel.getVisibility() == View.VISIBLE) {
            if (action == InputAction.BACK || action == InputAction.OPEN_MANAGEMENT) {
                closeManagementPanel();
            }
            return action != null || super.dispatchKeyEvent(event);
        }

        if (channelListPanel.getVisibility() == View.VISIBLE) {
            scheduleChannelListDismiss();
            if (action != null) {
                switch (action) {
                case BACK:
                case OPEN_MANAGEMENT:
                    closeChannelList();
                    return true;
                case SOURCE_PREV:
                    if (epgProgramList.hasFocus()) {
                        channelList.requestFocus();
                    } else {
                        groupList.requestFocus();
                        groupList.setSelection(browsingGroupIndex);
                    }
                    return true;
                case SOURCE_NEXT:
                    if (groupList.hasFocus()) {
                        channelList.requestFocus();
                    } else if (epgProgramAdapter.getCount() > 0) {
                        epgProgramList.requestFocus();
                    } else {
                        channelList.requestFocus();
                    }
                    return true;
                case CHANNEL_UP:
                    moveChannelMenuSelection(-1);
                    return true;
                case CHANNEL_DOWN:
                    moveChannelMenuSelection(1);
                    return true;
                case CONFIRM:
                    if (channelList.hasFocus()) {
                        int position = channelList.getSelectedItemPosition();
                        if (position != AdapterView.INVALID_POSITION) {
                            switchBrowsingChannel(position);
                        }
                    } else if (!epgProgramList.hasFocus()) {
                        channelList.requestFocus();
                    }
                    return true;
                default:
                    break;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        if (event.getRepeatCount() > 0 && action != null
                && action.allowsRepeatNavigation()) {
            return true;
        }
        if (action != null && action.isDigit()) {
            enterNumericChannel(action.digitValue());
            return true;
        }
        if (action == null) {
            return super.dispatchKeyEvent(event);
        }
        switch (action) {
            case SOURCE_PREV:
                if (switchCustomSource(-1, false, "")) {
                    return true;
                }
                return super.dispatchKeyEvent(event);
            case SOURCE_NEXT:
                if (switchCustomSource(1, false, "")) {
                    return true;
                }
                return super.dispatchKeyEvent(event);
            case CHANNEL_UP:
                switchRelative(reverseUpDown ? 1 : -1);
                return true;
            case CHANNEL_DOWN:
                switchRelative(reverseUpDown ? -1 : 1);
                return true;
            case CONFIRM:
                openChannelList();
                return true;
            case OPEN_MANAGEMENT:
                openManagement();
                return true;
            case PLAY_PAUSE:
                togglePlayback();
                return true;
            case BACK:
                onBackPressed();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public void onBackPressed() {
        cancelAutoRecovery("user");
        clearNumericChannelInput();
        if (managementPanel.getVisibility() == View.VISIBLE) {
            closeManagementPanel();
            return;
        }
        if (channelListPanel.getVisibility() == View.VISIBLE) {
            closeChannelList();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackPressedAt <= EXIT_CONFIRM_TIMEOUT_MS) {
            finish();
            return;
        }
        lastBackPressedAt = now;
        backPrompt.removeCallbacks(hideBackPrompt);
        backPrompt.setVisibility(View.VISIBLE);
        backPrompt.bringToFront();
        backPromptOk.requestFocus();
        backPrompt.postDelayed(hideBackPrompt, BACK_PROMPT_TIMEOUT_MS);
    }

    @Override
    protected void onPause() {
        if (videoView != null) {
            videoView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.onResume();
        }
        refreshManagementAddress();
        applySystemUiVisibility();
    }

    @Override
    protected void onDestroy() {
        playRequestId++;
        if (channelListClock != null) {
            channelListClock.removeCallbacks(updateChannelListClock);
        }
        if (backPrompt != null) {
            backPrompt.removeCallbacks(hideBackPrompt);
        }
        clearNumericChannelInput();
        if (controlServer != null) {
            controlServer.close();
            controlServer = null;
        }
        releasePlayer();
        if (yangshipinResolver != null) {
            yangshipinResolver.destroy();
        }
        if (gdtvLiveResolver != null) {
            gdtvLiveResolver.cancel();
        }
        if (proxy != null) {
            proxy.close();
        }
        super.onDestroy();
    }
}
