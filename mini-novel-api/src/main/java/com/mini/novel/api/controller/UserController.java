package com.mini.novel.api.controller;

import com.mini.novel.api.model.UserProfileVo;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.mapper.AppUserMapper;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final AppUserMapper appUserMapper;
    private final BookReadService bookReadService;

    public UserController(AppUserMapper appUserMapper, BookReadService bookReadService) {
        this.appUserMapper = appUserMapper;
        this.bookReadService = bookReadService;
    }

    @GetMapping("/profile")
    public Result<UserProfileVo> profile(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = loadUser(userId);
        return Result.ok(AuthController.toProfile(user));
    }

    @GetMapping("/bookshelf")
    public Result<List<Novel>> bookshelf(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        loadUser(userId);
        return Result.ok(bookReadService.latestNovels(20));
    }

    private AppUser loadUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return user;
    }
}
