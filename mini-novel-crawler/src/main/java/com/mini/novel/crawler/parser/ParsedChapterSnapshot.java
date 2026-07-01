package com.mini.novel.crawler.parser;

public record ParsedChapterSnapshot(String chapterId, String title, String url, int chapterNo, boolean vip,
                                    String content, int wordCount) {
    public ParsedChapterSnapshot(String chapterId, String title, String url, int chapterNo, boolean vip) {
        this(chapterId, title, url, chapterNo, vip, "", 0);
    }
}
