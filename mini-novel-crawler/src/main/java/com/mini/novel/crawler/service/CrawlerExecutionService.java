package com.mini.novel.crawler.service;

public interface CrawlerExecutionService {
    void executeAsync(Long taskId);

    void execute(Long taskId);
}
