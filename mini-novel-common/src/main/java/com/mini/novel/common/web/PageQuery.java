package com.mini.novel.common.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PageQuery {
    @Min(1)
    private long pageNo = 1;

    @Min(1)
    @Max(100)
    private long pageSize = 20;

    public long getPageNo() {
        return pageNo;
    }

    public void setPageNo(long pageNo) {
        this.pageNo = pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }
}
