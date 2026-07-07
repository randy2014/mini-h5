package com.mini.novel.api.support;

import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.NovelMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class CoverService {

    private static final String FALLBACK_SVG = "<svg xmlns='http://www.w3.org/2000/svg' width='150' height='200' viewBox='0 0 150 200'>" +
        "<rect width='150' height='200' fill='#e2e8f0'/><text x='75' y='100' text-anchor='middle' fill='#94a3b8' font-size='14' font-family='sans-serif'>\u6682\u65e0\u5c01\u9762</text></svg>";

    private final NovelMapper novelMapper;
    private final Path cacheDir;

    public CoverService(NovelMapper novelMapper) {
        this.novelMapper = novelMapper;
        String dir = System.getenv().getOrDefault("COVER_CACHE_DIR", "/var/cache/covers");
        this.cacheDir = Path.of(dir);
        try {
            Files.createDirectories(this.cacheDir);
        } catch (IOException ignored) {
        }
    }

    public void streamCover(Long novelId, HttpServletResponse response) {
        Path cached = cacheDir.resolve(novelId + ".img");

        if (Files.exists(cached) && Files.size(cached) > 0) {
            serveFile(cached, response);
            return;
        }

        Novel novel = novelMapper.selectById(novelId);
        if (novel == null || novel.getCoverUrl() == null || novel.getCoverUrl().isBlank()) {
            sendFallbackPixel(response);
            return;
        }

        String coverUrl = novel.getCoverUrl();
        if (coverUrl.startsWith("/api/")) {
            sendFallbackPixel(response);
            return;
        }

        try {
            downloadAndCache(coverUrl, cached, response);
        } catch (Exception e) {
            sendFallbackPixel(response);
        }
    }

    private void downloadAndCache(String url, Path cached, HttpServletResponse response) throws IOException {
        URL targetUrl = URI.create(url).toURL();
        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Referer", "https://www.23qb.net/");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        if (conn.getResponseCode() != 200) {
            sendFallbackPixel(response);
            return;
        }

        String contentType = conn.getContentType();
        response.setContentType(contentType != null && contentType.startsWith("image/") ? contentType : "image/jpeg");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=604800");

        try (InputStream in = conn.getInputStream(); OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            Path tmp = cached.resolveSibling(cached.getFileName() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
                while ((read = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            Files.move(tmp, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            out.flush();
        }
    }

    private void serveFile(Path file, HttpServletResponse response) {
        try {
            String contentType = Files.probeContentType(file);
            response.setContentType(contentType != null ? contentType : "image/jpeg");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=604800");
            Files.copy(file, response.getOutputStream());
            response.getOutputStream().flush();
        } catch (IOException e) {
            sendFallbackPixel(response);
        }
    }

    private void sendFallbackPixel(HttpServletResponse response) {
        try {
            response.setContentType("image/svg+xml;charset=utf-8");
            response.getOutputStream().write(FALLBACK_SVG.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            response.getOutputStream().flush();
        } catch (Exception ignored) {
        }
    }
}
