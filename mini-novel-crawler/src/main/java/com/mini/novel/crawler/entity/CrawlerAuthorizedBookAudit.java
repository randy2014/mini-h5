package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawler_authorized_book_audit")
public class CrawlerAuthorizedBookAudit {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long authorizedBookId;
    public String action;
    public String beforeJson;
    public String afterJson;
    public Long operatorId;
    public String remark;
    public LocalDateTime createdAt;
}
