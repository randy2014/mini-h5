package com.mini.novel.crawler.parser;

public record ParsedBookSeed(String url, String title, String author, String intro, long wordCount,
                             String chapterId, String rankUrl) {
}
