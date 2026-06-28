package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.ChapterMapper;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.common.result.Result;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/novels")
public class AdminNovelController {
    private final NovelMapper novelMapper;
    private final ChapterMapper chapterMapper;

    public AdminNovelController(NovelMapper novelMapper, ChapterMapper chapterMapper) {
        this.novelMapper = novelMapper;
        this.chapterMapper = chapterMapper;
    }

    @GetMapping
    public Result<List<Novel>> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Novel> wrapper = new LambdaQueryWrapper<Novel>()
                .orderByDesc(Novel::getUpdatedAt)
                .last("LIMIT 200");
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Novel::getTitle, keyword).or().like(Novel::getAuthor, keyword));
        }
        if (status != null) {
            wrapper.eq(Novel::getStatus, status);
        }
        return Result.ok(novelMapper.selectList(wrapper));
    }

    @GetMapping("/{id}")
    public Result<Novel> detail(@PathVariable("id") Long id) {
        return Result.ok(novelMapper.selectById(id));
    }

    @PostMapping
    public Result<Novel> create(@RequestBody Novel novel) {
        LocalDateTime now = LocalDateTime.now();
        novel.setStatus(novel.getStatus() == null ? 1 : novel.getStatus());
        novel.setVipRequired(Boolean.TRUE.equals(novel.getVipRequired()));
        novel.setFreeChapterCount(novel.getFreeChapterCount() == null ? 0 : novel.getFreeChapterCount());
        novel.setWordCount(novel.getWordCount() == null ? 0L : novel.getWordCount());
        novel.setCreatedAt(now);
        novel.setUpdatedAt(now);
        novelMapper.insert(novel);
        return Result.ok(novel);
    }

    @PutMapping("/{id}")
    public Result<Novel> update(@PathVariable("id") Long id, @RequestBody Novel novel) {
        novel.setId(id);
        novel.setUpdatedAt(LocalDateTime.now());
        novelMapper.updateById(novel);
        return Result.ok(novelMapper.selectById(id));
    }

    @PutMapping("/{id}/status")
    public Result<Novel> updateStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        Novel novel = new Novel();
        novel.setId(id);
        novel.setStatus(request.status());
        novel.setOperatorId(request.operatorId() == null ? 1L : request.operatorId());
        novel.setUpdatedAt(LocalDateTime.now());
        if (request.status() != null && request.status() == 0) {
            novel.setOfflineAt(LocalDateTime.now());
            novel.setOfflineReason(request.reason());
        } else {
            novel.setOfflineAt(null);
            novel.setOfflineReason(null);
        }
        novelMapper.updateById(novel);
        return Result.ok(novelMapper.selectById(id));
    }

    @GetMapping("/{id}/chapters")
    public Result<List<Chapter>> chapters(@PathVariable("id") Long id) {
        return Result.ok(chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, id)
                .orderByAsc(Chapter::getChapterNo)));
    }

    @PutMapping("/chapters/{id}/vip")
    public Result<Chapter> chapterVip(@PathVariable("id") Long id, @RequestBody ChapterVipRequest request) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setVip(Boolean.TRUE.equals(request.vip()));
        chapter.setPriceCoin(request.priceCoin() == null ? 0 : request.priceCoin());
        chapter.setUpdatedAt(LocalDateTime.now());
        chapterMapper.updateById(chapter);
        return Result.ok(chapterMapper.selectById(id));
    }

    public record StatusRequest(Integer status, String reason, Long operatorId) {
    }

    public record ChapterVipRequest(Boolean vip, Integer priceCoin) {
    }
}
