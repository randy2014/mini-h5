package com.mini.novel.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.api.support.VipPublicationProgress;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.service.VipAccessService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/novels")
public class NovelController {
    private final BookReadService bookReadService;
    private final VipAccessService vipAccessService;
    private final CurrentUserResolver currentUserResolver;
    private final VipPublicationProgress publicationProgress;

    public NovelController(BookReadService bookReadService, VipAccessService vipAccessService,
                           CurrentUserResolver currentUserResolver, VipPublicationProgress publicationProgress) {
        this.bookReadService = bookReadService;
        this.vipAccessService = vipAccessService;
        this.currentUserResolver = currentUserResolver;
        this.publicationProgress = publicationProgress;
    }

    @GetMapping("/search")
    public Result<List<Novel>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                      @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return Result.ok(bookReadService.searchNovels(keyword, limit));
    }

    @GetMapping("/rank")
    public Result<List<Novel>> rank(@RequestParam(value = "type", defaultValue = "HOT") String type,
                                    @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return Result.ok(bookReadService.rankNovels(type, limit));
    }

    @GetMapping("/{novelId}")
    public Result<Novel> detail(@PathVariable("novelId") Long novelId) {
        return Result.ok(publicationProgress.enrich(bookReadService.getNovel(novelId)));
    }

    @GetMapping("/{novelId}/chapters")
    public Result<Page<Chapter>> chapters(@PathVariable("novelId") Long novelId,
                                          @RequestParam(value = "page", defaultValue = "1") long page,
                                          @RequestParam(value = "size", defaultValue = "80") long size) {
        Novel novel = bookReadService.getNovel(novelId);
        if (publicationProgress.supports(novel)) return Result.ok(publicationProgress.chapters(novel, page, size));
        return Result.ok(bookReadService.listChapters(novelId, page, size));
    }

    @GetMapping("/chapters/{chapterId}")
    public Result<Chapter> chapter(@PathVariable("chapterId") Long chapterId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Chapter chapter = bookReadService.getChapter(chapterId);
        Long resolvedUserId = currentUserResolver.resolveUserId(userId);
        if (Boolean.TRUE.equals(chapter.getVip()) && !vipAccessService.hasActiveVip(resolvedUserId)) {
            throw new BusinessException(ErrorCode.VIP_REQUIRED, "该章节需要开通 VIP 后阅读");
        }
        return Result.ok(chapter);
    }

    @GetMapping("/chapters/{chapterId}/previous")
    public Result<Chapter> previousChapter(@PathVariable("chapterId") Long chapterId,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Chapter chapter = bookReadService.previousChapter(chapterId);
        Long resolvedUserId = currentUserResolver.resolveUserId(userId);
        if (Boolean.TRUE.equals(chapter.getVip()) && !vipAccessService.hasActiveVip(resolvedUserId)) {
            throw new BusinessException(ErrorCode.VIP_REQUIRED, "上一章需要开通 VIP 后阅读");
        }
        return Result.ok(chapter);
    }

    @GetMapping("/chapters/{chapterId}/next")
    public Result<Chapter> nextChapter(@PathVariable("chapterId") Long chapterId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Chapter chapter = bookReadService.nextChapter(chapterId);
        Long resolvedUserId = currentUserResolver.resolveUserId(userId);
        if (Boolean.TRUE.equals(chapter.getVip()) && !vipAccessService.hasActiveVip(resolvedUserId)) {
            throw new BusinessException(ErrorCode.VIP_REQUIRED, "下一章需要开通 VIP 后阅读");
        }
        return Result.ok(chapter);
    }
}
