package com.mini.novel.media.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 媒体文件存储：根目录下的相对路径管理（DB 只存相对路径，域名/端口不落库）。
 *
 * 布局（见 docs/media-pool-design.md §9）：
 * {root}/tmp/              上传/转码中间文件（成功后删除，7 天清理）
 * {root}/images/{yyyyMM}/  图片成品(main/thumb 同目录，见路径函数)
 * {root}/videos/{yyyyMM}/  视频成品 mp4
 * {root}/posters/{yyyyMM}/ 视频封面帧 jpg
 */
public class MediaFileStorage {

    public static final String SUB_TMP = "tmp";
    public static final String SUB_IMAGES = "images";
    public static final String SUB_VIDEOS = "videos";
    public static final String SUB_POSTERS = "posters";
    public static final String EXT_MAIN_IMAGE = "img";
    public static final String EXT_THUMB = "thumb";
    public static final String EXT_POSTER = "poster";

    private final Path root;

    public MediaFileStorage(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    private Path datedDir(String sub) throws IOException {
        Path dir = root.resolve(sub).resolve(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
        Files.createDirectories(dir);
        return dir;
    }

    /** 生成唯一 id（文件名主干，不入库即安全）。 */
    public String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 落一份待处理文件到 tmp（原文件中转，图片后续也会经此）。返回 [tmpPath, uuid]。 */
    public Path saveTmp(InputStream in) throws IOException {
        Path dir = root.resolve(SUB_TMP);
        Files.createDirectories(dir);
        Path tmp = dir.resolve(newId() + ".upload");
        try (in) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    public Path tmpFile() throws IOException {
        Path dir = root.resolve(SUB_TMP);
        Files.createDirectories(dir);
        return dir.resolve(newId() + ".tmp");
    }

    /** 图片成品目标路径（uuid.img 在 images 目录）。 */
    public Path imageMain(String uuid) throws IOException {
        return datedDir(SUB_IMAGES).resolve(uuid + "." + EXT_MAIN_IMAGE);
    }

    /** 图片/视频封面缩略目标路径（uuid.thumb 在 images 目录）。 */
    public Path thumbTarget(String uuid) throws IOException {
        return datedDir(SUB_IMAGES).resolve(uuid + "." + EXT_THUMB);
    }

    /** 视频成品目标路径（uuid.mp4 在 videos 目录）。 */
    public Path videoMain(String uuid) throws IOException {
        return datedDir(SUB_VIDEOS).resolve(uuid + ".mp4");
    }

    /** 视频封面帧目标路径（uuid.poster 在 posters 目录）。 */
    public Path posterTarget(String uuid) throws IOException {
        return datedDir(SUB_POSTERS).resolve(uuid + "." + EXT_POSTER);
    }

    public boolean exists(String relPath) {
        return relPath != null && !relPath.isBlank() && Files.exists(resolve(relPath));
    }

    public Path resolve(String relPath) {
        Path p = root.resolve(relPath).normalize();
        // 防目录穿越
        if (!p.startsWith(root.normalize())) {
            throw new IllegalArgumentException("非法路径: " + relPath);
        }
        return p;
    }

    public void delete(String relPath) {
        if (relPath == null || relPath.isBlank()) {
            return;
        }
        try {
            Path p = root.resolve(relPath).normalize();
            if (p.startsWith(root.normalize())) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
        }
    }

    public static String relOf(Path root, Path file) {
        return root.relativize(file.normalize()).toString().replace('\\', '/');
    }

    public static String relOf(Path root, Path dir, String fileName) {
        return root.relativize(dir.resolve(fileName).normalize()).toString().replace('\\', '/');
    }
}
