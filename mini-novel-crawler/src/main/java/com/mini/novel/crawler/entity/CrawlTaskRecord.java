package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_task_v2")
public class CrawlTaskRecord {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long scheduleId;
    public Long sourceId;
    public Long rankSourceId;
    public String taskType;
    public String triggerType;
    public String status;
    public String targetUrl;
    public Integer totalCount;
    public Integer successCount;
    public Integer failCount;
    public String message;
    public LocalDateTime startedAt;
    public LocalDateTime finishedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
