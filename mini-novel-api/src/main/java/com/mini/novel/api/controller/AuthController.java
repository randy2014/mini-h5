package com.mini.novel.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.mini.novel.api.model.LoginRequest;
import com.mini.novel.api.model.UserProfileVo;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.vip.service.VipInvitationService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final VipInvitationService vipInvitationService;

    public AuthController(VipInvitationService vipInvitationService) {
        this.vipInvitationService = vipInvitationService;
    }

    @PostMapping("/login")
    public Result<UserProfileVo> login(@Valid @RequestBody LoginRequest request) {
        String mobile = normalizeMobile(request.getMobile());
        VipInvitationService.LoginResult loginResult = vipInvitationService.loginOrCreate(
                mobile,
                request.getInvitationCode(),
                Boolean.TRUE.equals(request.getConfirmCreateNormal()));
        AppUser user = loginResult.getUser();
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        StpUtil.login(user.getId());
        UserProfileVo profile = toProfile(user);
        profile.setTokenName(StpUtil.getTokenName());
        profile.setTokenValue(StpUtil.getTokenValue());
        profile.setNewAccount(loginResult.isNewAccount());
        profile.setInviteCodeApplied(loginResult.isInviteCodeApplied());
        profile.setInviteQuotaLeft(loginResult.getInviteQuotaLeft());
        profile.setExclusiveInviteCode(loginResult.getExclusiveInviteCode());
        profile.setLoginErrorCode(loginResult.getLoginErrorCode());
        profile.setMessage(loginResult.getMessage());
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
        vo.setVipStatus(user.getVipStatus());
        vo.setVipActive(user.getVipExpireTime() != null && user.getVipExpireTime().isAfter(LocalDateTime.now()));
        return vo;
    }

    private String normalizeMobile(String mobile) {
        String value = mobile == null ? "" : mobile.trim();
        if (!value.matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请输入正确的手机号");
        }
        return value;
    }
}
