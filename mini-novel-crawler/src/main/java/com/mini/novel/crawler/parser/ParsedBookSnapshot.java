package com.mini.novel.crawler.parser;

public record ParsedBookSnapshot(String title, String author, String coverUrl, String intro, String sourceUrl,
                                 String sourceBookId, long wordCount, String chapterId, String chapterUrl) {
}
