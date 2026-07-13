package com.mini.novel.api.support;

import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.entity.Chapter;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class VipPublicationProgress {
    private final JdbcTemplate jdbc;
    public VipPublicationProgress(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Novel enrich(Novel novel) {
        if (novel == null || novel.getSourceUrl() == null || !novel.getSourceUrl().contains("book.xbookcn.net")) return novel;
        Integer approved = jdbc.queryForObject("SELECT COUNT(*) FROM chapter WHERE novel_id=?", Integer.class, novel.getId());
        List<Integer> totals = jdbc.query("""
            SELECT COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw c
            JOIN mini_novel_crawler.crawl_book_raw b ON b.id=c.book_raw_id
            WHERE b.source_code='xbookcn_authorized' AND b.source_url=?
            """, (rs, rowNum) -> rs.getInt(1), novel.getSourceUrl());
        int approvedCount = approved == null ? 0 : approved;
        int totalCount = totals.isEmpty() ? approvedCount : totals.get(0);
        novel.setApprovedChapterCount(approvedCount);
        novel.setTotalChapterCount(totalCount);
        novel.setReviewProgress(approvedCount + "/" + totalCount);
        novel.setPublishStatus(approvedCount > 0 && approvedCount < totalCount ? "REVIEWING" : approvedCount > 0 ? "PUBLISHED" : "UNPUBLISHED");
        return novel;
    }

    public boolean supports(Novel novel) {
        return novel != null && novel.getSourceUrl() != null && novel.getSourceUrl().contains("book.xbookcn.net");
    }

    public Page<Chapter> chapters(Novel novel, long page, long size) {
        long current = Math.max(1, page), pageSize = Math.max(1, Math.min(100, size));
        String countSql = """
            SELECT COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw rc
            JOIN mini_novel_crawler.crawl_book_raw rb ON rb.id=rc.book_raw_id
            WHERE rb.source_code='xbookcn_authorized' AND rb.source_url=?
              AND rc.content_status NOT IN ('RISK_BLOCKED','REVIEW_REJECTED')
            """;
        Long total = jdbc.queryForObject(countSql, Long.class, novel.getSourceUrl());
        String sql = """
            SELECT rc.id rawId,rc.chapter_no,mc.id approvedId,mc.title approvedTitle,mc.is_vip
            FROM mini_novel_crawler.crawl_chapter_raw rc
            JOIN mini_novel_crawler.crawl_book_raw rb ON rb.id=rc.book_raw_id
            LEFT JOIN chapter mc ON mc.novel_id=? AND mc.chapter_no=rc.chapter_no
            WHERE rb.source_code='xbookcn_authorized' AND rb.source_url=?
              AND rc.content_status NOT IN ('RISK_BLOCKED','REVIEW_REJECTED')
            ORDER BY rc.chapter_no,rc.id LIMIT ? OFFSET ?
            """;
        List<Chapter> records = jdbc.query(sql, (rs, rowNum) -> {
            Chapter chapter = new Chapter();
            boolean approved = rs.getObject("approvedId") != null;
            chapter.setId(approved ? rs.getLong("approvedId") : -rs.getLong("rawId"));
            chapter.setNovelId(novel.getId()); chapter.setChapterNo(rs.getInt("chapter_no"));
            chapter.setTitle(approved ? rs.getString("approvedTitle") : "第 " + rs.getInt("chapter_no") + " 章 · 待审核");
            chapter.setVip(true); chapter.setReadable(approved); chapter.setReviewStatus(approved ? "APPROVED" : "PENDING_REVIEW");
            return chapter;
        }, novel.getId(), novel.getSourceUrl(), pageSize, (current - 1) * pageSize);
        Page<Chapter> result = new Page<>(current, pageSize, total == null ? 0 : total);
        result.setRecords(records); return result;
    }
}
