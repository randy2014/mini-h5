package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("user_coin_log")
public class UserCoinLog {
    public static final int BIZ_RECHARGE = 1;
    public static final int BIZ_SUBSCRIBE = 2;
    public static final int BIZ_REFUND = 3;
    public static final int BIZ_GRANT = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long changeAmount;
    private Long balanceAfter;
    private Integer bizType;
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
    public Integer getBizType() { return bizType; }
    public void setBizType(Integer bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
