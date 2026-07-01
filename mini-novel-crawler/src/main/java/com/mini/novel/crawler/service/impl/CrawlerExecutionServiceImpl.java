package com.mini.novel.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mini.novel.crawler.entity.CrawlBookRaw;
import com.mini.novel.crawler.entity.CrawlChapterRaw;
import com.mini.novel.crawler.entity.CrawlContentRaw;
import com.mini.novel.crawler.entity.CrawlMergeTask;
import com.mini.novel.crawler.entity.CrawlRankSource;
import com.mini.novel.crawler.entity.CrawlTaskRecord;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import com.mini.novel.crawler.mapper.CrawlBookRawMapper;
import com.mini.novel.crawler.mapper.CrawlChapterRawMapper;
import com.mini.novel.crawler.mapper.CrawlContentRawMapper;
import com.mini.novel.crawler.mapper.CrawlMergeTaskMapper;
import com.mini.novel.crawler.mapper.CrawlRankSourceMapper;
import com.mini.novel.crawler.mapper.CrawlTaskRecordMapper;
import com.mini.novel.crawler.mapper.CrawlerSourceConfigMapper;
import com.mini.novel.crawler.parser.CrawlerRuleConfig;
import com.mini.novel.crawler.parser.CrawlerSiteParser;
import com.mini.novel.crawler.parser.ParsedBookSeed;
import com.mini.novel.crawler.parser.ParsedBookSnapshot;
import com.mini.novel.crawler.parser.ParsedChapterSnapshot;
import com.mini.novel.crawler.service.CrawlerExecutionService;
import com.mini.novel.crawler.service.CrawlerMergeService;
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
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CrawlerExecutionServiceImpl implements CrawlerExecutionService {
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
    private final CrawlRankSourceMapper rankSourceMapper;
    private final CrawlBookRawMapper bookRawMapper;
    private final CrawlChapterRawMapper chapterRawMapper;
    private final CrawlContentRawMapper contentRawMapper;
    private final CrawlMergeTaskMapper mergeTaskMapper;
    private final CrawlerMergeService mergeService;
    private final List<CrawlerSiteParser> siteParsers;
    private final TaskExecutor applicationTaskExecutor;

    public CrawlerExecutionServiceImpl(CrawlTaskRecordMapper taskMapper,
                                       CrawlerSourceConfigMapper sourceMapper,
                                       CrawlRankSourceMapper rankSourceMapper,
                                       CrawlBookRawMapper bookRawMapper,
                                       CrawlChapterRawMapper chapterRawMapper,
                                       CrawlContentRawMapper contentRawMapper,
                                       CrawlMergeTaskMapper mergeTaskMapper,
                                       CrawlerMergeService mergeService,
                                       List<CrawlerSiteParser> siteParsers,
                                       @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor) {
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.rankSourceMapper = rankSourceMapper;
        this.bookRawMapper = bookRawMapper;
        this.chapterRawMapper = chapterRawMapper;
        this.contentRawMapper = contentRawMapper;
        this.mergeTaskMapper = mergeTaskMapper;
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
        try {
            CrawlerSourceConfig source = sourceMapper.selectById(task.sourceId);
            if (source == null) {
                throw new IllegalStateException("Crawler source not found: " + task.sourceId);
            }

            List<CrawlRankSource> ranks = loadRanks(task, source);
            for (CrawlRankSource rank : ranks) {
                validateUrl(rank.rankUrl);
                CrawlerSiteParser parser = selectParser(source, rank.rankUrl);
                Document rankPage = fetch(rank.rankUrl);
                List<ParsedBookSeed> seeds = parser.parseBookSeeds(source, rankPage, rank.rankUrl, maxBooks(rank));
                if (seeds.isEmpty() && isQidian(source, rank.rankUrl) && !rank.rankUrl.contains("m.qidian.com")) {
                    Document mobilePage = fetch("https://m.qidian.com/");
                    seeds = parser.parseBookSeeds(source, mobilePage, "https://m.qidian.com/", maxBooks(rank));
                }

                total += seeds.size();
                for (ParsedBookSeed seed : seeds) {
                    try {
                        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, this::fetch);
                        if (!StringUtils.hasText(snapshot.title())) {
                            failed++;
                            continue;
                        }
                        CrawlBookRaw book = upsertBookRaw(task, source, rank, snapshot);
                        upsertChaptersAndContent(source, book, snapshot);
                        success++;
                        updateRunningProgress(task, total, success, failed, rank, seed);
                        updateMergeTask(task, true);
                    } catch (Exception itemEx) {
                        failed++;
                        updateRunningProgress(task, total, success, failed, rank, seed);
                    }
                }
            }

            if (total == 0) {
                task.status = "NO_DATA";
                task.message = "Crawler finished, but no book was parsed. Check rank URL or source rules.";
            } else {
                task.status = failed == 0 ? "SUCCESS" : "PARTIAL_SUCCESS";
                task.message = "Crawler finished: discovered " + total + ", saved " + success + ", failed " + failed + ".";
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
                + ", current rank " + limit(rank.rankName, 64)
                + ", current book " + limit(seed.url(), 160) + ".";
        taskMapper.updateById(task);
    }

    private List<CrawlRankSource> loadRanks(CrawlTaskRecord task, CrawlerSourceConfig source) {
        if (task.rankSourceId != null) {
            CrawlRankSource rank = rankSourceMapper.selectById(task.rankSourceId);
            return rank == null ? new ArrayList<>() : new ArrayList<>(List.of(rank));
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
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .timeout(FETCH_TIMEOUT_MILLIS)
                        .get();
            } catch (IOException ex) {
                lastException = ex;
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException;
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
        book.bookStatus = "UNKNOWN";
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
        book.contentStatus = readyCount > 0 ? "CONTENT_READY" : "CATALOG_READY";
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
        CrawlChapterRaw chapter = chapterRawMapper.selectOne(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", book.id)
                .eq("source_chapter_id", sourceChapterId)
                .last("LIMIT 1"));
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
        if (StringUtils.hasText(content)) {
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
        if (StringUtils.hasText(content)) {
            upsertContent(chapter, content);
        }
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
                removeRuleSelectors(document, rules);
                String pageText = extractChapterText(document, rules);
                if (!StringUtils.hasText(pageText)) {
                    break;
                }
                pageContents.add(pageText);
                currentUrl = nextChapterPageUrl(document, currentUrl, rules);
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
