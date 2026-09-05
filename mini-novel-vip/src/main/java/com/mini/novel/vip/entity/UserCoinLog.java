package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("user_coin_log")
public class UserCoinLog {
    public static final String BIZ_RECHARGE = "RECHARGE";
    public static final String BIZ_SUBSCRIBE = "SUBSCRIBE";
    public static final String BIZ_REFUND = "REFUND";
    public static final String BIZ_GRANT = "GRANT";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long changeAmount;
    private Long balanceAfter;
    private String bizType;
    private String bizId;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getChangeAmount() { return changeAmount; }
    public void setChangeAmount(Long changeAmount) { this.changeAmount = changeAmount; }
    public Long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(Long balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
