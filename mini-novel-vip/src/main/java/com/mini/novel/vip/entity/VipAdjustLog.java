package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("vip_adjust_log")
public class VipAdjustLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String action;
    private LocalDateTime beforeExpireTime;
    private LocalDateTime afterExpireTime;
    private String beforeStatus;
    private String afterStatus;
    private Integer days;
    private String reason;
    private Long operatorId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public LocalDateTime getBeforeExpireTime() { return beforeExpireTime; }
    public void setBeforeExpireTime(LocalDateTime beforeExpireTime) { this.beforeExpireTime = beforeExpireTime; }
    public LocalDateTime getAfterExpireTime() { return afterExpireTime; }
    public void setAfterExpireTime(LocalDateTime afterExpireTime) { this.afterExpireTime = afterExpireTime; }
    public String getBeforeStatus() { return beforeStatus; }
    public void setBeforeStatus(String beforeStatus) { this.beforeStatus = beforeStatus; }
    public String getAfterStatus() { return afterStatus; }
    public void setAfterStatus(String afterStatus) { this.afterStatus = afterStatus; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
