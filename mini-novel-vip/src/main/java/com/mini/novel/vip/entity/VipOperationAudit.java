package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("vip_operation_audit")
public class VipOperationAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String action;
    private Long targetUserId;
    private Long targetCodeId;
    private Long targetInvitationRecordId;
    private String beforeJson;
    private String afterJson;
    private Long operatorId;
    private String reason;
    private String requestId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public Long getTargetCodeId() { return targetCodeId; }
    public void setTargetCodeId(Long targetCodeId) { this.targetCodeId = targetCodeId; }
    public Long getTargetInvitationRecordId() { return targetInvitationRecordId; }
    public void setTargetInvitationRecordId(Long targetInvitationRecordId) { this.targetInvitationRecordId = targetInvitationRecordId; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
