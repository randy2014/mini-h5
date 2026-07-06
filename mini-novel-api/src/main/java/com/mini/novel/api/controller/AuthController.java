package com.mini.novel.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.api.model.LoginRequest;
import com.mini.novel.api.model.UserProfileVo;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserMapper appUserMapper;

    public AuthController(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @PostMapping("/login")
    public Result<UserProfileVo> login(@Valid @RequestBody LoginRequest request) {
        String mobile = normalizeMobile(request.getMobile());
        AppUser user = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getMobile, mobile)
                .last("limit 1"));
        if (user == null) {
            user = createUser(mobile);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        StpUtil.login(user.getId());
        UserProfileVo profile = toProfile(user);
        profile.setTokenName(StpUtil.getTokenName());
        profile.setTokenValue(StpUtil.getTokenValue());
        return Result.ok(profile);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        return Result.ok();
    }

    static UserProfileVo toProfile(AppUser user) {
        UserProfileVo vo = new UserProfileVo();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setMobile(user.getMobile());
        vo.setVipExpireTime(user.getVipExpireTime());
        vo.setVipActive(user.getVipExpireTime() != null && user.getVipExpireTime().isAfter(LocalDateTime.now()));
        return vo;
    }

    private AppUser createUser(String mobile) {
        LocalDateTime now = LocalDateTime.now();
        AppUser user = new AppUser();
        user.setMobile(mobile);
        user.setNickname("读者" + mobile.substring(mobile.length() - 4));
        user.setStatus(1);
        user.setVipStatus(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        appUserMapper.insert(user);
        return user;
    }

    private String normalizeMobile(String mobile) {
        String value = mobile == null ? "" : mobile.trim();
        if (!value.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请输入正确的手机号");
        }
        return value;
    }
}
