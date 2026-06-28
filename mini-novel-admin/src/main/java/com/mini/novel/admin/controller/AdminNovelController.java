package com.mini.novel.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.ChapterMapper;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.common.result.Result;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern CHAPTER_TITLE_PATTERN = Pattern.compile(
            "(?m)^\\s*(第[一二三四五六七八九十百千万零〇0-9]+[章节回卷].{0,80}|Chapter\\s+\\d+.{0,80})\\s*$");
    private static final int MIN_IMPORT_CONTENT_LENGTH = 120;

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

    @PostMapping("/import-text")
    public Result<Novel> importText(@RequestBody TextImportRequest request) {
        if (!StringUtils.hasText(request.title())) {
            return new Result<>(400, "标题不能为空", null);
        }
        if (!StringUtils.hasText(request.content()) || request.content().trim().length() < MIN_IMPORT_CONTENT_LENGTH) {
            return new Result<>(400, "导入正文过短，请提供真实小说正文", null);
        }
        if (looksLikeSourceNotice(request.content())) {
            return new Result<>(400, "导入内容像来源链接说明，不是真实小说正文", null);
        }

        LocalDateTime now = LocalDateTime.now();
        Novel novel = new Novel();
        novel.setTitle(request.title().trim());
        novel.setAuthor(StringUtils.hasText(request.author()) ? request.author().trim() : "未知作者");
        novel.setCoverUrl(StringUtils.hasText(request.coverUrl()) ? request.coverUrl().trim() : "https://dummyimage.com/300x420/20232a/ffffff&text=TXT");
        novel.setIntro(StringUtils.hasText(request.intro()) ? request.intro().trim() : "手动导入作品");
        novel.setCategoryId(request.categoryId() == null ? 1L : request.categoryId());
        novel.setStatus(request.status() == null ? 1 : request.status());
        novel.setVipRequired(Boolean.TRUE.equals(request.vipRequired()));
        novel.setFreeChapterCount(request.freeChapterCount() == null ? 0 : request.freeChapterCount());
        novel.setSourceUrl("manual://txt-import");
        novel.setCreatedAt(now);
        novel.setUpdatedAt(now);
        novelMapper.insert(novel);

        List<ChapterPart> parts = splitChapters(request.content());
        long wordCount = 0L;
        Chapter latest = null;
        int chapterNo = 1;
        for (ChapterPart part : parts) {
            if (!StringUtils.hasText(part.content())) {
                continue;
            }
            Chapter chapter = new Chapter();
            chapter.setNovelId(novel.getId());
            chapter.setChapterNo(chapterNo++);
            chapter.setTitle(limit(part.title(), 255));
            chapter.setContent(part.content().trim());
            chapter.setVip(Boolean.TRUE.equals(request.vipRequired())
                    && chapter.getChapterNo() > novel.getFreeChapterCount());
            chapter.setPriceCoin(chapter.getVip() ? 10 : 0);
            chapter.setSourceUrl("manual://txt-import");
            chapter.setCreatedAt(now);
            chapter.setUpdatedAt(now);
            chapterMapper.insert(chapter);
            latest = chapter;
            wordCount += chapter.getContent().length();
        }

        if (latest == null) {
            return new Result<>(400, "未解析到可导入章节", null);
        }

        novel.setWordCount(wordCount);
        novel.setLatestChapterId(latest.getId());
        novel.setLatestChapterTitle(latest.getTitle());
        novel.setUpdatedAt(LocalDateTime.now());
        novelMapper.updateById(novel);
        return Result.ok(novelMapper.selectById(novel.getId()));
    }

    private List<ChapterPart> splitChapters(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<ChapterPart> parts = new ArrayList<>();
        Matcher matcher = CHAPTER_TITLE_PATTERN.matcher(normalized);
        List<TitleHit> hits = new ArrayList<>();
        while (matcher.find()) {
            hits.add(new TitleHit(matcher.start(), matcher.end(), matcher.group(1).trim()));
        }
        if (hits.isEmpty()) {
            parts.add(new ChapterPart("第一章", normalized));
            return parts;
        }
        for (int i = 0; i < hits.size(); i++) {
            TitleHit current = hits.get(i);
            int bodyStart = current.end();
            int bodyEnd = i + 1 < hits.size() ? hits.get(i + 1).start() : normalized.length();
            String body = normalized.substring(bodyStart, bodyEnd).trim();
            if (StringUtils.hasText(body)) {
                parts.add(new ChapterPart(current.title(), body));
            }
        }
        return parts;
    }

    private boolean looksLikeSourceNotice(String content) {
        String text = content.trim();
        return text.contains("书籍来源：") || text.contains("章节入口：") || text.contains("版权说明：当前采集器");
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record StatusRequest(Integer status, String reason, Long operatorId) {
    }

    public record ChapterVipRequest(Boolean vip, Integer priceCoin) {
    }

    public record TextImportRequest(String title, String author, String coverUrl, String intro, Long categoryId,
                                    Integer status, Boolean vipRequired, Integer freeChapterCount, String content) {
    }

    private record TitleHit(int start, int end, String title) {
    }

    private record ChapterPart(String title, String content) {
    }
}
