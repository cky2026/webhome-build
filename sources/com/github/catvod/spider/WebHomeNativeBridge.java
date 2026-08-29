package com.github.catvod.spider;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

public class WebHomeNativeBridge {

    private static final String TAG = "WebHome";
    private final Overlay overlay;
    private final Handler main = new Handler(Looper.getMainLooper());

    public WebHomeNativeBridge(Overlay overlay) {
        this.overlay = overlay;
    }

    @JavascriptInterface
    public void push(String json) {
        handle(json);
    }

    @JavascriptInterface
    public void vodInline(String json) {
        handle(json);
    }

    @JavascriptInterface
    public void play(String json) {
        handle(json);
    }

    @JavascriptInterface
    public void toast(final String msg) {
        final Activity activity = overlay == null ? null : overlay.getActivity();
        if (activity == null) return;
        toast(activity, msg);
    }

    private void handle(String json) {
        try {
            JSONObject vod = new JSONObject(json);
            CspWebHomePlayback.register(vod, null, null, "");
            Activity activity = overlay == null ? null : overlay.getActivity();
            launchVideo(vod, activity);
        } catch (Throwable e) {
            Log.w(TAG, "push/vodInline error: " + e);
        }
    }

    private void launchVideo(final JSONObject vod, final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        final Class<?> videoCls;
        try {
            videoCls = Class.forName("com.fongmi.android.tv.ui.activity.VideoActivity");
        } catch (Throwable ignored) {
            toast(activity, "已收到视频，请关闭网页，在壳的「网页点播」分类中查看播放");
            return;
        }

        main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(activity, videoCls);
                    intent.putExtra("key", "webhome");
                    intent.putExtra("id", vod.optString("id", "0"));
                    intent.putExtra("name", vod.optString("name", vod.optString("vod_name", "")));
                    intent.putExtra("pic", vod.optString("pic", vod.optString("vod_pic", "")));
                    intent.putExtra("wallPic", vod.optString("wallPic", ""));
                    intent.putExtra("content", vod.optString("content", ""));
                    activity.startActivity(intent);
                } catch (Throwable t) {
                    Log.w(TAG, "start VideoActivity failed: " + t);
                }
            }
        });
    }

    private void toast(final Activity activity, final String msg) {
        main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
