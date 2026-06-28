package com.mini.novel.crawler.model;

import jakarta.validation.constraints.NotNull;

public class CrawlSubmitRequest {
    @NotNull
    private Long sourceId;
    private Long novelId;
    private String seedUrl;

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long novelId) { this.novelId = novelId; }
    public String getSeedUrl() { return seedUrl; }
    public void setSeedUrl(String seedUrl) { this.seedUrl = seedUrl; }
}
