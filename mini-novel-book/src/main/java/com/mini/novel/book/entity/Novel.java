package com.mini.novel.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("novel")
public class Novel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String author;
    private String coverUrl;
    private String intro;
    private Long categoryId;
    private Integer status;
    private Boolean vipRequired;
    private Integer freeChapterCount;
    private Long wordCount;
    private Long latestChapterId;
    private String latestChapterTitle;
    private String sourceUrl;
    private String offlineReason;
    private LocalDateTime offlineAt;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField(exist = false)
    private Integer approvedChapterCount;
    @TableField(exist = false)
    private Integer totalChapterCount;
    @TableField(exist = false)
    private String reviewProgress;
    @TableField(exist = false)
    private String publishStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Boolean getVipRequired() { return vipRequired; }
    public void setVipRequired(Boolean vipRequired) { this.vipRequired = vipRequired; }
    public Integer getFreeChapterCount() { return freeChapterCount; }
    public void setFreeChapterCount(Integer freeChapterCount) { this.freeChapterCount = freeChapterCount; }
    public Long getWordCount() { return wordCount; }
    public void setWordCount(Long wordCount) { this.wordCount = wordCount; }
    public Long getLatestChapterId() { return latestChapterId; }
    public void setLatestChapterId(Long latestChapterId) { this.latestChapterId = latestChapterId; }
    public String getLatestChapterTitle() { return latestChapterTitle; }
    public void setLatestChapterTitle(String latestChapterTitle) { this.latestChapterTitle = latestChapterTitle; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getOfflineReason() { return offlineReason; }
    public void setOfflineReason(String offlineReason) { this.offlineReason = offlineReason; }
    public LocalDateTime getOfflineAt() { return offlineAt; }
    public void setOfflineAt(LocalDateTime offlineAt) { this.offlineAt = offlineAt; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getApprovedChapterCount() { return approvedChapterCount; }
    public void setApprovedChapterCount(Integer approvedChapterCount) { this.approvedChapterCount = approvedChapterCount; }
    public Integer getTotalChapterCount() { return totalChapterCount; }
    public void setTotalChapterCount(Integer totalChapterCount) { this.totalChapterCount = totalChapterCount; }
    public String getReviewProgress() { return reviewProgress; }
    public void setReviewProgress(String reviewProgress) { this.reviewProgress = reviewProgress; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
}
