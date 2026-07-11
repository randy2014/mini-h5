package com.mini.novel.api.model;

import java.time.LocalDateTime;

public class UserProfileVo {
    private Long id;
    private String nickname;
    private String avatar;
    private String mobile;
    private boolean vipActive;
    private Integer vipStatus;
    private LocalDateTime vipExpireTime;
    private String tokenName;
    private String tokenValue;
    private Boolean newAccount;
    private Boolean inviteCodeApplied;
    private Integer inviteQuotaLeft;
    private String exclusiveInviteCode;
    private String loginErrorCode;
    private String message;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public boolean isVipActive() { return vipActive; }
    public void setVipActive(boolean vipActive) { this.vipActive = vipActive; }
    public Integer getVipStatus() { return vipStatus; }
    public void setVipStatus(Integer vipStatus) { this.vipStatus = vipStatus; }
    public LocalDateTime getVipExpireTime() { return vipExpireTime; }
    public void setVipExpireTime(LocalDateTime vipExpireTime) { this.vipExpireTime = vipExpireTime; }
    public String getTokenName() { return tokenName; }
    public void setTokenName(String tokenName) { this.tokenName = tokenName; }
    public String getTokenValue() { return tokenValue; }
    public void setTokenValue(String tokenValue) { this.tokenValue = tokenValue; }
    public Boolean getNewAccount() { return newAccount; }
    public void setNewAccount(Boolean newAccount) { this.newAccount = newAccount; }
    public Boolean getInviteCodeApplied() { return inviteCodeApplied; }
    public void setInviteCodeApplied(Boolean inviteCodeApplied) { this.inviteCodeApplied = inviteCodeApplied; }
    public Integer getInviteQuotaLeft() { return inviteQuotaLeft; }
    public void setInviteQuotaLeft(Integer inviteQuotaLeft) { this.inviteQuotaLeft = inviteQuotaLeft; }
    public String getExclusiveInviteCode() { return exclusiveInviteCode; }
    public void setExclusiveInviteCode(String exclusiveInviteCode) { this.exclusiveInviteCode = exclusiveInviteCode; }
    public String getLoginErrorCode() { return loginErrorCode; }
    public void setLoginErrorCode(String loginErrorCode) { this.loginErrorCode = loginErrorCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
