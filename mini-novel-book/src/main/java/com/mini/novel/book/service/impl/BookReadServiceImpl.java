package com.mini.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Category;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.CategoryMapper;
import com.mini.novel.book.mapper.ChapterMapper;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BookReadServiceImpl implements BookReadService {
    private final NovelMapper novelMapper;
    private final ChapterMapper chapterMapper;
    private final CategoryMapper categoryMapper;

    public BookReadServiceImpl(NovelMapper novelMapper, ChapterMapper chapterMapper, CategoryMapper categoryMapper) {
        this.novelMapper = novelMapper;
        this.chapterMapper = chapterMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<Novel> latestNovels(int limit) {
        return rankNovels("LATEST", limit);
    }

    @Override
    public List<Novel> searchNovels(String keyword, int limit) {
        int pageSize = normalizeLimit(limit, 50);
        LambdaQueryWrapper<Novel> wrapper = baseOnlineWrapper().orderByDesc(Novel::getUpdatedAt);
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(query -> query.like(Novel::getTitle, trimmed).or().like(Novel::getAuthor, trimmed));
        }
        return novelMapper.selectPage(Page.of(1, pageSize), wrapper).getRecords();
    }

    @Override
    public List<Novel> rankNovels(String rankType, int limit) {
        int pageSize = normalizeLimit(limit, 100);
        String type = StringUtils.hasText(rankType) ? rankType.trim().toUpperCase() : "HOT";
        LambdaQueryWrapper<Novel> wrapper = baseOnlineWrapper();
        switch (type) {
            case "COMPLETED" -> wrapper.eq(Novel::getStatus, 2)
                    .orderByDesc(Novel::getWordCount)
                    .orderByDesc(Novel::getUpdatedAt);
            case "LATEST" -> wrapper.orderByDesc(Novel::getUpdatedAt);
            case "LONG" -> wrapper.orderByDesc(Novel::getWordCount)
                    .orderByDesc(Novel::getUpdatedAt);
            default -> wrapper.orderByDesc(Novel::getUpdatedAt)
                    .orderByDesc(Novel::getWordCount);
        }
        return novelMapper.selectPage(Page.of(1, pageSize), wrapper).getRecords();
    }

    @Override
    public List<Category> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    @Override
    public List<Novel> novelsByCategory(Long categoryId, int limit) {
        return novelMapper.selectPage(Page.of(1, normalizeLimit(limit, 100)),
                baseOnlineWrapper()
                        .eq(Novel::getCategoryId, categoryId)
                        .orderByDesc(Novel::getUpdatedAt)).getRecords();
    }

    @Override
    public Novel getNovel(Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小说不存在");
        }
        if (novel.getStatus() != null && novel.getStatus() == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小说已下架");
        }
        return novel;
    }

    @Override
    public Page<Chapter> listChapters(Long novelId, long page, long size) {
        getNovel(novelId);
        long current = Math.max(1, page);
        long pageSize = Math.max(1, Math.min(size, 100));
        return chapterMapper.selectPage(Page.of(current, pageSize), new LambdaQueryWrapper<Chapter>()
                .select(Chapter::getId, Chapter::getNovelId, Chapter::getChapterNo, Chapter::getTitle,
                        Chapter::getVip, Chapter::getPriceCoin, Chapter::getSourceUrl, Chapter::getCreatedAt,
                        Chapter::getUpdatedAt)
                .eq(Chapter::getNovelId, novelId)
                .orderByAsc(Chapter::getChapterNo));
    }

    @Override
    public Chapter getChapter(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "章节不存在");
        }
        getNovel(chapter.getNovelId());
        return chapter;
    }

    @Override
    public Chapter previousChapter(Long chapterId) {
        Chapter current = getChapter(chapterId);
        Chapter previous = chapterMapper.selectOne(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, current.getNovelId())
                .lt(Chapter::getChapterNo, current.getChapterNo())
                .orderByDesc(Chapter::getChapterNo)
                .last("LIMIT 1"));
        if (previous == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "已经是第一章");
        }
        return previous;
    }

    @Override
    public Chapter nextChapter(Long chapterId) {
        Chapter current = getChapter(chapterId);
        Chapter next = chapterMapper.selectOne(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, current.getNovelId())
                .gt(Chapter::getChapterNo, current.getChapterNo())
                .orderByAsc(Chapter::getChapterNo)
                .last("LIMIT 1"));
        if (next == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "已经是最后一章");
        }
        return next;
    }

    private LambdaQueryWrapper<Novel> baseOnlineWrapper() {
        return new LambdaQueryWrapper<Novel>()
                .ne(Novel::getStatus, 0)
                .and(query -> query.like(Novel::getSourceUrl, "23qb.net")
                        .or()
                        .isNull(Novel::getSourceUrl)
                        .or()
                        .eq(Novel::getSourceUrl, ""));
    }

    private int normalizeLimit(int limit, int max) {
        return Math.max(1, Math.min(limit, max));
    }
}
