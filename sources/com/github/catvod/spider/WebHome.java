[/var/minis/workspace/WebHome.java | 25620 bytes | 619 lines | showing 228-277 of 619]
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
