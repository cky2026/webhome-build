package com.github.catvod.spider;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.github.catvod.crawler.Spider;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebHome extends Spider {

    private static final int MAX_RETRIES = 18;
    private static volatile Context appContext;
    private static volatile boolean lifecycleInstalled;
    private static volatile Overlay overlay;
    private static volatile WeakReference<Activity> foreground = new WeakReference<>(null);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private String extend = "";

    @Override
    public void init(Context context, String extend) {
        if (context != null) {
            Context app = context.getApplicationContext();
            appContext = app != null ? app : context;
            installLifecycleTracker(appContext);
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
        return "{\"parse\":0,\"playUrl\":\"\",\"url\":\"\",\"jx\":\"\"}";
    }

    @Override
    public void destroy() {
        close();
    }

    // ---------- 生命周期追踪 ----------

    private static void installLifecycleTracker(Context context) {
        if (lifecycleInstalled || !(context instanceof Application)) return;
        synchronized (LOCK) {
            if (lifecycleInstalled) return;
            ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity a, Bundle b) {
                    remember(a);
                }

                @Override
                public void onActivityStarted(Activity a) {
                    remember(a);
                }

                @Override
                public void onActivityResumed(Activity a) {
                    remember(a);
                }

                @Override
                public void onActivityPaused(Activity a) {
                }

                @Override
                public void onActivityStopped(Activity a) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity a, Bundle b) {
                }

                @Override
                public void onActivityDestroyed(Activity a) {
                    if (foreground.get() == a) foreground = new WeakReference<>(null);
                }
            });
            lifecycleInstalled = true;
        }
    }

    private static void remember(Activity activity) {
        if (usable(activity)) foreground = new WeakReference<>(activity);
    }

    private static boolean usable(Activity a) {
        return a != null && !a.isFinishing() && !a.isDestroyed();
    }

    private static Activity activity() {
        Activity a = foreground.get();
        if (usable(a)) return a;
        return findActivityReflectively();
    }

    private static Activity findActivityReflectively() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Object obj = f.get(thread);
            if (!(obj instanceof Map)) return null;
            for (Object r : ((Map<?, ?>) obj).values()) {
                if (r == null) continue;
                Field paused = r.getClass().getDeclaredField("paused");
                paused.setAccessible(true);
                if (Boolean.TRUE.equals(paused.get(r))) continue;
                Field af = r.getClass().getDeclaredField("activity");
                af.setAccessible(true);
                Object a = af.get(r);
                if (a instanceof Activity && usable((Activity) a)) return (Activity) a;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ---------- 打开 / 关闭网页（带重试） ----------

    private static void open(final String url, final int retry) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                Activity act = activity();
                if (!usable(act)) {
                    if (retry < MAX_RETRIES) {
                        MAIN.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                open(url, retry + 1);
                            }
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

    // ---------- 内置 WebView 全屏弹窗 ----------

    private static final class Overlay extends Dialog {

        private final Activity host;
        private final String source;
        private WebView web;

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
                w.setBackgroundDrawable(new ColorDrawable(0xFF000000));
                w.setLayout(-1, -1);
                hideSystemBars(w);
            }
            setupWebView(web);
            try {
                web.loadUrl(source);
            } catch (Throwable t) {
                web.loadDataWithBaseURL(null, message("加载失败", String.valueOf(t.getMessage())), "text/html", "UTF-8", null);
            }
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
        }

        @Override
        public void onBackPressed() {
            if (web != null && web.canGoBack()) web.goBack();
            else dismiss();
        }

        private void setupWebView(WebView v) {
            v.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
            WebSettings s = v.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            s.setSupportZoom(false);
            s.setBuiltInZoomControls(false);
            s.setTextZoom(100);
            s.setCacheMode(-1);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
            if (Build.VERSION.SDK_INT >= 26) v.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
            v.setBackgroundColor(0xFF000000);
            v.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(v, true);
            } catch (Throwable ignored) {
            }
            v.setWebChromeClient(new WebChromeClient());
            v.setWebViewClient(new WebViewClient() {
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
                public void onPageFinished(WebView view, String url) {
                    try {
                        CookieManager.getInstance().flush();
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        @Override
        public void dismiss() {
            try {
                CookieManager.getInstance().flush();
            } catch (Throwable ignored) {
            }
            if (web != null) {
                try {
                    web.stopLoading();
                    web.loadUrl("about:blank");
                    web.clearHistory();
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
            if (hasFocus) hideSystemBars(getWindow());
        }

        private String message(String title, String detail) {
            return "<!doctype html><html><head><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<style>body{margin:0;padding:28px;background:#050505;color:#fff;font:16px sans-serif}small{opacity:.65;word-break:break-all}</style></head>"
                    + "<body><h1>" + esc(title) + "</h1><small>" + esc(detail) + "</small></body></html>";
        }

        private String esc(String s) {
            return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }
}
