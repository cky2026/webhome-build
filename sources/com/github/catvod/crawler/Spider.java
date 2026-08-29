package com.github.catvod.crawler;

import android.content.Context;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Spider {
    public void init(Context context, String extend) {}
    public String homeContent(boolean filter) { return ""; }
    public String homeVideoContent() { return ""; }
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) { return ""; }
    public String detailContent(List<String> ids) { return ""; }
    public String searchContent(String key, boolean quick) { return ""; }
    public String searchContent(String key, boolean quick, String pg) { return searchContent(key, quick); }
    public String playerContent(String flag, String id, List<String> vipFlags) { return ""; }
    public Object proxy(Map<String, String> params) { return null; }
    public void destroy() {}
}
