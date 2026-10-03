package com.mini.novel.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("media_post")
public class MediaPost {
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_MIXED = 3;
    public static final int STATUS_DRAFT = 1;
    public static final int STATUS_PUBLISHED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long channelId;
    private String title;
    private Integer type;
    private Long coverAssetId;
    private Integer status;
    private Long operatorId;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public Long getCoverAssetId() { return coverAssetId; }
    public void setCoverAssetId(Long coverAssetId) { this.coverAssetId = coverAssetId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
