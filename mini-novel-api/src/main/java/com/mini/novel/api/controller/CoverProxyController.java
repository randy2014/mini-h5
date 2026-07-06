package com.mini.novel.api.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cover")
public class CoverProxyController {

    // 23qb.net 的真实服务器 IP（Cloudflare CDN），因 DNS 被劫持需要直连
    private static final String TWENTY_THREE_QB_HOST = "www.23qb.net";
    private static final String TWENTY_THREE_QB_IP = "104.21.70.191";

    @GetMapping
    public void proxy(@RequestParam("url") String url, HttpServletResponse response) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            response.setStatus(400);
            return;
        }

        try {
            // DNS 被劫持时，替换域名为真实 IP 并保留 Host 头
            String resolvedUrl = url;
            if (url.contains(TWENTY_THREE_QB_HOST)) {
                resolvedUrl = url.replace(TWENTY_THREE_QB_HOST, TWENTY_THREE_QB_IP);
            }

            URL targetUrl = URI.create(resolvedUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);

            conn.setRequestProperty("Host", TWENTY_THREE_QB_HOST);
            conn.setRequestProperty("Referer", "https://" + TWENTY_THREE_QB_HOST + "/");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int statusCode = conn.getResponseCode();
            if (statusCode != 200) {
                sendFallbackPixel(response);
                return;
            }

            String contentType = conn.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                response.setContentType(contentType);
            } else {
                response.setContentType("image/jpeg");
            }

            response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=604800");

            try (InputStream in = conn.getInputStream()) {
                in.transferTo(response.getOutputStream());
                response.getOutputStream().flush();
            }
        } catch (Exception e) {
            sendFallbackPixel(response);
        }
    }

    private void sendFallbackPixel(HttpServletResponse response) {
        response.setContentType("image/gif");
        try {
            byte[] pixel = { 71, 73, 70, 56, 57, 97, 1, 0, 1, 0, -128, 0, 0, -1, -1, -1, 33, -7, 4, 0, 0, 0, 0, 0, 44, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 68, 1, 0, 59 };
            response.getOutputStream().write(pixel);
            response.getOutputStream().flush();
        } catch (Exception ignored) {}
    }
}
