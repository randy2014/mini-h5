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

    @GetMapping
    public void proxy(@RequestParam("url") String url, HttpServletResponse response) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            response.setStatus(400);
            return;
        }

        try {
            URL targetUrl = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);

            conn.setRequestProperty("Referer", "https://www.23qb.net/");
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