package com.mini.novel.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("novel_identity")
public class NovelIdentity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String canonicalTitle;
    public String canonicalAuthor;
    public String normalizedTitle;
    public String normalizedAuthor;
    public Long novelId;
    public String matchStatus;
    public Integer confidenceScore;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
