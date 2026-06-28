package com.mini.novel.crawler.service;

import com.mini.novel.crawler.entity.CrawlTask;
import com.mini.novel.crawler.model.CrawlSubmitRequest;

public interface CrawlerTaskService {
    CrawlTask submit(CrawlSubmitRequest request);
}
