package com.mini.novel.api.support;

import com.mini.novel.book.entity.Novel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class VipPublicationProgress {
    private final JdbcTemplate jdbc;
    public VipPublicationProgress(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Novel enrich(Novel novel) {
        if (novel == null || novel.getSourceUrl() == null || !novel.getSourceUrl().contains("book.xbookcn.net")) return novel;
        Integer approved = jdbc.queryForObject("SELECT COUNT(*) FROM chapter WHERE novel_id=?", Integer.class, novel.getId());
        int approvedCount = approved == null ? 0 : approved;
        novel.setApprovedChapterCount(approvedCount);
        novel.setTotalChapterCount(approvedCount);
        novel.setReviewProgress(approvedCount + "章已开放");
        novel.setPublishStatus(approvedCount > 0 ? "PUBLISHED" : "UNPUBLISHED");
        return novel;
    }

}
