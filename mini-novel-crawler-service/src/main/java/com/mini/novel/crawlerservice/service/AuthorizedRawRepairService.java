package com.mini.novel.crawlerservice.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class AuthorizedRawRepairService {
    private static final String SOURCE_CODE = "xbookcn_authorized";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AuthorizedRawRepairService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public RepairResult repair(int limit, Long afterAuthorizedBookId, boolean dryRun) {
        ensureCursorTable();
        int size = Math.max(1, Math.min(50, limit));
        Long cursor = afterAuthorizedBookId == null ? readCursor() : afterAuthorizedBookId;
        List<AuthorizedBookRow> books = jdbc.query("""
                SELECT ab.id, ab.source_code, ab.source_book_id, ab.book_url
                FROM mini_novel_crawler.crawler_authorized_book ab
                JOIN mini_novel_crawler.crawl_book_raw br
                  ON br.source_code = ab.source_code AND br.source_url = ab.book_url
                WHERE ab.source_code = ? AND ab.id > ?
                GROUP BY ab.id, ab.source_code, ab.source_book_id, ab.book_url
                HAVING COUNT(br.id) > 1
                ORDER BY ab.id
                LIMIT ?
                """, (rs, rowNum) -> authorizedBook(rs), SOURCE_CODE, cursor == null ? 0L : cursor, size);
        RepairResult result = new RepairResult();
        result.dryRun = dryRun;
        result.afterAuthorizedBookId = cursor;
        for (AuthorizedBookRow book : books) {
            BookRepairStats stats = dryRun ? previewBook(book) : tx.execute(status -> repairBook(book));
            result.merge(stats);
            result.afterAuthorizedBookId = book.id;
            if (!dryRun) {
                writeCursor(book.id, stats);
            }
        }
        return result;
    }

    public RepairSummary summary() {
        RepairSummary summary = new RepairSummary();
        summary.duplicateRawBooks = singleLong("""
                SELECT COUNT(*) FROM mini_novel_crawler.crawl_book_raw dup
                JOIN mini_novel_crawler.crawler_authorized_book ab
                  ON ab.source_code = dup.source_code AND ab.book_url = dup.source_url
                LEFT JOIN mini_novel_crawler.crawl_book_raw canon
                  ON canon.source_code = ab.source_code AND canon.source_book_id = ab.source_book_id
                WHERE dup.source_code = ? AND (canon.id IS NULL OR dup.id <> canon.id)
                """, SOURCE_CODE);
        summary.metadataOnlyBooks = singleLong("SELECT COUNT(*) FROM mini_novel_crawler.crawl_book_raw WHERE source_code=? AND content_status='META_ONLY'", SOURCE_CODE);
        summary.completeBooks = singleLong("""
                SELECT COUNT(DISTINCT br.id)
                FROM mini_novel_crawler.crawl_book_raw br
                JOIN mini_novel_crawler.crawl_chapter_raw cr ON cr.book_raw_id = br.id
                JOIN mini_novel_crawler.crawl_content_raw ct ON ct.chapter_raw_id = cr.id AND ct.content_length > 0
                WHERE br.source_code = ? AND br.content_status IN ('CONTENT_READY','PENDING_REVIEW')
                """, SOURCE_CODE);
        summary.missingContentBooks = singleLong("""
                SELECT COUNT(DISTINCT br.id)
                FROM mini_novel_crawler.crawl_book_raw br
                JOIN mini_novel_crawler.crawl_chapter_raw cr ON cr.book_raw_id = br.id
                LEFT JOIN mini_novel_crawler.crawl_content_raw ct ON ct.chapter_raw_id = cr.id AND ct.content_length > 0
                WHERE br.source_code = ? AND ct.id IS NULL
                """, SOURCE_CODE);
        return summary;
    }

    private BookRepairStats repairBook(AuthorizedBookRow authorizedBook) {
        BookRepairStats stats = previewBook(authorizedBook);
        RawBook canonical = canonicalRawBook(authorizedBook, true);
        if (canonical == null) {
            return stats;
        }
        List<RawBook> duplicates = duplicateRawBooks(authorizedBook, canonical.id);
        for (RawBook duplicate : duplicates) {
            mergeDuplicateRaw(canonical, duplicate, stats);
            markDuplicateRaw(canonical.id, duplicate.id);
        }
        refreshBookStatus(canonical.id);
        stats.repairedBooks = duplicates.isEmpty() ? 0 : 1;
        return stats;
    }

    private BookRepairStats previewBook(AuthorizedBookRow authorizedBook) {
        BookRepairStats stats = new BookRepairStats();
        RawBook canonical = canonicalRawBook(authorizedBook, false);
        if (canonical == null) {
            return stats;
        }
        List<RawBook> duplicates = duplicateRawBooks(authorizedBook, canonical.id);
        stats.canonicalBooks = 1;
        stats.duplicateRawBooks = duplicates.size();
        for (RawBook duplicate : duplicates) {
            stats.duplicateChapters += singleLong("SELECT COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw WHERE book_raw_id=?", duplicate.id);
            stats.duplicateContents += singleLong("""
                    SELECT COUNT(*) FROM mini_novel_crawler.crawl_content_raw ct
                    JOIN mini_novel_crawler.crawl_chapter_raw cr ON cr.id = ct.chapter_raw_id
                    WHERE cr.book_raw_id=?
                    """, duplicate.id);
        }
        return stats;
    }

    private RawBook canonicalRawBook(AuthorizedBookRow authorizedBook, boolean promoteWhenMissing) {
        RawBook canonical = queryRaw("""
                SELECT id, source_book_id, source_url, content_status
                FROM mini_novel_crawler.crawl_book_raw
                WHERE source_code=? AND source_book_id=?
                LIMIT 1
                """, authorizedBook.sourceCode, authorizedBook.sourceBookId);
        if (canonical != null) {
            return canonical;
        }
        RawBook promoted = queryRaw("""
                SELECT id, source_book_id, source_url, content_status
                FROM mini_novel_crawler.crawl_book_raw
                WHERE source_code=? AND source_url=?
                ORDER BY id
                LIMIT 1
                """, authorizedBook.sourceCode, authorizedBook.bookUrl);
        if (promoted == null) {
            return null;
        }
        if (!promoteWhenMissing) {
            return promoted;
        }
        jdbc.update("""
                UPDATE mini_novel_crawler.crawl_book_raw
                SET source_book_id=?, raw_json=JSON_SET(COALESCE(raw_json, JSON_OBJECT()), '$.authorizedBookId', ?, '$.canonicalPromotedAt', ?), updated_at=NOW()
                WHERE id=?
                """, authorizedBook.sourceBookId, authorizedBook.id, LocalDateTime.now().toString(), promoted.id);
        promoted.sourceBookId = authorizedBook.sourceBookId;
        return promoted;
    }

    private List<RawBook> duplicateRawBooks(AuthorizedBookRow authorizedBook, long canonicalId) {
        return jdbc.query("""
                SELECT id, source_book_id, source_url, content_status
                FROM mini_novel_crawler.crawl_book_raw
                WHERE source_code=? AND source_url=? AND id<>?
                ORDER BY id
                """, (rs, rowNum) -> rawBook(rs), authorizedBook.sourceCode, authorizedBook.bookUrl, canonicalId);
    }

    private void mergeDuplicateRaw(RawBook canonical, RawBook duplicate, BookRepairStats stats) {
        List<RawChapter> duplicateChapters = jdbc.query("""
                SELECT id, source_chapter_id, source_url, chapter_no, title, content_status
                FROM mini_novel_crawler.crawl_chapter_raw
                WHERE book_raw_id=?
                ORDER BY id
                """, (rs, rowNum) -> rawChapter(rs), duplicate.id);
        for (RawChapter duplicateChapter : duplicateChapters) {
            RawChapter target = targetChapter(canonical.id, duplicateChapter);
            if (target == null) {
                jdbc.update("UPDATE mini_novel_crawler.crawl_chapter_raw SET book_raw_id=?, updated_at=NOW() WHERE id=?",
                        canonical.id, duplicateChapter.id);
                stats.mergedChapters++;
                moveContentIfNeeded(duplicateChapter.id, duplicateChapter.id, stats);
                continue;
            }
            mergeChapter(target, duplicateChapter, stats);
        }
    }

    private RawChapter targetChapter(long canonicalBookRawId, RawChapter duplicateChapter) {
        RawChapter byId = null;
        if (StringUtils.hasText(duplicateChapter.sourceChapterId)) {
            byId = queryChapter("""
                    SELECT id, source_chapter_id, source_url, chapter_no, title, content_status
                    FROM mini_novel_crawler.crawl_chapter_raw
                    WHERE book_raw_id=? AND source_chapter_id=?
                    LIMIT 1
                    """, canonicalBookRawId, duplicateChapter.sourceChapterId);
        }
        if (byId != null) {
            return byId;
        }
        if (!StringUtils.hasText(duplicateChapter.sourceUrl)) {
            return null;
        }
        return queryChapter("""
                SELECT id, source_chapter_id, source_url, chapter_no, title, content_status
                FROM mini_novel_crawler.crawl_chapter_raw
                WHERE book_raw_id=? AND source_url=?
                LIMIT 1
                """, canonicalBookRawId, duplicateChapter.sourceUrl);
    }

    private void mergeChapter(RawChapter target, RawChapter duplicate, BookRepairStats stats) {
        boolean targetHasContent = hasContent(target.id);
        boolean duplicateHasContent = hasContent(duplicate.id);
        if (AuthorizedRawRepairPlanner.shouldMoveContent(targetHasContent, duplicateHasContent)) {
            moveContentIfNeeded(duplicate.id, target.id, stats);
        }
        if (AuthorizedRawRepairPlanner.shouldPromoteStatus(target.contentStatus, duplicate.contentStatus)) {
            jdbc.update("UPDATE mini_novel_crawler.crawl_chapter_raw SET content_status=?, updated_at=NOW() WHERE id=?",
                    duplicate.contentStatus, target.id);
            if ("CONTENT_READY".equals(target.contentStatus) || "PENDING_REVIEW".equals(target.contentStatus) || "REVIEW_REJECTED".equals(target.contentStatus)) {
                stats.preservedManualOrReadyStatus++;
            }
        } else if (!String.valueOf(target.contentStatus).equals(String.valueOf(duplicate.contentStatus))
                && ("CONTENT_READY".equals(target.contentStatus) || "PENDING_REVIEW".equals(target.contentStatus) || "REVIEW_REJECTED".equals(target.contentStatus))) {
            stats.preservedManualOrReadyStatus++;
        }
        jdbc.update("""
                UPDATE mini_novel_crawler.crawl_chapter_raw
                SET content_status='DUPLICATE_RAW', updated_at=NOW()
                WHERE id=?
                """, duplicate.id);
        stats.mergedChapters++;
    }

    private void moveContentIfNeeded(long fromChapterId, long toChapterId, BookRepairStats stats) {
        if (fromChapterId == toChapterId) {
            stats.mergedContents += hasContent(toChapterId) ? 1 : 0;
            return;
        }
        if (hasContent(toChapterId) || !hasContent(fromChapterId)) {
            return;
        }
        jdbc.update("UPDATE mini_novel_crawler.crawl_content_raw SET chapter_raw_id=? WHERE chapter_raw_id=?",
                toChapterId, fromChapterId);
        stats.mergedContents++;
    }

    private boolean hasContent(long chapterRawId) {
        return singleLong("SELECT COUNT(*) FROM mini_novel_crawler.crawl_content_raw WHERE chapter_raw_id=? AND content_length>0", chapterRawId) > 0;
    }

    private void markDuplicateRaw(long canonicalId, long duplicateId) {
        jdbc.update("""
                UPDATE mini_novel_crawler.crawl_book_raw
                SET content_status='DUPLICATE_RAW',
                    raw_json=JSON_SET(COALESCE(raw_json, JSON_OBJECT()),
                      '$.canonicalBookRawId', ?,
                      '$.repairStatus', 'DUPLICATE_RAW',
                      '$.repairReason', ?),
                    updated_at=NOW()
                WHERE id=?
                """, canonicalId, "merged by xbookcn authorized raw repair", duplicateId);
    }

    private void refreshBookStatus(long canonicalBookRawId) {
        long ready = singleLong("""
                SELECT COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw cr
                JOIN mini_novel_crawler.crawl_content_raw ct ON ct.chapter_raw_id=cr.id AND ct.content_length>0
                WHERE cr.book_raw_id=?
                """, canonicalBookRawId);
        if (ready > 0) {
            jdbc.update("""
                    UPDATE mini_novel_crawler.crawl_book_raw
                    SET content_status=CASE WHEN content_status='CONTENT_READY' THEN 'CONTENT_READY' ELSE 'PENDING_REVIEW' END,
                        updated_at=NOW()
                    WHERE id=?
                    """, canonicalBookRawId);
        }
    }

    private void ensureCursorTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS mini_novel_crawler.xbookcn_raw_repair_cursor (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  repair_key VARCHAR(64) NOT NULL,
                  last_authorized_book_id BIGINT NOT NULL DEFAULT 0,
                  duplicate_raw_books INT NOT NULL DEFAULT 0,
                  repaired_books INT NOT NULL DEFAULT 0,
                  merged_chapters INT NOT NULL DEFAULT 0,
                  merged_contents INT NOT NULL DEFAULT 0,
                  preserved_statuses INT NOT NULL DEFAULT 0,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_repair_key (repair_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private Long readCursor() {
        List<Long> rows = jdbc.query("SELECT last_authorized_book_id FROM mini_novel_crawler.xbookcn_raw_repair_cursor WHERE repair_key='xbookcn_authorized_raw_v1'",
                (rs, rowNum) -> rs.getLong(1));
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    private void writeCursor(long authorizedBookId, BookRepairStats stats) {
        jdbc.update("""
                INSERT INTO mini_novel_crawler.xbookcn_raw_repair_cursor
                  (repair_key,last_authorized_book_id,duplicate_raw_books,repaired_books,merged_chapters,merged_contents,preserved_statuses)
                VALUES ('xbookcn_authorized_raw_v1',?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                  last_authorized_book_id=VALUES(last_authorized_book_id),
                  duplicate_raw_books=duplicate_raw_books+VALUES(duplicate_raw_books),
                  repaired_books=repaired_books+VALUES(repaired_books),
                  merged_chapters=merged_chapters+VALUES(merged_chapters),
                  merged_contents=merged_contents+VALUES(merged_contents),
                  preserved_statuses=preserved_statuses+VALUES(preserved_statuses)
                """, authorizedBookId, stats.duplicateRawBooks, stats.repairedBooks,
                stats.mergedChapters, stats.mergedContents, stats.preservedManualOrReadyStatus);
    }

    private RawBook queryRaw(String sql, Object... args) {
        List<RawBook> rows = jdbc.query(sql, (rs, rowNum) -> rawBook(rs), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private RawChapter queryChapter(String sql, Object... args) {
        List<RawChapter> rows = jdbc.query(sql, (rs, rowNum) -> rawChapter(rs), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long singleLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private AuthorizedBookRow authorizedBook(ResultSet rs) throws SQLException {
        return new AuthorizedBookRow(rs.getLong("id"), rs.getString("source_code"), rs.getString("source_book_id"), rs.getString("book_url"));
    }

    private RawBook rawBook(ResultSet rs) throws SQLException {
        RawBook row = new RawBook();
        row.id = rs.getLong("id");
        row.sourceBookId = rs.getString("source_book_id");
        row.sourceUrl = rs.getString("source_url");
        row.contentStatus = rs.getString("content_status");
        return row;
    }

    private RawChapter rawChapter(ResultSet rs) throws SQLException {
        RawChapter row = new RawChapter();
        row.id = rs.getLong("id");
        row.sourceChapterId = rs.getString("source_chapter_id");
        row.sourceUrl = rs.getString("source_url");
        row.chapterNo = rs.getInt("chapter_no");
        row.title = rs.getString("title");
        row.contentStatus = rs.getString("content_status");
        return row;
    }

    private record AuthorizedBookRow(long id, String sourceCode, String sourceBookId, String bookUrl) {
    }

    private static class RawBook {
        long id;
        String sourceBookId;
        String sourceUrl;
        String contentStatus;
    }

    private static class RawChapter {
        long id;
        String sourceChapterId;
        String sourceUrl;
        int chapterNo;
        String title;
        String contentStatus;
    }

    public static class RepairResult {
        public boolean dryRun;
        public Long afterAuthorizedBookId;
        public int canonicalBooks;
        public int duplicateRawBooks;
        public int duplicateChapters;
        public int duplicateContents;
        public int repairedBooks;
        public int mergedChapters;
        public int mergedContents;
        public int preservedManualOrReadyStatus;

        void merge(BookRepairStats stats) {
            canonicalBooks += stats.canonicalBooks;
            duplicateRawBooks += stats.duplicateRawBooks;
            duplicateChapters += stats.duplicateChapters;
            duplicateContents += stats.duplicateContents;
            repairedBooks += stats.repairedBooks;
            mergedChapters += stats.mergedChapters;
            mergedContents += stats.mergedContents;
            preservedManualOrReadyStatus += stats.preservedManualOrReadyStatus;
        }
    }

    public static class RepairSummary {
        public long duplicateRawBooks;
        public long metadataOnlyBooks;
        public long missingContentBooks;
        public long completeBooks;
    }

    private static class BookRepairStats {
        int canonicalBooks;
        int duplicateRawBooks;
        int duplicateChapters;
        int duplicateContents;
        int repairedBooks;
        int mergedChapters;
        int mergedContents;
        int preservedManualOrReadyStatus;
    }
}
