package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_book_raw")
public class CrawlBookRaw {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String sourceCode;
    public String sourceBookId;
    public String sourceUrl;
    public String title;
    public String author;
    public String intro;
    public String coverUrl;
    public String categoryName;
    public String bookStatus;
    public Long wordCount;
    public Long heatScore;
    public String rankType;
    public String contentStatus;
    public String rawJson;
    public LocalDateTime crawledAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
