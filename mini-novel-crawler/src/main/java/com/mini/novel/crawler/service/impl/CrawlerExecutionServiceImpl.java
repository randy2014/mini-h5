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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern MOBILE_BOOK_PATTERN = Pattern.compile(
            "\\{[^{}]*?\"bid\"\\s*:\\s*\"?(\\d+)\"?[^{}]*?\"cid\"\\s*:\\s*\"?(\\d+)\"?[^{}]*?\"bName\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"bAuth\"\\s*:\\s*\"([^\"]+)\"[^{}]*?(?:\"desc\"\\s*:\\s*\"([^\"]*)\")?[^{}]*?(?:\"cnt\"\\s*:\\s*\"([^\"]*)\")?[^{}]*?\\}");
    private static final Pattern BOOK_ID_PATTERN = Pattern.compile("(?:/info/|/book/)(\\d+)");

    private final CrawlTaskRecordMapper taskMapper;
    private final CrawlerSourceConfigMapper sourceMapper;
    private final CrawlRankSourceMapper rankSourceMapper;
    private final CrawlBookRawMapper bookRawMapper;
    private final CrawlChapterRawMapper chapterRawMapper;
    private final CrawlContentRawMapper contentRawMapper;
    private final CrawlMergeTaskMapper mergeTaskMapper;
    private final TaskExecutor applicationTaskExecutor;

    public CrawlerExecutionServiceImpl(CrawlTaskRecordMapper taskMapper,
                                       CrawlerSourceConfigMapper sourceMapper,
                                       CrawlRankSourceMapper rankSourceMapper,
                                       CrawlBookRawMapper bookRawMapper,
                                       CrawlChapterRawMapper chapterRawMapper,
                                       CrawlContentRawMapper contentRawMapper,
                                       CrawlMergeTaskMapper mergeTaskMapper,
                                       @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor) {
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.rankSourceMapper = rankSourceMapper;
        this.bookRawMapper = bookRawMapper;
        this.chapterRawMapper = chapterRawMapper;
        this.contentRawMapper = contentRawMapper;
        this.mergeTaskMapper = mergeTaskMapper;
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
        task.message = "采集执行中：正在读取榜单和章节入口。";
        taskMapper.updateById(task);

        int total = 0;
        int success = 0;
        int failed = 0;
        try {
            CrawlerSourceConfig source = sourceMapper.selectById(task.sourceId);
            if (source == null) {
                throw new IllegalStateException("采集源不存在：" + task.sourceId);
            }

            List<CrawlRankSource> ranks = loadRanks(task);
            if (ranks.isEmpty()) {
                CrawlRankSource fallback = new CrawlRankSource();
                fallback.id = 0L;
                fallback.sourceId = source.id;
                fallback.rankName = "站点首页";
                fallback.rankType = "HOME";
                fallback.rankUrl = source.baseUrl;
                fallback.maxBooks = DEFAULT_MAX_BOOKS;
                fallback.preferCompleted = true;
                fallback.enabled = true;
                ranks.add(fallback);
            }

            for (CrawlRankSource rank : ranks) {
                validateUrl(rank.rankUrl);
                Document rankPage = fetch(rank.rankUrl);
                List<BookSeed> seeds = parseBookSeeds(rankPage, rank.rankUrl, maxBooks(rank));
                if (seeds.isEmpty() && source.sourceCode != null && source.sourceCode.toLowerCase().contains("qidian")
                        && !rank.rankUrl.contains("m.qidian.com")) {
                    Document mobilePage = fetch("https://m.qidian.com/");
                    seeds = parseBookSeeds(mobilePage, "https://m.qidian.com/", maxBooks(rank));
                }
                total += seeds.size();
                for (BookSeed seed : seeds) {
                    try {
                        BookSnapshot snapshot = fetchBook(seed);
                        if (!StringUtils.hasText(snapshot.title)) {
                            failed++;
                            continue;
                        }
                        CrawlBookRaw book = upsertBookRaw(source, rank, snapshot);
                        upsertChapterAndContent(book, snapshot);
                        success++;
                    } catch (Exception itemEx) {
                        failed++;
                    }
                }
            }

            if (total == 0) {
                task.status = "NO_DATA";
                task.message = "采集完成但未解析到书籍，请检查榜单地址或解析规则。";
            } else {
                task.status = failed == 0 ? "SUCCESS" : "PARTIAL_SUCCESS";
                task.message = "采集完成：发现 " + total + " 本，写入/更新 " + success + " 本，失败 " + failed + " 本。";
            }
        } catch (Exception ex) {
            task.status = "FAILED";
            task.message = "采集失败：" + ex.getMessage();
        } finally {
            task.totalCount = total;
            task.successCount = success;
            task.failCount = failed;
            task.finishedAt = LocalDateTime.now();
            task.updatedAt = task.finishedAt;
            taskMapper.updateById(task);
            updateMergeTask(task);
        }
    }

    private List<CrawlRankSource> loadRanks(CrawlTaskRecord task) {
        if (task.rankSourceId != null) {
            CrawlRankSource rank = rankSourceMapper.selectById(task.rankSourceId);
            return rank == null ? new ArrayList<>() : new ArrayList<>(List.of(rank));
        }
        return rankSourceMapper.selectList(new QueryWrapper<CrawlRankSource>()
                .eq("source_id", task.sourceId)
                .eq("enabled", true)
                .orderByAsc("id")
                .last("LIMIT 20"));
    }

    private Document fetch(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(url.contains("m.qidian.com") ? MOBILE_USER_AGENT : USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .timeout(18000)
                .get();
    }

    private List<BookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks) {
        List<BookSeed> mobileSeeds = parseMobileEmbeddedBooks(document.html(), maxBooks);
        if (!mobileSeeds.isEmpty()) {
            return mobileSeeds;
        }

        Set<String> links = new LinkedHashSet<>();
        for (Element link : document.select("a[href*='book.qidian.com/info/'], a[href*='www.qidian.com/book/']")) {
            String href = normalizeUrl(link.absUrl("href"));
            if (StringUtils.hasText(href)) {
                links.add(href);
            }
            if (links.size() >= maxBooks) {
                break;
            }
        }

        List<BookSeed> seeds = new ArrayList<>();
        for (String link : links) {
            seeds.add(new BookSeed(link, "", "", "", 0L, "", rankUrl));
        }
        return seeds;
    }

    private List<BookSeed> parseMobileEmbeddedBooks(String html, int maxBooks) {
        List<BookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = MOBILE_BOOK_PATTERN.matcher(html);
        while (matcher.find() && seeds.size() < maxBooks) {
            String bid = matcher.group(1);
            String cid = matcher.group(2);
            if (!seen.add(bid)) {
                continue;
            }
            seeds.add(new BookSeed(
                    "https://book.qidian.com/info/" + bid,
                    unescapeJson(matcher.group(3)),
                    unescapeJson(matcher.group(4)),
                    matcher.group(5) == null ? "" : unescapeJson(matcher.group(5)),
                    parseWordCount(matcher.group(6)),
                    cid,
                    "https://m.qidian.com/"));
        }
        return seeds;
    }

    private BookSnapshot fetchBook(BookSeed seed) throws IOException {
        String sourceBookId = extractBookId(seed.url);
        if (StringUtils.hasText(seed.title)) {
            return new BookSnapshot(seed.title, seed.author, "https://dummyimage.com/300x420/20232a/ffffff&text=Qidian",
                    seed.intro, seed.url, sourceBookId, seed.wordCount, seed.chapterId, publicChapterUrl(seed.url, seed.chapterId));
        }

        Document detail = fetch(seed.url);
        String title = firstText(detail, ".book-info h1 em", ".book-information h1", "h1 em", "h1");
        String author = firstText(detail, ".book-info h1 a", ".writer", ".book-information .author", "a[href*='/author/']");
        String intro = firstText(detail, ".book-intro p", ".intro", ".book-info-detail .book-intro", "meta[name=description]");
        String cover = detail.select(".book-img img, .book-img-box img, img[src*='bookcover']").stream()
                .map(img -> normalizeImageUrl(img.absUrl("src")))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("https://dummyimage.com/300x420/20232a/ffffff&text=Qidian");
        long wordCount = parseWordCount(firstText(detail, ".book-info p em", ".total .num", ".count"));
        String chapterId = firstChapterId(detail);
        return new BookSnapshot(title, cleanAuthor(author), cover, intro, seed.url, sourceBookId, wordCount,
                chapterId, publicChapterUrl(seed.url, chapterId));
    }

    private CrawlBookRaw upsertBookRaw(CrawlerSourceConfig source, CrawlRankSource rank, BookSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        String sourceBookId = StringUtils.hasText(snapshot.sourceBookId) ? snapshot.sourceBookId : sha256(snapshot.sourceUrl).substring(0, 24);
        CrawlBookRaw book = bookRawMapper.selectOne(new QueryWrapper<CrawlBookRaw>()
                .eq("source_code", source.sourceCode)
                .eq("source_book_id", sourceBookId)
                .last("LIMIT 1"));
        if (book == null) {
            book = new CrawlBookRaw();
            book.createdAt = now;
        }
        book.sourceCode = source.sourceCode;
        book.sourceBookId = sourceBookId;
        book.sourceUrl = limit(snapshot.sourceUrl, 512);
        book.title = limit(snapshot.title, 128);
        book.author = limit(StringUtils.hasText(snapshot.author) ? snapshot.author : "未知作者", 64);
        book.intro = snapshot.intro;
        book.coverUrl = limit(snapshot.coverUrl, 512);
        book.categoryName = "未分类";
        book.bookStatus = "UNKNOWN";
        book.wordCount = snapshot.wordCount;
        book.heatScore = 0L;
        book.rankType = rank.rankType;
        book.contentStatus = StringUtils.hasText(snapshot.chapterId) ? "CATALOG_READY" : "META_ONLY";
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

    private void upsertChapterAndContent(CrawlBookRaw book, BookSnapshot snapshot) {
        if (!StringUtils.hasText(snapshot.chapterId) && !StringUtils.hasText(snapshot.chapterUrl)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String sourceChapterId = StringUtils.hasText(snapshot.chapterId) ? snapshot.chapterId : sha256(snapshot.chapterUrl).substring(0, 24);
        CrawlChapterRaw chapter = chapterRawMapper.selectOne(new QueryWrapper<CrawlChapterRaw>()
                .eq("book_raw_id", book.id)
                .eq("source_chapter_id", sourceChapterId)
                .last("LIMIT 1"));
        if (chapter == null) {
            chapter = new CrawlChapterRaw();
            chapter.bookRawId = book.id;
            chapter.createdAt = now;
        }
        chapter.sourceChapterId = sourceChapterId;
        chapter.sourceUrl = limit(snapshot.chapterUrl, 512);
        chapter.chapterNo = 1;
        chapter.title = "公开章节入口 #" + sourceChapterId;
        chapter.vip = false;
        chapter.priceCoin = 0;
        chapter.contentStatus = "ENTRY_READY";
        chapter.crawledAt = now;
        chapter.updatedAt = now;

        String content = fetchPublicChapterContent(snapshot.chapterUrl);
        if (StringUtils.hasText(content)) {
            chapter.contentHash = sha256(content);
            chapter.contentStatus = "CONTENT_READY";
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

    private String fetchPublicChapterContent(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            validateUrl(url);
            Document document = fetch(url);
            String text = document.select(".read-content p, .chapter-content p, .content p, #chapterContent p").eachText()
                    .stream()
                    .filter(StringUtils::hasText)
                    .reduce("", (left, right) -> left + (left.isEmpty() ? "" : "\n") + right.trim());
            if (text.length() < 80 || containsBlockedText(text)) {
                return "";
            }
            return text;
        } catch (Exception ex) {
            return "";
        }
    }

    private void upsertContent(CrawlChapterRaw chapter, String content) {
        CrawlContentRaw existing = contentRawMapper.selectOne(new QueryWrapper<CrawlContentRaw>()
                .eq("chapter_raw_id", chapter.id)
                .last("LIMIT 1"));
        CrawlContentRaw raw = existing == null ? new CrawlContentRaw() : existing;
        raw.chapterRawId = chapter.id;
        raw.content = content;
        raw.contentHash = sha256(content);
        raw.contentLength = content.length();
        raw.storageMode = "MYSQL_LONGTEXT";
        if (raw.id == null) {
            raw.createdAt = LocalDateTime.now();
            contentRawMapper.insert(raw);
        } else {
            contentRawMapper.updateById(raw);
        }
    }

    private void updateMergeTask(CrawlTaskRecord task) {
        CrawlMergeTask mergeTask = mergeTaskMapper.selectOne(new QueryWrapper<CrawlMergeTask>()
                .eq("crawl_task_id", task.id)
                .last("LIMIT 1"));
        if (mergeTask == null) {
            return;
        }
        mergeTask.totalCount = task.successCount == null ? 0 : task.successCount;
        mergeTask.status = "PENDING";
        mergeTask.message = "采集完成，等待清洗匹配与入业务库。";
        mergeTask.updatedAt = LocalDateTime.now();
        mergeTaskMapper.updateById(mergeTask);
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("仅允许 http/https 地址");
            }
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                throw new IllegalArgumentException("地址缺少域名");
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("禁止采集内网或本机地址");
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("采集域名无法解析");
        }
    }

    private String firstText(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String text = "meta[name=description]".equals(selector) ? element.attr("content") : element.text();
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }
        return "";
    }

    private String firstChapterId(Document document) {
        for (Element link : document.select("a[href*='/chapter/']")) {
            Matcher matcher = Pattern.compile("/chapter/\\d+/(\\d+)").matcher(link.attr("href"));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private String publicChapterUrl(String bookUrl, String chapterId) {
        String bookId = extractBookId(bookUrl);
        if (StringUtils.hasText(bookId) && StringUtils.hasText(chapterId)) {
            return "https://www.qidian.com/chapter/" + bookId + "/" + chapterId + "/";
        }
        return "";
    }

    private String extractBookId(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        Matcher matcher = BOOK_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private int maxBooks(CrawlRankSource rank) {
        return rank.maxBooks == null || rank.maxBooks <= 0 ? DEFAULT_MAX_BOOKS : Math.min(rank.maxBooks, 100);
    }

    private boolean containsBlockedText(String text) {
        return text.contains("登录") || text.contains("付费") || text.contains("订阅") || text.contains("VIP");
    }

    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(0, queryIndex) : url;
    }

    private String normalizeImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        return url.startsWith("//") ? "https:" + url : url;
    }

    private String cleanAuthor(String author) {
        if (!StringUtils.hasText(author)) {
            return "";
        }
        return author.replace("作者：", "").replace("作家：", "").trim();
    }

    private String unescapeJson(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\t", " ")
                .trim();
    }

    private long parseWordCount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        String normalized = text.replace(",", "").trim();
        try {
            if (normalized.contains("万")) {
                return Math.round(Double.parseDouble(normalized.replaceAll("[^0-9.]", "")) * 10000);
            }
            String digits = normalized.replaceAll("[^0-9]", "");
            return StringUtils.hasText(digits) ? Long.parseLong(digits) : 0L;
        } catch (NumberFormatException ex) {
            return 0L;
        }
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
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record BookSeed(String url, String title, String author, String intro, long wordCount,
                            String chapterId, String rankUrl) {
    }

    private record BookSnapshot(String title, String author, String coverUrl, String intro, String sourceUrl,
                                String sourceBookId, long wordCount, String chapterId, String chapterUrl) {
    }
}
