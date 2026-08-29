package com.github.catvod.spider;

import java.util.HashMap;
import java.util.Map;

public class Proxy {

    public static void init(Context context) {
    }

    public static Object proxy(Map<String, String> params) {
        // 本 jar 不需要本地代理服务，返回 null 让壳跳过
        return null;
    }

    public static String proxyUrl() {
        return "";
    }
}
