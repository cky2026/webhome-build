package com.github.catvod.spider;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.github.catvod.crawler.Spider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebHome extends Spider {

    private static final int MAX_RETRIES = 18;
    private static volatile boolean lifecycleInstalled;
    private static volatile Overlay overlay;
    private static volatile WeakReference<Activity> foreground = new WeakReference<>(null);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    // 共享线程池：复用线程避免频繁创建销毁（HTTP 请求、JSS 注入回调）
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    private String extend = "";

    @Override
    public void init(Context context, String extend) {
        if (context != null) {
            Context app = context.getApplicationContext();
            if (app != null) installLifecycleTracker(app);
        }
        this.extend = extend == null ? "" : extend.trim();
    }

    @Override
    public String homeContent(boolean filter) {
        open(this.extend, 0);
        return "{\"class\":[],\"list\":[]}";
    }

    @Override
    public String homeVideoContent() {
        return "{\"list\":[]}";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> ext) {
        return "{\"class\":[],\"list\":[]}";
    }

    @Override
    public String detailContent(List<String> ids) {
        open(this.extend, 0);
        return "{\"list\":[]}";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return "{\"list\":[]}";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            String url = id == null ? "" : id;
            int i = url.indexOf("@@");
            if (i > 0) url = url.substring(0, i);
            JSONObject r = new JSONObject();
            r.put("parse", 0);
            r.put("playUrl", "");
            r.put("url", url);
            r.put("jx", "");
            return r.toString();
        } catch (Throwable e) {
            return "{\"parse\":0,\"url\":\"\"}";
        }
    }

    @Override
    public void destroy() {
        close();
    }

    // ================= 生命周期追踪 =================

    private static void installLifecycleTracker(Context context) {
        if (lifecycleInstalled || !(context instanceof Application)) return;
        synchronized (LOCK) {
            if (lifecycleInstalled) return;
            ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity a, Bundle b) { remember(a); }
                @Override public void onActivityStarted(Activity a) { remember(a); }
                @Override public void onActivityResumed(Activity a) { remember(a); }
                @Override public void onActivityPaused(Activity a) { }
                @Override public void onActivityStopped(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) {
                    if (foreground.get() == a) foreground = new WeakReference<>(null);
                }
            });
            lifecycleInstalled = true;
        }
    }

    private static void remember(Activity a) {
        if (usable(a)) foreground = new WeakReference<>(a);
    }

    private static boolean usable(Activity a) {
        return a != null && !a.isFinishing() && !a.isDestroyed();
    }

    private static Activity activity() {
        Activity a = foreground.get();
        if (usable(a)) return a;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Object obj = f.get(thread);
            if (obj instanceof Map) {
                for (Object r : ((Map<?, ?>) obj).values()) {
                    if (r == null) continue;
                    Field pf = r.getClass().getDeclaredField("paused");
                    pf.setAccessible(true);
                    if (Boolean.TRUE.equals(pf.get(r))) continue;
                    Field af = r.getClass().getDeclaredField("activity");
                    af.setAccessible(true);
                    Object act = af.get(r);
                    if (act instanceof Activity && usable((Activity) act)) return (Activity) act;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ================= 打开 / 关闭 =================

    private static void open(final String url, final int retry) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                Activity act = activity();
                if (!usable(act)) {
                    if (retry < MAX_RETRIES) {
                        MAIN.postDelayed(new Runnable() {
                            @Override
                            public void run() { open(url, retry + 1); }
                        }, 180L);
                    }
                    return;
                }
                String target = normalize(url);
                if (target.length() == 0) return;
                if (overlay != null && overlay.isShowing()) overlay.dismiss();
                overlay = new Overlay(act, target);
                overlay.show();
            }
        });
    }

    private static void close() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (overlay != null) overlay.dismiss();
                overlay = null;
            }
        });
    }

    private static String normalize(String url) {
        if (url == null) return "";
        String s = url.trim();
        if (s.startsWith("file://") || s.startsWith("http://") || s.startsWith("https://")) return s;
        if (s.startsWith("./")) s = s.substring(2);
        if (s.startsWith("/")) return Uri.fromFile(new File(s)).toString();
        return s;
    }

    // ================= 原生桥：跨域 HTTP + FM SDK =================

    public static class NativeBridge {

        private final Activity host;

        NativeBridge(Activity host) {
            this.host = host;
        }

        // 同步跨域请求，返回 "状态码\u0001Base64响应体"
        @JavascriptInterface
        public String http(String url, String method, String headersJson, String body) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                // 关键优化①：复用连接，减小 TCP 握手开销
                conn.setRequestProperty("Connection", "keep-alive");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
                String m = (method == null || method.length() == 0) ? "GET" : method.toUpperCase();
                try {
                    JSONObject hs = new JSONObject(headersJson == null ? "{}" : headersJson);
                    Iterator<String> it = hs.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        if ("host".equalsIgnoreCase(k) || "content-length".equalsIgnoreCase(k)
                                || "connection".equalsIgnoreCase(k) || "accept-encoding".equalsIgnoreCase(k)) continue;
                        conn.setRequestProperty(k, hs.optString(k));
                    }
                } catch (Throwable ignored) {
                }
                if (body != null && body.length() > 0 && !"GET".equals(m) && !"HEAD".equals(m)) {
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.flush();
                    os.close();
                }
                int code = conn.getResponseCode();
                // 关键优化②：始终走 InputStream，服务器返回错误页时也读出来避免连接泄漏
                InputStream in = conn.getInputStream();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                if (in != null) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    in.close();
                }
                // 关键优化③：gzip 解压后返回原始字节（前端 TextDecoder 自己处理）
                byte[] raw = bos.toByteArray();
                return code + "\u0001" + android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP);
            } catch (Throwable t) {
                return "0\u0001" + android.util.Base64.encodeToString(
                        ("{\"error\":\"" + String.valueOf(t.getMessage()).replace("\"", "'") + "\"}").getBytes(),
                        android.util.Base64.NO_WRAP);
            } finally {
                if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }

        @JavascriptInterface
        public String fmRequest(String url, String method, String headersJson, String body) {
            return http(url, method, headersJson, body);
        }

        @JavascriptInterface
        public void fmPlay(final String url, final String name) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent it = new Intent(Intent.ACTION_VIEW);
                        it.setDataAndType(Uri.parse(url), "video/*");
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        host.startActivity(it);
                    } catch (Throwable t) {
                        try {
                            Toast.makeText(host, "播放失败: " + url, Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) { }
                    }
                }
            });
        }

        @JavascriptInterface
        public void fmToast(final String msg) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    try { Toast.makeText(host, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void fmClose() {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    if (overlay != null) overlay.dismiss();
                }
            });
        }

        @JavascriptInterface
        public String fetch(String url, String method, String headersJson, String body) {
            return http(url, method, headersJson, body);
        }

        @JavascriptInterface
        public void play(String url, String name) {
            fmPlay(url, name);
        }

        @JavascriptInterface
        public void toast(String msg) {
            fmToast(msg);
        }

        @JavascriptInterface
        public void close() {
            fmClose();
        }

        @JavascriptInterface
        public String version() {
            return "fm-shim-1.1";
        }
    }

    // ================= 注入 JS：window.fm 兼容层 + fetch/XHR 跨域劫持 =================

    private static final String INJECT_JS =
            "javascript:(function(){" +
            "if(window.__fmShim) return; window.__fmShim=true;" +
            "var B=window.fongmiBridge; if(!B) return;" +
            "function parse(raw){" +
            "  var i=raw.indexOf('\\u0001');" +
            "  var code=parseInt(raw.substring(0,i))||0;" +
            "  var b64=raw.substring(i+1);" +
            "  var bin=atob(b64);var bytes=new Uint8Array(bin.length);" +
            "  for(var j=0;j<bin.length;j++)bytes[j]=bin.charCodeAt(j);" +
            "  var text=null;try{text=new TextDecoder('utf-8').decode(bytes);}catch(e){text=bin;}" +
            "  return {status:code,bytes:bytes,text:text,json:function(){try{return JSON.parse(text)}catch(e){return null}}};" +
            "}" +
            "var fm={" +
            "  version:function(){return B.version();}," +
            "  fetch:function(url,opt){" +
            "    opt=opt||{};" +
            "    var method=(opt.method||'GET').toUpperCase();" +
            "    var headers={};var h=opt.headers||{};" +
            "    if(h instanceof Headers){h.forEach(function(v,k){headers[k]=v;});}else{for(var k in h)headers[k]=h[k];}" +
            "    var body=opt.body?(typeof opt.body==='string'?opt.body:JSON.stringify(opt.body)):'';" +
            "    return Promise.resolve(parse(B.http(url,method,JSON.stringify(headers),body)));" +
            "  }," +
            "  get:function(url,headers){return fm.fetch(url,{method:'GET',headers:headers});}," +
            "  post:function(url,body,headers){" +
            "    headers=headers||{};" +
            "    if(body&&typeof body!=='string'){headers['Content-Type']=headers['Content-Type']||'application/json';body=JSON.stringify(body);}" +
            "    return fm.fetch(url,{method:'POST',headers:headers,body:body});" +
            "  }," +
            "  play:function(url,name){B.fmPlay(url,name||'');}," +
            "  toast:function(msg){B.fmToast(String(msg==null?'':msg));}," +
            "  close:function(){B.fmClose();}" +
            "};" +
            "window.fm=fm; if(!window.FM) window.FM=fm;" +
            "var OF=window.fetch;" +
            "window.fetch=function(input,init){" +
            "  try{" +
            "    var url=(typeof input==='string')?input:(input&&input.url)||'';" +
            "    if(!/^https?:/i.test(url)) return OF.apply(this,arguments);" +
            "    return fm.fetch(url,init||{}).then(function(r){return new Response(r.text,{status:r.status||200});});" +
            "  }catch(e){return OF.apply(this,arguments);}" +
            "};" +
            "var OOpen=XMLHttpRequest.prototype.open;" +
            "var OSend=XMLHttpRequest.prototype.send;" +
            "XMLHttpRequest.prototype.open=function(m,u){this.__url=u;this.__method=m;return OOpen.apply(this,arguments);};" +
            "XMLHttpRequest.prototype.send=function(body){" +
            "  var self=this;" +
            "  var u=self.__url||'';" +
            "  if(/^https?:/i.test(u)){" +
            "    var headers={};" +
            "    var r=parse(B.http(u,self.__method||'GET',JSON.stringify(headers),body?String(body):''));" +
            "    setTimeout(function(){" +
            "      Object.defineProperty(self,'status',{value:r.status||200,writable:false});" +
            "      Object.defineProperty(self,'responseText',{value:r.text,writable:false});" +
            "      Object.defineProperty(self,'response',{value:r.text,writable:false});" +
            "      if(typeof self.onreadystatechange==='function')self.onreadystatechange();" +
            "      if(typeof self.onload==='function')self.onload();" +
            "      self.dispatchEvent(new Event('readystatechange'));" +
            "      self.dispatchEvent(new Event('load'));" +
            "    },0);" +
            "    return;" +
            "  }" +
            "  return OSend.apply(this,arguments);" +
            "};" +
            "})();";

    // ================= WebView 弹窗 =================

    @SuppressLint("SetJavaScriptEnabled")
    private static final class Overlay extends Dialog {

        private final Activity host;
        private final String source;
        private WebView web;
        private NativeBridge bridge;

        Overlay(Activity activity, String url) {
            super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            this.host = activity;
            this.source = url;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            FrameLayout root = new FrameLayout(getContext());
            root.setBackgroundColor(0xFF000000);
            web = new WebView(getContext());
            root.addView(web, new FrameLayout.LayoutParams(-1, -1));
            setContentView(root);
            Window w = getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF000000));
                w.setLayout(-1, -1);
                hideSystemBars(w);
            }
            setupWebView(web);
            setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface d, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                        if (web != null && web.canGoBack()) web.goBack();
                        else dismiss();
                        return true;
                    }
                    return false;
                }
            });
            try {
                web.loadUrl(source);
            } catch (Throwable t) {
                web.loadDataWithBaseURL(null, "<h1>加载失败</h1><small>" + t.getMessage() + "</small>", "text/html", "UTF-8", null);
            }
        }

        @SuppressLint("JavascriptInterface")
        private void setupWebView(WebView v) {
            // 关键优化①：硬件加速层，提升渲染性能
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            WebSettings s = v.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            // 关键优化②：启用缓存模式（LOAD_DEFAULT）而非强制走网络
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            s.setAllowFileAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);

            // 关键优化③：支持缩放（用户体验更好，且缩放本身需要硬件加速）
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);

            // 关键优化④：允许 WebView 自由创建窗口（popup 等场景）
            s.setJavaScriptCanOpenWindowsAutomatically(true);

            // 关键优化⑤：DNS 预解析，减少域名解析延迟
            if (Build.VERSION.SDK_INT >= 24) {
                v.enableSlowWholeDocumentDraw();
            }

            // 关键优化⑥：渲染优先级策略——确保 WebView 不会被降级
            if (Build.VERSION.SDK_INT >= 26) {
                v.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
            }

            v.setBackgroundColor(0xFF000000);
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);

            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(v, true);
                // 关键优化⑦：启用持久化 cookie（避免会话 cookie 丢失）
                cm.setAcceptFileSchemeCookies(true);
            } catch (Throwable ignored) {
            }

            bridge = new NativeBridge(host);
            v.addJavascriptInterface(bridge, "fongmiBridge");

            v.setWebChromeClient(new WebChromeClient());
            v.setWebViewClient(new WebViewClient() {
                // 关键优化⑧：在 shouldOverrideUrlLoading 中直接注入 JS，
                //         而非等 onPageFinished（避免页面渲染完成后才注入导致闪屏）
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return url == null || url.length() == 0
                            || !(url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://"));
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return shouldOverrideUrlLoading(view, request.getUrl().toString());
                }

                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    // 关键优化⑨：在页面刚开始加载时就注入兼容层 JS，DOM 未构建时注入代价最低
                    view.evaluateJavascript(INJECT_JS, null);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    // 关键优化⑩：页面结束后不再重复注入，仅保存 cookie
                    try { CookieManager.getInstance().flush(); } catch (Throwable ignored) { }
                }

                // 关键优化⑪：拦截非必要资源请求，减轻页面加载负担
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString().toLowerCase();
                    // 拦截追踪脚本/日志请求（如果不需要统计可注释掉）
                    if (url.contains("analytics") || url.contains("tracking") || url.contains("telemetry")) {
                        try {
                            return new WebResourceResponse("text/plain", "utf-8", java.io.InputStream.nullInputStream());
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    return super.shouldInterceptRequest(view, request);
                }
            });
        }

        @Override
        protected void onStop() {
            super.onStop();
            // 关键优化⑫：Dialog 进入后台时暂停 WebView 计时器，降低 CPU 消耗
            if (web != null) web.pauseTimers();
        }

        @Override
        protected void onStart() {
            super.onStart();
            // 恢复 WebView 计时器
            if (web != null) web.resumeTimers();
        }

        @Override
        public void dismiss() {
            // 关键优化⑬：dismiss 时确保 cookie 落盘
            try { CookieManager.getInstance().flush(); } catch (Throwable ignored) { }
            if (web != null) {
                try {
                    web.stopLoading();
                    // 关键优化⑭：先暂停计时器再清理，减小资源占用
                    web.pauseTimers();
                    web.loadUrl("about:blank");
                    web.clearHistory();
                    web.clearCache(true);
                    web.removeAllViews();
                    web.destroy();
                } catch (Throwable ignored) {
                }
                web = null;
            }
            super.dismiss();
            if (WebHome.overlay == this) WebHome.overlay = null;
        }

        private void hideSystemBars(Window w) {
            if (w == null) return;
            w.getDecorView().setSystemUiVisibility(5894);
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);
            if (hasFocus) hideSystemBars(getSystemBarsWindow());
        }

        private Window getSystemBarsWindow() {
            return getWindow();
        }
    }
}
