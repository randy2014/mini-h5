package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_content_raw")
public class CrawlContentRaw {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long chapterRawId;
    public String content;
    public String contentHash;
    public Integer contentLength;
    public String storageMode;
    public LocalDateTime createdAt;
}
