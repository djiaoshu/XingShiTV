package com.xingshi.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class WebPlayerActivity extends Activity {
    static final String EXTRA_URL = "web_url";
    static final String EXTRA_EXTRA = "web_extra";
    static final String EXTRA_FULLSCREEN_TYPE = "fullscreen_type";
    static final String EXTRA_GROUP_INDEX = "group_index";
    static final String EXTRA_CHANNEL_INDEX = "channel_index";
    static final String EXTRA_MANAGEMENT_URL = "management_url";
    static final int RESULT_OPEN_CHANNEL_MENU = Activity.RESULT_FIRST_USER + 1;
    static final int RESULT_SWITCH_CHANNEL = Activity.RESULT_FIRST_USER + 2;
    static final int RESULT_EXIT_APP = Activity.RESULT_FIRST_USER + 3;
    private static final String TAG = "WEBVIEW_TEST";
    private static final long PANEL_TIMEOUT_MS = 12000L;
    private static final long BACK_PROMPT_TIMEOUT_MS = 5000L;
    private static final long EXIT_CONFIRM_TIMEOUT_MS = BACK_PROMPT_TIMEOUT_MS;
    private static final String PC_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/129.0.0.0 Safari/537.36";
    private static final String GDTV_PC_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/149.0.0.0 Safari/537.36";
    private static final String VIDEO_DETECT_JS =
            "(function(){"
                    + "if(window.nativeTvVideoWatcherStarted){console.log('WEBVIEW_TEST JS already injected');return;}"
                    + "window.nativeTvVideoWatcherStarted=true;"
                    + "console.log('WEBVIEW_TEST JS injected');"
                    + "document.addEventListener('fullscreenchange',function(){"
                    + "console.log('WEBVIEW_TEST fullscreenchange '+document.fullscreenElement);"
                    + "});"
                    + "document.addEventListener('webkitfullscreenchange',function(){"
                    + "console.log('WEBVIEW_TEST webkitfullscreenchange');"
                    + "});"
                    + "function detectorText(value){"
                    + "if(value===undefined||value===null){return '';}"
                    + "return String(value).replace(/\\s+/g,' ').trim();"
                    + "}"
                    + "function detectorClass(element){"
                    + "if(!element||!element.className){return '';}"
                    + "if(typeof element.className==='string'){return element.className;}"
                    + "if(element.className.baseVal){return element.className.baseVal;}"
                    + "return String(element.className);"
                    + "}"
                    + "document.addEventListener('dblclick',function(e){"
                    + "var target=e&&e.target?e.target:null;"
                    + "console.log('WEBVIEW_TEST dblclick target='+detectorClass(target));"
                    + "},true);"
                    + "document.addEventListener('click',function(e){"
                    + "var target=e&&e.target?e.target:null;"
                    + "var closest=target&&target.closest?target.closest('[class*=full]'):null;"
                    + "if(closest){"
                    + "console.log('WEBVIEW_TEST full click target='+detectorClass(target)"
                    + "+' closest='+detectorClass(closest));"
                    + "}"
                    + "},true);"
                    + "function detectorInfo(element){"
                    + "return {"
                    + "tag:detectorText(element.tagName),"
                    + "id:detectorText(element.id),"
                    + "cls:detectorText(detectorClass(element)),"
                    + "text:detectorText(element.innerText||element.textContent).substring(0,60),"
                    + "aria:detectorText(element.getAttribute&&element.getAttribute('aria-label')),"
                    + "title:detectorText(element.getAttribute&&element.getAttribute('title'))"
                    + "};"
                    + "}"
                    + "function detectorMatch(info){"
                    + "var s=(info.id+' '+info.cls+' '+info.text+' '+info.aria+' '+info.title).toLowerCase();"
                    + "return s.indexOf('fullscreen')>=0||s.indexOf('full-screen')>=0"
                    + "||s.indexOf('full_screen')>=0||s.indexOf('expand')>=0"
                    + "||s.indexOf('全屏')>=0||s.indexOf('放大')>=0;"
                    + "}"
                    + "function detectorRect(element){"
                    + "try{var r=element.getBoundingClientRect();return Math.round(r.left)+','+Math.round(r.top)+','+Math.round(r.width)+'x'+Math.round(r.height);}"
                    + "catch(e){return 'rect-error';}"
                    + "}"
                    + "function isVisible(element){"
                    + "try{"
                    + "var r=element.getBoundingClientRect();"
                    + "var s=window.getComputedStyle(element);"
                    + "return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0';"
                    + "}catch(e){return false;}"
                    + "}"
                    + "function currentStrategy(){"
                    + "var url=String(location.href||'').toLowerCase();"
                    + "if(url.indexOf('mgtv.com')>=0){return 'mgtv';}"
                    + "if(url.indexOf('yangshipin.cn')>=0){return 'yangshipin';}"
                    + "if(url.indexOf('gdtv.cn')>=0){return 'gdtv';}"
                    + "return 'generic';"
                    + "}"
                    + "function scanFullscreenDetector(){"
                    + "var fullscreen=document.fullscreenElement||document.webkitFullscreenElement||null;"
                    + "console.log('WEBVIEW_TEST fullscreen state element='+(fullscreen?fullscreen.tagName:'null')"
                    + "+' htmlClass='+detectorClass(document.documentElement)"
                    + "+' bodyClass='+detectorClass(document.body));"
                    + "var elements=document.getElementsByTagName('*');"
                    + "var count=0;"
                    + "for(var i=0;i<elements.length;i++){"
                    + "var element=elements[i];"
                    + "var info=detectorInfo(element);"
                    + "if(!detectorMatch(info)){continue;}"
                    + "if(count<40){"
                    + "console.log('WEBVIEW_TEST fullscreen candidate: '+info.tag"
                    + "+' id='+info.id+' class='+info.cls+' text='+info.text"
                    + "+' aria='+info.aria+' title='+info.title"
                    + "+' rect='+detectorRect(element));"
                    + "}"
                    + "count++;"
                    + "}"
                    + "console.log('WEBVIEW_TEST fullscreen candidate count='+count);"
                    + "}"
                    + "function scanVisiblePlayerDom(label){"
                    + "var elements=document.getElementsByTagName('*');"
                    + "var count=0;"
                    + "for(var i=0;i<elements.length;i++){"
                    + "var element=elements[i];"
                    + "if(!isVisible(element)){continue;}"
                    + "var info=detectorInfo(element);"
                    + "var s=(info.id+' '+info.cls+' '+info.text+' '+info.aria+' '+info.title).toLowerCase();"
                    + "if(s.indexOf('player')<0&&s.indexOf('video')<0&&s.indexOf('container')<0"
                    + "&&s.indexOf('layer')<0&&s.indexOf('fullscreen')<0&&s.indexOf('full-screen')<0"
                    + "&&s.indexOf('全屏')<0){continue;}"
                    + "if(count<60){"
                    + "console.log('WEBVIEW_TEST '+label+' visible dom: '+info.tag"
                    + "+' id='+info.id+' class='+info.cls+' text='+info.text"
                    + "+' aria='+info.aria+' title='+info.title"
                    + "+' rect='+detectorRect(element));"
                    + "}"
                    + "count++;"
                    + "}"
                    + "console.log('WEBVIEW_TEST '+label+' visible dom count='+count);"
                    + "}"
                    + "function findPlayerTouchTarget(){"
                    + "var elements=document.getElementsByTagName('*');"
                    + "function has(cls,name){return cls.indexOf(name)>=0;}"
                    + "function pick(rule){"
                    + "for(var i=0;i<elements.length;i++){"
                    + "var cls=detectorClass(elements[i]);"
                    + "if(rule(cls)){return elements[i];}"
                    + "}"
                    + "return null;"
                    + "}"
                    + "return pick(function(cls){return has(cls,'mango-kerne-layer');})"
                    + "||pick(function(cls){return has(cls,'kernel-container');});"
                    + "}"
                    + "function logPlayerLayers(){"
                    + "var elements=document.getElementsByTagName('*');"
                    + "var out=[];"
                    + "for(var i=0;i<elements.length&&out.length<30;i++){"
                    + "var cls=detectorClass(elements[i]);"
                    + "var lower=cls.toLowerCase();"
                    + "if(lower.indexOf('mango')>=0||lower.indexOf('kernel')>=0"
                    + "||lower.indexOf('player')>=0||lower.indexOf('layer')>=0){"
                    + "out.push(cls);"
                    + "}"
                    + "}"
                    + "console.log('WEBVIEW_TEST all player layers='+out.join(' | '));"
                    + "}"
                    + "function findYangshipinPlayerTarget(){"
                    + "var elements=document.getElementsByTagName('*');"
                    + "function has(cls,name){return cls.indexOf(name)>=0;}"
                    + "function pick(rule){"
                    + "for(var i=0;i<elements.length;i++){"
                    + "var cls=detectorClass(elements[i]);"
                    + "if(rule(elements[i],cls)){return elements[i];}"
                    + "}"
                    + "return null;"
                    + "}"
                    + "return pick(function(el,cls){return has(cls,'video-con');})"
                    + "||pick(function(el,cls){return has(cls,'c-container');})"
                    + "||pick(function(el,cls){return el.tagName==='VIDEO'&&has(cls,'video-js');});"
                    + "}"
                    + "function triggerNativeTouchPlayer(target){"
                    + "try{"
                    + "var rect=target.getBoundingClientRect();"
                    + "var x=rect.left+rect.width/2;"
                    + "var y=rect.top+rect.height/2;"
                    + "console.log('WEBVIEW_TEST player rect x='+x+' y='+y+' class='+detectorClass(target));"
                    + "window.main.realTouchPlayer(x,y,window.innerWidth,window.innerHeight,detectorClass(target));"
                    + "}catch(e){console.log('WEBVIEW_TEST real touch player failed '+e);}"
                    + "}"
                    + "function triggerNativeTouchFullscreenButton(target){"
                    + "try{"
                    + "var rect=target.getBoundingClientRect();"
                    + "var x=rect.left+rect.width/2;"
                    + "var y=rect.top+rect.height/2;"
                    + "console.log('WEBVIEW_TEST gdtv fullscreen button class='+detectorClass(target)"
                    + "+' rect='+detectorRect(target));"
                    + "window.main.realTouchFullscreenButton(x,y,window.innerWidth,window.innerHeight,detectorClass(target));"
                    + "}catch(e){console.log('WEBVIEW_TEST real touch fullscreen button failed '+e);}"
                    + "}"
                    + "function findGdtvFullscreenButton(){"
                    + "var elements=document.getElementsByTagName('*');"
                    + "for(var i=0;i<elements.length;i++){"
                    + "var cls=detectorClass(elements[i]);"
                    + "if(cls.indexOf('prism-fullscreen-btn')>=0&&isVisible(elements[i])){return elements[i];}"
                    + "}"
                    + "return null;"
                    + "}"
                    + "function scheduleGdtvFullscreenButton(){"
                    + "if(window.__gdtvFullscreenTriggered){return;}"
                    + "window.__gdtvFullscreenTriggered=true;"
                    + "var attempts=0;"
                    + "var timer=setInterval(function(){"
                    + "attempts++;"
                    + "var target=findGdtvFullscreenButton();"
                    + "if(target){"
                    + "clearInterval(timer);"
                    + "triggerNativeTouchFullscreenButton(target);"
                    + "return;"
                    + "}"
                    + "if(attempts>=20){"
                    + "clearInterval(timer);"
                    + "console.log('WEBVIEW_TEST gdtv fullscreen button not found');"
                    + "}"
                    + "},500);"
                    + "}"
                    + "function scheduleMgtvRealTouchPlayer(){"
                    + "if(window.__mgtvFullscreenTriggered){return;}"
                    + "window.__mgtvFullscreenTriggered=true;"
                    + "var attempts=0;"
                    + "var timer=setInterval(function(){"
                    + "attempts++;"
                    + "logPlayerLayers();"
                    + "var target=findPlayerTouchTarget();"
                    + "if(target){"
                    + "clearInterval(timer);"
                    + "triggerNativeTouchPlayer(target);"
                    + "return;"
                    + "}"
                    + "if(attempts>=30){"
                    + "clearInterval(timer);"
                    + "console.log('WEBVIEW_TEST real touch player not found');"
                    + "}"
                    + "},500);"
                    + "}"
                    + "function scheduleYangshipinDetector(){"
                    + "if(window.__yangshipinFullscreenDetector){return;}"
                    + "window.__yangshipinFullscreenDetector=true;"
                    + "var attempts=0;"
                    + "var timer=setInterval(function(){"
                    + "attempts++;"
                    + "scanVisiblePlayerDom('yangshipin');"
                    + "var target=findYangshipinPlayerTarget();"
                    + "if(target){"
                    + "clearInterval(timer);"
                    + "console.log('WEBVIEW_TEST yangshipin selected target class='+detectorClass(target)"
                    + "+' tag='+target.tagName+' rect='+detectorRect(target));"
                    + "triggerNativeTouchPlayer(target);"
                    + "return;"
                    + "}"
                    + "if(attempts>=30){clearInterval(timer);}"
                    + "},1000);"
                    + "}"
                    + "function scheduleYangshipinStablePlayback(video){"
                    + "if(window.__yangshipinStablePlaybackWait){return;}"
                    + "window.__yangshipinStablePlaybackWait=true;"
                    + "var stableCount=0;"
                    + "var attempts=0;"
                    + "var timer=setInterval(function(){"
                    + "attempts++;"
                    + "var v=video||window.nativeTvVideo;"
                    + "if(!v){if(attempts>=40){clearInterval(timer);}return;}"
                    + "var ready=v.readyState||0;"
                    + "var current=v.currentTime||0;"
                    + "if(ready>=3&&current>1){"
                    + "stableCount++;"
                    + "console.log('WEBVIEW_TEST stable playback readyState='+ready);"
                    + "console.log('WEBVIEW_TEST stable playback currentTime='+current);"
                    + "}else{"
                    + "stableCount=0;"
                    + "}"
                    + "if(stableCount>=3){"
                    + "clearInterval(timer);"
                    + "setTimeout(function(){"
                    + "console.log('WEBVIEW_TEST auto fullscreen trigger');"
                    + "scheduleYangshipinDetector();"
                    + "},1500);"
                    + "return;"
                    + "}"
                    + "if(attempts>=60){clearInterval(timer);}"
                    + "},500);"
                    + "}"
                    + "function scheduleAutoFullscreen(video){"
                    + "var strategy=currentStrategy();"
                    + "console.log('WEBVIEW_TEST fullscreen strategy='+strategy+' url='+location.href);"
                    + "if(strategy==='mgtv'){scheduleMgtvRealTouchPlayer();return;}"
                    + "if(strategy==='yangshipin'){scheduleYangshipinStablePlayback(video);return;}"
                    + "if(strategy==='gdtv'){scheduleGdtvFullscreenButton();return;}"
                    + "scanVisiblePlayerDom('generic');"
                    + "}"
                    + "function setup(video){"
                    + "if(!video||video.nativeTvSetup){return;}"
                    + "video.nativeTvSetup=true;"
                    + "window.nativeTvVideo=video;"
                    + "video.autoplay=true;"
                    + "video.defaultMuted=false;"
                    + "video.muted=false;"
                    + "video.style.objectFit='fill';"
                    + "console.log('WEBVIEW_TEST found video');"
                    + "function notifyPlaying(){console.log('WEBVIEW_TEST video playing');scheduleAutoFullscreen(video);}"
                    + "function notifyReady(e){console.log('WEBVIEW_TEST video '+e.type);}"
                    + "video.addEventListener('play',notifyPlaying);"
                    + "video.addEventListener('playing',notifyPlaying);"
                    + "video.addEventListener('canplay',notifyReady);"
                    + "video.addEventListener('loadedmetadata',notifyReady);"
                    + "if(!video.paused&&!video.ended){notifyPlaying();}"
                    + "}"
                    + "function scanNode(node){"
                    + "if(!node){return;}"
                    + "if(node.tagName==='VIDEO'){setup(node);}"
                    + "if(node.getElementsByTagName){"
                    + "var videos=node.getElementsByTagName('video');"
                    + "for(var i=0;i<videos.length;i++){setup(videos[i]);}"
                    + "}"
                    + "if(node.shadowRoot){scanNode(node.shadowRoot);}"
                    + "}"
                    + "function scanAll(){"
                    + "scanNode(document.documentElement);"
                    + "}"
                    + "scanAll();"
                    + "setInterval(scanAll,500);"
                    + "scanFullscreenDetector();"
                    + "setInterval(scanFullscreenDetector,1000);"
                    + "if(window.MutationObserver){"
                    + "var observer=new MutationObserver(function(list){"
                    + "for(var i=0;i<list.length;i++){"
                    + "var nodes=list[i].addedNodes;"
                    + "for(var j=0;j<nodes.length;j++){scanNode(nodes[j]);}"
                    + "}"
                    + "scanAll();"
                    + "});"
                    + "observer.observe(document.documentElement||document.body,{childList:true,subtree:true});"
                    + "}"
                    + "})();";
    private static final String GDTV_PC_PLAYER_JS =
            "(function(){"
                    + "if(window.__xstvGdtvPcStarted){console.log('WEBVIEW_TEST gdtv pc already injected');return;}"
                    + "window.__xstvGdtvPcStarted=true;"
                    + "console.log('WEBVIEW_TEST gdtv pc injected');"
                    + "function cls(e){"
                    + "if(!e||!e.className){return '';}"
                    + "if(typeof e.className==='string'){return e.className;}"
                    + "if(e.className.baseVal){return e.className.baseVal;}"
                    + "return String(e.className);"
                    + "}"
                    + "function visible(e){"
                    + "try{var r=e.getBoundingClientRect();var s=getComputedStyle(e);"
                    + "return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';"
                    + "}catch(err){return false;}"
                    + "}"
                    + "function fill(el){"
                    + "el.style.position='fixed';"
                    + "el.style.left='0';"
                    + "el.style.top='0';"
                    + "el.style.right='0';"
                    + "el.style.bottom='0';"
                    + "el.style.width='100vw';"
                    + "el.style.height='100vh';"
                    + "el.style.margin='0';"
                    + "el.style.background='#000';"
                    + "el.style.zIndex='2147483647';"
                    + "}"
                    + "function layoutPlayer(player){"
                    + "document.documentElement.style.margin='0';"
                    + "document.documentElement.style.padding='0';"
                    + "document.documentElement.style.width='100vw';"
                    + "document.documentElement.style.height='100vh';"
                    + "document.documentElement.style.overflow='hidden';"
                    + "document.documentElement.style.background='#000';"
                    + "document.body.style.margin='0';"
                    + "document.body.style.padding='0';"
                    + "document.body.style.width='100vw';"
                    + "document.body.style.height='100vh';"
                    + "document.body.style.overflow='hidden';"
                    + "document.body.style.background='#000';"
                    + "if(player.parentNode!==document.body){document.body.appendChild(player);}"
                    + "for(var i=0;i<document.body.children.length;i++){"
                    + "var child=document.body.children[i];"
                    + "if(child!==player){child.style.display='none';}"
                    + "}"
                    + "fill(player);"
                    + "var videos=player.getElementsByTagName('video');"
                    + "for(var j=0;j<videos.length;j++){"
                    + "videos[j].style.width='100%';"
                    + "videos[j].style.height='100%';"
                    + "videos[j].style.objectFit='fill';"
                    + "videos[j].style.background='#000';"
                    + "}"
                    + "}"
                    + "function notifyPlaying(){"
                    + "if(window.__xstvGdtvPcPlaying){return;}"
                    + "window.__xstvGdtvPcPlaying=true;"
                    + "console.log('WEBVIEW_TEST gdtv pc video playing');"
                    + "}"
                    + "function watchVideos(player){"
                    + "var videos=player.getElementsByTagName('video');"
                    + "console.log('WEBVIEW_TEST gdtv pc video count='+videos.length);"
                    + "for(var i=0;i<videos.length;i++){"
                    + "var v=videos[i];"
                    + "if(v.__xstvGdtvPcVideo){continue;}"
                    + "v.__xstvGdtvPcVideo=true;"
                    + "v.autoplay=true;"
                    + "v.muted=false;"
                    + "v.addEventListener('playing',notifyPlaying);"
                    + "v.addEventListener('play',function(){console.log('WEBVIEW_TEST gdtv pc video play');});"
                    + "v.addEventListener('canplay',function(){console.log('WEBVIEW_TEST gdtv pc video canplay');});"
                    + "v.addEventListener('loadedmetadata',function(){console.log('WEBVIEW_TEST gdtv pc video loadedmetadata');});"
                    + "if(!v.paused&&!v.ended&&v.readyState>=2){notifyPlaying();}"
                    + "}"
                    + "}"
                    + "function setup(player){"
                    + "if(!player||!visible(player)){return false;}"
                    + "if(!window.__xstvGdtvPcPlayerFound){"
                    + "window.__xstvGdtvPcPlayerFound=true;"
                    + "console.log('WEBVIEW_TEST gdtv pc player found class='+cls(player));"
                    + "}"
                    + "layoutPlayer(player);"
                    + "watchVideos(player);"
                    + "return true;"
                    + "}"
                    + "function scan(){"
                    + "var player=document.querySelector('#J_prismPlayer');"
                    + "if(player){setup(player);return true;}"
                    + "return false;"
                    + "}"
                    + "var attempts=0;"
                    + "var timer=setInterval(function(){"
                    + "attempts++;"
                    + "if(scan()&&window.__xstvGdtvPcPlaying){clearInterval(timer);return;}"
                    + "if(attempts>=160){clearInterval(timer);console.log('WEBVIEW_TEST gdtv pc player timeout');}"
                    + "},250);"
                    + "if(window.MutationObserver){"
                    + "var observer=new MutationObserver(function(){scan();});"
                    + "observer.observe(document.documentElement||document.body,{childList:true,subtree:true});"
                    + "}"
                    + "scan();"
                    + "})();";

    private FrameLayout root;
    private FrameLayout customViewContainer;
    private WebView webView;
    private View loadingOverlay;
    private TextView loadingBrand;
    private TextView loadingChannel;
    private TextView loadingStatus;
    private TextView loadingPercent;
    private ProgressBar loadingProgress;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String webExtra;
    private String fullscreenType;
    private String managementUrl;
    private View channelListPanel;
    private TextView channelListTitle;
    private TextView channelListClock;
    private ListView groupList;
    private ListView channelList;
    private View backPrompt;
    private Button backPromptOk;
    private ChannelListAdapter groupAdapter;
    private ChannelListAdapter channelAdapter;
    private int currentGroupIndex;
    private int currentChannelIndex;
    private int browsingGroupIndex;
    private int loadingProgressValue;
    private boolean gdtvFullscreenChanged;
    private boolean gdtvFullscreenPlaying;
    private long lastBackPressedAt;
    private final SimpleDateFormat clockFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final Runnable hideChannelList = new Runnable() {
        @Override
        public void run() {
            closeChannelList();
        }
    };
    private final Runnable updateChannelListClock = new Runnable() {
        @Override
        public void run() {
            if (channelListClock != null
                    && channelListPanel != null
                    && channelListPanel.getVisibility() == View.VISIBLE) {
                channelListClock.setText(clockFormat.format(new Date()));
                channelListClock.postDelayed(this, 1000L);
            }
        }
    };
    private final Runnable hideBackPrompt = new Runnable() {
        @Override
        public void run() {
            if (backPrompt != null) {
                backPrompt.setVisibility(View.GONE);
                if (webView != null) {
                    webView.requestFocus();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ChannelCatalog.initialize(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        enterFullscreen();

        root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        customViewContainer = new FrameLayout(this);
        customViewContainer.setBackgroundColor(Color.BLACK);
        customViewContainer.setVisibility(View.GONE);
        root.addView(customViewContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        loadingOverlay = createLoadingOverlay();
        root.addView(loadingOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        updateLoadingProgress(10, "WebView创建");
        channelListPanel = getLayoutInflater().inflate(R.layout.view_channel_list_panel,
                root, false);
        root.addView(channelListPanel);
        backPrompt = getLayoutInflater().inflate(R.layout.view_back_navigation_prompt,
                root, false);
        root.addView(backPrompt);
        setContentView(root);

        configureWebView();
        configureChannelMenu();
        configureBackPrompt();

        String url = getIntent().getStringExtra(EXTRA_URL);
        webExtra = getIntent().getStringExtra(EXTRA_EXTRA);
        fullscreenType = getIntent().getStringExtra(EXTRA_FULLSCREEN_TYPE);
        managementUrl = getIntent().getStringExtra(EXTRA_MANAGEMENT_URL);
        currentGroupIndex = ChannelCatalog.wrapGroupIndex(
                getIntent().getIntExtra(EXTRA_GROUP_INDEX, 0));
        currentChannelIndex = ChannelCatalog.wrapIndex(
                ChannelCatalog.GROUPS[currentGroupIndex].channels,
                getIntent().getIntExtra(EXTRA_CHANNEL_INDEX, 0));
        browsingGroupIndex = currentGroupIndex;
        updateLoadingChannelName();
        Log.i(TAG, "Intent url=" + url);
        Log.i(TAG, "Intent extra=" + webExtra);
        Log.i(TAG, "Intent fullscreenType=" + fullscreenType);
        Log.i(TAG, "Intent managementUrl=" + managementUrl);
        Log.i(TAG, "打开网页: url=" + url);
        if (url == null || url.length() == 0) {
            finish();
            return;
        }
        if (isGdtvPcWebPlayer()) {
            webView.getSettings().setUserAgentString(GDTV_PC_USER_AGENT);
            Log.i(TAG, "GDTV PC UA=" + webView.getSettings().getUserAgentString());
        }
        Log.i(TAG, "loadUrl=" + url);
        Log.i(TAG, "before loadUrl=" + url);
        updateLoadingProgress(30, "开始加载网页");
        webView.loadUrl(url);
        Log.i(TAG, "WebView currentUrl=" + webView.getUrl());
    }

    private View createLoadingOverlay() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER);
        panel.setBackgroundColor(Color.rgb(7, 10, 16));
        panel.setPadding(dp(36), dp(26), dp(36), dp(26));
        panel.setClickable(true);
        panel.setFocusable(false);

        loadingBrand = new TextView(this);
        loadingBrand.setText("星视TV");
        loadingBrand.setTextColor(Color.WHITE);
        loadingBrand.setTextSize(30);
        loadingBrand.setGravity(android.view.Gravity.CENTER);
        panel.addView(loadingBrand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView loadingTitle = new TextView(this);
        loadingTitle.setText("正在加载");
        loadingTitle.setTextColor(Color.rgb(190, 205, 226));
        loadingTitle.setTextSize(18);
        loadingTitle.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(12);
        panel.addView(loadingTitle, titleParams);

        loadingChannel = new TextView(this);
        loadingChannel.setText("");
        loadingChannel.setTextColor(Color.rgb(230, 236, 246));
        loadingChannel.setTextSize(22);
        loadingChannel.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        channelParams.topMargin = dp(18);
        panel.addView(loadingChannel, channelParams);

        loadingProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        loadingProgress.setMax(100);
        loadingProgress.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                dp(260), dp(16));
        progressParams.topMargin = dp(24);
        panel.addView(loadingProgress, progressParams);

        loadingPercent = new TextView(this);
        loadingPercent.setText("0%");
        loadingPercent.setTextColor(Color.WHITE);
        loadingPercent.setTextSize(24);
        loadingPercent.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams percentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        percentParams.topMargin = dp(14);
        panel.addView(loadingPercent, percentParams);

        loadingStatus = new TextView(this);
        loadingStatus.setText("");
        loadingStatus.setTextColor(Color.rgb(139, 157, 182));
        loadingStatus.setTextSize(14);
        loadingStatus.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        panel.addView(loadingStatus, statusParams);
        return panel;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateLoadingChannelName() {
        if (loadingChannel == null) {
            return;
        }
        ChannelCatalog.Group group = ChannelCatalog.GROUPS[currentGroupIndex];
        Channel channel = group.channels[currentChannelIndex];
        loadingChannel.setText(channel.name);
    }

    private void updateLoadingProgress(final int progress, final String status) {
        if (loadingOverlay == null || progress < loadingProgressValue) {
            return;
        }
        loadingProgressValue = progress;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (loadingOverlay == null) {
                    return;
                }
                loadingOverlay.setVisibility(View.VISIBLE);
                if (isGdtvLoadingControlled()) {
                    loadingOverlay.bringToFront();
                }
                if (loadingProgress != null) {
                    loadingProgress.setProgress(progress);
                }
                if (loadingPercent != null) {
                    loadingPercent.setText(progress + "%");
                }
                if (loadingStatus != null) {
                    loadingStatus.setText(status);
                }
                Log.i(TAG, "loading progress=" + progress + " status=" + status);
                if (progress >= 100) {
                    if (isGdtvLoadingControlled()) {
                        return;
                    }
                    loadingOverlay.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (loadingOverlay != null) {
                                loadingOverlay.setVisibility(View.GONE);
                            }
                        }
                    }, 350L);
                }
            }
        });
    }

    private void handleWebConsoleProgress(String message) {
        if (isGdtvPcWebPlayer()) {
            if (message.contains("gdtv pc player found")) {
                updateLoadingProgress(70, "找到播放器");
            } else if (message.contains("gdtv pc video playing")) {
                updateLoadingProgress(100, "video playing");
                hideLoadingOverlayNow("GDTV PC video playing");
            } else if (message.contains("gdtv pc player timeout")) {
                updateLoadingProgress(60, "等待播放器");
            }
            return;
        }
        if (isGdtvWebPlayer()) {
            if (message.contains("fullscreenchange [object HTMLDivElement]")) {
                gdtvFullscreenChanged = true;
                Log.i(TAG, "GDTV fullscreenchange ready");
                tryHideGdtvFullscreenLoading();
            } else if (message.contains("video playing") && customView != null) {
                gdtvFullscreenPlaying = true;
                Log.i(TAG, "GDTV fullscreen video playing ready");
                tryHideGdtvFullscreenLoading();
            }
        }
        if (message.contains("found video")) {
            updateLoadingProgress(70, "找到播放器");
        } else if (message.contains("video playing")) {
            updateLoadingProgress(90, "video playing");
        }
    }

    private void configureBackPrompt() {
        backPromptOk = (Button) backPrompt.findViewById(R.id.back_prompt_ok);
        backPrompt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                focusBackPromptConfirm();
            }
        });
        backPromptOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmBackPrompt();
            }
        });
        backPromptOk.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                return handleBackPromptKey(keyCode, event);
            }
        });
    }

    private void configureChannelMenu() {
        channelListTitle = (TextView) channelListPanel.findViewById(R.id.channel_list_title);
        channelListClock = (TextView) channelListPanel.findViewById(R.id.channel_list_clock);
        groupList = (ListView) channelListPanel.findViewById(R.id.channel_group_list);
        channelList = (ListView) channelListPanel.findViewById(R.id.channel_list);
        groupAdapter = new ChannelListAdapter(this);
        channelAdapter = new ChannelListAdapter(this);
        groupList.setAdapter(groupAdapter);
        channelList.setAdapter(channelAdapter);
        groupList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showChannelMenu(position);
            }
        });
        channelList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                requestSwitchChannel(position);
            }
        });
    }

    private void openChannelList() {
        Log.i(TAG, "open overlay channel menu");
        channelListPanel.setVisibility(View.VISIBLE);
        channelListPanel.bringToFront();
        showChannelMenu(currentGroupIndex);
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

    private void closeChannelList() {
        if (channelListPanel == null) {
            return;
        }
        channelListPanel.removeCallbacks(hideChannelList);
        if (channelListClock != null) {
            channelListClock.removeCallbacks(updateChannelListClock);
        }
        channelListPanel.setVisibility(View.GONE);
        if (webView != null) {
            webView.requestFocus();
        }
    }

    private void confirmBackPrompt() {
        backPrompt.removeCallbacks(hideBackPrompt);
        backPrompt.setVisibility(View.GONE);
        lastBackPressedAt = 0L;
        openManagementPage("back-confirm");
    }

    private void openManagementPage(String reason) {
        Log.i(TAG, "open management page reason=" + reason);
        closeChannelList();
        backPrompt.removeCallbacks(hideBackPrompt);
        backPrompt.setVisibility(View.GONE);
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        lastBackPressedAt = 0L;
        if (managementUrl == null || managementUrl.length() == 0) {
            Log.w(TAG, "management page skipped: missing url");
            return;
        }
        try {
            Intent intent = new Intent(this, ManagementActivity.class);
            intent.putExtra(ManagementActivity.EXTRA_URL, managementUrl);
            startActivity(intent);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to open management page", error);
        }
    }

    private void showBackPrompt() {
        lastBackPressedAt = SystemClock.elapsedRealtime();
        backPrompt.removeCallbacks(hideBackPrompt);
        backPrompt.setVisibility(View.VISIBLE);
        backPrompt.bringToFront();
        focusBackPromptConfirm();
        backPrompt.postDelayed(hideBackPrompt, BACK_PROMPT_TIMEOUT_MS);
    }

    private boolean isBackPromptVisible() {
        return backPrompt != null && backPrompt.getVisibility() == View.VISIBLE;
    }

    private void focusBackPromptConfirm() {
        if (backPrompt == null || backPromptOk == null) {
            return;
        }
        backPrompt.setFocusable(true);
        backPrompt.setFocusableInTouchMode(true);
        backPromptOk.setFocusable(true);
        backPromptOk.setFocusableInTouchMode(true);
        backPromptOk.requestFocusFromTouch();
        backPromptOk.requestFocus();
        backPromptOk.post(new Runnable() {
            @Override
            public void run() {
                backPromptOk.requestFocusFromTouch();
                backPromptOk.requestFocus();
            }
        });
    }

    private boolean handleBackPromptKey(int keyCode, KeyEvent event) {
        if (!isBackPromptVisible()) {
            return false;
        }
        InputAction action = InputAction.fromKeyCode(keyCode);
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true;
        }
        if (action == null) {
            focusBackPromptConfirm();
            return true;
        }
        switch (action) {
            case CONFIRM:
                confirmBackPrompt();
                return true;
            case BACK:
                requestExitApp();
                return true;
            default:
                focusBackPromptConfirm();
                return true;
        }
    }

    private void requestExitApp() {
        Log.i(TAG, "request exit app from WebView");
        setResult(RESULT_EXIT_APP);
        finish();
    }

    private boolean isGdtvWebPlayer() {
        return fullscreenType != null && "GDTV".equalsIgnoreCase(fullscreenType);
    }

    private boolean isGdtvPcWebPlayer() {
        return fullscreenType != null && "GDTV_PC".equalsIgnoreCase(fullscreenType);
    }

    private boolean isGdtvLoadingControlled() {
        return isGdtvWebPlayer() || isGdtvPcWebPlayer();
    }

    private void handleBackPressed() {
        if (channelListPanel.getVisibility() == View.VISIBLE) {
            closeChannelList();
            return;
        }
        if (backPrompt.getVisibility() == View.VISIBLE) {
            requestExitApp();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackPressedAt <= EXIT_CONFIRM_TIMEOUT_MS) {
            requestExitApp();
            return;
        }
        showBackPrompt();
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
        scheduleChannelListDismiss();
    }

    private void scheduleChannelListDismiss() {
        channelListPanel.removeCallbacks(hideChannelList);
        channelListPanel.postDelayed(hideChannelList, PANEL_TIMEOUT_MS);
    }

    private void moveChannelMenuSelection(int offset) {
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

    private void requestSwitchChannel(int channelIndex) {
        Log.i(TAG, "overlay channel selected group=" + browsingGroupIndex
                + " channel=" + channelIndex);
        setResult(RESULT_SWITCH_CHANNEL, getIntent()
                .putExtra(EXTRA_GROUP_INDEX, browsingGroupIndex)
                .putExtra(EXTRA_CHANNEL_INDEX, channelIndex));
        finish();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(PC_USER_AGENT);
        Log.i(TAG, "UA=" + settings.getUserAgentString());
        webView.addJavascriptInterface(this, "main");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.i(TAG, "navigate=" + url);
                view.loadUrl(url);
                Log.i(TAG, "WebView currentUrl=" + view.getUrl());
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.i(TAG, "onPageStarted url=" + url);
                Log.i(TAG, "WebView currentUrl=" + view.getUrl());
                updateLoadingProgress(30, "开始加载网页");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i(TAG, "onPageFinished url=" + url);
                Log.i(TAG, "WebView currentUrl=" + view.getUrl());
                updateLoadingProgress(50, "网页加载完成");
                injectMgtvExtraScript(url);
                if (isGdtvPcWebPlayer()) {
                    injectGdtvPcPlayerScript();
                } else {
                    injectVideoDetectScript();
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                String message = consoleMessage == null ? "" : consoleMessage.message();
                if (message != null && message.contains("WEBVIEW_TEST")) {
                    Log.i(TAG, message.replace("WEBVIEW_TEST ", ""));
                    handleWebConsoleProgress(message);
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showCustomView(view, callback);
            }

            @Override
            public void onShowCustomView(View view, int requestedOrientation,
                    CustomViewCallback callback) {
                showCustomView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                Log.i(TAG, "onHideCustomView called");
                hideCustomView();
            }
        });
    }

    private void showCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        Log.i(TAG, "onShowCustomView called");
        if (customView != null) {
            if (callback != null) {
                callback.onCustomViewHidden();
            }
            return;
        }
        customView = view;
        customViewCallback = callback;
        if (isGdtvWebPlayer()) {
            gdtvFullscreenChanged = false;
            gdtvFullscreenPlaying = false;
        }
        customViewContainer.setVisibility(View.VISIBLE);
        customViewContainer.bringToFront();
        customViewContainer.addView(customView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        Log.i(TAG, "customView added");
        enterFullscreen();
        Log.i(TAG, "fullscreen entered");
        updateLoadingProgress(100, "进入全屏");
    }

    private void tryHideGdtvFullscreenLoading() {
        if (!isGdtvWebPlayer() || loadingOverlay == null || customView == null
                || !gdtvFullscreenChanged || !gdtvFullscreenPlaying) {
            return;
        }
        Log.i(TAG, "GDTV fullscreen conditions met, hide loading");
        hideLoadingOverlayNow("GDTV fullscreen conditions met");
    }

    private void hideLoadingOverlayNow(String reason) {
        if (loadingOverlay == null) {
            return;
        }
        Log.i(TAG, "hide loading reason=" + reason);
        loadingOverlay.setVisibility(View.GONE);
    }

    private void injectMgtvExtraScript(String url) {
        if (webView == null || webExtra == null || webExtra.length() == 0) {
            return;
        }
        if (url == null || url.indexOf("mgtv.com") < 0) {
            return;
        }
        Log.i(TAG, "MGTV extra=" + webExtra);
        webView.evaluateJavascript(buildMgtvExtraScript(webExtra), null);
    }

    private String buildMgtvExtraScript(String extra) {
        return "(function(extra){"
                + "if(!extra){return;}"
                + "if(window.nativeMgtvExtra===extra&&window.nativeMgtvPreClickStarted){return;}"
                + "window.nativeMgtvExtra=extra;"
                + "window.nativeMgtvPreClickStarted=true;"
                + "var parts=extra.split(/\\s+/);"
                + "var category=parts[0]||'';"
                + "var channel=parts.slice(1).join(' ');"
                + "var categoryClicked=false;"
                + "var channelClicked=false;"
                + "var attempts=0;"
                + "function text(e){return (e.textContent||'').trim();}"
                + "function click(el,label){"
                + "try{el.click();console.log('WEBVIEW_TEST MGTV '+label+' clicked');return true;}"
                + "catch(e){console.log('WEBVIEW_TEST MGTV '+label+' click failed '+e);return false;}"
                + "}"
                + "function findCategory(){"
                + "var nodes=document.querySelectorAll('div.item');"
                + "for(var i=0;i<nodes.length;i++){if(text(nodes[i])===category){return nodes[i];}}"
                + "return null;"
                + "}"
                + "function findChannel(){"
                + "var nodes=document.querySelectorAll('a.channel');"
                + "for(var i=0;i<nodes.length;i++){"
                + "var n=nodes[i].querySelector('.name');"
                + "if(n&&text(n)===channel){return nodes[i];}"
                + "}"
                + "return null;"
                + "}"
                + "function loop(){"
                + "attempts++;"
                + "if(!categoryClicked){var c=findCategory();if(c){categoryClicked=click(c,'category '+category);}}"
                + "var ch=findChannel();"
                + "if(ch){channelClicked=click(ch,'channel '+channel);console.log('WEBVIEW_TEST MGTV extra done '+extra);return;}"
                + "if(attempts<30){setTimeout(loop,500);}else{console.log('WEBVIEW_TEST MGTV extra timeout '+extra);}"
                + "}"
                + "console.log('WEBVIEW_TEST MGTV extra start '+extra);"
                + "setTimeout(loop,500);"
                + "})(" + jsQuote(extra) + ");";
    }

    private String jsQuote(String value) {
        StringBuilder builder = new StringBuilder();
        builder.append('\"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '\"') {
                builder.append('\\').append(c);
            } else if (c == '\n') {
                builder.append("\\n");
            } else if (c == '\r') {
                builder.append("\\r");
            } else if (c == '\t') {
                builder.append("\\t");
            } else {
                builder.append(c);
            }
        }
        builder.append('\"');
        return builder.toString();
    }

    private void injectVideoDetectScript() {
        if (webView == null) {
            return;
        }
        Log.i(TAG, "JS injected");
        webView.evaluateJavascript(VIDEO_DETECT_JS, null);
    }

    private void injectGdtvPcPlayerScript() {
        if (webView == null) {
            return;
        }
        Log.i(TAG, "GDTV PC JS injected");
        webView.evaluateJavascript(GDTV_PC_PLAYER_JS, null);
    }

    @JavascriptInterface
    public void realTouchPlayer(final float cssX, final float cssY,
            final float viewportWidth, final float viewportHeight, final String className) {
        if (webView == null) {
            return;
        }
        webView.post(new Runnable() {
            @Override
            public void run() {
                if (webView == null) {
                    return;
                }
                float x = cssX;
                float y = cssY;
                if (viewportWidth > 0 && viewportHeight > 0) {
                    x = cssX * webView.getWidth() / viewportWidth;
                    y = cssY * webView.getHeight() / viewportHeight;
                }
                Log.i(TAG, "real touch player x=" + x + " y=" + y
                        + " class=" + className);
                dispatchTapWithClick(x, y, 1);
                webView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) {
                            float secondX = cssX;
                            float secondY = cssY;
                            if (viewportWidth > 0 && viewportHeight > 0) {
                                secondX = cssX * webView.getWidth() / viewportWidth;
                                secondY = cssY * webView.getHeight() / viewportHeight;
                            }
                            dispatchTapWithClick(secondX, secondY, 2);
                        }
                    }
                }, 100);
            }
        });
    }

    @JavascriptInterface
    public void realTouchFullscreenButton(final float cssX, final float cssY,
            final float viewportWidth, final float viewportHeight, final String className) {
        if (webView == null) {
            return;
        }
        webView.post(new Runnable() {
            @Override
            public void run() {
                if (webView == null) {
                    return;
                }
                float x = cssX;
                float y = cssY;
                if (viewportWidth > 0 && viewportHeight > 0) {
                    x = cssX * webView.getWidth() / viewportWidth;
                    y = cssY * webView.getHeight() / viewportHeight;
                }
                Log.i(TAG, "real touch fullscreenButton x=" + x + " y=" + y
                        + " class=" + className);
                dispatchTapWithClick(x, y, 1);
            }
        });
    }

    private void dispatchTapWithClick(float x, float y, int index) {
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, x, y, 0);
        Log.i(TAG, "dispatch native tap index=" + index + " down x=" + x + " y=" + y);
        webView.dispatchTouchEvent(down);
        Log.i(TAG, "dispatch native tap index=" + index + " up x=" + x + " y=" + y);
        webView.dispatchTouchEvent(up);
        webView.performClick();
        Log.i(TAG, "dispatch native tap index=" + index + " click");
        down.recycle();
        up.recycle();
    }

    private void enterFullscreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        customViewContainer.removeView(customView);
        customViewContainer.setVisibility(View.GONE);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        gdtvFullscreenChanged = false;
        gdtvFullscreenPlaying = false;
        enterFullscreen();
    }

    private void requestOriginalChannelMenu(String reason) {
        Log.i(TAG, "show overlay channel menu reason=" + reason);
        openChannelList();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        InputAction action = InputAction.fromKeyCode(keyCode);
        if (isBackPromptVisible()) {
            return handleBackPromptKey(keyCode, event);
        }
        if (customView != null && action == InputAction.BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (isGdtvWebPlayer()) {
                    Log.i(TAG, "GDTV back pressed in customView, show back prompt");
                    showBackPrompt();
                } else {
                    Log.i(TAG, "back pressed in customView, hide fullscreen");
                    hideCustomView();
                }
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && isWebPlayerHandledAction(action)) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (channelListPanel.getVisibility() == View.VISIBLE) {
                scheduleChannelListDismiss();
                if (action != null) {
                    switch (action) {
                    case BACK:
                    case OPEN_MANAGEMENT:
                        closeChannelList();
                        return true;
                    case SOURCE_PREV:
                        groupList.requestFocus();
                        groupList.setSelection(browsingGroupIndex);
                        return true;
                    case SOURCE_NEXT:
                        channelList.requestFocus();
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
                                requestSwitchChannel(position);
                            }
                        } else {
                            channelList.requestFocus();
                        }
                        return true;
                    default:
                        break;
                    }
                }
            }
            switch (action == null ? InputAction.CLOSE_MENU : action) {
                case CONFIRM:
                    requestOriginalChannelMenu("ok");
                    return true;
                case BACK:
                    handleBackPressed();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isBackPromptVisible()) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                focusBackPromptConfirm();
            }
            Rect promptRect = new Rect();
            backPrompt.getGlobalVisibleRect(promptRect);
            if (promptRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                return super.dispatchTouchEvent(event);
            }
            return true;
        }
        if (channelListPanel.getVisibility() == View.VISIBLE) {
            scheduleChannelListDismiss();
            Rect menuRect = new Rect();
            channelListPanel.getGlobalVisibleRect(menuRect);
            if (menuRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                return super.dispatchTouchEvent(event);
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            requestOriginalChannelMenu("touch");
            return true;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (InputAction.fromKeyCode(keyCode) == InputAction.BACK) {
            if (customView != null) {
                if (isGdtvWebPlayer()) {
                    Log.i(TAG, "onKeyDown GDTV back in customView, show back prompt");
                    showBackPrompt();
                } else {
                    Log.i(TAG, "onKeyDown back in customView, hide fullscreen");
                    hideCustomView();
                }
                return true;
            }
            handleBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isWebPlayerHandledAction(InputAction action) {
        return action == InputAction.CONFIRM
                || action == InputAction.BACK
                || action == InputAction.OPEN_MANAGEMENT
                || action == InputAction.SOURCE_PREV
                || action == InputAction.SOURCE_NEXT
                || action == InputAction.CHANNEL_UP
                || action == InputAction.CHANNEL_DOWN;
    }

    @Override
    protected void onDestroy() {
        if (customView != null) {
            hideCustomView();
        }
        if (webView != null) {
            root.removeView(webView);
            webView.stopLoading();
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}

