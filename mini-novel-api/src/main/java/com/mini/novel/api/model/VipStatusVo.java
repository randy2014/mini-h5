package com.mini.novel.api.model;

import java.time.LocalDateTime;

public class VipStatusVo {
    private boolean active;
    private LocalDateTime vipExpireTime;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getVipExpireTime() {
        return vipExpireTime;
    }

    public void setVipExpireTime(LocalDateTime vipExpireTime) {
        this.vipExpireTime = vipExpireTime;
    }
}
