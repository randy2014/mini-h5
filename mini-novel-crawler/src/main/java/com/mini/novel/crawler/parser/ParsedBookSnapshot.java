package com.mini.novel.crawler.parser;

import java.util.List;

public record ParsedBookSnapshot(String title, String author, String coverUrl, String intro, String sourceUrl,
                                 String sourceBookId, long wordCount, String categoryName, String bookStatus,
                                 String chapterId, String chapterUrl, List<ParsedChapterSnapshot> chapters,
                                 String tagsJson) {
    public ParsedBookSnapshot(String title, String author, String coverUrl, String intro, String sourceUrl,
                              String sourceBookId, long wordCount, String categoryName, String bookStatus,
                              String chapterId, String chapterUrl, List<ParsedChapterSnapshot> chapters) {
        this(title, author, coverUrl, intro, sourceUrl, sourceBookId, wordCount, categoryName, bookStatus,
                chapterId, chapterUrl, chapters, "[]");
    }

    public ParsedBookSnapshot(String title, String author, String coverUrl, String intro, String sourceUrl,
                              String sourceBookId, long wordCount, String chapterId, String chapterUrl) {
        this(title, author, coverUrl, intro, sourceUrl, sourceBookId, wordCount, "", "UNKNOWN", chapterId, chapterUrl,
                chapterUrl == null || chapterUrl.isBlank()
                        ? List.of()
                        : List.of(new ParsedChapterSnapshot(chapterId, "", chapterUrl, 1, false)),
                "[]");
    }

    public ParsedBookSnapshot(String title, String author, String coverUrl, String intro, String sourceUrl,
                              String sourceBookId, long wordCount, String chapterId, String chapterUrl,
                              List<ParsedChapterSnapshot> chapters) {
        this(title, author, coverUrl, intro, sourceUrl, sourceBookId, wordCount, "", "UNKNOWN", chapterId, chapterUrl, chapters);
    }

    public ParsedBookSnapshot(String title, String author, String coverUrl, String intro, String sourceUrl,
                              String sourceBookId, long wordCount, String categoryName, String chapterId,
                              String chapterUrl, List<ParsedChapterSnapshot> chapters) {
        this(title, author, coverUrl, intro, sourceUrl, sourceBookId, wordCount, categoryName, "UNKNOWN",
                chapterId, chapterUrl, chapters);
    }
}
