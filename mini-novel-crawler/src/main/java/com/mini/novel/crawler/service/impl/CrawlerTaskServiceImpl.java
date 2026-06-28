package com.mini.novel.crawler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.ChapterMapper;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.crawler.entity.CrawlTask;
import com.mini.novel.crawler.mapper.CrawlTaskMapper;
import com.mini.novel.crawler.model.CrawlSubmitRequest;
import com.mini.novel.crawler.service.CrawlerTaskService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
public class CrawlerTaskServiceImpl implements CrawlerTaskService {
    private static final String DEFAULT_QIDIAN_URL = "https://m.qidian.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final int MAX_BOOKS = 12;
    private static final Pattern MOBILE_BOOK_PATTERN = Pattern.compile(
            "\\{[^{}]*?\"bid\"\\s*:\\s*\"?(\\d+)\"?[^{}]*?\"cid\"\\s*:\\s*\"?(\\d+)\"?[^{}]*?\"bName\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"bAuth\"\\s*:\\s*\"([^\"]+)\"[^{}]*?(?:\"desc\"\\s*:\\s*\"([^\"]*)\")?[^{}]*?(?:\"cnt\"\\s*:\\s*\"([^\"]*)\")?[^{}]*?\\}");

    private final CrawlTaskMapper crawlTaskMapper;
    private final NovelMapper novelMapper;
    private final ChapterMapper chapterMapper;
    private final TaskExecutor applicationTaskExecutor;

