package com.mini.novel.api.model;

import com.mini.novel.book.entity.Novel;
import java.util.List;

public class ChannelNovelsVo {
    private List<Novel> records;
    private long total;
    private boolean restricted;

    public List<Novel> getRecords() { return records; }
    public void setRecords(List<Novel> records) { this.records = records; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public boolean isRestricted() { return restricted; }
    public void setRestricted(boolean restricted) { this.restricted = restricted; }
}
