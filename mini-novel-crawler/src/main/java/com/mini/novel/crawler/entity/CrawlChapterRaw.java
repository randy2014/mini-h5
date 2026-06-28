package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_chapter_raw")
public class CrawlChapterRaw {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long bookRawId;
    public String sourceChapterId;
    public String sourceUrl;
    public Integer chapterNo;
    public String title;
    @TableField("is_vip")
    public Boolean vip;
    public Integer priceCoin;
    public String contentStatus;
    public String contentHash;
    public LocalDateTime crawledAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
