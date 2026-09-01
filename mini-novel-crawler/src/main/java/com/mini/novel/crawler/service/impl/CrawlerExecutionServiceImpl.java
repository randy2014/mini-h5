package com.mini.novel.crawler.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.mini.novel.crawler.entity.CrawlRankSource;
import com.mini.novel.crawler.entity.CrawlSourceCredential;
import com.mini.novel.crawler.entity.CrawlTaskRecord;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import com.mini.novel.crawler.mapper.CrawlBookRawMapper;
import com.mini.novel.crawler.mapper.CrawlChapterRawMapper;
import com.mini.novel.crawler.mapper.CrawlContentRawMapper;
import com.mini.novel.crawler.mapper.CrawlRankSourceMapper;
import com.mini.novel.crawler.mapper.CrawlSourceCredentialMapper;
import com.mini.novel.crawler.mapper.CrawlTaskRecordMapper;
import com.mini.novel.crawler.mapper.CrawlerSourceConfigMapper;
import com.mini.novel.crawler.parser.CrawlerRuleConfig;
import com.mini.novel.crawler.parser.CrawlerSiteParser;
import com.mini.novel.crawler.parser.ParsedBookSeed;
import com.mini.novel.crawler.parser.ParsedBookSnapshot;
import com.mini.novel.crawler.parser.ParsedChapterSnapshot;
import com.mini.novel.crawler.service.CrawlerExecutionService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
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
    private static final String KKXSZ_USER_AGENT = "curl/8.18.0";
    private static final int DEFAULT_MAX_BOOKS = 20;
    private static final int DEFAULT_MAX_CHAPTER_PAGES = 8;
    private static final int MAX_CHAPTER_PAGES_CAP = 30;
    private static final int FETCH_TIMEOUT_MILLIS = 45000;

    private final CrawlTaskRecordMapper taskMapper;
    private final CrawlerSourceConfigMapper sourceMapper;
    private final CrawlRankSourceMapper rankSourceMapper;
    private final CrawlBookRawMapper bookRawMapper;
    private final CrawlChapterRawMapper chapterRawMapper;
    private final CrawlContentRawMapper contentRawMapper;
    private final CrawlSourceCredentialMapper credentialMapper;
    private final NovelSourceMappingMapper novelSourceMappingMapper;
    private final ChapterSourceMappingMapper chapterSourceMappingMapper;
    private final ChapterMapper chapterMapper;
    private final List<CrawlerSiteParser> siteParsers;
    private final ObjectMapper objectMapper;
    private final TaskExecutor applicationTaskExecutor;

    public CrawlerExecutionServiceImpl(CrawlTaskRecordMapper taskMapper,
                                       CrawlerSourceConfigMapper sourceMapper,
                                       CrawlRankSourceMapper rankSourceMapper,
                                       CrawlBookRawMapper bookRawMapper,
                                       CrawlChapterRawMapper chapterRawMapper,
                                       CrawlContentRawMapper contentRawMapper,
                                       CrawlSourceCredentialMapper credentialMapper,
                                       NovelSourceMappingMapper novelSourceMappingMapper,
                                       ChapterSourceMappingMapper chapterSourceMappingMapper,
                                       ChapterMapper chapterMapper,
                                       List<CrawlerSiteParser> siteParsers,
                                       ObjectMapper objectMapper,
                                       @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor) {
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.rankSourceMapper = rankSourceMapper;
        this.bookRawMapper = bookRawMapper;
        this.chapterRawMapper = chapterRawMapper;
        this.contentRawMapper = contentRawMapper;
        this.credentialMapper = credentialMapper;
        this.novelSourceMappingMapper = novelSourceMappingMapper;
        this.chapterSourceMappingMapper = chapterSourceMappingMapper;
        this.chapterMapper = chapterMapper;
        this.siteParsers = siteParsers;
        this.objectMapper = objectMapper;
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
        int pendingReviewCount = 0;
        int processedCount = 0;
        int insertedBooks = 0;
        int updatedBooks = 0;
        int insertedChapters = 0;
        int updatedChapters = 0;
        int deduplicated = 0;
        int timeoutCount = 0;
        try {
            CrawlerSourceConfig source = sourceMapper.selectById(task.sourceId);
            if (source == null) {
                throw new IllegalStateException("Crawler source not found: " + task.sourceId);
            }
            List<CrawlRankSource> ranks = loadRanks(task, source);
            for (CrawlRankSource rank : ranks) {
                validateUrl(rank.rankUrl);
                CrawlerSiteParser parser = selectParser(source, rank.rankUrl);
                List<ParsedBookSeed> seeds = collectRankSeeds(task, source, rank, parser);
                if (seeds.isEmpty() && isQidian(source, rank.rankUrl) && !rank.rankUrl.contains("m.qidian.com")) {
                    Document mobilePage = fetch("https://m.qidian.com/", source);
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
                        BookOutcome outcome = processBook(task, source, rank, parser, seed);
                        if (outcome.success()) {
                            success++;
                            rankSaved++;
                        } else {
                            failed++;
                            rankFailed++;
                        }
                        processedCount += outcome.processed();
                        insertedBooks += outcome.insertedBooks();
                        updatedBooks += outcome.updatedBooks();
                        insertedChapters += outcome.insertedChapters();
                        updatedChapters += outcome.updatedChapters();
                        deduplicated += outcome.deduplicated();
                        pendingReviewCount += outcome.pendingReview();
                        timeoutCount += outcome.timeout();
                        updateRunningProgress(task, total, success, failed, rank, seed);
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
                task.status = "NO_DATA";
                task.message = "Crawler finished, but no book was parsed. Check rank URL or source rules.";
            } else {
                task.status = failed == 0 ? "SUCCESS" : "PARTIAL_SUCCESS";
                task.message = publicCrawlerMessage(source, total, success, failed);
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
        }
    }

    private BookOutcome processBook(CrawlTaskRecord task, CrawlerSourceConfig source, CrawlRankSource rank,
                                    CrawlerSiteParser parser, ParsedBookSeed seed) throws Exception {
        CrawlBookRaw completedBook = completedReadyBook(source, seed);
        if (completedBook != null) {
            return BookOutcome.deduplicatedOutcome();
        }
        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed,
                isKkxszPublicSource(source) ? url -> fetch(url, source) : this::fetch);
        if (!StringUtils.hasText(snapshot.title())) {
            return BookOutcome.failed();
        }
        if (isSnapshotFullyMapped(source, snapshot)) {
            return BookOutcome.deduplicatedOutcome();
        }
        boolean bookExists = rawBookExists(source, snapshot);
        CrawlBookRaw book = upsertBookRaw(task, source, rank, snapshot);
        if (isCompletedBookReady(book)) {
            return BookOutcome.deduplicatedOutcome();
        }
        long beforeChapters = countRawChapters(book.id);
        boolean completed = upsertChaptersAndContent(source, book, snapshot);
        long afterChapters = countRawChapters(book.id);
        long pendingReview = pendingReviewChapterCount(book.id);
        return new BookOutcome(completed, 1, bookExists ? 0 : 1, bookExists ? 1 : 0,
                (int) Math.max(0, afterChapters - beforeChapters),
                (int) Math.min(beforeChapters, afterChapters), 0, pendingReview,
                completed ? 0 : 1);
    }

    private List<ParsedBookSeed> collectRankSeeds(CrawlTaskRecord task, CrawlerSourceConfig source,
                                                  CrawlRankSource rank, CrawlerSiteParser parser) throws IOException {
        int maxBooks = maxBooks(rank);
        List<ParsedBookSeed> seeds = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        Set<String> seenPages = new LinkedHashSet<>();
        String currentUrl = isNovel69hAuthorizedSource(source) ? firstNonBlank(novel69hStartPage(task), rank.rankUrl) : rank.rankUrl;
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        int maxPages = Math.max(1, Math.min(rules.intValue(1, "rankRules.maxPages", "rank.maxPages"), 100));
        while (StringUtils.hasText(currentUrl) && seenPages.size() < maxPages && seenPages.add(currentUrl)
                && seeds.size() < maxBooks) {
            Document rankPage = fetch(currentUrl, source);
            List<ParsedBookSeed> pageSeeds = parser.parseBookSeeds(source, rankPage, currentUrl, maxBooks - seeds.size());
            int added = 0;
            int newlyDiscovered = 0;
            int existing = 0;
            for (ParsedBookSeed seed : pageSeeds) {
                if (seenUrls.add(seed.url())) {
                    seeds.add(seed);
                    added++;
                    if (rawBookExists(source, seed.intro())) {
                        existing++;
                    } else {
                        newlyDiscovered++;
                    }
                    if (seeds.size() >= maxBooks) {
                        break;
                    }
                }
            }
            String nextUrl = parser.nextRankPage(source, rankPage, currentUrl);
            if (isNovel69hAuthorizedSource(source)) {
                String continuation = StringUtils.hasText(nextUrl) && !seenPages.contains(nextUrl) ? nextUrl : "";
                log.info("69hnovel rank page: taskId={}, page={}, discovered={}, existing={}, added={}, failed=0, continuation={}",
                        task.id, currentUrl, pageSeeds.size(), existing, newlyDiscovered,
                        StringUtils.hasText(continuation) ? continuation : "none");
                task.message = "69hnovel discovery: page=" + limit(currentUrl, 160)
                        + ", discovered=" + seeds.size()
                        + ", existing=" + existing
                        + ", added=" + newlyDiscovered
                        + ", failed=0"
                        + ", continuation=" + (StringUtils.hasText(continuation) ? "pageUrl:" + continuation : "none")
                        + ".";
                task.updatedAt = LocalDateTime.now();
                taskMapper.updateById(task);
            }
            if ((!isH528AuthorizedSource(source) && !isNovel69hAuthorizedSource(source)) || (added == 0 && !seeds.isEmpty())) {
                break;
            }
            if (!StringUtils.hasText(nextUrl) || seenPages.contains(nextUrl)) {
                break;
            }
            currentUrl = nextUrl;
            sleepBeforeRetry(1);
        }
        return seeds;
    }

    private String publicCrawlerMessage(CrawlerSourceConfig source, int total, int success, int failed) {
        String message = "Crawler finished: discovered " + total + ", saved " + success
                + ", failed " + failed + ", merge task reports merged counts";
        if (isNovel69hAuthorizedSource(source)) {
            String continuation = novel69hContinuation(previousNovel69hMessage(source));
            message += ", continuation=" + (StringUtils.hasText(continuation) ? "pageUrl:" + continuation : "none");
        }
        return message + ".";
    }

    private String previousNovel69hMessage(CrawlerSourceConfig source) {
        if (!isNovel69hAuthorizedSource(source) || source.id == null) {
            return "";
        }
        List<CrawlTaskRecord> tasks = taskMapper.selectList(new QueryWrapper<CrawlTaskRecord>()
                .eq("source_id", source.id)
                .in("status", List.of("SUCCESS", "PARTIAL_SUCCESS", "RUNNING"))
                .orderByDesc("id")
                .last("LIMIT 1"));
        return tasks.isEmpty() ? "" : tasks.get(0).message;
    }

    private String novel69hStartPage(CrawlTaskRecord task) {
        if (task == null) {
            return "";
        }
        String continuation = novel69hContinuation(task.targetUrl);
        if (StringUtils.hasText(continuation)) {
            return continuation;
        }
        List<CrawlTaskRecord> previousTasks = taskMapper.selectList(new QueryWrapper<CrawlTaskRecord>()
                .eq("source_id", task.sourceId)
                .in("status", List.of("SUCCESS", "PARTIAL_SUCCESS"))
                .lt("id", task.id)
                .orderByDesc("id")
                .last("LIMIT 20"));
        for (CrawlTaskRecord previous : previousTasks) {
            continuation = novel69hContinuation(previous.message);
            if (StringUtils.hasText(continuation)) {
                return continuation;
            }
        }
        return "";
    }

    private String novel69hContinuation(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("continuation=pageUrl:([^,\\s]+)")
                .matcher(value);
        return matcher.find() ? matcher.group(1).replaceAll("\\.$", "") : "";
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

    private long pendingReviewChapterCount(Long bookRawId) {
        if (bookRawId == null) {
            return 0L;
        }
        Long pendingReview = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", bookRawId)
                .eq("content_status", "PENDING_REVIEW"));
        return pendingReview == null ? 0L : pendingReview;
    }

    private record BookOutcome(boolean success, int processed, int insertedBooks, int updatedBooks,
                               int insertedChapters, int updatedChapters, int deduplicated,
                               long pendingReview, int timeout) {
        static BookOutcome failed() {
            return new BookOutcome(false, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        static BookOutcome deduplicatedOutcome() {
            return new BookOutcome(true, 0, 0, 0, 0, 0, 1, 0, 0);
        }
    }

    private boolean isH528AuthorizedSource(CrawlerSourceConfig source) {
        return source != null && "h528_authorized".equalsIgnoreCase(source.sourceCode);
    }

    private boolean isNovel69hAuthorizedSource(CrawlerSourceConfig source) {
        return source != null && "novel69h_authorized".equalsIgnoreCase(source.sourceCode);
    }

    private boolean isKkxszPublicSource(CrawlerSourceConfig source) {
        return isKkxszPublicSourceCode(source);
    }

    private static boolean isKkxszPublicSourceCode(CrawlerSourceConfig source) {
        return source != null && "kkxsz_public".equalsIgnoreCase(source.sourceCode);
    }

    private List<CrawlRankSource> loadRanks(CrawlTaskRecord task, CrawlerSourceConfig source) {
        if (task.rankSourceId != null) {
            CrawlRankSource rank = rankSourceMapper.selectById(task.rankSourceId);
            if (rank == null || !task.sourceId.equals(rank.sourceId) || !Boolean.TRUE.equals(rank.enabled)) {
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
        return fetch(url, null);
    }

    private Document fetch(String url, CrawlerSourceConfig source) throws IOException {
        validateUrl(url);
        IOException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Connection connection = configureFetchConnection(url, source);
                applyCredentialHeaders(connection, source);
                return connection.get();
            } catch (IOException ex) {
                lastException = ex;
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException;
    }

    static Connection configureFetchConnection(String url, CrawlerSourceConfig source) {
        Connection connection = Jsoup.connect(url)
                .followRedirects(true)
                .timeout(FETCH_TIMEOUT_MILLIS);
        if (isKkxszPublicSourceCode(source)) {
            return connection
                    .userAgent(KKXSZ_USER_AGENT)
                    .header("Accept", "*/*");
        }
        return connection
                .userAgent(url.contains("m.qidian.com") ? MOBILE_USER_AGENT : USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", origin(url))
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Upgrade-Insecure-Requests", "1");
    }

    private void applyCredentialHeaders(Connection connection, CrawlerSourceConfig source) {
        if (source == null || source.id == null || !"COOKIE".equalsIgnoreCase(source.authMode)) {
            return;
        }
        CrawlSourceCredential credential = credentialMapper.selectOne(new QueryWrapper<CrawlSourceCredential>()
                .eq("source_id", source.id)
                .eq("enabled", true)
                .orderByDesc("updated_at")
                .last("LIMIT 1"));
        if (credential == null) {
            return;
        }
        if (StringUtils.hasText(credential.cookieText)) {
            connection.header("Cookie", credential.cookieText);
        }
        if (StringUtils.hasText(credential.headersJson)) {
            try {
                Map<String, String> headers = objectMapper.readValue(credential.headersJson, new TypeReference<>() {});
                headers.forEach((name, value) -> {
                    if (StringUtils.hasText(name) && StringUtils.hasText(value)
                            && !"cookie".equalsIgnoreCase(name)) {
                        connection.header(name, value);
                    }
                });
            } catch (Exception ex) {
                log.warn("Ignoring invalid crawler credential headers for source {}", source.sourceCode);
            }
        }
    }

    private static String origin(String url) {
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
        book.categoryName = limit(rawCategoryName(source, rank, snapshot), 64);
        book.bookStatus = normalizeBookStatus(snapshot.bookStatus());
        book.wordCount = snapshot.wordCount();
        book.heatScore = 0L;
        book.rankType = rank.rankType;
        book.contentStatus = StringUtils.hasText(snapshot.chapterId()) ? "CATALOG_READY" : "META_ONLY";
        book.rawJson = "{\"rankName\":\"" + json(rank.rankName) + "\",\"rankUrl\":\"" + json(rank.rankUrl) + "\"}";
        book.crawledAt = now;
        book.updatedAt = now;
        if (book.id == null) {
            bookRawMapper.insert(book);
        } else {
            bookRawMapper.updateById(book);
        }
        return book;
    }

    private boolean rawBookExists(CrawlerSourceConfig source, ParsedBookSnapshot snapshot) {
        if (source == null || snapshot == null) {
            return false;
        }
        String sourceBookId = StringUtils.hasText(snapshot.sourceBookId())
                ? snapshot.sourceBookId()
                : sha256(snapshot.sourceUrl()).substring(0, 24);
        Long count = bookRawMapper.selectCount(new QueryWrapper<CrawlBookRaw>()
                .eq("source_code", source.sourceCode)
                .eq("source_book_id", sourceBookId));
        return count != null && count > 0;
    }

    private String rawCategoryName(CrawlerSourceConfig source, CrawlRankSource rank, ParsedBookSnapshot snapshot) {
        if (isH528AuthorizedSource(source) || isNovel69hAuthorizedSource(source)) {
            return snapshot == null ? "" : firstNonBlank(snapshot.categoryName());
        }
        return firstNonBlank(snapshot == null ? "" : snapshot.categoryName(), rank.rankName, "Unknown");
    }

    private boolean rawBookExists(CrawlerSourceConfig source, String sourceBookId) {
        if (source == null || !StringUtils.hasText(source.sourceCode) || !StringUtils.hasText(sourceBookId)) {
            return false;
        }
        Long count = bookRawMapper.selectCount(new QueryWrapper<CrawlBookRaw>()
                .eq("source_code", source.sourceCode)
                .eq("source_book_id", sourceBookId));
        return count != null && count > 0;
    }

    private long countRawChapters(Long bookRawId) {
        if (bookRawId == null) {
            return 0L;
        }
        Long count = chapterRawMapper.selectCount(new QueryWrapper<CrawlChapterRaw>().eq("book_raw_id", bookRawId));
        return count == null ? 0L : count;
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

    private boolean upsertChaptersAndContent(CrawlerSourceConfig source, CrawlBookRaw book, ParsedBookSnapshot snapshot) {
        List<ParsedChapterSnapshot> chapters = snapshot.chapters();
        if (chapters == null || chapters.isEmpty()) {
            upsertChapterAndContent(source, book, snapshot, null);
            return true;
        }
        int readyCount = 0;
        boolean completed = true;
        for (ParsedChapterSnapshot parsedChapter : chapters) {
            String sourceChapterId = StringUtils.hasText(parsedChapter.chapterId())
                    ? parsedChapter.chapterId()
                    : sha256(parsedChapter.url()).substring(0, 24);
            CrawlChapterRaw existing = chapterRawMapper.selectOne(new QueryWrapper<CrawlChapterRaw>()
                    .eq("book_raw_id", book.id)
                    .eq("source_chapter_id", sourceChapterId)
                    .last("LIMIT 1"));
            if (isTerminalChapter(existing)) {
                if ("PENDING_REVIEW".equals(existing.contentStatus) || "CONTENT_READY".equals(existing.contentStatus)) {
                    readyCount++;
                }
                continue;
            }
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
                    .eq("source_chapter_id", sourceChapterId)
                    .last("LIMIT 1"));
            if (raw != null && "PENDING_REVIEW".equals(raw.contentStatus)) {
                readyCount++;
            }
        }
        book.contentStatus = readyCount > 0 ? "PENDING_REVIEW" : "CATALOG_READY";
        bookRawMapper.updateById(book);
        return completed;
    }

    private boolean isTerminalChapter(CrawlChapterRaw chapter) {
        if (chapter == null || chapter.id == null) {
            return false;
        }
        if ("CONTENT_READY".equals(chapter.contentStatus) || "PENDING_REVIEW".equals(chapter.contentStatus)) {
            return true;
        }
        return contentRawMapper.selectCount(new QueryWrapper<CrawlContentRaw>()
                .eq("chapter_raw_id", chapter.id)
                .gt("content_length", 0)) > 0;
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
        boolean matchedBySourceUrl = false;
        if (chapter == null && StringUtils.hasText(snapshot.chapterUrl())) {
            chapter = chapterRawMapper.selectOne(new QueryWrapper<CrawlChapterRaw>()
                    .eq("book_raw_id", book.id)
                    .eq("source_url", limit(snapshot.chapterUrl(), 512))
                    .last("LIMIT 1"));
            matchedBySourceUrl = chapter != null;
        }
        if (hasExistingContent(chapter)) {
            return;
        }
        if (chapter == null) {
            chapter = new CrawlChapterRaw();
            chapter.bookRawId = book.id;
            chapter.sourceChapterId = sourceChapterId;
            chapter.createdAt = now;
        }
        int chapterNo = parsedChapter == null || parsedChapter.chapterNo() <= 0 ? 1 : parsedChapter.chapterNo();
        String title = parsedChapter == null || !StringUtils.hasText(parsedChapter.title())
                ? "Public chapter entry #" + sourceChapterId
                : parsedChapter.title();
        boolean vip = parsedChapter != null && parsedChapter.vip();
        if (!StringUtils.hasText(chapter.sourceChapterId) || matchedBySourceUrl) {
            chapter.sourceChapterId = sourceChapterId;
        }
        if (!StringUtils.hasText(chapter.sourceUrl)) {
            chapter.sourceUrl = limit(snapshot.chapterUrl(), 512);
        }
        if (chapter.chapterNo == null || chapter.chapterNo <= 0) {
            chapter.chapterNo = chapterNo;
        }
        if (!StringUtils.hasText(chapter.title)) {
            chapter.title = limit(title, 255);
        }
        if (chapter.vip == null) {
            chapter.vip = vip;
        }
        if (chapter.priceCoin == null) {
            chapter.priceCoin = 0;
        }
        if (!StringUtils.hasText(chapter.contentStatus)) {
            chapter.contentStatus = "ENTRY_READY";
        }
        chapter.crawledAt = now;
        chapter.updatedAt = now;

        String content = parsedChapter == null ? "" : parsedChapter.content();
        if (!StringUtils.hasText(content)) {
            content = fetchPublicChapterContent(snapshot.chapterUrl(), source);
        }
        if (StringUtils.hasText(content)) {
            chapter.contentHash = sha256(content);
            chapter.contentStatus = "PENDING_REVIEW";
            book.contentStatus = "PENDING_REVIEW";
            bookRawMapper.updateById(book);
        }
        if (chapter.id == null) {
            chapterRawMapper.insert(chapter);
        } else {
            chapterRawMapper.updateById(chapter);
        }
        if (StringUtils.hasText(content) && "PENDING_REVIEW".equals(chapter.contentStatus)) {
            upsertContent(chapter, content);
        }
    }

    private boolean hasExistingContent(CrawlChapterRaw chapter) {
        if (chapter == null || chapter.id == null) {
            return false;
        }
        CrawlContentRaw raw = contentRawMapper.selectOne(new QueryWrapper<CrawlContentRaw>()
                .select("id", "chapter_raw_id", "content_hash", "content_length")
                .eq("chapter_raw_id", chapter.id)
                .last("LIMIT 1"));
        if (raw == null || raw.contentLength == null || raw.contentLength <= 0) {
            return false;
        }
        return true;
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
                Document document = fetch(currentUrl, source);
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
        if (raw != null && raw.contentLength != null && raw.contentLength > 0) {
            return;
        }
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
