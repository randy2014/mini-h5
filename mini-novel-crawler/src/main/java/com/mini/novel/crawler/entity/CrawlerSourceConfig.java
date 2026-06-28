package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_source")
public class CrawlerSourceConfig {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String sourceCode;
    public String name;
    public String baseUrl;
    public String sourceType;
    public String authMode;
    public Boolean enabled;
    public Integer priority;
    public String remark;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
