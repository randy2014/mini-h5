package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawler_authorized_book")
public class CrawlerAuthorizedBook {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String sourceCode;
    public String sourceBookId;
    public String bookUrl;
    public String title;
    public String author;
    public String intro;
    public String categoryName;
    public String tagsJson;
    public String coverUrl;
    public String authorizationStatus;
    public Boolean allowCrawlMeta;
    public Boolean allowCrawlChapters;
    public Boolean allowStore;
    public Boolean allowDisplay;
    public Boolean allowVipDisplay;
    public String reviewStatus;
    public String riskLevel;
    public String riskReason;
    public String authorizationNote;
    public String proofRef;
    public Long authorizedBy;
    public Long reviewedBy;
    public LocalDateTime discoveredAt;
    public LocalDateTime authorizedAt;
    public LocalDateTime reviewedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
