package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_schedule")
public class CrawlSchedule {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String name;
    public Long sourceId;
    public Long credentialId;
    public String scheduleTimes;
    public String timezone;
    public Boolean crawlPublic;
    public Boolean crawlVip;
    public Boolean autoMerge;
    public Boolean enabled;
    public LocalDateTime lastRunAt;
    public LocalDateTime nextRunAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
