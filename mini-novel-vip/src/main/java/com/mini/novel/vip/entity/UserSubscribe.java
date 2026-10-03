package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("user_subscribe")
public class UserSubscribe {
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_EXPIRED = 2;
    public static final int STATUS_CANCELLED = 3;

    public static final int PERIOD_WEEK = 1;
    public static final int PERIOD_MONTH = 2;
    public static final int PERIOD_QUARTER = 3;
    public static final int PERIOD_YEAR = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long channelId;
    private Integer periodType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Long costCoins;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getChannelId() { return channelId; }
    public void setChannelId(Long channelId) { this.channelId = channelId; }
    public Integer getPeriodType() { return periodType; }
    public void setPeriodType(Integer periodType) { this.periodType = periodType; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCostCoins() { return costCoins; }
    public void setCostCoins(Long costCoins) { this.costCoins = costCoins; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
