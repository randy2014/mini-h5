package com.mini.novel.api.support;

import cn.dev33.satoken.stp.StpUtil;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    private final AppUserMapper appUserMapper;

    public CurrentUserResolver(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    public Long resolveUserId(Long headerUserId) {
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsLong();
        }
        return headerUserId;
    }

    public AppUser requireUser(Long headerUserId) {
        Long userId = resolveUserId(headerUserId);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        return user;
    }
}
