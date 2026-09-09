package com.mini.novel.api.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 频道聚合内容（小说 + 多媒体帖统一时间倒序流），供频道详情网格/列表混排。
 */
public class ChannelFeedVo {
    private List<FeedItem> records;
    private long total;
    private boolean restricted;

    /** 单条内容：kind=NOVEL|IMAGE|VIDEO|MIXED。 */
    public static class FeedItem {
        private String kind;
        private Long id;            // NOVEL → novel.id；媒体 → post.id
        private String title;
        private String author;      // 小说作者
        private String coverKind;   // 'novel'（H5 走 /api/cover/{id}）| 'thumb' | 'poster'（媒体）
        private Long coverAssetId;  // 媒体封面素材 id（thumb/poster）
        private Integer imageCount;
        private Integer videoCount;
        private Long videoDurationMs;
        private LocalDateTime sortTime;

        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getCoverKind() { return coverKind; }
        public void setCoverKind(String coverKind) { this.coverKind = coverKind; }
        public Long getCoverAssetId() { return coverAssetId; }
        public void setCoverAssetId(Long coverAssetId) { this.coverAssetId = coverAssetId; }
        public Integer getImageCount() { return imageCount; }
        public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }
        public Integer getVideoCount() { return videoCount; }
        public void setVideoCount(Integer videoCount) { this.videoCount = videoCount; }
        public Long getVideoDurationMs() { return videoDurationMs; }
        public void setVideoDurationMs(Long videoDurationMs) { this.videoDurationMs = videoDurationMs; }
        public LocalDateTime getSortTime() { return sortTime; }
        public void setSortTime(LocalDateTime sortTime) { this.sortTime = sortTime; }
    }

    public List<FeedItem> getRecords() { return records; }
    public void setRecords(List<FeedItem> records) { this.records = records; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public boolean isRestricted() { return restricted; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }
}
