package com.mini.novel.book.service;

import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
import java.util.List;

public interface BookReadService {
    List<Novel> latestNovels(int limit);

    Novel getNovel(Long novelId);

    List<Chapter> listChapters(Long novelId);

    Chapter getChapter(Long chapterId);
}
