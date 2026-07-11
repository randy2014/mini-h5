package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("vip_invitation_code")
public class VipInvitationCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String code;
    private String status;
    private Integer totalQuota;
    private Integer usedQuota;
    private Integer remainingQuota;
    @TableField("is_current")
    private Boolean current;
    private LocalDateTime generatedAt;
    private LocalDateTime enabledAt;
    private LocalDateTime disabledAt;
    private LocalDateTime revokedAt;
    private LocalDateTime lastUsedAt;
    private Long replacedByCodeId;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalQuota() { return totalQuota; }
    public void setTotalQuota(Integer totalQuota) { this.totalQuota = totalQuota; }
    public Integer getUsedQuota() { return usedQuota; }
    public void setUsedQuota(Integer usedQuota) { this.usedQuota = usedQuota; }
    public Integer getRemainingQuota() { return remainingQuota; }
    public void setRemainingQuota(Integer remainingQuota) { this.remainingQuota = remainingQuota; }
    public Boolean getCurrent() { return current; }
    public void setCurrent(Boolean current) { this.current = current; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public LocalDateTime getEnabledAt() { return enabledAt; }
    public void setEnabledAt(LocalDateTime enabledAt) { this.enabledAt = enabledAt; }
    public LocalDateTime getDisabledAt() { return disabledAt; }
    public void setDisabledAt(LocalDateTime disabledAt) { this.disabledAt = disabledAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public Long getReplacedByCodeId() { return replacedByCodeId; }
    public void setReplacedByCodeId(Long replacedByCodeId) { this.replacedByCodeId = replacedByCodeId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
