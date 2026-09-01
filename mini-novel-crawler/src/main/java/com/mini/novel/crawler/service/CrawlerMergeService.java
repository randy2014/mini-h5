package com.mini.novel.crawler.service;

public interface CrawlerMergeService {
    void mergeByCrawlTaskId(Long crawlTaskId);

    void mergePending();

    void retryMergeItem(Long mergeItemId);

    void ignoreMergeItem(Long mergeItemId, String reason);
}
