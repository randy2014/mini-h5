package com.mini.novel.api.controller;

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
        AppUser user = appUserMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getMobile, request.getMobile())
                .last("limit 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "演示用户不存在");
        }
        return Result.ok(toProfile(user));
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
}
