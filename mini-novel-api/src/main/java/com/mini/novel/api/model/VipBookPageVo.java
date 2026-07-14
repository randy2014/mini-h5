package com.mini.novel.api.model;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.book.entity.Novel;
import java.util.List;

public class VipBookPageVo {
    private List<Novel> records;
    private long total;
    private long pages;
    private long page;
    private long pageSize;
    private boolean hasMore;

    public static VipBookPageVo from(Page<Novel> source) {
        VipBookPageVo result = new VipBookPageVo();
        result.setRecords(source.getRecords());
        result.setTotal(source.getTotal());
        result.setPages(source.getPages());
        result.setPage(source.getCurrent());
        result.setPageSize(source.getSize());
        result.setHasMore(source.getCurrent() < source.getPages());
        return result;
    }

    public List<Novel> getRecords() { return records; }
    public void setRecords(List<Novel> records) { this.records = records; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getPages() { return pages; }
    public void setPages(long pages) { this.pages = pages; }
    public long getPage() { return page; }
    public void setPage(long page) { this.page = page; }
    public long getPageSize() { return pageSize; }
    public void setPageSize(long pageSize) { this.pageSize = pageSize; }
    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
}
