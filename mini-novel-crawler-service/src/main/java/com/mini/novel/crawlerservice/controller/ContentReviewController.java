package com.mini.novel.crawlerservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.crawler.entity.CrawlerAuthorizedBookAudit;
import com.mini.novel.crawler.mapper.CrawlerAuthorizedBookAuditMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crawler/content-review")
public class ContentReviewController {
    private static final String SOURCE = "xbookcn_authorized";
    private final JdbcTemplate jdbc;
    private final CrawlerAuthorizedBookAuditMapper audits;
    private final ObjectMapper json;
    private final String adminToken;

    public ContentReviewController(JdbcTemplate jdbc, CrawlerAuthorizedBookAuditMapper audits, ObjectMapper json,
                                   @Value("${admin.review-token:dev-admin-token}") String adminToken) {
        this.jdbc = jdbc;
        this.audits = audits;
        this.json = json;
        this.adminToken = adminToken;
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAdmin(token);
        String sql = """
            SELECT
              SUM(c.content_status='PENDING_REVIEW') pendingTotal,
              SUM(c.content_status='PENDING_REVIEW' AND r.id IS NOT NULL) reviewableTotal,
              SUM(c.content_status='PENDING_REVIEW' AND r.id IS NULL) recrawlTotal,
              SUM(c.content_status='RISK_BLOCKED') blockedTotal
            FROM mini_novel_crawler.crawl_chapter_raw c
            JOIN mini_novel_crawler.crawl_book_raw b ON b.id=c.book_raw_id
            LEFT JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE b.source_code=?
            """;
        return Result.ok(nonNullCounts(jdbc.queryForMap(sql, SOURCE)));
    }

