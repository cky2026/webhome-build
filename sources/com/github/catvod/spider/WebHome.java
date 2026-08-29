package com.github.catvod.spider;

import android.app.Activity;
import android.content.Context;

import com.github.catvod.crawler.Spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WebHome extends Spider {

    private static final String OPEN_ID = "open_web";
    private String siteUrl = "https://www.baidu.com";

    @Override
    public void init(Context context, String extend) {
        if (extend == null) return;
        String ext = extend.trim();
        try {
            if (ext.startsWith("{")) {
                siteUrl = new JSONObject(ext).optString("site", siteUrl);
            } else if (ext.startsWith("http")) {
                siteUrl = ext;
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject cls = new JSONObject().put("type_id", "webhome").put("type_name", "🌐 网页点播");
            JSONObject root = new JSONObject().put("class", new JSONArray().put(cls));

            JSONArray list = new JSONArray();
            Set<String> keys = CspWebHomePlayback.keys();
            for (String key : keys) {
                JSONObject v = CspWebHomePlayback.get(key);
                if (v == null) continue;
                JSONObject item = new JSONObject();
                item.put("vod_id", key);
                item.put("vod_name", v.optString("name", v.optString("vod_name", "网页视频")));
                item.put("vod_pic", v.optString("pic", v.optString("vod_pic", "")));
                item.put("vod_remarks", "网页");
                list.put(item);
            }

            JSONObject entry = new JSONObject();
            entry.put("vod_id", OPEN_ID);
            entry.put("vod_name", "打开网页");
            entry.put("vod_pic", "");
            entry.put("vod_remarks", "点击进入");
            list.put(entry);

            root.put("list", list);
            return root.toString();
        } catch (Throwable e) {
            return "{\"class\":[],\"list\":[]}";
        }
    }

    @Override
    public String homeVideoContent() {
        // 返回空，避免壳反复追加导致条目重复；入口条目放在 homeContent 的 list 里
        return "";
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        return homeContent(false);
    }

    @Override
    public String detailContent(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "{}";
        String id = ids.get(0);
        if (OPEN_ID.equals(id)) {
            showOverlay();
            return "{}";
        }
        return CspWebHomePlayback.detail(id);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return "{\"class\":[],\"list\":[]}";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        // 兜底：如果壳把入口条目当成播放处理，播放前先把网页弹出来
        if (id != null && (OPEN_ID.equals(id) || OPEN_ID.equals(flag))) {
            showOverlay();
            return "{\"parse\":0,\"playUrl\":\"\",\"url\":\"\",\"jx\":\"\"}";
        }
        if (id != null && (id.startsWith("http://") || id.startsWith("https://")
                || (!id.isEmpty() && id.trim().charAt(0) == '{'))) {
            try {
                JSONObject vod = id.trim().charAt(0) == '{'
                        ? new JSONObject(id.trim())
                        : new JSONObject().put("url", id);
                if (vod.optString("url", "").isEmpty()) {
                    vod.put("url", vod.optString("playUrl", id));
                }
                String key = CspWebHomePlayback.register(vod, null, null, "");
                return CspWebHomePlayback.player(key + "@@0");
            } catch (Throwable e) {
                try {
                    return "{\"parse\":0,\"url\":\"" + id + "\"}";
                } catch (Throwable ignored) {
                }
            }
        }
        return CspWebHomePlayback.player(id);
    }

    @Override
    public void destroy() {
        Overlay.closeAll();
    }

    private void showOverlay() {
        Activity act = currentActivity();
        if (act == null) return;
        String url = siteUrl;
        if (url.startsWith("./")) url = url.substring(2);
        if (url.endsWith(".html") || url.endsWith(".htm")) {
            if (!url.startsWith("file://") && !url.startsWith("http")) {
                url = "file://" + (url.startsWith("/") ? url : "/" + url);
            }
        }
        final Activity activity = act;
        final String openUrl = url;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Overlay.show(activity, openUrl);
            }
        });
    }

    private static Activity currentActivity() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Object activities = f.get(thread);
            if (activities instanceof Map) {
                for (Object r : ((Map<?, ?>) activities).values()) {
                    if (r == null) continue;
                    Method m = r.getClass().getMethod("getActivity");
                    Object a = m.invoke(r);
                    if (a instanceof Activity) {
                        Activity act = (Activity) a;
                        if (!act.isFinishing() && !act.isDestroyed()) return act;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
