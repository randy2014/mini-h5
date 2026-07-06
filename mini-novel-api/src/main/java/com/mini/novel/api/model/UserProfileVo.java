package com.mini.novel.api.model;

import java.time.LocalDateTime;

public class UserProfileVo {
    private Long id;
    private String nickname;
    private String avatar;
    private String mobile;
    private boolean vipActive;
    private LocalDateTime vipExpireTime;
    private String tokenName;
    private String tokenValue;

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
    public LocalDateTime getVipExpireTime() { return vipExpireTime; }
    public void setVipExpireTime(LocalDateTime vipExpireTime) { this.vipExpireTime = vipExpireTime; }
    public String getTokenName() { return tokenName; }
    public void setTokenName(String tokenName) { this.tokenName = tokenName; }
    public String getTokenValue() { return tokenValue; }
    public void setTokenValue(String tokenValue) { this.tokenValue = tokenValue; }
}
