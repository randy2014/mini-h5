package com.mini.novel.api.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.mini.novel.api.model.LoginRequest;
import com.mini.novel.api.model.CaptchaVo;
import com.mini.novel.api.service.CaptchaService;
import com.mini.novel.api.model.UserProfileVo;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.vip.service.VipInvitationService;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final VipInvitationService vipInvitationService;
    private final CaptchaService captchaService;

    public AuthController(VipInvitationService vipInvitationService, CaptchaService captchaService) {
        this.vipInvitationService = vipInvitationService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public Result<CaptchaVo> captcha(HttpServletRequest request) {
        return Result.ok(captchaService.create(clientIp(request)));
    }

    @PostMapping("/login")
    public Result<UserProfileVo> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletResponse response) {
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode());
        String mobile = normalizeMobile(request.getMobile());
        VipInvitationService.LoginResult loginResult = vipInvitationService.loginOrCreate(
                mobile, request.getPassword(), request.getInvitationCode());
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
        // 同步种同名 Cookie：<img>/<video> 等无法带自定义头的媒体请求可凭 Cookie 鉴权
        writeTokenCookie(response, StpUtil.getTokenName(), StpUtil.getTokenValue());
        return Result.ok(profile);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
        expireTokenCookie(response, StpUtil.getTokenName());
        return Result.ok();
    }

    private void writeTokenCookie(HttpServletResponse response, String tokenName, String tokenValue) {
        Cookie cookie = new Cookie(tokenName, tokenValue);
        cookie.setHttpOnly(false);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 3600); // 与 sa-token timeout 对齐
        response.addCookie(cookie);
    }

    private void expireTokenCookie(HttpServletResponse response, String tokenName) {
        Cookie cookie = new Cookie(tokenName, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    static UserProfileVo toProfile(AppUser user) {
        UserProfileVo vo = new UserProfileVo();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setMobile(user.getMobile());
        vo.setVipExpireTime(user.getVipExpireTime());
        vo.setVipActivatedAt(user.getVipActivatedAt());
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

    private String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
