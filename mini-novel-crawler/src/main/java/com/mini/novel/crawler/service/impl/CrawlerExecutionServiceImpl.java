package com.mini.novel.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.ChapterSourceMapping;
import com.mini.novel.book.entity.NovelSourceMapping;
import com.mini.novel.book.mapper.ChapterMapper;
import com.mini.novel.book.mapper.ChapterSourceMappingMapper;
import com.mini.novel.book.mapper.NovelSourceMappingMapper;
import com.mini.novel.crawler.entity.CrawlBookRaw;
import com.mini.novel.crawler.entity.CrawlChapterRaw;
import com.mini.novel.crawler.entity.CrawlContentRaw;
import com.mini.novel.crawler.entity.CrawlMergeTask;
import com.mini.novel.crawler.entity.CrawlRankSource;
import com.mini.novel.crawler.entity.CrawlTaskRecord;
import com.mini.novel.crawler.entity.CrawlerAuthorizedBook;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import com.mini.novel.crawler.mapper.CrawlBookRawMapper;
import com.mini.novel.crawler.mapper.CrawlChapterRawMapper;
import com.mini.novel.crawler.mapper.CrawlContentRawMapper;
import com.mini.novel.crawler.mapper.CrawlMergeTaskMapper;
import com.mini.novel.crawler.mapper.CrawlRankSourceMapper;
import com.mini.novel.crawler.mapper.CrawlTaskRecordMapper;
import com.mini.novel.crawler.mapper.CrawlerAuthorizedBookMapper;
import com.mini.novel.crawler.mapper.CrawlerSourceConfigMapper;
import com.mini.novel.crawler.parser.ContentRiskGuard;
import com.mini.novel.crawler.parser.CrawlerRuleConfig;
import com.mini.novel.crawler.parser.CrawlerSiteParser;
import com.mini.novel.crawler.parser.ParsedBookSeed;
import com.mini.novel.crawler.parser.ParsedBookSnapshot;
import com.mini.novel.crawler.parser.ParsedChapterSnapshot;
import com.mini.novel.crawler.service.CrawlerExecutionService;
import com.mini.novel.crawler.service.CompanyAuthorization;
import com.mini.novel.crawler.service.CrawlerMergeService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CrawlerExecutionServiceImpl implements CrawlerExecutionService {
    private static final Logger log = LoggerFactory.getLogger(CrawlerExecutionServiceImpl.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final int DEFAULT_MAX_BOOKS = 20;
    private static final int DEFAULT_MAX_CHAPTER_PAGES = 8;
    private static final int MAX_CHAPTER_PAGES_CAP = 30;
    private static final int FETCH_TIMEOUT_MILLIS = 45000;

    private final CrawlTaskRecordMapper taskMapper;
    private final CrawlerSourceConfigMapper sourceMapper;
    private final CrawlerAuthorizedBookMapper authorizedBookMapper;
    private final CrawlRankSourceMapper rankSourceMapper;
    private final CrawlBookRawMapper bookRawMapper;
    private final CrawlChapterRawMapper chapterRawMapper;
    private final CrawlContentRawMapper contentRawMapper;
    private final CrawlMergeTaskMapper mergeTaskMapper;
    private final NovelSourceMappingMapper novelSourceMappingMapper;
    private final ChapterSourceMappingMapper chapterSourceMappingMapper;
    private final ChapterMapper chapterMapper;
    private final CrawlerMergeService mergeService;
    private final List<CrawlerSiteParser> siteParsers;
    private final TaskExecutor applicationTaskExecutor;

    public CrawlerExecutionServiceImpl(CrawlTaskRecordMapper taskMapper,
                                       CrawlerSourceConfigMapper sourceMapper,
                                       CrawlerAuthorizedBookMapper authorizedBookMapper,
                                       CrawlRankSourceMapper rankSourceMapper,
                                       CrawlBookRawMapper bookRawMapper,
                                       CrawlChapterRawMapper chapterRawMapper,
                                       CrawlContentRawMapper contentRawMapper,
                                       CrawlMergeTaskMapper mergeTaskMapper,
                                       NovelSourceMappingMapper novelSourceMappingMapper,
                                       ChapterSourceMappingMapper chapterSourceMappingMapper,
                                       ChapterMapper chapterMapper,
                                       CrawlerMergeService mergeService,
                                       List<CrawlerSiteParser> siteParsers,
                                       @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor) {
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.authorizedBookMapper = authorizedBookMapper;
        this.rankSourceMapper = rankSourceMapper;
        this.bookRawMapper = bookRawMapper;
        this.chapterRawMapper = chapterRawMapper;
        this.contentRawMapper = contentRawMapper;
        this.mergeTaskMapper = mergeTaskMapper;
        this.novelSourceMappingMapper = novelSourceMappingMapper;
        this.chapterSourceMappingMapper = chapterSourceMappingMapper;
        this.chapterMapper = chapterMapper;
        this.mergeService = mergeService;
        this.siteParsers = siteParsers;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public void executeAsync(Long taskId) {
        applicationTaskExecutor.execute(() -> execute(taskId));
    }

    @Override
    public void execute(Long taskId) {
        CrawlTaskRecord task = taskMapper.selectById(taskId);
        if (task == null || !"PENDING".equals(task.status)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        task.status = "RUNNING";
        task.startedAt = now;
        task.updatedAt = now;
        task.message = "Crawler is reading rank pages, book details, catalogs and public chapter content.";
        taskMapper.updateById(task);

        int total = 0;
        int success = 0;
        int failed = 0;
        int eligibleCount = 0;
        int selectedCount = 0;
        int riskBlockedCount = 0;
        int pendingReviewCount = 0;
        int processedCount = 0;
        Long continuationId = null;
        try {
            CrawlerSourceConfig source = sourceMapper.selectById(task.sourceId);
            if (source == null) {
                throw new IllegalStateException("Crawler source not found: " + task.sourceId);
            }

            boolean authorizedContentTask = "AUTHORIZED_BOOK_CONTENT".equals(task.taskType);
            if (authorizedContentTask) source = authorizedContentSource(source);
            List<CrawlRankSource> ranks = loadRanks(task, source);
            for (CrawlRankSource rank : ranks) {
                validateUrl(rank.rankUrl);
                CrawlerSiteParser parser = selectParser(source, rank.rankUrl);
                List<ParsedBookSeed> seeds;
                List<CrawlerAuthorizedBook> selectedAuthorizedBooks = List.of();
                if (authorizedContentTask) {
                    List<CrawlerAuthorizedBook> eligibleBooks = authorizedBookMapper.selectList(authorizedEligibleBookQuery(source.sourceCode)
                            .orderByAsc("id")
                            .last("LIMIT 500"));
                    eligibleCount = eligibleBooks.size();
                    int limit = approvedContentLimit(task);
                    selectedAuthorizedBooks = eligibleBooks.stream()
                            .filter(book -> StringUtils.hasText(book.bookUrl))
                            .filter(book -> !isAuthorizedBookFinished(source, book))
                            .limit(limit)
                            .toList();
                    selectedCount = selectedAuthorizedBooks.size();
                    continuationId = selectedAuthorizedBooks.stream().map(book -> book.id).max(Long::compareTo).orElse(null);
                    seeds = selectedAuthorizedBooks.stream()
                            .filter(b -> StringUtils.hasText(b.bookUrl))
                            .map(b -> new ParsedBookSeed(b.bookUrl, b.title, b.author, "", 0L, "", rank.rankUrl))
                            .toList();
                } else {
                    Document rankPage = fetch(rank.rankUrl);
                    seeds = parser.parseBookSeeds(source, rankPage, rank.rankUrl, maxBooks(rank));
                }
                if (seeds.isEmpty() && isQidian(source, rank.rankUrl) && !rank.rankUrl.contains("m.qidian.com")) {
                    Document mobilePage = fetch("https://m.qidian.com/");
                    seeds = parser.parseBookSeeds(source, mobilePage, "https://m.qidian.com/", maxBooks(rank));
                }

                int rankDiscovered = seeds.size();
                int rankSaved = 0;
                int rankFailed = 0;
                log.info("Crawler rank started: taskId={}, rank={}, discovered={}",
                        task.id, rankLabel(rank), rankDiscovered);
                total += seeds.size();
                for (ParsedBookSeed seed : seeds) {
                    try {
                        CrawlBookRaw completedBook = completedReadyBook(source, seed);
                        if (completedBook != null) {
                            success++;
                            rankSaved++;
                            updateRunningProgress(task, total, success, failed, rank, seed);
                            continue;
                        }
                        if (!authorizedContentTask && isXbookcnAuthorizedSource(source) && !isAuthorizedMetadataMode(source)
                                && !canCrawlAuthorizedChapters(source, sourceBookIdFromUrl(seed.url()))) {
                            failed++;
                            rankFailed++;
                            updateRunningProgress(task, total, success, failed, rank, seed);
                            continue;
                        }
                        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, this::fetch);
                        if (!StringUtils.hasText(snapshot.title())) {
                            failed++;
                            rankFailed++;
                            updateRunningProgress(task, total, success, failed, rank, seed);
                            continue;
                        }
                        if (isAuthorizedMetadataMode(source)) {
                            upsertAuthorizedBook(source, snapshot);
                            success++;
                            rankSaved++;
                            updateRunningProgress(task, total, success, failed, rank, seed);
                            continue;
                        }
                        if (isXbookcnAuthorizedSource(source) && !canCrawlAuthorizedChapters(source, snapshot)) {
                            failed++;
                            rankFailed++;
                            updateRunningProgress(task, total, success, failed, rank, seed);
                            continue;
                        }
                        if (isSnapshotFullyMapped(source, snapshot)) {
                            success++;
                            rankSaved++;
                            updateRunningProgress(task, total, success, failed, rank, seed);
                            continue;
                        }
                        CrawlBookRaw book = upsertBookRaw(task, source, rank, snapshot);
                        if (isCompletedBookReady(book)) {
                            success++;
                            rankSaved++;
                            updateRunningProgress(task, total, success, failed, rank, seed);
                            updateMergeTask(task, true);
                            continue;
                        }
                        upsertChaptersAndContent(source, book, snapshot);
                        success++;
                        rankSaved++;
                        processedCount++;
                        if (authorizedContentTask) {
                            ChapterStatusStats stats = chapterStatusStats(book.id);
                            riskBlockedCount += stats.riskBlocked();
                            pendingReviewCount += stats.pendingReview();
                        }
                        updateRunningProgress(task, total, success, failed, rank, seed);
                        updateMergeTask(task, true);
                    } catch (Exception itemEx) {
                        failed++;
                        rankFailed++;
                        updateRunningProgress(task, total, success, failed, rank, seed);
                        log.warn("Crawler book failed: taskId={}, rank={}, bookUrl={}, message={}",
                                task.id, rankLabel(rank), seed.url(), itemEx.getMessage());
                    }
                }
                log.info("Crawler rank finished: taskId={}, rank={}, discovered={}, saved={}, failed={}",
                        task.id, rankLabel(rank), rankDiscovered, rankSaved, rankFailed);
            }

            if (total == 0) {
                task.status = authorizedContentTask ? "SUCCESS" : "NO_DATA";
                task.message = authorizedContentTask
                        ? authorizedContentMessage(eligibleCount, selectedCount, processedCount, success,
                        riskBlockedCount, pendingReviewCount, failed, continuationId)
                        : "Crawler finished, but no book was parsed. Check rank URL or source rules.";
            } else {
                task.status = failed == 0 ? "SUCCESS" : "PARTIAL_SUCCESS";
                task.message = authorizedContentTask
                        ? authorizedContentMessage(eligibleCount, selectedCount, processedCount, success,
                        riskBlockedCount, pendingReviewCount, failed, continuationId)
                        : "Crawler finished: discovered " + total + ", saved " + success
                        + ", failed " + failed + ", merge task reports merged counts.";
            }
        } catch (Exception ex) {
            task.status = "FAILED";
            task.message = "Crawler failed: " + ex.getMessage();
        } finally {
            task.totalCount = total;
            task.successCount = success;
            task.failCount = failed;
            task.finishedAt = LocalDateTime.now();
            task.updatedAt = task.finishedAt;
            taskMapper.updateById(task);
            updateMergeTask(task, false);
        }
    }

    private void updateRunningProgress(CrawlTaskRecord task, int total, int success, int failed,
                                       CrawlRankSource rank, ParsedBookSeed seed) {
        task.totalCount = total;
        task.successCount = success;
        task.failCount = failed;
        task.updatedAt = LocalDateTime.now();
        task.message = "Crawler is running: discovered " + total
                + ", saved " + success
                + ", failed " + failed
                + ", current rank " + limit(rankLabel(rank), 96)
                + ", current book " + limit(seed.url(), 160) + ".";
        taskMapper.updateById(task);
    }

    private QueryWrapper<CrawlerAuthorizedBook> authorizedEligibleBookQuery(String sourceCode) {
        return new QueryWrapper<CrawlerAuthorizedBook>()
                .eq("source_code", sourceCode)
                .eq("authorization_status", "AUTHORIZED")
                .eq("review_status", "APPROVED")
                .eq("allow_crawl_chapters", true)
                .ne("risk_level", "BLOCKED");
    }

    private int approvedContentLimit(CrawlTaskRecord task) {
        int limit = 5;
        if (task != null && StringUtils.hasText(task.targetUrl)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:#|&|\\?)limit=(\\d+)")
                    .matcher(task.targetUrl);
            if (matcher.find()) {
                limit = Integer.parseInt(matcher.group(1));
            }
        }
        return Math.max(1, Math.min(20, limit));
    }

    private boolean isAuthorizedBookFinished(CrawlerSourceConfig source, CrawlerAuthorizedBook authorizedBook) {
        if (source == null || authorizedBook == null || !StringUtils.hasText(authorizedBook.bookUrl)) {
            return false;
        }
        CrawlBookRaw book = bookRawMapper.selectOne(new QueryWrapper<CrawlBookRaw>()
                .eq("source_code", source.sourceCode)
                .eq("source_url", limit(authorizedBook.bookUrl, 512))
                .in("content_status", List.of("CONTENT_READY", "PENDING_REVIEW"))
                .last("LIMIT 1"));
        if (book == null || book.id == null) {
            return false;
        }
        Long chapters = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", book.id));
        return chapters != null && chapters > 0;
    }

    private ChapterStatusStats chapterStatusStats(Long bookRawId) {
        if (bookRawId == null) {
            return new ChapterStatusStats(0, 0);
        }
        Long riskBlocked = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", bookRawId)
                .eq("content_status", "RISK_BLOCKED"));
        Long pendingReview = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", bookRawId)
                .eq("content_status", "PENDING_REVIEW"));
        return new ChapterStatusStats(riskBlocked == null ? 0 : riskBlocked.intValue(),
                pendingReview == null ? 0 : pendingReview.intValue());
    }

    private String authorizedContentMessage(int eligible, int selected, int processed, int saved,
                                            int riskBlocked, int pendingReview, int failed, Long continuationId) {
        return "Approved adult-book content task finished: eligible=" + eligible
                + ", selected=" + selected
                + ", processed=" + processed
                + ", saved=" + saved
                + ", riskBlocked=" + riskBlocked
                + ", pendingReview=" + pendingReview
                + ", failed=" + failed
                + ", continuation=" + (continuationId == null ? "none" : "afterId:" + continuationId)
                + ", mode=authorized-book-list-batch.";
    }

    private record ChapterStatusStats(int riskBlocked, int pendingReview) {
    }

    private boolean isAuthorizedMetadataMode(CrawlerSourceConfig source) {
        if (!isXbookcnAuthorizedSource(source)) {
            return false;
        }
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        return rules.boolValue(false, "poc.metadataOnly", "metadataOnly", "authorizedBook.metadataOnly");
    }

    private CrawlerSourceConfig authorizedContentSource(CrawlerSourceConfig original) {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.id=original.id;source.sourceCode=original.sourceCode;source.name=original.name;source.baseUrl=original.baseUrl;
        source.sourceType=original.sourceType;source.authMode=original.authMode;source.enabled=original.enabled;source.priority=original.priority;
        source.ruleConfigJson=(original.ruleConfigJson==null?"{}":original.ruleConfigJson).replaceAll("(\\\"metadataOnly\\\"\\s*:\\s*)true", "$1false");
        return source;
    }

    private boolean isXbookcnAuthorizedSource(CrawlerSourceConfig source) {
        return source != null && "xbookcn_authorized".equalsIgnoreCase(source.sourceCode);
    }

    private boolean canCrawlAuthorizedChapters(CrawlerSourceConfig source, ParsedBookSnapshot snapshot) {
        if (!isXbookcnAuthorizedSource(source) || snapshot == null || !StringUtils.hasText(snapshot.sourceBookId())) {
            return false;
        }
        if (canCrawlAuthorizedChapters(source, snapshot.sourceBookId())) return true;
        return authorizedBookMapper.selectCount(new QueryWrapper<CrawlerAuthorizedBook>()
                .eq("source_code",source.sourceCode).eq("book_url",snapshot.sourceUrl())
                .eq("authorization_status","AUTHORIZED").eq("review_status","APPROVED")
                .eq("allow_crawl_chapters",true).ne("risk_level","BLOCKED"))>0;
    }

    private boolean canCrawlAuthorizedChapters(CrawlerSourceConfig source, String sourceBookId) {
        if (!isXbookcnAuthorizedSource(source) || !StringUtils.hasText(sourceBookId)) {
            return false;
        }
        CrawlerAuthorizedBook authorized = authorizedBookMapper.selectOne(new QueryWrapper<CrawlerAuthorizedBook>()
                .eq("source_code", source.sourceCode)
                .eq("source_book_id", sourceBookId)
                .eq("authorization_status", "AUTHORIZED")
                .eq("allow_crawl_chapters", true)
                .last("LIMIT 1"));
        return authorized != null;
    }

    private String sourceBookIdFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("/(?:book|novel)/(\\d+|[A-Za-z0-9_-]+)")
                .matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void upsertAuthorizedBook(CrawlerSourceConfig source, ParsedBookSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        ContentRiskGuard.RiskResult risk = ContentRiskGuard.evaluate(
                snapshot.title(), snapshot.intro(), "", rules.list("riskRules.blockedTerms"));

        CrawlerAuthorizedBook book = authorizedBookMapper.selectOne(new QueryWrapper<CrawlerAuthorizedBook>()
                .eq("source_code", source.sourceCode)
                .eq("source_book_id", snapshot.sourceBookId())
                .last("LIMIT 1"));
        if (book == null) {
            book = new CrawlerAuthorizedBook();
            book.sourceCode = source.sourceCode;
            book.sourceBookId = snapshot.sourceBookId();
            book.authorizationStatus = "PENDING";
            book.allowCrawlMeta = true;
            book.allowCrawlChapters = false;
            book.allowStore = false;
            book.allowDisplay = false;
            book.allowVipDisplay = false;
            book.reviewStatus = risk.reviewRequired() ? "RISK_REVIEW" : "PENDING";
            book.discoveredAt = now;
            book.createdAt = now;
        }
        book.bookUrl = limit(snapshot.sourceUrl(), 512);
        book.title = limit(snapshot.title(), 128);
        book.author = limit(StringUtils.hasText(snapshot.author()) ? snapshot.author() : "", 64);
        book.intro = snapshot.intro();
        book.categoryName = limit(snapshot.categoryName(), 64);
        book.tagsJson = StringUtils.hasText(snapshot.tagsJson()) ? snapshot.tagsJson() : "[]";
        book.coverUrl = limit(snapshot.coverUrl(), 512);
        book.riskLevel = risk.blocked() ? "BLOCKED" : risk.reviewRequired() ? "HIGH" : "LOW";
        book.riskReason = risk.reviewRequired() ? limit(risk.reason(), 1000) : null;
        if (risk.blocked()) {
            book.authorizationStatus = "REJECTED";
            book.reviewStatus = "RISK_REVIEW";
            book.allowCrawlChapters = false;
            book.allowStore = false;
            book.allowDisplay = false;
            book.allowVipDisplay = false;
        } else if (CompanyAuthorization.isActive(source, CompanyAuthorization.read(source), LocalDate.now())) {
            CompanyAuthorization.apply(book, CompanyAuthorization.read(source));
        }
        book.updatedAt = now;
        if (book.id == null) {
            authorizedBookMapper.insert(book);
        } else {
            authorizedBookMapper.updateById(book);
        }
    }

    private List<CrawlRankSource> loadRanks(CrawlTaskRecord task, CrawlerSourceConfig source) {
        if (task.rankSourceId != null) {
            CrawlRankSource rank = rankSourceMapper.selectById(task.rankSourceId);
            if (rank == null || !task.sourceId.equals(rank.sourceId) || (!"AUTHORIZED_BOOK_CONTENT".equals(task.taskType) && !Boolean.TRUE.equals(rank.enabled))) {
                return new ArrayList<>();
            }
            return new ArrayList<>(List.of(scopedRank(rank, task)));
        }
        List<CrawlRankSource> ranks = rankSourceMapper.selectList(new QueryWrapper<CrawlRankSource>()
                .eq("source_id", task.sourceId)
                .eq("enabled", true)
                .orderByAsc("id")
                .last("LIMIT 20"));
        if (!ranks.isEmpty()) {
            return ranks;
        }
        CrawlRankSource fallback = new CrawlRankSource();
        fallback.id = 0L;
        fallback.sourceId = source.id;
        fallback.rankName = "Site home";
        fallback.rankType = "HOME";
        fallback.rankUrl = source.baseUrl;
        fallback.maxBooks = DEFAULT_MAX_BOOKS;
        fallback.preferCompleted = true;
        fallback.enabled = true;
        return new ArrayList<>(List.of(fallback));
    }

    private CrawlerSiteParser selectParser(CrawlerSourceConfig source, String rankUrl) {
        return siteParsers.stream()
                .filter(parser -> parser.supports(source, rankUrl))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No available crawler parser"));
    }

    private Document fetch(String url) throws IOException {
        validateUrl(url);
        IOException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return Jsoup.connect(url)
                        .userAgent(url.contains("m.qidian.com") ? MOBILE_USER_AGENT : USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("Referer", origin(url))
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("Upgrade-Insecure-Requests", "1")
                        .timeout(FETCH_TIMEOUT_MILLIS)
                        .get();
            } catch (IOException ex) {
                lastException = ex;
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException;
    }

    private String origin(String url) {
        try { URI uri=URI.create(url);return uri.getScheme()+"://"+uri.getHost()+"/"; }
        catch (Exception ignored) { return url; }
    }

    private void sleepBeforeRetry(int attempt) {
        if (attempt >= 3) {
            return;
        }
        try {
            Thread.sleep(attempt * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private CrawlBookRaw upsertBookRaw(CrawlTaskRecord task, CrawlerSourceConfig source, CrawlRankSource rank,
                                       ParsedBookSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        String sourceBookId = StringUtils.hasText(snapshot.sourceBookId())
                ? snapshot.sourceBookId()
                : sha256(snapshot.sourceUrl()).substring(0, 24);
        CrawlBookRaw book = bookRawMapper.selectOne(new QueryWrapper<CrawlBookRaw>()
                .eq("source_code", source.sourceCode)
                .eq("source_book_id", sourceBookId)
                .last("LIMIT 1"));
        if (book == null) {
            book = new CrawlBookRaw();
            book.createdAt = now;
        }
        book.crawlTaskId = task.id;
        book.sourceCode = source.sourceCode;
        book.sourceBookId = sourceBookId;
        book.sourceUrl = limit(snapshot.sourceUrl(), 512);
        book.title = limit(snapshot.title(), 128);
        book.author = limit(StringUtils.hasText(snapshot.author()) ? snapshot.author() : "Unknown", 64);
        book.intro = snapshot.intro();
        book.coverUrl = limit(snapshot.coverUrl(), 512);
        book.categoryName = limit(firstNonBlank(snapshot.categoryName(), rank.rankName, "Unknown"), 64);
        book.bookStatus = normalizeBookStatus(snapshot.bookStatus());
        book.wordCount = snapshot.wordCount();
        book.heatScore = 0L;
        book.rankType = rank.rankType;
        boolean isolated = isIsolatedReviewSource(source);
        book.contentStatus = StringUtils.hasText(snapshot.chapterId()) ? "CATALOG_READY" : "META_ONLY";
        if (isolated) {
            book.contentStatus = "PENDING_REVIEW";
        }
        book.rawJson = "{\"rankName\":\"" + json(rank.rankName) + "\",\"rankUrl\":\"" + json(rank.rankUrl)
                + "\",\"isolation\":\"" + (isolated ? "VIP_REVIEW" : "NONE") + "\"}";
        book.crawledAt = now;
        book.updatedAt = now;
        if (book.id == null) {
            bookRawMapper.insert(book);
        } else {
            bookRawMapper.updateById(book);
        }
        return book;
    }

    private CrawlBookRaw completedReadyBook(CrawlerSourceConfig source, ParsedBookSeed seed) {
        if (source == null || seed == null || !StringUtils.hasText(seed.url())) {
            return null;
        }
        CrawlBookRaw book = bookRawMapper.selectOne(new QueryWrapper<CrawlBookRaw>()
                .eq("source_code", source.sourceCode)
                .eq("source_url", limit(seed.url(), 512))
                .eq("book_status", "COMPLETED")
                .last("LIMIT 1"));
        return isCompletedBookReady(book) ? book : null;
    }

    private boolean isSnapshotFullyMapped(CrawlerSourceConfig source, ParsedBookSnapshot snapshot) {
        if (source == null || snapshot == null || !StringUtils.hasText(snapshot.sourceBookId())) {
            return false;
        }
        List<ParsedChapterSnapshot> chapters = snapshot.chapters();
        if (chapters == null || chapters.isEmpty()) {
            return false;
        }
        NovelSourceMapping mapping = mappedReadyNovel(source.sourceCode, snapshot.sourceBookId());
        if (mapping == null) {
            return false;
        }
        for (ParsedChapterSnapshot chapter : chapters) {
            String sourceChapterId = StringUtils.hasText(chapter.chapterId())
                    ? chapter.chapterId()
                    : sha256(chapter.url()).substring(0, 24);
            if (!isChapterMappedWithContent(mapping, sourceChapterId)) {
                return false;
            }
        }
        return true;
    }

    private boolean isChapterMappedWithContent(CrawlerSourceConfig source, CrawlBookRaw book, String sourceChapterId) {
        if (source == null || book == null || !StringUtils.hasText(book.sourceBookId)
                || !StringUtils.hasText(sourceChapterId)) {
            return false;
        }
        NovelSourceMapping mapping = mappedReadyNovel(source.sourceCode, book.sourceBookId);
        return isChapterMappedWithContent(mapping, sourceChapterId);
    }

    private NovelSourceMapping mappedReadyNovel(String sourceCode, String sourceBookId) {
        if (!StringUtils.hasText(sourceCode) || !StringUtils.hasText(sourceBookId)) {
            return null;
        }
        NovelSourceMapping mapping = novelSourceMappingMapper.selectOne(new QueryWrapper<NovelSourceMapping>()
                .eq("source_code", sourceCode)
                .eq("source_book_id", sourceBookId)
                .eq("content_status", "CONTENT_READY")
                .last("LIMIT 1"));
        return mapping != null && mapping.novelId != null ? mapping : null;
    }

    private boolean isChapterMappedWithContent(NovelSourceMapping mapping, String sourceChapterId) {
        if (mapping == null || mapping.id == null || !StringUtils.hasText(sourceChapterId)) {
            return false;
        }
        ChapterSourceMapping chapterMapping = chapterSourceMappingMapper.selectOne(new QueryWrapper<ChapterSourceMapping>()
                .eq("novel_mapping_id", mapping.id)
                .eq("source_chapter_id", sourceChapterId)
                .eq("content_status", "MERGED")
                .last("LIMIT 1"));
        if (chapterMapping == null || chapterMapping.chapterId == null) {
            return false;
        }
        Chapter chapter = chapterMapper.selectById(chapterMapping.chapterId);
        return chapter != null && StringUtils.hasText(chapter.getContent());
    }

    private boolean isCompletedBookReady(CrawlBookRaw book) {
        return book != null && "COMPLETED".equals(book.bookStatus) && isBookFullyContentReady(book);
    }

    private boolean isBookFullyContentReady(CrawlBookRaw book) {
        if (book == null || book.id == null) {
            return false;
        }
        Long chapterCount = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", book.id));
        if (chapterCount == null || chapterCount <= 0) {
            return false;
        }
        Long readyChapterCount = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", book.id)
                .eq("content_status", "CONTENT_READY"));
        if (!chapterCount.equals(readyChapterCount)) {
            return false;
        }
        Long readyContentCount = contentRawMapper.selectCount(new QueryWrapper<CrawlContentRaw>()
                .inSql("chapter_raw_id", "SELECT id FROM mini_novel_crawler.crawl_chapter_raw WHERE book_raw_id = " + book.id)
                .gt("content_length", 0));
        return chapterCount.equals(readyContentCount);
    }

    private String normalizeBookStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "UNKNOWN";
        }
        String value = status.trim().toUpperCase();
        return switch (value) {
            case "COMPLETED", "FINISHED" -> "COMPLETED";
            case "SERIALIZING", "ONGOING" -> "SERIALIZING";
            default -> "UNKNOWN";
        };
    }

    private void upsertChaptersAndContent(CrawlerSourceConfig source, CrawlBookRaw book, ParsedBookSnapshot snapshot) {
        List<ParsedChapterSnapshot> chapters = snapshot.chapters();
        if (chapters == null || chapters.isEmpty()) {
            upsertChapterAndContent(source, book, snapshot, null);
            return;
        }
        int readyCount = 0;
        for (ParsedChapterSnapshot parsedChapter : chapters) {
            ParsedBookSnapshot chapterSnapshot = new ParsedBookSnapshot(
                    snapshot.title(),
                    snapshot.author(),
                    snapshot.coverUrl(),
                    snapshot.intro(),
                    snapshot.sourceUrl(),
                    snapshot.sourceBookId(),
                    snapshot.wordCount(),
                    parsedChapter.chapterId(),
                    parsedChapter.url());
            upsertChapterAndContent(source, book, chapterSnapshot, parsedChapter);
            CrawlChapterRaw raw = chapterRawMapper.selectOne(new QueryWrapper<CrawlChapterRaw>()
                    .eq("book_raw_id", book.id)
                    .eq("source_chapter_id", StringUtils.hasText(parsedChapter.chapterId())
                            ? parsedChapter.chapterId()
                            : sha256(parsedChapter.url()).substring(0, 24))
                    .last("LIMIT 1"));
            if (raw != null && "CONTENT_READY".equals(raw.contentStatus)) {
                readyCount++;
            }
        }
        book.contentStatus = isIsolatedReviewSource(source) ? "PENDING_REVIEW"
                : readyCount > 0 ? "CONTENT_READY" : "CATALOG_READY";
        bookRawMapper.updateById(book);
    }

    private void upsertChapterAndContent(CrawlerSourceConfig source, CrawlBookRaw book,
                                         ParsedBookSnapshot snapshot, ParsedChapterSnapshot parsedChapter) {
        if (!StringUtils.hasText(snapshot.chapterId()) && !StringUtils.hasText(snapshot.chapterUrl())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String sourceChapterId = StringUtils.hasText(snapshot.chapterId())
                ? snapshot.chapterId()
                : sha256(snapshot.chapterUrl()).substring(0, 24);
        if (isChapterMappedWithContent(source, book, sourceChapterId)) {
            return;
        }
        CrawlChapterRaw chapter = chapterRawMapper.selectOne(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", book.id)
                .eq("source_chapter_id", sourceChapterId)
                .last("LIMIT 1"));
        if (isContentReady(chapter)) {
            return;
        }
        if (chapter == null) {
            chapter = new CrawlChapterRaw();
            chapter.bookRawId = book.id;
            chapter.createdAt = now;
        }
        int chapterNo = parsedChapter == null || parsedChapter.chapterNo() <= 0 ? 1 : parsedChapter.chapterNo();
        String title = parsedChapter == null || !StringUtils.hasText(parsedChapter.title())
                ? "Public chapter entry #" + sourceChapterId
                : parsedChapter.title();
        boolean vip = parsedChapter != null && parsedChapter.vip();
        chapter.sourceChapterId = sourceChapterId;
        chapter.sourceUrl = limit(snapshot.chapterUrl(), 512);
        chapter.chapterNo = chapterNo;
        chapter.title = limit(title, 255);
        chapter.vip = vip;
        chapter.priceCoin = 0;
        chapter.contentStatus = "ENTRY_READY";
        chapter.crawledAt = now;
        chapter.updatedAt = now;

        String content = parsedChapter == null ? "" : parsedChapter.content();
        if (!StringUtils.hasText(content)) {
            content = fetchPublicChapterContent(snapshot.chapterUrl(), source);
        }
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        if (rules.boolValue(false, "riskRules.enabled")) {
            ContentRiskGuard.RiskResult risk = ContentRiskGuard.evaluate(
                    book.title, book.intro, content, rules.list("riskRules.blockedTerms"));
            if (risk.blocked()) {
                chapter.contentStatus = "RISK_BLOCKED";
                chapter.updatedAt = now;
                if (chapter.id == null) {
                    chapterRawMapper.insert(chapter);
                } else {
                    chapterRawMapper.updateById(chapter);
                }
                book.contentStatus = "PENDING_REVIEW";
                bookRawMapper.updateById(book);
                return;
            }
            if (risk.reviewRequired()) {
                chapter.contentStatus = "PENDING_REVIEW";
                book.contentStatus = "PENDING_REVIEW";
            }
        }
        if (StringUtils.hasText(content) && !"PENDING_REVIEW".equals(chapter.contentStatus)) {
            chapter.contentHash = sha256(content);
            chapter.contentStatus = "CONTENT_READY";
            book.contentStatus = "CONTENT_READY";
            bookRawMapper.updateById(book);
        }
        if (chapter.id == null) {
            chapterRawMapper.insert(chapter);
        } else {
            chapterRawMapper.updateById(chapter);
        }
        if (StringUtils.hasText(content) && "CONTENT_READY".equals(chapter.contentStatus)) {
            upsertContent(chapter, content);
        }
    }

    private boolean isIsolatedReviewSource(CrawlerSourceConfig source) {
        if (source == null) {
            return false;
        }
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        return rules.boolValue(false, "isolation.reviewOnly", "reviewOnly")
                || "AUTHORIZED_VIP".equalsIgnoreCase(source.sourceType);
    }

    private boolean isContentReady(CrawlChapterRaw chapter) {
        if (chapter == null || chapter.id == null || !"CONTENT_READY".equals(chapter.contentStatus)) {
            return false;
        }
        CrawlContentRaw raw = contentRawMapper.selectOne(new QueryWrapper<CrawlContentRaw>()
                .select("id", "chapter_raw_id", "content_hash", "content_length")
                .eq("chapter_raw_id", chapter.id)
                .last("LIMIT 1"));
        if (raw == null || raw.contentLength == null || raw.contentLength <= 0) {
            return false;
        }
        return !StringUtils.hasText(chapter.contentHash) || chapter.contentHash.equals(raw.contentHash);
    }

    private String fetchPublicChapterContent(String url, CrawlerSourceConfig source) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        try {
            Set<String> visited = new LinkedHashSet<>();
            List<String> pageContents = new ArrayList<>();
            String currentUrl = url;
            int maxPages = Math.max(1, Math.min(rules.intValue(DEFAULT_MAX_CHAPTER_PAGES,
                    "chapterRules.maxPages", "chapter.maxPages", "content.maxPages"), MAX_CHAPTER_PAGES_CAP));
            while (StringUtils.hasText(currentUrl) && visited.size() < maxPages
                && visited.add(normalizeFetchUrl(currentUrl))) {
                validateUrl(currentUrl);
                Document document = fetch(currentUrl);
                String nextUrl = nextChapterPageUrl(document, currentUrl, rules);
                removeRuleSelectors(document, rules);
                String pageText = extractChapterText(document, rules);
                if (!StringUtils.hasText(pageText)) {
                    break;
                }
                pageContents.add(pageText);
                currentUrl = nextUrl;
            }
            String text = cleanContent(String.join("\n\n", pageContents));
            int minLength = Math.max(1, rules.intValue(80,
                    "chapterRules.minContentLength", "chapter.minContentLength", "qualityRules.minContentLength"));
            if (text.length() < minLength || containsBlockedText(text, rules)) {
                return "";
            }
            return text;
        } catch (Exception ex) {
            return "";
        }
    }

    private String extractChapterText(Document document, CrawlerRuleConfig rules) {
        String contentRule = rules.text("chapterRules.content", "chapter.content", "content.rule", "content.selector");
        String text = "";
        if (StringUtils.hasText(contentRule)) {
            for (String rule : contentRule.split("\\|\\|")) {
                text = extractTextByRule(document, rule);
                if (StringUtils.hasText(text)) {
                    break;
                }
            }
        }
        if (!StringUtils.hasText(text)) {
            text = document.select(".read-content p, .chapter-content p, .content p, #chapterContent p, "
                        + "#content p, .chapterContent p, .article-content p, article p")
                .eachText()
                .stream()
                .filter(StringUtils::hasText)
                .reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right.trim());
        }
        if (!StringUtils.hasText(text)) {
            text = document.select("#content, .chapterContent, .read-content, .chapter-content, .content, "
                            + ".article-content, article")
                    .eachText()
                    .stream()
                    .filter(StringUtils::hasText)
                    .reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right.trim());
        }
        return cleanContent(text);
    }

    private String extractTextByRule(Document document, String rule) {
        if (!StringUtils.hasText(rule)) {
            return "";
        }
        String selector = rule.trim();
        if (selector.startsWith("css:")) {
            selector = selector.substring("css:".length()).trim();
        }
        String attr = "";
        int attrIndex = selector.lastIndexOf('@');
        if (attrIndex > 0 && attrIndex < selector.length() - 1) {
            attr = selector.substring(attrIndex + 1).trim();
            selector = selector.substring(0, attrIndex).trim();
        }
        if (!StringUtils.hasText(selector)) {
            return "";
        }
        if ("html".equalsIgnoreCase(attr)) {
            return document.select(selector).stream()
                    .map(Element::html)
                    .filter(StringUtils::hasText)
                    .reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right.trim());
        }
        if (StringUtils.hasText(attr)) {
            String finalAttr = attr;
            return document.select(selector).stream()
                    .map(element -> element.attr(finalAttr))
                    .filter(StringUtils::hasText)
                    .reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right.trim());
        }
        String paragraphText = document.select(selector + " p").eachText().stream()
                .filter(StringUtils::hasText)
                .reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right.trim());
        if (StringUtils.hasText(paragraphText)) {
            return paragraphText;
        }
        return document.select(selector).eachText().stream()
                .filter(StringUtils::hasText)
                .reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right.trim());
    }

    private String nextChapterPageUrl(Document document, String currentUrl, CrawlerRuleConfig rules) {
        String nextSelector = rules.text("chapterRules.nextPage", "chapter.nextPage", "content.nextPage");
        Element relNext = StringUtils.hasText(nextSelector)
                ? document.select(nextSelector).stream()
                        .filter(link -> StringUtils.hasText(link.attr("href")))
                        .findFirst()
                        .orElse(null)
                : document.selectFirst("a[rel=next][href]");
        String nextUrl = nextPageHref(relNext, currentUrl, rules);
        if (StringUtils.hasText(nextUrl)) {
            return nextUrl;
        }
        for (Element link : document.select("a[href]")) {
            nextUrl = nextPageHref(link, currentUrl, rules);
            if (StringUtils.hasText(nextUrl)) {
                return nextUrl;
            }
        }
        return "";
    }

    private void removeRuleSelectors(Document document, CrawlerRuleConfig rules) {
        for (String selector : rules.list("chapterRules.removeSelectors", "chapter.removeSelectors", "content.removeSelectors")) {
            document.select(selector).remove();
        }
    }

    private String nextPageHref(Element link, String currentUrl, CrawlerRuleConfig rules) {
        if (link == null) {
            return "";
        }
        String text = link.text() == null ? "" : link.text().trim().toLowerCase();
        String aria = link.attr("aria-label") == null ? "" : link.attr("aria-label").trim().toLowerCase();
        String value = text + " " + aria;
        if (!isChapterPageNextHint(value)) {
            return "";
        }
        String href = normalizeFetchUrl(link.absUrl("href"));
        boolean allowCrossHost = rules.boolValue(false,
                "chapterRules.allowCrossHostNextPage", "chapter.allowCrossHostNextPage");
        if (!StringUtils.hasText(href)
                || href.equals(normalizeFetchUrl(currentUrl))
                || (!allowCrossHost && !sameHost(currentUrl, href))) {
            return "";
        }
        return href;
    }

    private boolean isChapterPageNextHint(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.contains("\u4e0b\u4e00\u7ae0")
                || value.contains("\u4e0b\u7ae0")
                || value.contains("\u540e\u4e00\u7ae0")
                || value.contains("next chapter")) {
            return false;
        }
        return value.contains("\u4e0b\u4e00\u9875")
                || value.contains("\u4e0b\u9875")
                || value.contains("\u7ee7\u7eed\u9605\u8bfb")
                || value.equals("next")
                || value.contains("next page");
    }

    private boolean sameHost(String left, String right) {
        try {
            URI leftUri = URI.create(left);
            URI rightUri = URI.create(right);
            return leftUri.getHost() != null && leftUri.getHost().equalsIgnoreCase(rightUri.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String normalizeFetchUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        int hashIndex = url.indexOf('#');
        return hashIndex >= 0 ? url.substring(0, hashIndex) : url;
    }

    private String cleanContent(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("(?i)please\\s+login.*", "")
                .replaceAll("\u8bf7\u6536\u85cf\u672c\u7ad9.*", "")
                .replaceAll("\u624b\u673a\u7528\u6237\u8bf7\u6d4f\u89c8.*", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void upsertContent(CrawlChapterRaw chapter, String content) {
        CrawlContentRaw raw = contentRawMapper.selectOne(new QueryWrapper<CrawlContentRaw>()
                .eq("chapter_raw_id", chapter.id)
                .last("LIMIT 1"));
        if (raw == null) {
            raw = new CrawlContentRaw();
            raw.createdAt = LocalDateTime.now();
        }
        raw.chapterRawId = chapter.id;
        raw.content = content;
        raw.contentHash = sha256(content);
        raw.contentLength = content.length();
        raw.storageMode = "MYSQL_LONGTEXT";
        if (raw.id == null) {
            contentRawMapper.insert(raw);
        } else {
            contentRawMapper.updateById(raw);
        }
    }

    private void updateMergeTask(CrawlTaskRecord task, boolean forceMerge) {
        CrawlMergeTask mergeTask = mergeTaskMapper.selectOne(new QueryWrapper<CrawlMergeTask>()
                .eq("crawl_task_id", task.id)
                .last("LIMIT 1"));
        if (mergeTask == null) {
            return;
        }
        mergeTask.totalCount = task.successCount == null ? 0 : task.successCount;
        mergeTask.status = "PENDING";
        mergeTask.message = forceMerge
                ? "Crawler is running; incrementally merging ready books into business database."
                : "Crawler finished; waiting for clean merge into business database.";
        mergeTask.updatedAt = LocalDateTime.now();
        mergeTaskMapper.updateById(mergeTask);
        if (forceMerge || "SUCCESS".equals(task.status) || "PARTIAL_SUCCESS".equals(task.status)) {
            mergeService.mergeByCrawlTaskId(task.id);
        }
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Only http/https URLs are allowed");
            }
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                throw new IllegalArgumentException("URL host is required");
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("Private or local network URLs are not allowed");
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Crawler host cannot be resolved");
        }
    }

    private int maxBooks(CrawlRankSource rank) {
        return rank.maxBooks == null || rank.maxBooks <= 0 ? DEFAULT_MAX_BOOKS : Math.min(rank.maxBooks, 100);
    }

    private CrawlRankSource scopedRank(CrawlRankSource rank, CrawlTaskRecord task) {
        Integer scopedMaxBooks = taskMaxBooks(task);
        if (scopedMaxBooks == null) {
            return rank;
        }
        CrawlRankSource scoped = new CrawlRankSource();
        scoped.id = rank.id;
        scoped.sourceId = rank.sourceId;
        scoped.rankName = rank.rankName;
        scoped.rankType = rank.rankType;
        scoped.rankUrl = rank.rankUrl;
        scoped.preferCompleted = rank.preferCompleted;
        scoped.maxBooks = scopedMaxBooks;
        scoped.enabled = rank.enabled;
        scoped.createdAt = rank.createdAt;
        scoped.updatedAt = rank.updatedAt;
        return scoped;
    }

    private Integer taskMaxBooks(CrawlTaskRecord task) {
        if (task == null || !StringUtils.hasText(task.targetUrl)) {
            return null;
        }
        String marker = "#maxBooks=";
        int index = task.targetUrl.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String value = task.targetUrl.substring(index + marker.length()).trim();
        int ampIndex = value.indexOf('&');
        if (ampIndex >= 0) {
            value = value.substring(0, ampIndex);
        }
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(1, Math.min(parsed, 100));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String rankLabel(CrawlRankSource rank) {
        if (rank == null) {
            return "unknown rank";
        }
        String type = StringUtils.hasText(rank.rankType) ? rank.rankType : "rank-" + rank.id;
        String name = StringUtils.hasText(rank.rankName) ? rank.rankName : "";
        return name.isEmpty() ? type : type + "/" + name;
    }

    private boolean containsBlockedText(String text, CrawlerRuleConfig rules) {
        for (String pattern : rules.list("qualityRules.rejectPatterns",
                "chapterRules.rejectPatterns", "chapter.rejectPatterns", "content.rejectPatterns")) {
            if (StringUtils.hasText(pattern) && text.contains(pattern)) {
                return true;
            }
        }
        return text.contains("acw_sc__v2")
                || text.contains("aliyunwaf")
                || text.contains("\u9a8c\u8bc1\u7801")
                || text.contains("\u8bf7\u767b\u5f55")
                || text.contains("\u8bf7\u8ba2\u9605")
                || text.contains("\u8d2d\u4e70\u672c\u7ae0");
    }

    private boolean isQidian(CrawlerSourceConfig source, String rankUrl) {
        String value = ((source.sourceCode == null ? "" : source.sourceCode) + " " + rankUrl).toLowerCase();
        return value.contains("qidian");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
