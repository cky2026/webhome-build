package com.github.catvod.spider;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

public class Overlay {

    private static Overlay instance;

    private Activity activity;
    private WebView webView;
    private FrameLayout container;

    public static synchronized Overlay show(Activity activity, String url) {
        try {
            if (instance != null) instance.close();
            instance = new Overlay();
            instance.attach(activity, url);
        } catch (Throwable ignored) {
        }
        return instance;
    }

    public static synchronized Overlay get() {
        return instance;
    }

    public static synchronized void closeAll() {
        if (instance != null) instance.close();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void attach(Activity activity, String url) {
        this.activity = activity;

        FrameLayout root = (FrameLayout) activity.getWindow().getDecorView();
        container = new FrameLayout(activity);
        container.setBackgroundColor(Color.BLACK);
        root.addView(container, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(activity);
        WebSettings st = webView.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setAllowFileAccess(true);
        st.setAllowFileAccessFromFileURLs(true);
        st.setAllowUniversalAccessFromFileURLs(true);
        st.setUseWideViewPort(true);
        st.setLoadWithOverviewMode(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new WebHomeNativeBridge(this), "fm");
        container.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.loadUrl(url);

        TextView close = new TextView(activity);
        close.setText("✕ 关闭网页并返回");
        close.setTextColor(Color.WHITE);
        close.setTextSize(16);
        close.setPadding(30, 20, 30, 20);
        close.setBackgroundColor(0xAA000000);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Overlay.closeAll();
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        container.addView(close, lp);
    }

    public Activity getActivity() {
        return activity;
    }

    public WebView getWebView() {
        return webView;
    }

    public void close() {
        try {
            if (webView != null) {
                webView.removeJavascriptInterface("fm");
                webView.loadUrl("about:blank");
                webView.destroy();
            }
            if (container != null && container.getParent() instanceof ViewGroup) {
                ((ViewGroup) container.getParent()).removeView(container);
            }
        } catch (Throwable ignored) {
        }
        webView = null;
        container = null;
        activity = null;
        instance = null;
    }
}
