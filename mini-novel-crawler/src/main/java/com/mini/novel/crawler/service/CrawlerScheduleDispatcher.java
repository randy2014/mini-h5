package com.mini.novel.crawler.service;

public interface CrawlerScheduleDispatcher {
    void dispatchDueSchedules();

    void runPendingTasks();
}
