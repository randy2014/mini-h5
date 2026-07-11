package com.mini.novel.crawler.service;

public interface CrawlerMergeService {
    void mergeByCrawlTaskId(Long crawlTaskId);

    void mergePending();

    void retryMergeItem(Long mergeItemId);

    void approveAuthorizedMergeItem(Long mergeItemId, Long operatorId, String remark);

    void ignoreMergeItem(Long mergeItemId, String reason);
}
