package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("vip_invitation_record")
public class VipInvitationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long invitationCodeId;
    private String codeSnapshot;
    private Long inviterUserId;
    private Long inviteeUserId;
    private String status;
    private LocalDateTime activatedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInvitationCodeId() { return invitationCodeId; }
    public void setInvitationCodeId(Long invitationCodeId) { this.invitationCodeId = invitationCodeId; }
    public String getCodeSnapshot() { return codeSnapshot; }
    public void setCodeSnapshot(String codeSnapshot) { this.codeSnapshot = codeSnapshot; }
    public Long getInviterUserId() { return inviterUserId; }
    public void setInviterUserId(Long inviterUserId) { this.inviterUserId = inviterUserId; }
    public Long getInviteeUserId() { return inviteeUserId; }
    public void setInviteeUserId(Long inviteeUserId) { this.inviteeUserId = inviteeUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
