package com.github.catvod.spider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CspWebHomePlayback {

    private static final ConcurrentHashMap<String, JSONObject> STORE = new ConcurrentHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    public static String register(JSONObject vod, Object a, Object b, Object c) {
        String key = "wh" + SEQ.getAndIncrement();
        STORE.put(key, vod);
        return key;
    }

    public static JSONObject get(String key) {
        return STORE.get(key);
    }

    public static Set<String> keys() {
        return STORE.keySet();
    }

    public static String player(String id) {
        try {
            String key = id.contains("@@") ? id.split("@@")[0] : id;
            JSONObject vod = STORE.get(key);
            String url = (vod == null) ? id : vod.optString("url", id);
            JSONObject out = new JSONObject();
            out.put("parse", 0);
            out.put("playUrl", "");
            out.put("url", url);
            out.put("flag", "webhome");
            JSONObject header = (vod == null) ? null : vod.optJSONObject("header");
            if (header == null && vod != null) header = vod.optJSONObject("headers");
            if (header != null) out.put("header", header);
            return out.toString();
        } catch (Throwable e) {
            return "{\"parse\":0,\"url\":\"" + (id == null ? "" : id) + "\"}";
        }
    }

    public static String detail(String key) {
        try {
            JSONObject vod = STORE.get(key);
            if (vod == null) return "{}";
            String name = vod.optString("name", vod.optString("vod_name", "网页视频"));
            String pic = vod.optString("pic", vod.optString("vod_pic", ""));
            JSONObject item = new JSONObject();
            item.put("vod_id", key);
            item.put("vod_name", name);
            item.put("vod_pic", pic);
            item.put("type_name", "WebHome");
            item.put("vod_play_from", "WebHome");
            item.put("vod_play_url", "播放$" + key + "@@0");
            item.put("vod_content", "由 webhome 扩展提供");
            JSONObject root = new JSONObject();
            root.put("list", new JSONArray().put(item));
            return root.toString();
        } catch (Throwable e) {
            return "{}";
        }
    }

    public static List<JSONObject> all() {
        return new ArrayList<>(STORE.values());
    }
}
