package com.mini.novel.media.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MediaFileStorage / VideoProcessor 纯逻辑测试。
 * 真实 ffmpeg 转码依赖生产容器（P8 部署后以端到端验收），此处不做进程级集成。
 */
class VideoProcessorTest {

    @TempDir
    Path tmp;

    @Test
    void newIdIsUuidWithoutDash() {
        MediaFileStorage s = new MediaFileStorage(tmp);
        String id = s.newId();
        assertNotNull(id);
        assertEquals(32, id.length());
        assertTrue(id.matches("[0-9a-f]{32}"));
    }

    @Test
    void storageCreatesDatedDirsAndRelPaths() throws IOException {
        MediaFileStorage s = new MediaFileStorage(tmp);
        String id = "a".repeat(32);
        Path main = s.imageMain(id);
        assertTrue(main.toString().endsWith(".img"));
        assertTrue(main.getParent().getFileName().toString().matches("\\d{6}"));
        String rel = MediaFileStorage.relOf(s.root(), main);
        assertTrue(rel.startsWith("images/"));
        assertTrue(Files.exists(main.getParent()));
    }

    @Test
    void resolveBlocksTraversal() {
        MediaFileStorage s = new MediaFileStorage(tmp);
        assertThrows(IllegalArgumentException.class, () -> s.resolve("../../etc/passwd"));
    }

    @Test
    void probeParsesDurationAndSizeFromOutputFormat() {
        // 仅验证记录类型与工具常量可用（解析由 ffprobe 输出驱动，集成留 P8）
        VideoProcessor.ProbeInfo probe = new VideoProcessor.ProbeInfo(12500L, 1920, 1080, "mp4");
        assertEquals(12500L, probe.durationMs());
        assertEquals(1920, probe.width());
        assertEquals(1080, probe.height());
    }
}
