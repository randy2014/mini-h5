package com.mini.novel.api.model;

public class VipBookCategoryVo {
    private String key;
    private Long categoryId;
    private String categoryName;
    private long count;

    public VipBookCategoryVo() {
    }

    public VipBookCategoryVo(String key, Long categoryId, String categoryName, long count) {
        this.key = key;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.count = count;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
