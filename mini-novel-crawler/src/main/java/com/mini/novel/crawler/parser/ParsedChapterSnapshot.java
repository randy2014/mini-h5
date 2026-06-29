package com.mini.novel.crawler.parser;

public record ParsedChapterSnapshot(String chapterId, String title, String url, int chapterNo, boolean vip) {
}
