package com.mini.novel.vip.service.impl;

import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import com.mini.novel.vip.service.VipAccessService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class VipAccessServiceImpl implements VipAccessService {
    private final AppUserMapper appUserMapper;

    public VipAccessServiceImpl(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    public boolean hasActiveVip(Long userId) {
        if (userId == null) {
            return false;
        }
        AppUser user = appUserMapper.selectById(userId);
        return user != null
                && user.getVipExpireTime() != null
                && user.getVipExpireTime().isAfter(LocalDateTime.now());
    }
}
