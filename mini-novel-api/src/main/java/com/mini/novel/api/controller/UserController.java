package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.api.model.UserProfileVo;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.common.result.Result;
import com.mini.novel.user.entity.AppUser;
import com.mini.novel.user.entity.UserBookshelf;
import com.mini.novel.user.mapper.UserBookshelfMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final CurrentUserResolver currentUserResolver;
    private final BookReadService bookReadService;
    private final UserBookshelfMapper bookshelfMapper;
    private final NovelMapper novelMapper;

    public UserController(CurrentUserResolver currentUserResolver, BookReadService bookReadService,
                          UserBookshelfMapper bookshelfMapper, NovelMapper novelMapper) {
        this.currentUserResolver = currentUserResolver;
        this.bookReadService = bookReadService;
        this.bookshelfMapper = bookshelfMapper;
        this.novelMapper = novelMapper;
    }

    @GetMapping("/profile")
    public Result<UserProfileVo> profile(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        return Result.ok(AuthController.toProfile(user));
    }

    @GetMapping("/bookshelf")
    public Result<List<Novel>> bookshelf(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        List<UserBookshelf> rows = bookshelfMapper.selectList(new QueryWrapper<UserBookshelf>()
                .eq("user_id", user.getId())
                .orderByDesc("updated_at")
                .last("LIMIT 100"));
        List<Novel> novels = new ArrayList<>();
        for (UserBookshelf row : rows) {
            Novel novel = novelMapper.selectById(row.getNovelId());
            if (novel != null && (novel.getStatus() == null || novel.getStatus() != 0)) {
                novels.add(novel);
            }
        }
        return Result.ok(novels);
    }

    @PostMapping("/bookshelf/{novelId}")
    public Result<Void> addBookshelf(@PathVariable("novelId") Long novelId,
                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        bookReadService.getNovel(novelId);
        LocalDateTime now = LocalDateTime.now();
        UserBookshelf bookshelf = bookshelfMapper.selectOne(new QueryWrapper<UserBookshelf>()
                .eq("user_id", user.getId())
                .eq("novel_id", novelId)
                .last("LIMIT 1"));
        if (bookshelf == null) {
            bookshelf = new UserBookshelf();
            bookshelf.setUserId(user.getId());
            bookshelf.setNovelId(novelId);
            bookshelf.setProgress(0);
            bookshelf.setCreatedAt(now);
        }
        bookshelf.setUpdatedAt(now);
        if (bookshelf.getId() == null) {
            bookshelfMapper.insert(bookshelf);
        } else {
            bookshelfMapper.updateById(bookshelf);
        }
        return Result.ok(null);
    }

    @DeleteMapping("/bookshelf/{novelId}")
    public Result<Void> removeBookshelf(@PathVariable("novelId") Long novelId,
                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AppUser user = currentUserResolver.requireUser(userId);
        bookshelfMapper.delete(new QueryWrapper<UserBookshelf>()
                .eq("user_id", user.getId())
                .eq("novel_id", novelId));
        return Result.ok(null);
    }
}
