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
        return novelMapper.selectPage(Page.of(1, limit),
                new LambdaQueryWrapper<Novel>()
                        .ne(Novel::getStatus, 0)
                        .orderByDesc(Novel::getUpdatedAt)).getRecords();
    }

    @Override
    public List<Category> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    @Override
    public List<Novel> novelsByCategory(Long categoryId, int limit) {
        return novelMapper.selectPage(Page.of(1, Math.max(1, Math.min(limit, 100))),
                new LambdaQueryWrapper<Novel>()
                        .eq(Novel::getCategoryId, categoryId)
                        .ne(Novel::getStatus, 0)
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
}
