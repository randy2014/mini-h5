package com.mini.novel.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("chapter_source_mapping")
public class ChapterSourceMapping {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long novelMappingId;
    public Long chapterId;
    public String sourceChapterId;
    public String sourceUrl;
    public String sourceTitle;
    public Integer chapterNo;
    @TableField("is_vip")
    public Boolean vip;
    public String contentHash;
    public String contentStatus;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
