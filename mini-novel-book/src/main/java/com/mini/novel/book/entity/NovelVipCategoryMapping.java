package com.mini.novel.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("novel_vip_category_mapping")
public class NovelVipCategoryMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Long vipCategoryId;
    private String sourceCode;
    private String sourceBookId;
    private String sourceCategoryName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long novelId) { this.novelId = novelId; }
    public Long getVipCategoryId() { return vipCategoryId; }
    public void setVipCategoryId(Long vipCategoryId) { this.vipCategoryId = vipCategoryId; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceBookId() { return sourceBookId; }
    public void setSourceBookId(String sourceBookId) { this.sourceBookId = sourceBookId; }
    public String getSourceCategoryName() { return sourceCategoryName; }
    public void setSourceCategoryName(String sourceCategoryName) { this.sourceCategoryName = sourceCategoryName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
