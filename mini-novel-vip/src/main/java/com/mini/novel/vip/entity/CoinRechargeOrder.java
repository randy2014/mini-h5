package com.mini.novel.vip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("coin_recharge_order")
public class CoinRechargeOrder {
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_CONFIRMED = 1;
    public static final int STATUS_CANCELLED = 2;

    public static final int PAY_WECHAT = 1;
    public static final int PAY_ALIPAY = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long packageId;
    private BigDecimal amountYuan;
    private Long coins;
    private Integer status;
    private Integer payMethod;
    private String remark;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime paidHintAt;
    private String approveToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public BigDecimal getAmountYuan() { return amountYuan; }
    public void setAmountYuan(BigDecimal amountYuan) { this.amountYuan = amountYuan; }
    public Long getCoins() { return coins; }
    public void setCoins(Long coins) { this.coins = coins; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getPayMethod() { return payMethod; }
    public void setPayMethod(Integer payMethod) { this.payMethod = payMethod; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getPaidHintAt() { return paidHintAt; }
    public void setPaidHintAt(LocalDateTime paidHintAt) { this.paidHintAt = paidHintAt; }
    public String getApproveToken() { return approveToken; }
    public void setApproveToken(String approveToken) { this.approveToken = approveToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
