package com.mini.novel.crawlerservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.crawler.entity.CrawlerAuthorizedBookAudit;
import com.mini.novel.crawler.mapper.CrawlerAuthorizedBookAuditMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private static final String DEFAULT_SOURCE = "xbookcn_authorized";
    private static final int MAX_BATCH_SIZE = 100;
    private static final Set<String> BATCH_REVIEW_SOURCES = Set.of("h528_authorized", "novel69h_authorized");
    private final JdbcTemplate jdbc;
    private final CrawlerAuthorizedBookAuditMapper audits;
    private final ObjectMapper json;
    private final String adminToken;
    private final TransactionTemplate transactionTemplate;

    public ContentReviewController(JdbcTemplate jdbc, CrawlerAuthorizedBookAuditMapper audits, ObjectMapper json,
                                   @Value("${admin.review-token:dev-admin-token}") String adminToken,
                                   PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.audits = audits;
        this.json = json;
        this.adminToken = adminToken;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                               @RequestParam(required = false) String sourceCode) {
        requireAdmin(token);
        String source = authorizedVipSource(sourceCode);
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
        return Result.ok(nonNullCounts(jdbc.queryForMap(sql, source)));
    }

    @GetMapping("/books")
    public Result<Map<String, Object>> books(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                             @RequestParam(required = false) String sourceCode,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        requireAdmin(token);
        String source = authorizedVipSource(sourceCode);
        int safeSize = Math.max(1, Math.min(100, size));
        int safePage = Math.max(1, page);
        String where = " b.source_code=? AND c.content_status IN ('PENDING_REVIEW','RISK_BLOCKED','ENTRY_READY','REVIEW_REJECTED') ";
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT b.id) FROM mini_novel_crawler.crawl_book_raw b JOIN mini_novel_crawler.crawl_chapter_raw c ON c.book_raw_id=b.id WHERE" + where, Long.class, source);
        String sql = """
            SELECT b.id bookRawId,b.title,b.source_code sourceCode,COUNT(*) chapterCount,
              SUM(c.content_status='PENDING_REVIEW' AND r.id IS NOT NULL) reviewableCount,
              SUM(c.content_status='PENDING_REVIEW' AND r.id IS NULL) recrawlCount,
              SUM(c.content_status='RISK_BLOCKED') blockedCount,
              SUM(c.content_status='ENTRY_READY') missingCount,
              SUM(c.content_status='CONTENT_READY') readyCount,
              SUM(c.content_status='REVIEW_REJECTED') rejectedCount,
              GROUP_CONCAT(CASE WHEN c.content_status='PENDING_REVIEW' AND r.id IS NOT NULL THEN c.id END ORDER BY c.id) reviewableChapterIdsCsv
            FROM mini_novel_crawler.crawl_book_raw b
            JOIN mini_novel_crawler.crawl_chapter_raw c ON c.book_raw_id=b.id
            LEFT JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE b.source_code=?
            GROUP BY b.id,b.title,b.source_code
            HAVING reviewableCount>0 OR recrawlCount>0 OR blockedCount>0 OR missingCount>0 OR rejectedCount>0
            ORDER BY reviewableCount DESC,recrawlCount DESC,blockedCount DESC,b.id DESC LIMIT ? OFFSET ?
            """;
        List<Map<String, Object>> records = jdbc.queryForList(sql, source, safeSize, (safePage - 1) * safeSize);
        records.forEach(this::decorateBook);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("records", records); out.put("total", total == null ? 0 : total); out.put("page", safePage); out.put("size", safeSize);
        return Result.ok(out);
    }

    @GetMapping("/books/{bookRawId}/chapters")
    public Result<List<Map<String, Object>>> chapters(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                       @RequestParam(required = false) String sourceCode,
                                                       @PathVariable Long bookRawId) {
        requireAdmin(token);
        verifyBook(bookRawId, sourceCode);
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
                                               @RequestParam(required = false) String sourceCode,
                                               @PathVariable Long chapterRawId) {
        requireAdmin(token);
        String source = authorizedVipSource(sourceCode);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.id chapterRawId,c.title,r.content,r.content_length contentLength
            FROM mini_novel_crawler.crawl_chapter_raw c
            JOIN mini_novel_crawler.crawl_book_raw b ON b.id=c.book_raw_id
            JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE c.id=? AND b.source_code=? AND c.content_status='PENDING_REVIEW' LIMIT 1
            """, chapterRawId, source);
        if (rows.isEmpty()) throw new IllegalArgumentException("Chapter has no isolated pending-review content.");
        return Result.ok(rows.get(0));
    }

    @PostMapping("/chapters/{chapterRawId}/decision")
    @Transactional
    public Result<Map<String, Object>> decideChapter(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                      @RequestHeader(value = "X-Operator-Id", defaultValue = "0") Long operatorId,
                                                      @RequestParam(required = false) String sourceCode,
                                                      @PathVariable Long chapterRawId, @RequestBody Decision request) {
        requireAdmin(token); validateDecision(request, operatorId);
        return Result.ok(applyChapterDecision(chapterRawId, sourceCode, request, operatorId, "CHAPTER_REVIEW_"));
    }

    @PostMapping("/chapters/batch-decision")
    public Result<Map<String, Object>> decideChapters(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                       @RequestHeader(value = "X-Operator-Id", defaultValue = "0") Long operatorId,
                                                       @RequestParam String sourceCode,
                                                       @RequestBody BatchDecision request) {
        requireAdmin(token);
        validateBatchDecision(request, operatorId);
        if (!isBatchReviewSource(sourceCode)) {
            throw new IllegalArgumentException("Batch review is limited to h528_authorized and novel69h_authorized.");
        }
        authorizedVipSource(sourceCode);
        List<Long> ids = uniqueBatchIds(request.chapterRawIds);
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        for (Long chapterRawId : ids) {
            try {
                Map<String, Object> item = transactionTemplate.execute(status ->
                        applyChapterDecision(chapterRawId, sourceCode, request, operatorId, "BATCH_CHAPTER_REVIEW_"));
                results.add(item);
                success++;
            } catch (RuntimeException error) {
                results.add(Map.of("chapterRawId", chapterRawId, "success", false,
                        "reason", safeFailureReason(error)));
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestedCount", request.chapterRawIds.size());
        response.put("uniqueCount", ids.size());
        response.put("successCount", success);
        response.put("failureCount", ids.size() - success);
        response.put("results", results);
        return Result.ok(response);
    }

    private Map<String, Object> applyChapterDecision(Long chapterRawId, String sourceCode, Decision request,
                                                      Long operatorId, String actionPrefix) {
        Map<String, Object> chapter = chapterForUpdate(chapterRawId, sourceCode);
        String before = Objects.toString(chapter.get("contentStatus"), "");
        if ("RISK_BLOCKED".equals(before)) throw new IllegalArgumentException("Explicit-minor hard-blocked chapters cannot be approved or changed here.");
        if (!"PENDING_REVIEW".equals(before) || chapter.get("contentRawId") == null) throw new IllegalArgumentException("Only pending chapters with isolated content can be reviewed.");
        String after = "APPROVE".equals(request.decision) ? "CONTENT_READY" : "REVIEW_REJECTED";
        jdbc.update("UPDATE mini_novel_crawler.crawl_chapter_raw SET content_status=?,updated_at=NOW() WHERE id=?", after, chapterRawId);
        if ("APPROVE".equals(request.decision)) publishChapter(chapter, operatorId); else unpublishChapter(chapter);
        audit(chapter, before, after, operatorId, actionPrefix + request.decision, request.remark);
        recomputeBook(((Number) chapter.get("bookRawId")).longValue());
        return Map.of("chapterRawId", chapterRawId, "success", true, "before", before, "after", after);
    }

    @PostMapping("/books/{bookRawId}/decision")
    @Transactional
    public Result<Map<String, Object>> decideBook(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                   @RequestHeader(value = "X-Operator-Id", defaultValue = "0") Long operatorId,
                                                   @RequestParam(required = false) String sourceCode,
                                                   @PathVariable Long bookRawId, @RequestBody Decision request) {
        requireAdmin(token); validateDecision(request, operatorId);
        Map<String, Object> book = verifyBook(bookRawId, sourceCode);
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
            Map<String, Object> fullChapter = chapterForUpdate(chapterId, sourceCode);
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
    private void validateBatchDecision(BatchDecision request, Long operatorId) {
        validateDecision(request, operatorId);
        if (request.chapterRawIds == null || request.chapterRawIds.isEmpty()) {
            throw new IllegalArgumentException("At least one chapter is required.");
        }
        if (request.chapterRawIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("A batch cannot contain more than 100 chapters.");
        }
        if (request.chapterRawIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Chapter ids must be positive integers.");
        }
    }
    static List<Long> uniqueBatchIds(List<Long> ids) {
        return ids == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(ids));
    }
    static boolean isBatchReviewSource(String sourceCode) { return BATCH_REVIEW_SOURCES.contains(sourceCode); }
    private String safeFailureReason(RuntimeException error) {
        String message = error.getMessage();
        return StringUtils.hasText(message) ? message : "Review failed and this item was rolled back.";
    }
    private String authorizedVipSource(String sourceCode) {
        String source = StringUtils.hasText(sourceCode) ? sourceCode : DEFAULT_SOURCE;
        List<String> rows = jdbc.query("SELECT source_code FROM mini_novel_crawler.crawl_source WHERE source_code=? AND source_type='AUTHORIZED_VIP' LIMIT 1",
                (rs, row) -> rs.getString(1), source);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Review source must be an AUTHORIZED_VIP source.");
        }
        return source;
    }

    private Map<String, Object> verifyBook(Long id, String sourceCode) {
        String source = authorizedVipSource(sourceCode);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id bookRawId,source_code sourceCode,source_book_id sourceBookId,title,content_status contentStatus FROM mini_novel_crawler.crawl_book_raw WHERE id=? AND source_code=? LIMIT 1", id, source);
        if (rows.isEmpty()) throw new IllegalArgumentException("Review book does not exist."); return rows.get(0);
    }
    private Map<String, Object> chapterForUpdate(Long id, String sourceCode) {
        String source = authorizedVipSource(sourceCode);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT c.id chapterRawId,c.book_raw_id bookRawId,c.content_status contentStatus,r.id contentRawId,
                   c.chapter_no chapterNo,c.source_chapter_id sourceChapterId,c.title chapterTitle,c.source_url chapterSourceUrl,r.content,r.content_hash contentHash,
                   b.source_code sourceCode,b.source_book_id sourceBookId,b.source_url bookSourceUrl,
                   b.title bookTitle,b.author,b.intro,b.cover_url coverUrl,b.category_name categoryName
            FROM mini_novel_crawler.crawl_chapter_raw c JOIN mini_novel_crawler.crawl_book_raw b ON b.id=c.book_raw_id
            LEFT JOIN mini_novel_crawler.crawl_content_raw r ON r.chapter_raw_id=c.id
            WHERE c.id=? AND b.source_code=? FOR UPDATE
            """, id, source);
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
        String publicationUrl = chapter.get("bookSourceUrl") + "#rawBook=" + chapter.get("bookRawId");
        List<Long> ids = jdbc.query("SELECT id FROM mini_novel.novel WHERE source_url IN (?,?) ORDER BY source_url=? DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), publicationUrl, chapter.get("bookSourceUrl"), publicationUrl);
        Long novelId;
        if (ids.isEmpty()) {
            jdbc.update("""
                INSERT INTO mini_novel.novel(title,author,cover_url,intro,status,vip_required,free_chapter_count,
                  word_count,source_url,operator_id,created_at,updated_at)
                VALUES(?,?,?,?,1,1,0,0,?,?,NOW(),NOW())
                """, chapter.get("bookTitle"), Objects.toString(chapter.get("author"), ""), chapter.get("coverUrl"),
                    chapter.get("intro"), publicationUrl, operatorId);
            novelId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            novelId = ids.get(0);
            jdbc.update("UPDATE mini_novel.novel SET status=1,vip_required=1,free_chapter_count=0,offline_reason=NULL,offline_at=NULL,operator_id=?,updated_at=NOW() WHERE id=?", operatorId, novelId);
        }
        syncVipCategory(chapter, novelId);
        Long mappedChapterId = mappedChapterId(chapter, novelId);
        if (mappedChapterId != null) {
            jdbc.update("""
                UPDATE mini_novel.chapter
                SET chapter_no=?,title=?,content=?,is_vip=1,price_coin=0,source_url=?,updated_at=NOW()
                WHERE id=? AND novel_id=?
                """, chapter.get("chapterNo"), chapter.get("chapterTitle"), chapter.get("content"),
                    chapter.get("chapterSourceUrl"), mappedChapterId, novelId);
        } else {
            assertChapterSlotIsStable(chapter, novelId);
            jdbc.update("""
                INSERT INTO mini_novel.chapter(novel_id,chapter_no,title,content,is_vip,price_coin,source_url,created_at,updated_at)
                VALUES(?,?,?,?,1,0,?,NOW(),NOW())
                ON DUPLICATE KEY UPDATE title=VALUES(title),content=VALUES(content),is_vip=1,source_url=VALUES(source_url),updated_at=NOW()
                """, novelId, chapter.get("chapterNo"), chapter.get("chapterTitle"), chapter.get("content"), chapter.get("chapterSourceUrl"));
        }
        syncChapterMapping(chapter, novelId);
        refreshNovel(novelId);
    }

    private void syncVipCategory(Map<String, Object> chapter, Long novelId) {
        String sourceCategory = Objects.toString(chapter.get("categoryName"), "");
        String categoryName = normalizeVipCategoryName(sourceCategory);
        String normalizedName = normalizeCategoryKey(categoryName);
        Long categoryId = mappedVipCategoryId(Objects.toString(chapter.get("sourceCode"), ""), normalizedName);
        if (categoryId == null) {
            jdbc.update("""
                INSERT INTO mini_novel.vip_category(name,normalized_name,sort,enabled,created_at,updated_at)
                VALUES(?,?,100,1,NOW(),NOW())
                ON DUPLICATE KEY UPDATE name=VALUES(name),enabled=1,updated_at=NOW()
                """, categoryName, normalizedName);
            categoryId = jdbc.queryForObject("""
                SELECT id FROM mini_novel.vip_category WHERE normalized_name=? LIMIT 1
                """, Long.class, normalizedName);
        }
        jdbc.update("""
            INSERT INTO mini_novel.novel_vip_category_mapping
              (novel_id,vip_category_id,source_code,source_book_id,source_category_name,created_at,updated_at)
            VALUES(?,?,?,?,?,NOW(),NOW())
            ON DUPLICATE KEY UPDATE
              vip_category_id=VALUES(vip_category_id),source_code=VALUES(source_code),
              source_book_id=VALUES(source_book_id),source_category_name=VALUES(source_category_name),updated_at=NOW()
            """, novelId, categoryId, chapter.get("sourceCode"), chapter.get("sourceBookId"), limit(sourceCategory, 64));
    }

    private Long mappedVipCategoryId(String sourceCode, String normalizedName) {
        List<Long> ids = jdbc.query("""
            SELECT vip_category_id
            FROM mini_novel.vip_source_category_mapping
            WHERE source_code=? AND normalized_name=? AND enabled=1 LIMIT 1
            """, (rs, row) -> rs.getLong(1), sourceCode, normalizedName);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void syncChapterMapping(Map<String, Object> chapter, Long novelId) {
        Long novelMappingId = ensureNovelMapping(chapter, novelId);
        String publicationUrl = chapter.get("bookSourceUrl") + "#rawBook=" + chapter.get("bookRawId");
        jdbc.update("UPDATE mini_novel.novel SET source_url=? WHERE id=? AND source_url=?", publicationUrl, novelId, chapter.get("bookSourceUrl"));
        List<Long> chapterIds = jdbc.query("SELECT id FROM mini_novel.chapter WHERE novel_id=? AND source_url=? LIMIT 1",
                (rs, row) -> rs.getLong(1), novelId, chapter.get("chapterSourceUrl"));
        if (chapterIds.isEmpty()) {
            return;
        }
        jdbc.update("""
            INSERT INTO mini_novel.chapter_source_mapping
              (novel_mapping_id,chapter_id,source_chapter_id,source_url,source_title,chapter_no,is_vip,content_hash,content_status,created_at,updated_at)
            VALUES (?,?,?,?,?,?,1,?,'CONTENT_READY',NOW(),NOW())
            ON DUPLICATE KEY UPDATE
              chapter_id=VALUES(chapter_id),source_title=VALUES(source_title),source_url=VALUES(source_url),
              chapter_no=VALUES(chapter_no),is_vip=1,content_hash=VALUES(content_hash),content_status='CONTENT_READY',updated_at=NOW()
            """, novelMappingId, chapterIds.get(0), chapter.get("sourceChapterId"), chapter.get("chapterSourceUrl"),
                chapter.get("chapterTitle"), chapter.get("chapterNo"), chapter.get("contentHash"));
    }

    private Long mappedChapterId(Map<String, Object> chapter, Long novelId) {
        List<Long> ids = jdbc.query("""
            SELECT csm.chapter_id
            FROM mini_novel.novel_source_mapping nsm
            JOIN mini_novel.chapter_source_mapping csm ON csm.novel_mapping_id=nsm.id
            JOIN mini_novel.chapter c ON c.id=csm.chapter_id AND c.novel_id=?
            WHERE csm.novel_mapping_id=? AND csm.source_chapter_id=?
            LIMIT 1
            """, (rs, row) -> rs.getLong(1), novelId, ensureNovelMapping(chapter, novelId), chapter.get("sourceChapterId"));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void assertChapterSlotIsStable(Map<String, Object> chapter, Long novelId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id,source_url FROM mini_novel.chapter
            WHERE novel_id=? AND chapter_no=? LIMIT 1
            """, novelId, chapter.get("chapterNo"));
        if (rows.isEmpty()) {
            return;
        }
        Object existingUrl = rows.get(0).get("source_url");
        if (!Objects.equals(Objects.toString(existingUrl, ""), Objects.toString(chapter.get("chapterSourceUrl"), ""))) {
            throw new IllegalStateException("Chapter slot already belongs to another source URL; manual mapping repair is required.");
        }
    }

    private Long ensureNovelMapping(Map<String, Object> chapter, Long novelId) {
        String title = Objects.toString(chapter.get("bookTitle"), "");
        String author = Objects.toString(chapter.get("author"), "");
        String normalizedTitle = normalizeIdentity(title);
        String normalizedAuthor = normalizeIdentity(author);
        List<Long> identityIds = jdbc.query("""
            SELECT id FROM mini_novel.novel_identity
            WHERE normalized_title=? AND normalized_author=? LIMIT 1
            """, (rs, row) -> rs.getLong(1), normalizedTitle, normalizedAuthor);
        Long identityId;
        if (identityIds.isEmpty()) {
            jdbc.update("""
                INSERT INTO mini_novel.novel_identity
                  (canonical_title,canonical_author,normalized_title,normalized_author,novel_id,match_status,confidence_score,created_at,updated_at)
                VALUES (?,?,?,?,?,'ACTIVE',100,NOW(),NOW())
                """, limit(title, 128), limit(StringUtils.hasText(author) ? author : "Unknown", 64),
                    limit(normalizedTitle, 128), limit(normalizedAuthor, 64), novelId);
            identityId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            identityId = identityIds.get(0);
            jdbc.update("UPDATE mini_novel.novel_identity SET novel_id=?,updated_at=NOW() WHERE id=?", novelId, identityId);
        }
        jdbc.update("""
            INSERT INTO mini_novel.novel_source_mapping
              (identity_id,novel_id,source_code,source_book_id,source_url,source_title,source_author,content_status,match_status,confidence_score,last_crawled_at,created_at,updated_at)
            VALUES (?,?,?,?,?, ?,?,'CONTENT_READY','MATCHED',100,NOW(),NOW(),NOW())
            ON DUPLICATE KEY UPDATE
              identity_id=VALUES(identity_id),novel_id=VALUES(novel_id),source_url=VALUES(source_url),
              source_title=VALUES(source_title),source_author=VALUES(source_author),
              content_status='CONTENT_READY',match_status='MATCHED',confidence_score=100,last_crawled_at=NOW(),updated_at=NOW()
            """, identityId, novelId, chapter.get("sourceCode"), chapter.get("sourceBookId"),
                chapter.get("bookSourceUrl"), limit(title, 128), limit(StringUtils.hasText(author) ? author : "Unknown", 64));
        return jdbc.queryForObject("""
            SELECT id FROM mini_novel.novel_source_mapping
            WHERE source_code=? AND source_book_id=? LIMIT 1
            """, Long.class, chapter.get("sourceCode"), chapter.get("sourceBookId"));
    }

    private String normalizeIdentity(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.toLowerCase().replaceAll("[\\s\\p{Punct}]+", "").trim();
    }
    private String normalizeVipCategoryName(String value) {
        if (!StringUtils.hasText(value)) return "\u5176\u4ed6";
        String cleaned = value.replaceAll("[\\[\\]\"]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(cleaned) || "AUTHORIZED_VIP".equalsIgnoreCase(cleaned) || "UNKNOWN".equalsIgnoreCase(cleaned)) {
            return "\u5176\u4ed6";
        }
        return limit(cleaned, 64);
    }
    private String normalizeCategoryKey(String value) {
        String normalized = normalizeVipCategoryName(value).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "");
        return StringUtils.hasText(normalized) ? limit(normalized, 64) : "other";
    }
    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
    private void unpublishChapter(Map<String, Object> chapter) {
        String publicationUrl = chapter.get("bookSourceUrl") + "#rawBook=" + chapter.get("bookRawId");
        List<Long> ids = jdbc.query("SELECT id FROM mini_novel.novel WHERE source_url IN (?,?) ORDER BY source_url=? DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), publicationUrl, chapter.get("bookSourceUrl"), publicationUrl);
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
        String csv = Objects.toString(row.remove("reviewableChapterIdsCsv"), "");
        List<Long> ids = StringUtils.hasText(csv) ? java.util.Arrays.stream(csv.split(",")).map(Long::valueOf).toList() : List.of();
        row.put("reviewableChapterIds", ids);
    }
    private long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    public static class Decision { public String decision; public String remark; }
    public static class BatchDecision extends Decision { public List<Long> chapterRawIds; }

    @ExceptionHandler(SecurityException.class) @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    public Result<Void> unauthorized(SecurityException e) { return Result.fail(401, e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Result<Void> invalid(IllegalArgumentException e) { return Result.fail(400, e.getMessage()); }
}
