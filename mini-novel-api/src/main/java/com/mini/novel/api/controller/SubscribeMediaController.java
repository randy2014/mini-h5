package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.service.SubscribeService;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.media.entity.MediaAsset;
import com.mini.novel.media.entity.MediaPost;
import com.mini.novel.media.service.MediaPostService;
import com.mini.novel.media.support.MediaFileStorage;
import com.mini.novel.media.support.MediaStreamer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端：订阅频道内多媒体内容（图文/视频帖）读取。
 * 访问控制（见 docs §6.2/§7）：媒体帖无未订阅预览——未订阅/试用外用户列表不含媒体、
 * 详情与媒体字节 403；前端按 restricted 隐藏媒体区块。
 */
@RestController
@RequestMapping("/api/subscribe")
public class SubscribeMediaController {

    private final MediaPostService postService;
    private final SubscribeService subscribeService;
    private final CurrentUserResolver currentUserResolver;
    private final MediaFileStorage storage;

    public SubscribeMediaController(MediaPostService postService,
                                    SubscribeService subscribeService,
                                    CurrentUserResolver currentUserResolver,
                                    MediaFileStorage storage) {
        this.postService = postService;
        this.subscribeService = subscribeService;
        this.currentUserResolver = currentUserResolver;
        this.storage = storage;
    }

    /** 频道媒体内容分页（仅订阅/试用可见）。未订阅返回 empty + restricted=true。 */
    @GetMapping("/channels/{channelId}/media")
    public Result<MediaPageVo> media(@PathVariable Long channelId,
                                     @RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "20") long pageSize) {
        Long userId = currentUserResolver.requireUser(null).getId();
        boolean accessible = subscribeService.isAccessible(userId, channelId);
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, pageSize);
        MediaPageVo vo = new MediaPageVo();
        if (!accessible) {
            vo.setRestricted(true);
            vo.setTotal(0);
            vo.setRecords(new ArrayList<>());
            return Result.ok(vo);
        }
        Page<MediaPostService.MediaPostDetail> p = postService.pageChannelPublished(channelId, safePage, safeSize);
        vo.setRestricted(false);
        vo.setTotal(p.getTotal());
        vo.setRecords(toCards(p.getRecords()));
        return Result.ok(vo);
    }

    /** 媒体帖详情（未订阅/试用外 403）。 */
    @GetMapping("/channels/{channelId}/media/posts/{postId}")
    public Result<MediaPostService.MediaPostDetail> post(@PathVariable Long channelId,
                                                         @PathVariable Long postId) {
        Long userId = currentUserResolver.requireUser(null).getId();
        if (!subscribeService.isAccessible(userId, channelId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "订阅后查看");
        }
        MediaPostService.MediaPostDetail detail = postService.getDetail(postId);
        if (detail.post() == null || detail.post().getChannelId() == null
                || !detail.post().getChannelId().equals(channelId)
                || !MediaPost.STATUS_PUBLISHED.equals(detail.post().getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        return Result.ok(detail);
    }

    /** 鉴权流式字节：kind=full|thumb|poster。full 需订阅；thumb/poster 需登录（供列表封面）。 */
    @GetMapping("/channels/{channelId}/media/assets/{assetId}/file")
    public void file(@PathVariable Long channelId,
                     @PathVariable Long assetId,
                     @RequestParam(defaultValue = "thumb") String kind,
                     HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long userId = currentUserResolver.requireUser(null).getId();
        boolean accessible = subscribeService.isAccessible(userId, channelId);
        if (!accessible) {
            // 媒体无未订阅预览（含封面/缩略），未订阅直接 403
            throw new BusinessException(ErrorCode.FORBIDDEN, "订阅后查看");
        }
        MediaAsset asset = postService.requireAsset(assetId);
        if (!postService.isAssetInPublishedPostOfChannel(assetId, channelId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材不存在于该频道");
        }
        String rel;
        switch (kind) {
            case "thumb", "poster" -> {
                rel = "thumb".equals(kind) ? asset.getThumbPath() : asset.getPosterPath();
            }
            default -> rel = asset.getMainPath();
        }
        MediaStreamer.streamFile(storage, rel, request, response);
    }

    private List<MediaCardVo> toCards(List<MediaPostService.MediaPostDetail> details) {
        List<MediaCardVo> out = new ArrayList<>();
        for (MediaPostService.MediaPostDetail d : details) {
            MediaPost post = d.post();
            MediaAsset cover = d.cover();
            MediaCardVo card = new MediaCardVo();
            card.setId(post.getId());
            card.setChannelId(post.getChannelId());
            card.setTitle(post.getTitle());
            card.setType(post.getType());
            card.setPublishedAt(post.getPublishedAt());
            card.setAssetCount(d.assets().size());
            card.setVideoDurationMs(cover != null && MediaAsset.TYPE_VIDEO.equals(cover.getFileType())
                    ? cover.getDurationMs() : null);
            card.setCoverAssetId(cover == null ? null : cover.getId());
            card.setCoverKind(cover == null ? null
                    : (MediaAsset.TYPE_VIDEO.equals(cover.getFileType()) ? "poster" : "thumb"));
            out.add(card);
        }
        return out;
    }

    public static class MediaPageVo {
        private boolean restricted;
        private long total;
        private List<MediaCardVo> records;

        public boolean isRestricted() { return restricted; }
        public void setRestricted(boolean restricted) { this.restricted = restricted; }
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
        public List<MediaCardVo> getRecords() { return records; }
        public void setRecords(List<MediaCardVo> records) { this.records = records; }
    }

    public static class MediaCardVo {
        private Long id;
        private Long channelId;
        private String title;
        private String type;
        private java.time.LocalDateTime publishedAt;
        private int assetCount;
        private Long videoDurationMs;
        private Long coverAssetId;
        private String coverKind;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getChannelId() { return channelId; }
        public void setChannelId(Long channelId) { this.channelId = channelId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public java.time.LocalDateTime getPublishedAt() { return publishedAt; }
        public void setPublishedAt(java.time.LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
        public int getAssetCount() { return assetCount; }
        public void setAssetCount(int assetCount) { this.assetCount = assetCount; }
        public Long getVideoDurationMs() { return videoDurationMs; }
        public void setVideoDurationMs(Long videoDurationMs) { this.videoDurationMs = videoDurationMs; }
        public Long getCoverAssetId() { return coverAssetId; }
        public void setCoverAssetId(Long coverAssetId) { this.coverAssetId = coverAssetId; }
        public String getCoverKind() { return coverKind; }
        public void setCoverKind(String coverKind) { this.coverKind = coverKind; }
    }
}
