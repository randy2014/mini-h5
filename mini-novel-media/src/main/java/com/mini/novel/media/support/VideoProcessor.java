package com.mini.novel.media.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 视频转码/抽帧的 ffmpeg/ffprobe 命令行包装（生产容器需安装 ffmpeg）。
 *
 * 参数遵循 docs/media-pool-design.md §5.2（Android/iOS 兼容硬约束）：
 * H.264 Main@4.0 + yuv420p + AAC + faststart + 限码率 + 物理转正旋转 + GOP 对齐。
 */
public class VideoProcessor {

    public static final long DEFAULT_TIMEOUT_SECONDS = 900;

    private final String ffmpegBin;
    private final String ffprobeBin;
    private final long timeoutSeconds;

    public VideoProcessor() {
        this("ffmpeg", "ffprobe", DEFAULT_TIMEOUT_SECONDS);
    }

    public VideoProcessor(String ffmpegBin, String ffprobeBin, long timeoutSeconds) {
        this.ffmpegBin = ffmpegBin;
        this.ffprobeBin = ffprobeBin;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 探测结果。 */
    public record ProbeInfo(long durationMs, int width, int height, String format) {
    }

    /** ffprobe：时长(ms)、分辨率、封装格式。探测失败抛 IOException。 */
    public ProbeInfo probe(Path input) throws IOException {
        List<String> cmd = List.of(ffprobeBin,
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "format=duration:stream=width,height",
                "-of", "default=noprint_wrappers=1",
                input.toString());
        String out = run(cmd);
        long durationMs = 0;
        int width = 0;
        int height = 0;
        for (String line : out.split("\\R")) {
            String[] kv = line.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String val = kv[1].trim();
            switch (key) {
                case "duration" -> durationMs = Math.round(Double.parseDouble(val) * 1000);
                case "width" -> width = Integer.parseInt(val);
                case "height" -> height = Integer.parseInt(val);
                default -> {
                }
            }
        }
        return new ProbeInfo(durationMs, width, height, probeFormat(input));
    }

    /** 封装格式（container），如 mp4/mov/mkv。 */
    public String probeFormat(Path input) throws IOException {
        List<String> cmd = List.of(ffprobeBin,
                "-v", "error",
                "-show_entries", "format=format_name",
                "-of", "default=noprint_wrappers=1:nokey=1",
                input.toString());
        String out = run(cmd);
        String[] parts = out.trim().split(",");
        return parts.length == 0 ? "" : parts[0].trim();
    }

    /**
     * 转码为 H.264/AAC mp4（faststart）。输出文件名固定 <uuid>.mp4。
     *
     * @return 输出文件路径（已存在）
     */
    public Path transcode(Path input, Path outputDir, String outputName) throws IOException {
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve(outputName + ".mp4");
        List<String> cmd = new ArrayList<>(List.of(
                ffmpegBin, "-y",
                "-i", input.toString(),
                "-map", "0:v:0", "-map", "0:a:0?", // 视频 + 可选音频
                "-c:v", "libx264",
                "-profile:v", "main", "-level", "4.0",
                "-crf", "23", "-preset", "veryfast",
                "-pix_fmt", "yuv420p",
                "-vf", "scale='min(1920,iw)':-2:force_original_aspect_ratio=decrease", // 长边≤1920
                "-r", "30", "-g", "60", "-keyint_min", "60", "-sc_threshold", "0",
                "-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "44100",
                "-movflags", "+faststart",
                "-metadata:s:v", "rotate=0",
                "-sn", // 去字幕
                "-threads", "2",
                "-maxrate", "3000k", "-bufsize", "6000k",
                "-f", "mp4",
                output.toString()));
        run(cmd);
        if (!Files.exists(output) || Files.size(output) == 0) {
            throw new IOException("转码失败：输出文件为空");
        }
        return output;
    }

    /**
     * 抽封面帧：25%~75% 时间窗等距抽 3 帧 → 调用方据像素方差选择（或本方法返回最大信息量一帧的 jpg）。
     * 简化实现：取 40% 处一帧（信息量居中且避开片头黑场/片尾字幕），输出 <uuid>.jpg。
     */
    public Path extractPoster(Path input, Path outputDir, String outputName, long durationMs) throws IOException {
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve(outputName + ".jpg");
        double ts = Math.max(0.25, Math.min(0.75, 0.4)); // 统一 40%，稳定可复现
        double seconds = durationMs / 1000.0 * ts;
        List<String> cmd = List.of(ffmpegBin, "-y",
                "-ss", String.format(java.util.Locale.ROOT, "%.2f", seconds),
                "-i", input.toString(),
                "-frames:v", "1",
                "-vf", "scale='min(1280,iw)':-2",
                "-q:v", "3",
                output.toString());
        run(cmd);
        if (!Files.exists(output) || Files.size(output) == 0) {
            throw new IOException("抽帧失败：输出为空");
        }
        return output;
    }

    private String run(List<String> cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        try (var in = p.getInputStream()) {
            output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            boolean done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                throw new IOException("命令超时: " + String.join(" ", cmd) + "\n" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("命令被中断: " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            throw new IOException("命令失败(exit=" + p.exitValue() + "): "
                    + String.join(" ", cmd) + "\n" + abbreviate(output));
        }
        return output;
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 2000 ? s.substring(s.length() - 2000) : s);
    }
}
