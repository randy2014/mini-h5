package com.mini.novel.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("vip_source_category_mapping")
public class VipSourceCategoryMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceCode;
    private String sourceCategoryName;
    private String normalizedName;
    private Long vipCategoryId;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getSourceCategoryName() { return sourceCategoryName; }
    public void setSourceCategoryName(String sourceCategoryName) { this.sourceCategoryName = sourceCategoryName; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public Long getVipCategoryId() { return vipCategoryId; }
    public void setVipCategoryId(Long vipCategoryId) { this.vipCategoryId = vipCategoryId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
