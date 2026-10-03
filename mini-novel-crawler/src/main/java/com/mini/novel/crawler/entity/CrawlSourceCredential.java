package com.mini.novel.crawler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mini_novel_crawler.crawl_source_credential")
public class CrawlSourceCredential {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long sourceId;
    public String name;
    public Integer authMode;
    public String username;
    public String passwordCipher;
    public String cookieText;
    public String headersJson;
    public String loginUrl;
    public Integer status;
    public Boolean enabled;
    public Integer lastCheckStatus;
    public LocalDateTime lastCheckAt;
    public String remark;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
