package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.service.VipAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/novels")
public class NovelController {
    private final BookReadService bookReadService;
    private final VipAccessService vipAccessService;

    public NovelController(BookReadService bookReadService, VipAccessService vipAccessService) {
        this.bookReadService = bookReadService;
        this.vipAccessService = vipAccessService;
    }

    @GetMapping("/{novelId}")
    public Result<Novel> detail(@PathVariable("novelId") Long novelId) {
        return Result.ok(bookReadService.getNovel(novelId));
    }

    @GetMapping("/{novelId}/chapters")
    public Result<Page<Chapter>> chapters(@PathVariable("novelId") Long novelId,
                                          @RequestParam(value = "page", defaultValue = "1") long page,
                                          @RequestParam(value = "size", defaultValue = "80") long size) {
        return Result.ok(bookReadService.listChapters(novelId, page, size));
    }

    @GetMapping("/chapters/{chapterId}")
    public Result<Chapter> chapter(@PathVariable("chapterId") Long chapterId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Chapter chapter = bookReadService.getChapter(chapterId);
        if (Boolean.TRUE.equals(chapter.getVip()) && !vipAccessService.hasActiveVip(userId)) {
            throw new BusinessException(ErrorCode.VIP_REQUIRED, "该章节需要开通 VIP 后阅读");
        }
        return Result.ok(chapter);
    }

    @GetMapping("/chapters/{chapterId}/next")
    public Result<Chapter> nextChapter(@PathVariable("chapterId") Long chapterId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Chapter chapter = bookReadService.nextChapter(chapterId);
        if (Boolean.TRUE.equals(chapter.getVip()) && !vipAccessService.hasActiveVip(userId)) {
            throw new BusinessException(ErrorCode.VIP_REQUIRED, "下一章需要开通 VIP 后阅读");
        }
        return Result.ok(chapter);
    }
}
