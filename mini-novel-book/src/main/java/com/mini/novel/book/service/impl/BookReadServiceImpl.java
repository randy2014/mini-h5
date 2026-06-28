package com.mini.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
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

    public BookReadServiceImpl(NovelMapper novelMapper, ChapterMapper chapterMapper) {
        this.novelMapper = novelMapper;
        this.chapterMapper = chapterMapper;
    }

    @Override
    public List<Novel> latestNovels(int limit) {
        return novelMapper.selectPage(Page.of(1, limit),
                new LambdaQueryWrapper<Novel>().orderByDesc(Novel::getUpdatedAt)).getRecords();
    }

    @Override
    public Novel getNovel(Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "小说不存在");
        }
        return novel;
    }

    @Override
    public List<Chapter> listChapters(Long novelId) {
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, novelId)
                .orderByAsc(Chapter::getChapterNo));
    }

    @Override
    public Chapter getChapter(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "章节不存在");
        }
        return chapter;
    }
}
