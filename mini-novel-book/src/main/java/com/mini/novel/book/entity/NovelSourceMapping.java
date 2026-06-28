package com.mini.novel.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("novel_source_mapping")
public class NovelSourceMapping {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long identityId;
    public Long novelId;
    public String sourceCode;
    public String sourceBookId;
    public String sourceUrl;
    public String sourceTitle;
    public String sourceAuthor;
    public String contentStatus;
    public String matchStatus;
    public Integer confidenceScore;
    public LocalDateTime lastCrawledAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
