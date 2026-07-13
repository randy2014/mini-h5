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
        Long rawBookId = rawBookId(novel.getSourceUrl());
        List<Integer> totals = rawBookId == null
                ? jdbc.query("""
                    SELECT COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw c
                    JOIN mini_novel_crawler.crawl_book_raw b ON b.id=c.book_raw_id
                    WHERE b.id=(SELECT MAX(b2.id) FROM mini_novel_crawler.crawl_book_raw b2
                                WHERE b2.source_code='xbookcn_authorized' AND b2.source_url=?)
                    """, (rs, rowNum) -> rs.getInt(1), novel.getSourceUrl())
                : jdbc.query("""
                    SELECT COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw c
                    JOIN mini_novel_crawler.crawl_book_raw b ON b.id=c.book_raw_id
                    WHERE b.source_code='xbookcn_authorized' AND b.id=?
                    """, (rs, rowNum) -> rs.getInt(1), rawBookId);
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
        Long rawBookId = rawBookId(novel.getSourceUrl());
        String identity = rawBookId == null ? "rb.source_url=?" : "rb.id=?";
        Object identityValue = rawBookId == null ? novel.getSourceUrl() : rawBookId;
        String countSql = """
            SELECT COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw rc
            JOIN mini_novel_crawler.crawl_book_raw rb ON rb.id=rc.book_raw_id
            WHERE rb.source_code='xbookcn_authorized' AND %s
              AND rc.content_status NOT IN ('RISK_BLOCKED','REVIEW_REJECTED')
            """.formatted(identity);
        Long total = jdbc.queryForObject(countSql, Long.class, identityValue);
        String sql = """
            SELECT rc.id rawId,rc.chapter_no,mc.id approvedId,mc.title approvedTitle,mc.is_vip
            FROM mini_novel_crawler.crawl_chapter_raw rc
            JOIN mini_novel_crawler.crawl_book_raw rb ON rb.id=rc.book_raw_id
            LEFT JOIN chapter mc ON mc.novel_id=? AND mc.chapter_no=rc.chapter_no
            WHERE rb.source_code='xbookcn_authorized' AND %s
              AND rc.content_status NOT IN ('RISK_BLOCKED','REVIEW_REJECTED')
            ORDER BY rc.chapter_no,rc.id LIMIT ? OFFSET ?
            """.formatted(identity);
        List<Chapter> records = jdbc.query(sql, (rs, rowNum) -> {
            Chapter chapter = new Chapter();
            boolean approved = rs.getObject("approvedId") != null;
            chapter.setId(approved ? rs.getLong("approvedId") : -rs.getLong("rawId"));
            chapter.setNovelId(novel.getId()); chapter.setChapterNo(rs.getInt("chapter_no"));
            chapter.setTitle(approved ? rs.getString("approvedTitle") : "第 " + rs.getInt("chapter_no") + " 章 · 待审核");
            chapter.setVip(true); chapter.setReadable(approved); chapter.setReviewStatus(approved ? "APPROVED" : "PENDING_REVIEW");
            return chapter;
        }, novel.getId(), identityValue, pageSize, (current - 1) * pageSize);
        Page<Chapter> result = new Page<>(current, pageSize, total == null ? 0 : total);
        result.setRecords(records); return result;
    }
    private Long rawBookId(String sourceUrl) {
        String marker = "#rawBook=";
        int index = sourceUrl == null ? -1 : sourceUrl.indexOf(marker);
        if (index < 0) return null;
        try { return Long.valueOf(sourceUrl.substring(index + marker.length())); }
        catch (NumberFormatException ignored) { return null; }
    }
}
