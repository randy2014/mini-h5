package com.mini.novel.api.model;

import java.time.LocalDateTime;

public class SubscribeChannelVo {
    private Long id;
    private String name;
    private String cover;
    private String description;
    private Integer sort;
    private boolean subscribed;
    private boolean trial;
    private LocalDateTime trialEndTime;
    private String periodType;
    private LocalDateTime endTime;
    private long daysLeft;
    private long novelCount;
    /** 多媒体内容（图文+视频帖）数量。 */
    private long mediaCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public boolean isSubscribed() { return subscribed; }
    public void setSubscribed(boolean subscribed) { this.subscribed = subscribed; }
    public boolean isTrial() { return trial; }
    public void setTrial(boolean trial) { this.trial = trial; }
    public LocalDateTime getTrialEndTime() { return trialEndTime; }
    public void setTrialEndTime(LocalDateTime trialEndTime) { this.trialEndTime = trialEndTime; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public long getDaysLeft() { return daysLeft; }
    public void setDaysLeft(long daysLeft) { this.daysLeft = daysLeft; }
    public long getNovelCount() { return novelCount; }
    public void setNovelCount(long novelCount) { this.novelCount = novelCount; }
    public long getMediaCount() { return mediaCount; }
    public void setMediaCount(long mediaCount) { this.mediaCount = mediaCount; }
}
