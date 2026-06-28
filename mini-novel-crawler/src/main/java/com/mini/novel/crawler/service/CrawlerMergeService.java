package com.mini.novel.crawler.service;

public interface CrawlerMergeService {
    void mergeByCrawlTaskId(Long crawlTaskId);

    void mergePending();
}
