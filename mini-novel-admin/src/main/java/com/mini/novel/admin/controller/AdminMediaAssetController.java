package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.common.result.Result;
import com.mini.novel.media.entity.MediaAsset;
import com.mini.novel.media.service.MediaAssetProcessService;
import com.mini.novel.media.service.MediaPostService;
import com.mini.novel.media.support.MediaFileStorage;
import com.mini.novel.media.support.MediaStreamer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台：多媒体素材上传 / 列表 / 删除 / 重处理 / 预览（图片查看、视频播放走内联流式）。
 * 上传鉴权/operator 沿用现有后台约定（请求方传入 operatorId）。
 */
@RestController
@RequestMapping("/admin/media/assets")
public class AdminMediaAssetController {

    private static final java.util.Set<String> VIDEO_EXT =
            java.util.Set.of("mp4", "mov", "mkv", "webm", "avi", "flv", "m4v");

    /** 流式上传上限：与 nginx（320m）、multipart（300MB）口径一致。 */
    private static final long MAX_STREAM_BYTES = 300L * 1024 * 1024;

    private final MediaAssetProcessService processService;
    private final MediaPostService postService;
    private final MediaFileStorage storage;

    public AdminMediaAssetController(MediaAssetProcessService processService,
                                     MediaPostService postService,
                                     MediaFileStorage storage) {
        this.processService = processService;
        this.postService = postService;
        this.storage = storage;
    }

    /** 上传结果（单文件失败不整体回滚）。 */
    public record UploadResult(String fileName, boolean ok, Long assetId, Integer fileType,
                               Integer status, String reason) {
    }

    /** 批量上传：图片同步压缩返回 READY；视频登记后异步转码返回 PROCESSING。 */
    @PostMapping("/upload")
    public Result<List<UploadResult>> upload(@RequestParam("files") MultipartFile[] files,
                                             @RequestParam(value = "operatorId", required = false) Long operatorId)
            throws IOException {
        List<UploadResult> results = new ArrayList<>();
        if (files == null) {
            return Result.ok(results);
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                results.add(new UploadResult("", false, null, null, null, "空文件"));
                continue;
            }
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            try (var in = file.getInputStream()) {
                MediaAsset asset = isVideo(name)
                        ? processService.registerVideo(in, name, operatorId)
                        : processService.processImage(in, name, operatorId);
                results.add(new UploadResult(name, true, asset.getId(),
                        asset.getFileType(), asset.getStatus(), null));
            } catch (Exception e) {
                results.add(new UploadResult(name, false, null, null, MediaAsset.STATUS_FAILED,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        return Result.ok(results);
    }

    /**
     * 流式上传（前端默认入口）：请求体即原始文件字节，不走 multipart。
     *
     * <p>multipart 方案下 Spring 会先把整包落到容器 /tmp，再由业务层复制到媒体卷，
     * 300MB 视频等于写两遍盘（磁盘是本项目既定第一风险源）。这里把请求体直接流式写进
     * `/data/media/tmp`，只写一遍。
     *
     * <p>调用方式：`POST /admin/media/assets/upload-stream?fileName=xxx.mp4`，
     * `Content-Type: application/octet-stream`，body = 文件字节；单文件一次调用。
     * 业务失败（超限/格式不支持）同样返回 HTTP 200 + `ok=false`，前端按 reason 提示。
     */
    @PostMapping("/upload-stream")
    public Result<UploadResult> uploadStream(@RequestParam("fileName") String fileName,
                                             @RequestParam(value = "operatorId", required = false) Long operatorId,
                                             HttpServletRequest request) {
        String name = sanitizeName(fileName);
        try (InputStream in = new LimitedInputStream(request.getInputStream(), MAX_STREAM_BYTES)) {
            MediaAsset asset = isVideo(name)
                    ? processService.registerVideo(in, name, operatorId)
                    : processService.processImage(in, name, operatorId);
            return Result.ok(new UploadResult(name, true, asset.getId(),
                    asset.getFileType(), asset.getStatus(), null));
        } catch (Exception e) {
            return Result.ok(new UploadResult(name, false, null, null, MediaAsset.STATUS_FAILED,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    @GetMapping
    public Result<Page<MediaAsset>> list(@RequestParam(required = false) Integer type,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(postService.pageAssets(type, status, keyword,
                Math.max(1, page), Math.min(100, pageSize)));
    }

    /**
     * 单个素材详情：前端上传视频后按 id 轮询转码状态（PROCESSING → READY/FAILED）。
     * 不能靠列表接口「最新一条」猜，否则同时上传图片时视频状态永远刷不到。
     */
    @GetMapping("/{id}")
    public Result<MediaAsset> detail(@PathVariable Long id) {
        return Result.ok(postService.requireAsset(id));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        postService.deleteAsset(id);
        return Result.ok(true);
    }

    @PostMapping("/{id}/reprocess")
    public Result<MediaAsset> reprocess(@PathVariable Long id) throws IOException {
        return Result.ok(processService.reprocess(id));
    }

    /** 后台运营预览/播放：kind=main|thumb|poster，内联流式（Range 支持，非附件下载）。 */
    @GetMapping("/{id}/file")
    public void stream(@PathVariable Long id,
                       @RequestParam(defaultValue = "main") String kind,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        MediaAsset asset = postService.requireAsset(id);
        String rel = switch (kind) {
            case "thumb" -> asset.getThumbPath();
            case "poster" -> asset.getPosterPath();
            default -> asset.getMainPath();
        };
        MediaStreamer.streamFile(storage, rel, request, response);
    }

    private static boolean isVideo(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return VIDEO_EXT.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** 只取文件名本身（去掉路径），并限长到 200 字符（DB 列 varchar(255)），截断保留尾部以保住扩展名。 */
    private static String sanitizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            name = "upload";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    /** 请求体读取上限：超过直接中断，避免无限制写入磁盘。 */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long read;

        private LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws IOException {
            read += n;
            if (read > limit) {
                throw new IOException("文件超过 " + (limit / 1024 / 1024) + "MB 上限");
            }
        }
    }
}
