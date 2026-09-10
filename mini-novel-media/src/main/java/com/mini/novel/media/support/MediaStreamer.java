package com.mini.novel.media.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpHeaders;

/**
 * 媒体字节流式输出：支持 HTTP Range(206) 分段（视频拖动进度必需）。
 * 统一 inline（内联播放，不触发附件下载）+ private 缓存 + nosniff。
 */
public final class MediaStreamer {

    private MediaStreamer() {
    }

    /**
     * 流式输出本地文件（自动 Range）。
     *
     * @param relPath 媒体相对路径（storage.resolve 校验目录穿越）
     */
    public static void streamFile(MediaFileStorage storage, String relPath,
                                  HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (relPath == null || relPath.isBlank()) {
            response.setStatus(404);
            return;
        }
        Path file;
        try {
            file = storage.resolve(relPath);
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            return;
        }
        if (!Files.exists(file) || Files.size(file) == 0) {
            response.setStatus(404);
            return;
        }
        long total = Files.size(file);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=600");

        String range = request.getHeader(HttpHeaders.RANGE);
        long start = 0;
        long end = total - 1;
        boolean partial = false;
        if (range != null && range.startsWith("bytes=")) {
            try {
                String spec = range.substring(6).trim();
                int dash = spec.indexOf('-');
                if (dash < 0) {
                    start = Long.parseLong(spec);
                } else if (dash == 0) {
                    start = Math.max(0, total - Long.parseLong(spec.substring(1)));
                } else {
                    start = Long.parseLong(spec.substring(0, dash));
                    String endStr = spec.substring(dash + 1);
                    if (!endStr.isEmpty()) {
                        end = Math.min(total - 1, Long.parseLong(endStr));
                    }
                }
                if (start < 0 || start > end || start >= total) {
                    response.setStatus(416);
                    response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + total);
                    return;
                }
                partial = true;
            } catch (NumberFormatException ignored) {
                partial = false; // 非法 Range → 全量
            }
        }
        long length = end - start + 1;
        if (partial) {
            response.setStatus(206);
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total);
        }
        response.setContentType(probeMime(file));
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
        try (InputStream in = Files.newInputStream(file); OutputStream out = response.getOutputStream()) {
            in.skipNBytes(start);
            long remaining = length;
            byte[] buf = new byte[64 * 1024];
            int n;
            while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();
        }
    }

    private static String probeMime(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp4")) {
            return "video/mp4";
        }
        // 媒体池自定义扩展名（见 MediaFileStorage.EXT_*）：均为 JPEG 成品/缩略/封面帧
        if (name.endsWith("." + MediaFileStorage.EXT_MAIN_IMAGE)
                || name.endsWith("." + MediaFileStorage.EXT_THUMB)
                || name.endsWith("." + MediaFileStorage.EXT_POSTER)) {
            return "image/jpeg";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        try {
            String probe = Files.probeContentType(file);
            return probe == null ? "application/octet-stream" : probe;
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
