package com.github.catvod.spider;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

public class WebHomeNativeBridge {

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
            "  search:function(key){return B.search(key);};" +
            "  play:function(url,name){B.play(url,name||'');}," +
            "  toast:function(msg){B.toast(String(msg==null?'':msg));}," +
            "  close:function(){B.close();}," +
            "  setCookie:function(url,cookie){B.setCookie(url,cookie);}," +
            "  getCookie:function(url){return B.getCookie(url);}," +
            "  config:function(key){return B.config(key);}" +
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

    private final Activity host;
    private final WebView webView;
    private final String baseUrl;

    public WebHomeNativeBridge(Activity host, WebView webView, String baseUrl) {
        this.host = host;
        this.webView = webView;
        this.baseUrl = baseUrl;
    }

    public static String getInjectJs() {
        return INJECT_JS;
    }

    @JavascriptInterface
    public String http(String url, String method, String headersJson, String body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            String m = (method == null || method.length() == 0) ? "GET" : method.toUpperCase();
            conn.setRequestMethod(m);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");
            try {
                JSONObject hs = new JSONObject(headersJson == null ? "{}" : headersJson);
                Iterator<String> it = hs.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if ("host".equalsIgnoreCase(k) || "content-length".equalsIgnoreCase(k)) continue;
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
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (in != null) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
            }
            return code + "\u0001" + android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Throwable t) {
            return "0\u0001" + android.util.Base64.encodeToString(
                    ("{\"error\":\"" + String.valueOf(t.getMessage()).replace("\"", "'") + "\"}").getBytes(),
                    android.util.Base64.NO_WRAP);
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) { }
        }
    }

    @JavascriptInterface
    public String fetch(String url, String method, String headersJson, String body) {
        return http(url, method, headersJson, body);
    }

    @JavascriptInterface
    public void play(final String url, final String name) {
        (new Handler(Looper.getMainLooper())).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent it = new Intent(Intent.ACTION_VIEW);
                    it.setDataAndType(Uri.parse(url), "video/*");
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    host.startActivity(it);
                } catch (Throwable t) {
                    try { Toast.makeText(host, "播放失败: " + t.getMessage(), Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
                }
            }
        });
    }

    @JavascriptInterface
    public void toast(final String msg) {
        (new Handler(Looper.getMainLooper())).post(new Runnable() {
            @Override
            public void run() {
                try { Toast.makeText(host, msg, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { }
            }
        });
    }

    @JavascriptInterface
    public void close() {
        (new Handler(Looper.getMainLooper())).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> cls = Class.forName("com.github.catvod.spider.WebHome");
                    cls.getMethod("close").invoke(null);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @JavascriptInterface
    public String version() {
        return "1.0";
    }

    @JavascriptInterface
    public String search(String key) {
        return "";
    }

    @JavascriptInterface
    public void setCookie(final String url, final String cookie) {
        (new Handler(Looper.getMainLooper())).post(new Runnable() {
            @Override
            public void run() {
                try {
                    android.webkit.CookieManager.getInstance().setCookie(url, cookie);
                } catch (Throwable ignored) { }
            }
        });
    }

    @JavascriptInterface
    public String getCookie(final String url) {
        try {
            return android.webkit.CookieManager.getInstance().getCookie(url);
        } catch (Throwable ignored) {
            return "";
        }
    }

    @JavascriptInterface
    public String config(String key) {
        return "";
    }
}
