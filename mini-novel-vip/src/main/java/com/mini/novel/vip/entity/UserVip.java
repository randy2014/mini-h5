package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("user_vip")
public class UserVip {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long vipPlanId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private Long sourceOrderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getVipPlanId() { return vipPlanId; }
    public void setVipPlanId(Long vipPlanId) { this.vipPlanId = vipPlanId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getSourceOrderId() { return sourceOrderId; }
    public void setSourceOrderId(Long sourceOrderId) { this.sourceOrderId = sourceOrderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
