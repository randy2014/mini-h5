package com.mini.novel.crawlerservice.service;

import com.mini.novel.crawler.entity.CrawlerAuthorizedBook;

public final class AuthorizedBookPolicy {
    private AuthorizedBookPolicy() {}
    public static void validate(CrawlerAuthorizedBook book) {
        boolean blocked = "BLOCKED".equalsIgnoreCase(book.riskLevel);
        boolean authorized = "AUTHORIZED".equalsIgnoreCase(book.authorizationStatus);
        boolean approved = "APPROVED".equalsIgnoreCase(book.reviewStatus);
        if (blocked && (authorized || yes(book.allowCrawlChapters) || yes(book.allowStore) || yes(book.allowDisplay) || yes(book.allowVipDisplay)))
            throw new IllegalArgumentException("BLOCKED books cannot be authorized, crawl chapters, store, or display.");
        if (yes(book.allowCrawlChapters) && !authorized)
            throw new IllegalArgumentException("Only AUTHORIZED books may enable chapter crawling.");
        if (yes(book.allowVipDisplay) && !(authorized && approved && yes(book.allowDisplay)))
            throw new IllegalArgumentException("VIP display requires AUTHORIZED + APPROVED + allow_display=true.");
    }
    private static boolean yes(Boolean value) { return Boolean.TRUE.equals(value); }
}
