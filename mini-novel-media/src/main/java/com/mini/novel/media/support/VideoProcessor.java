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

    /** 子进程输出只保留尾部（ffmpeg 进度输出可能很大，旧实现会把全部输出读进内存）。 */
    private static final int MAX_CAPTURE_CHARS = 64 * 1024;

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
                ffmpegBin, "-y", "-nostdin", "-loglevel", "error",
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
        List<String> cmd = List.of(ffmpegBin, "-y", "-nostdin", "-loglevel", "error",
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

    /**
     * 执行子进程：并发排空输出 + 真正的超时控制。
     *
     * <p><b>为什么不能先 readAllBytes 再 waitFor：</b>旧实现先阻塞读完全部输出，
     * 之后才 {@code waitFor(timeout)}，超时实际永远来不及生效 —— ffmpeg 卡死时
     * 这里会无限阻塞，素材永久停在 PROCESSING（只能等每日 03:30 的兜底任务标 FAILED）。
     * 现在由守护线程持续排空管道（顺带避免 ffmpeg 写满 pipe 阻塞），
     * 主线程用 {@code waitFor(timeout, …)} 真正掌控超时。
     */
    private String run(List<String> cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder captured = new StringBuilder();
        Thread drainer = new Thread(() -> drain(p, captured), "media-proc-drain");
        drainer.setDaemon(true);
        drainer.start();

        boolean done;
        try {
            done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("命令被中断: " + String.join(" ", cmd));
        }
        if (!done) {
            p.destroyForcibly();
            joinQuietly(drainer);
            throw new IOException("命令超时(" + timeoutSeconds + "s): " + String.join(" ", cmd)
                    + "\n" + tail(captured));
        }
        joinQuietly(drainer);
        if (p.exitValue() != 0) {
            throw new IOException("命令失败(exit=" + p.exitValue() + "): "
                    + String.join(" ", cmd) + "\n" + tail(captured));
        }
        return fullOutput(captured);
    }

    /** 持续读取子进程输出，只保留尾部 MAX_CAPTURE_CHARS 个字符。 */
    private static void drain(Process p, StringBuilder sink) {
        char[] buf = new char[4096];
        try (var reader = new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
            int n;
            while ((n = reader.read(buf)) != -1) {
                synchronized (sink) {
                    sink.append(buf, 0, n);
                    int overflow = sink.length() - MAX_CAPTURE_CHARS;
                    if (overflow > 0) {
                        sink.delete(0, overflow);
                    }
                }
            }
        } catch (IOException ignored) {
            // 进程被强杀时管道会异常关闭，属预期
        }
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String tail(StringBuilder sink) {
        synchronized (sink) {
            return abbreviate(sink.toString());
        }
    }

    /** 完整输出（已按 MAX_CAPTURE_CHARS 截尾保留），供 probe 解析使用。 */
    private static String fullOutput(StringBuilder sink) {
        synchronized (sink) {
            return sink.toString();
        }
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 2000 ? s.substring(s.length() - 2000) : s);
    }
}
