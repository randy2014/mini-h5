package com.mini.novel.book.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Category;
import com.mini.novel.book.entity.Novel;
import java.util.List;

public interface BookReadService {
    List<Novel> latestNovels(int limit);

    List<Novel> searchNovels(String keyword, int limit);

    List<Novel> rankNovels(String rankType, int limit);

    List<Category> listCategories();

    List<Novel> novelsByCategory(Long categoryId, int limit);

    Novel getNovel(Long novelId);

    Page<Chapter> listChapters(Long novelId, long page, long size);

    Chapter getChapter(Long chapterId);

    Chapter nextChapter(Long chapterId);
}