    @GetMapping("/books")
    public Result<Map<String, Object>> books(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        requireAdmin(token);
        int safeSize = Math.max(1, Math.min(100, size));
        int safePage = Math.max(1, page);
        String where = " b.source_code=? AND c.content_status IN ('PENDING_REVIEW','RISK_BLOCKED','ENTRY_READY','REVIEW_REJECTED') ";
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT b.id) FROM mini_novel_crawler.crawl_book_raw b JOIN mini_novel_crawler.crawl_chapter_raw c ON c.book_raw_id=b.id WHERE" + where, Long.class, SOURCE);
        String sql = """
            SELECT b.id bookRawId,b.title,b.source_code sourceCode,COUNT(*) chapterCount,
              SUM(c.content_status='PENDING_REVIEW' AND r.id IS NOT NULL) reviewableCount,
              SUM(c.content_status='PENDING_REVIEW' AND r.id IS NULL) recrawlCount,
              SUM(c.content_status='RISK_BLOCKED') blockedCount,
              SUM(c.content_status='ENTRY_READY') missingCount,
              SUM(c.content_status='CONTENT_READY') readyCount,
              SUM(c.content_status='REVIEW_REJECTED') rejectedCount
            FROM mini_novel_crawler.crawl_book_raw b
            JOIN mini_novel_crawler.crawl_chapter_raw c ON c.book_raw_id=b.id
            LEFT JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE b.source_code=?
            GROUP BY b.id,b.title,b.source_code
            HAVING reviewableCount>0 OR recrawlCount>0 OR blockedCount>0 OR missingCount>0 OR rejectedCount>0
            ORDER BY reviewableCount DESC,recrawlCount DESC,blockedCount DESC,b.id DESC LIMIT ? OFFSET ?
            """;
        List<Map<String, Object>> records = jdbc.queryForList(sql, SOURCE, safeSize, (safePage - 1) * safeSize);
        records.forEach(this::decorateBook);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records); out.put("total", total == null ? 0 : total); out.put("page", safePage); out.put("size", safeSize);
        return Result.ok(out);
    }

    @GetMapping("/books/{bookRawId}/chapters")
    public Result<List<Map<String, Object>>> chapters(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                       @PathVariable Long bookRawId) {
        requireAdmin(token);
        verifyBook(bookRawId);
        String sql = """
            SELECT c.id chapterRawId,c.chapter_no chapterNo,c.title,c.content_status contentStatus,
                   COALESCE(r.content_length,0) contentLength,r.id contentRawId
            FROM mini_novel_crawler.crawl_chapter_raw c
            LEFT JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE c.book_raw_id=? ORDER BY c.chapter_no,c.id
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, bookRawId);
        rows.forEach(row -> row.put("reviewState", reviewState(Objects.toString(row.get("contentStatus"), ""), row.get("contentRawId") != null)));
        return Result.ok(rows);
    }

    @GetMapping("/chapters/{chapterRawId}/content")
    public Result<Map<String, Object>> content(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                               @PathVariable Long chapterRawId) {
        requireAdmin(token);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.id chapterRawId,c.title,r.content,r.content_length contentLength
            FROM mini_novel_crawler.crawl_chapter_raw c
            JOIN mini_novel_crawler.crawl_book_raw b ON b.id=c.book_raw_id
            JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE c.id=? AND b.source_code=? AND c.content_status='PENDING_REVIEW' LIMIT 1
            """, chapterRawId, SOURCE);
        if (rows.isEmpty()) throw new IllegalArgumentException("Chapter has no isolated pending-review content.");
        return Result.ok(rows.get(0));
    }

    @PostMapping("/chapters/{chapterRawId}/decision")
    @Transactional
    public Result<Map<String, Object>> decideChapter(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                      @RequestHeader(value = "X-Operator-Id", defaultValue = "0") Long operatorId,
                                                      @PathVariable Long chapterRawId, @RequestBody Decision request) {
        requireAdmin(token); validateDecision(request, operatorId);
        Map<String, Object> chapter = chapterForUpdate(chapterRawId);
        String before = Objects.toString(chapter.get("contentStatus"), "");
        if ("RISK_BLOCKED".equals(before)) throw new IllegalArgumentException("Explicit-minor hard-blocked chapters cannot be approved or changed here.");
        if (!"PENDING_REVIEW".equals(before) || chapter.get("contentRawId") == null) throw new IllegalArgumentException("Only pending chapters with isolated content can be reviewed.");
        String after = "APPROVE".equals(request.decision) ? "CONTENT_READY" : "REVIEW_REJECTED";
        jdbc.update("UPDATE mini_novel_crawler.crawl_chapter_raw SET content_status=?,updated_at=NOW() WHERE id=?", after, chapterRawId);
        if ("APPROVE".equals(request.decision)) publishChapter(chapter, operatorId); else unpublishChapter(chapter);
        audit(chapter, before, after, operatorId, "CHAPTER_REVIEW_" + request.decision, request.remark);
        recomputeBook(((Number) chapter.get("bookRawId")).longValue());
        return Result.ok(Map.of("chapterRawId", chapterRawId, "before", before, "after", after));
    }

    @PostMapping("/books/{bookRawId}/decision")
    @Transactional
    public Result<Map<String, Object>> decideBook(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                   @RequestHeader(value = "X-Operator-Id", defaultValue = "0") Long operatorId,
                                                   @PathVariable Long bookRawId, @RequestBody Decision request) {
        requireAdmin(token); validateDecision(request, operatorId);
        Map<String, Object> book = verifyBook(bookRawId);
        List<Map<String, Object>> pending = jdbc.queryForList("""
            SELECT c.id chapterRawId,c.book_raw_id bookRawId,c.content_status contentStatus,r.id contentRawId
            FROM mini_novel_crawler.crawl_chapter_raw c LEFT JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE c.book_raw_id=? AND c.content_status='PENDING_REVIEW' FOR UPDATE
            """, bookRawId);
        if (pending.isEmpty()) throw new IllegalArgumentException("This book has no reviewable pending chapters.");
        long blocked = count(bookRawId, "RISK_BLOCKED");
        long missing = pending.stream().filter(row -> row.get("contentRawId") == null).count() + count(bookRawId, "ENTRY_READY");
        if ("APPROVE".equals(request.decision) && (blocked > 0 || missing > 0)) throw new IllegalArgumentException("Book approval requires no hard-blocked or missing chapters.");
        List<Map<String, Object>> reviewable = pending.stream().filter(row -> row.get("contentRawId") != null).toList();
        if (reviewable.isEmpty()) throw new IllegalArgumentException("This book has no isolated content available for review.");
        String after = "APPROVE".equals(request.decision) ? "CONTENT_READY" : "REVIEW_REJECTED";
        for (Map<String, Object> chapter : reviewable) {
            Long chapterId = ((Number) chapter.get("chapterRawId")).longValue();
            jdbc.update("UPDATE mini_novel_crawler.crawl_chapter_raw SET content_status=?,updated_at=NOW() WHERE id=?", after, chapterId);
            chapter.put("sourceCode", book.get("sourceCode")); chapter.put("sourceBookId", book.get("sourceBookId"));
            Map<String, Object> fullChapter = chapterForUpdate(chapterId);
            if ("APPROVE".equals(request.decision)) publishChapter(fullChapter, operatorId); else unpublishChapter(fullChapter);
            audit(chapter, "PENDING_REVIEW", after, operatorId, "BOOK_CHAPTER_REVIEW_" + request.decision, request.remark);
        }
        String bookStatus = recomputeBook(bookRawId);
        return Result.ok(Map.of("bookRawId", bookRawId, "reviewedChapters", reviewable.size(), "contentStatus", bookStatus));
    }

    static String reviewState(String status, boolean hasContent) {
        if ("RISK_BLOCKED".equals(status)) return "EXPLICIT_MINOR_BLOCKED";
        if ("PENDING_REVIEW".equals(status)) return hasContent ? "PENDING_REVIEW" : "MISSING";
        if ("CONTENT_READY".equals(status)) return "CONTENT_READY";
        if ("REVIEW_REJECTED".equals(status)) return "REVIEW_REJECTED";
        return "MISSING";
    }

    private void requireAdmin(String token) {
        if (!StringUtils.hasText(token) || !Objects.equals(adminToken, token)) throw new SecurityException("Administrator authentication is required.");
    }
    private void validateDecision(Decision request, Long operatorId) {
        if (request == null || !("APPROVE".equals(request.decision) || "REJECT".equals(request.decision))) throw new IllegalArgumentException("Decision must be APPROVE or REJECT.");
        if (!StringUtils.hasText(request.remark)) throw new IllegalArgumentException("Review remark is required.");
        if (operatorId == null || operatorId <= 0) throw new SecurityException("A valid administrator operator id is required.");
    }
    private Map<String, Object> verifyBook(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id bookRawId,source_code sourceCode,source_book_id sourceBookId,title,content_status contentStatus FROM mini_novel_crawler.crawl_book_raw WHERE id=? AND source_code=? LIMIT 1", id, SOURCE);
        if (rows.isEmpty()) throw new IllegalArgumentException("Review book does not exist."); return rows.get(0);
    }
    private Map<String, Object> chapterForUpdate(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.id chapterRawId,c.book_raw_id bookRawId,c.content_status contentStatus,r.id contentRawId,
                   c.chapter_no chapterNo,c.title chapterTitle,c.source_url chapterSourceUrl,r.content,
                   b.source_code sourceCode,b.source_book_id sourceBookId,b.source_url bookSourceUrl,
                   b.title bookTitle,b.author,b.intro,b.cover_url coverUrl
            FROM mini_novel_crawler.crawl_chapter_raw c JOIN mini_novel_crawler.crawl_book_raw b ON b.id=c.book_raw_id
            LEFT JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE c.id=? AND b.source_code=? FOR UPDATE
            """, id, SOURCE);
        if (rows.isEmpty()) throw new IllegalArgumentException("Review chapter does not exist."); return rows.get(0);
    }
    private long count(Long bookId, String status) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM mini_novel_crawler.crawl_chapter_raw WHERE book_raw_id=? AND content_status=?", Long.class, bookId, status);
        return value == null ? 0 : value;
    }
    private String recomputeBook(Long bookId) {
        long blocked = count(bookId, "RISK_BLOCKED"), pending = count(bookId, "PENDING_REVIEW"), missing = count(bookId, "ENTRY_READY"), rejected = count(bookId, "REVIEW_REJECTED");
        String status = blocked > 0 || pending > 0 || missing > 0 ? "PENDING_REVIEW" : rejected > 0 ? "REVIEW_REJECTED" : "PUBLISH_READY";
        jdbc.update("UPDATE mini_novel_crawler.crawl_book_raw SET content_status=?,updated_at=NOW() WHERE id=?", status, bookId);
        return status;
    }
    private void publishChapter(Map<String, Object> chapter, Long operatorId) {
        Map<String, Object> permission = jdbc.queryForMap("""
            SELECT authorization_status authorizationStatus,review_status reviewStatus,risk_level riskLevel,
                   allow_store allowStore,allow_display allowDisplay,allow_vip_display allowVipDisplay
            FROM mini_novel_crawler.crawler_authorized_book WHERE source_code=? AND source_book_id=? LIMIT 1
            """, chapter.get("sourceCode"), chapter.get("sourceBookId"));
        if (!"AUTHORIZED".equals(permission.get("authorizationStatus")) || !"APPROVED".equals(permission.get("reviewStatus")) || "BLOCKED".equals(permission.get("riskLevel"))
                || !truthy(permission.get("allowStore")) || !truthy(permission.get("allowDisplay")) || !truthy(permission.get("allowVipDisplay"))) {
            throw new IllegalArgumentException("Authorized VIP store/display permissions are required before chapter approval.");
        }
        List<Long> ids = jdbc.query("SELECT id FROM mini_novel.novel WHERE source_url=? LIMIT 1",
                (rs, row) -> rs.getLong(1), chapter.get("bookSourceUrl"));
        Long novelId;
        if (ids.isEmpty()) {
            jdbc.update("""
                INSERT INTO mini_novel.novel(title,author,cover_url,intro,status,vip_required,free_chapter_count,
                  word_count,source_url,operator_id,created_at,updated_at)
                VALUES(?,?,?,?,1,1,0,0,?,?,NOW(),NOW())
                """, chapter.get("bookTitle"), Objects.toString(chapter.get("author"), ""), chapter.get("coverUrl"),
                    chapter.get("intro"), chapter.get("bookSourceUrl"), operatorId);
            novelId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            novelId = ids.get(0);
            jdbc.update("UPDATE mini_novel.novel SET status=1,vip_required=1,free_chapter_count=0,offline_reason=NULL,offline_at=NULL,operator_id=?,updated_at=NOW() WHERE id=?", operatorId, novelId);
        }
        jdbc.update("""
            INSERT INTO mini_novel.chapter(novel_id,chapter_no,title,content,is_vip,price_coin,source_url,created_at,updated_at)
            VALUES(?,?,?,?,1,0,?,NOW(),NOW())
            ON DUPLICATE KEY UPDATE title=VALUES(title),content=VALUES(content),is_vip=1,source_url=VALUES(source_url),updated_at=NOW()
            """, novelId, chapter.get("chapterNo"), chapter.get("chapterTitle"), chapter.get("content"), chapter.get("chapterSourceUrl"));
        refreshNovel(novelId);
    }
    private void unpublishChapter(Map<String, Object> chapter) {
        List<Long> ids = jdbc.query("SELECT id FROM mini_novel.novel WHERE source_url=? LIMIT 1",
                (rs, row) -> rs.getLong(1), chapter.get("bookSourceUrl"));
        if (ids.isEmpty()) return;
        Long novelId = ids.get(0);
        jdbc.update("DELETE FROM mini_novel.chapter WHERE novel_id=? AND chapter_no=?", novelId, chapter.get("chapterNo"));
        refreshNovel(novelId);
    }
    private void refreshNovel(Long novelId) {
        Long approved = jdbc.queryForObject("SELECT COUNT(*) FROM mini_novel.chapter WHERE novel_id=?", Long.class, novelId);
        if (approved == null || approved == 0) {
            jdbc.update("UPDATE mini_novel.novel SET status=0,latest_chapter_id=NULL,latest_chapter_title=NULL,word_count=0,updated_at=NOW() WHERE id=?", novelId);
            return;
        }
        jdbc.update("""
            UPDATE mini_novel.novel n SET n.status=1,n.vip_required=1,n.free_chapter_count=0,
              n.word_count=(SELECT COALESCE(SUM(CHAR_LENGTH(c.content)),0) FROM mini_novel.chapter c WHERE c.novel_id=n.id),
              n.latest_chapter_id=(SELECT c.id FROM mini_novel.chapter c WHERE c.novel_id=n.id ORDER BY c.chapter_no DESC LIMIT 1),
              n.latest_chapter_title=(SELECT c.title FROM mini_novel.chapter c WHERE c.novel_id=n.id ORDER BY c.chapter_no DESC LIMIT 1),
              n.updated_at=NOW() WHERE n.id=?
            """, novelId);
    }
    private boolean truthy(Object value) { return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() != 0; }
    private void audit(Map<String, Object> row, String before, String after, Long operatorId, String action, String remark) {
        Long authorizedId = jdbc.queryForObject("SELECT id FROM mini_novel_crawler.crawler_authorized_book WHERE source_code=? AND source_book_id=? LIMIT 1", Long.class, row.get("sourceCode"), row.get("sourceBookId"));
        if (authorizedId == null) throw new IllegalArgumentException("Authorized-book audit target is missing.");
        CrawlerAuthorizedBookAudit audit = new CrawlerAuthorizedBookAudit(); audit.authorizedBookId = authorizedId; audit.action = action; audit.operatorId = operatorId; audit.remark = remark; audit.createdAt = LocalDateTime.now();
        try { audit.beforeJson = json.writeValueAsString(Map.of("chapterRawId", row.get("chapterRawId"), "contentStatus", before)); audit.afterJson = json.writeValueAsString(Map.of("chapterRawId", row.get("chapterRawId"), "contentStatus", after)); }
        catch (Exception e) { throw new IllegalStateException(e); }
        audits.insert(audit);
    }
    private Map<String, Object> nonNullCounts(Map<String, Object> source) { source.replaceAll((k, v) -> v == null ? 0 : v); return source; }
    private void decorateBook(Map<String, Object> row) {
        long reviewable = number(row.get("reviewableCount")), recrawl = number(row.get("recrawlCount")), blocked = number(row.get("blockedCount"));
        List<String> labels = new ArrayList<>(); if (blocked > 0) labels.add("EXPLICIT_MINOR_BLOCKED"); if (reviewable > 0) labels.add("PENDING_REVIEW"); if (recrawl > 0) labels.add("MISSING_CONTENT"); row.put("riskLabels", labels);
    }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    public static class Decision { public String decision; public String remark; }

    @ExceptionHandler(SecurityException.class) @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    public Result<Void> unauthorized(SecurityException e) { return Result.fail(401, e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Result<Void> invalid(IllegalArgumentException e) { return Result.fail(400, e.getMessage()); }
}
