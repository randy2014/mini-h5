package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_merge_task")
public class CrawlMergeTask {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long crawlTaskId;
    public String status;
    public Integer totalCount;
    public Integer mergedCount;
    public Integer pendingReviewCount;
    public Integer failedCount;
    public String message;
    public LocalDateTime startedAt;
    public LocalDateTime finishedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
