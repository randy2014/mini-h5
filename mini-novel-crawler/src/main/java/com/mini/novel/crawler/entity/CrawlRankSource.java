package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_rank_source")
public class CrawlRankSource {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long sourceId;
    public String rankName;
    public String rankType;
    public String rankUrl;
    public Boolean preferCompleted;
    public Integer maxBooks;
    public Boolean enabled;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