    public CrawlerTaskServiceImpl(
            CrawlTaskMapper crawlTaskMapper,
            NovelMapper novelMapper,
            ChapterMapper chapterMapper,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor) {
        this.crawlTaskMapper = crawlTaskMapper;
        this.novelMapper = novelMapper;
        this.chapterMapper = chapterMapper;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public CrawlTask submit(CrawlSubmitRequest request) {
        CrawlTask task = new CrawlTask();
        task.setSourceId(request.getSourceId());
        task.setNovelId(request.getNovelId());
        task.setTaskType("MANUAL");
        task.setStatus(0);
        task.setRetryCount(0);
        task.setCreatedAt(LocalDateTime.now());
        crawlTaskMapper.insert(task);
        applicationTaskExecutor.execute(() -> execute(task.getId(), request.getSeedUrl()));
        return task;
    }

    private void execute(Long taskId, String seedUrl) {
        CrawlTask task = crawlTaskMapper.selectById(taskId);
        task.setStartedAt(LocalDateTime.now());
        crawlTaskMapper.updateById(task);
        try {
            String url = StringUtils.hasText(seedUrl) ? seedUrl : DEFAULT_QIDIAN_URL;
            validateSeedUrl(url);
            int imported = crawlQidian(url);
            if (imported == 0) {
                throw new IllegalStateException("未从公开页面解析到书籍数据，可能被站点校验页拦截。");
            }
            task.setStatus(1);
            task.setMessage("起点公开元数据采集完成：最近5天任务窗口内写入/更新 " + imported + " 本书。");
        } catch (Exception ex) {
            task.setStatus(2);
            task.setMessage("起点采集失败：" + ex.getMessage());
        } finally {
            task.setFinishedAt(LocalDateTime.now());
            crawlTaskMapper.updateById(task);
        }
    }

    private int crawlQidian(String seedUrl) throws IOException {
        Document listPage = fetch(seedUrl);
        List<BookSeed> seeds = parseBookSeeds(listPage);
        int imported = 0;
        for (BookSeed seed : seeds) {
            BookSnapshot snapshot = fetchBook(seed);
            if (!StringUtils.hasText(snapshot.title)) {
                continue;
            }
            Novel novel = upsertNovel(snapshot);
            upsertPublicChapter(novel, snapshot);
            imported++;
        }
        return imported;
    }

    private Document fetch(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(url.contains("m.qidian.com") ? MOBILE_USER_AGENT : USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .timeout(15000)
                .get();
    }

    private void validateSeedUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("仅允许 http/https 采集地址");
            }
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                throw new IllegalArgumentException("采集地址缺少域名");
            }
            String lowerHost = host.toLowerCase();
            if ("localhost".equals(lowerHost) || lowerHost.endsWith(".localhost")) {
                throw new IllegalArgumentException("禁止采集 localhost 地址");
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("禁止采集内网或本机地址");
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("采集域名无法解析");
        }
    }

    private List<BookSeed> parseBookSeeds(Document document) {
        Set<String> links = new LinkedHashSet<>();
        List<BookSeed> mobileSeeds = parseMobileEmbeddedBooks(document.html());
        if (!mobileSeeds.isEmpty()) {
            return mobileSeeds;
        }
        for (Element link : document.select("a[href*='book.qidian.com/info/'], a[href*='www.qidian.com/book/']")) {
            String href = normalizeUrl(link.absUrl("href"));
            if (StringUtils.hasText(href)) {
                links.add(href);
            }
            if (links.size() >= MAX_BOOKS) {
                break;
            }
        }
        List<BookSeed> seeds = new ArrayList<>();
        for (String link : links) {
            seeds.add(new BookSeed(link, "", "", "", 0L, ""));
        }
        return seeds;
    }

    private BookSnapshot fetchBook(BookSeed seed) throws IOException {
        if (StringUtils.hasText(seed.title)) {
            return new BookSnapshot(seed.title, seed.author, "https://dummyimage.com/300x420/20232a/ffffff&text=Qidian",
                    seed.intro, seed.url, seed.wordCount, seed.chapterId);
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
        return new BookSnapshot(title, cleanAuthor(author), cover, intro, seed.url, wordCount, seed.chapterId);
    }

    private List<BookSeed> parseMobileEmbeddedBooks(String html) {
        List<BookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = MOBILE_BOOK_PATTERN.matcher(html);
        while (matcher.find() && seeds.size() < MAX_BOOKS) {
            String bid = matcher.group(1);
            String cid = matcher.group(2);
            if (!seen.add(bid)) {
                continue;
            }
            String title = unescapeJson(matcher.group(3));
            String author = unescapeJson(matcher.group(4));
            String intro = matcher.group(5) == null ? "" : unescapeJson(matcher.group(5));
            long wordCount = parseWordCount(matcher.group(6));
            seeds.add(new BookSeed(
                    "https://book.qidian.com/info/" + bid,
                    title,
                    author,
                    intro,
                    wordCount,
                    cid));
        }
        return seeds;
    }

    private Novel upsertNovel(BookSnapshot snapshot) {
        Novel novel = novelMapper.selectOne(new LambdaQueryWrapper<Novel>()
                .eq(Novel::getSourceUrl, snapshot.sourceUrl)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (novel == null) {
            novel = new Novel();
            novel.setCreatedAt(now);
        }
        novel.setTitle(limit(snapshot.title, 128));
        novel.setAuthor(limit(StringUtils.hasText(snapshot.author) ? snapshot.author : "起点作者", 64));
        novel.setCoverUrl(limit(snapshot.coverUrl, 512));
        novel.setIntro(snapshot.intro);
        novel.setCategoryId(1L);
        novel.setStatus(1);
        novel.setWordCount(snapshot.wordCount);
        novel.setLatestChapterTitle(publicChapterTitle(snapshot));
        novel.setSourceUrl(limit(snapshot.sourceUrl, 512));
        novel.setUpdatedAt(now);
        if (novel.getId() == null) {
            novelMapper.insert(novel);
        } else {
            novelMapper.updateById(novel);
        }
        return novel;
    }

    private void upsertPublicChapter(Novel novel, BookSnapshot snapshot) {
        Chapter chapter = chapterMapper.selectOne(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, novel.getId())
                .eq(Chapter::getChapterNo, 1)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (chapter == null) {
            chapter = new Chapter();
            chapter.setNovelId(novel.getId());
            chapter.setChapterNo(1);
            chapter.setCreatedAt(now);
        }
        chapter.setTitle(publicChapterTitle(snapshot));
        chapter.setContent(buildPublicChapterNotice(snapshot));
        chapter.setVip(false);
        chapter.setPriceCoin(0);
        chapter.setSourceUrl(limit(publicChapterUrl(snapshot), 512));
        chapter.setUpdatedAt(now);
        if (chapter.getId() == null) {
            chapterMapper.insert(chapter);
        } else {
            chapterMapper.updateById(chapter);
        }
        novel.setLatestChapterId(chapter.getId());
        novelMapper.updateById(novel);
    }

    private String publicChapterTitle(BookSnapshot snapshot) {
        if (StringUtils.hasText(snapshot.chapterId)) {
            return "起点公开章节入口 #" + snapshot.chapterId;
        }
        return "起点公开书籍资料";
    }

    private String publicChapterUrl(BookSnapshot snapshot) {
        String bookId = snapshot.sourceUrl == null ? "" : snapshot.sourceUrl.replaceAll(".*/", "");
        if (StringUtils.hasText(bookId) && StringUtils.hasText(snapshot.chapterId)) {
            return "https://www.qidian.com/chapter/" + bookId + "/" + snapshot.chapterId + "/";
        }
        return snapshot.sourceUrl;
    }

    private String buildPublicChapterNotice(BookSnapshot snapshot) {
        String intro = StringUtils.hasText(snapshot.intro) ? snapshot.intro : "该书来自起点公开页面，本次采集未获取到公开简介。";
        return snapshot.title + "\n\n" + intro.trim()
                + "\n\n书籍来源：" + snapshot.sourceUrl
                + "\n章节入口：" + publicChapterUrl(snapshot)
                + "\n\n版权说明：当前采集器只写入公开元数据和来源链接，不复制起点登录、VIP、付费或受版权保护的章节正文。";
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record BookSeed(String url, String title, String author, String intro, long wordCount, String chapterId) {
    }

    private record BookSnapshot(String title, String author, String coverUrl, String intro, String sourceUrl, long wordCount,
                                String chapterId) {
    }
}
