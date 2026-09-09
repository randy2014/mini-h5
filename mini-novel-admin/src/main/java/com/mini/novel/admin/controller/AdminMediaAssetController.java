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
import java.io.IOException;
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
    public record UploadResult(String fileName, boolean ok, Long assetId, String fileType,
                               String status, String reason) {
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
                results.add(new UploadResult(name, false, null, null, "FAILED",
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        return Result.ok(results);
    }

    @GetMapping
    public Result<Page<MediaAsset>> list(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(postService.pageAssets(type, status, keyword,
                Math.max(1, page), Math.min(100, pageSize)));
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
}
