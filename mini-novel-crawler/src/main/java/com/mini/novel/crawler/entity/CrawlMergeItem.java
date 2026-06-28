package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_merge_item")
public class CrawlMergeItem {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long mergeTaskId;
    public Long bookRawId;
    public Long identityId;
    public Long novelId;
    public String matchStatus;
    public Integer confidenceScore;
    public String message;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
