package com.mini.novel.crawler.parser;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(30)
public class XbookcnCrawlerSiteParser implements CrawlerSiteParser {
    private static final Pattern BOOK_ID_PATTERN = Pattern.compile("/(?:book|novel)/(\\d+|[A-Za-z0-9_-]+)");
    private static final int DEFAULT_MAX_CATALOG_PAGES = 20;
    private static final int DEFAULT_MAX_CHAPTERS = 5000;

    @Override
    public boolean supports(CrawlerSourceConfig source, String rankUrl) {
        String value = ((source == null ? "" : source.sourceCode + " " + source.baseUrl) + " " + rankUrl).toLowerCase();
        return value.contains("xbookcn") || value.contains("book.xbookcn.net");
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks) {
        return parseBookSeeds(null, document, rankUrl, maxBooks);
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(CrawlerSourceConfig source, Document document, String rankUrl, int maxBooks) {
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        String singleBookUrl = rules.text("poc.bookUrl", "bookUrl");
        if (StringUtils.hasText(singleBookUrl)) {
            return List.of(new ParsedBookSeed(abs(document, singleBookUrl), "", "", "", 0L, "", rankUrl));
        }

        List<ParsedBookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String selector = firstNonBlank(rules.text("rankRules.bookList"), "a[href*='/book/'], a[href*='/novel/']");
        for (Element item : document.select(selector)) {
            String href = normalize(item.hasAttr("href") ? item.absUrl("href") : firstHref(item));
            if (!isAllowedBookUrl(href) || !seen.add(href)) {
                continue;
            }
            seeds.add(new ParsedBookSeed(href, clean(item.text()), "", "", 0L, "", rankUrl));
            if (seeds.size() >= maxBooks) {
                break;
            }
        }
        return seeds;
    }

    @Override
    public ParsedBookSnapshot fetchBook(ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        return fetchBook(null, seed, fetcher);
    }

    @Override
    public ParsedBookSnapshot fetchBook(CrawlerSourceConfig source, ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        Document detail = fetcher.fetch(seed.url());
        String title = cleanBookTitle(firstNonBlank(firstText(detail, "meta[property=og:novel:book_name]", "meta[property=og:title]"),
                firstText(detail, "h1", ".book-title", ".novel-title", ".title"), seed.title()));
        String author = cleanAuthor(firstNonBlank(firstText(detail, "meta[property=og:novel:author]"),
                firstText(detail, ".author", ".book-author", ".writer", "a[href*='author']"), seed.author()));
        String intro = firstNonBlank(firstText(detail, "meta[property=og:description]", "meta[name=description]"),
                firstText(detail, ".intro", ".book-intro", ".summary", ".description"));
        String cover = firstNonBlank(firstRawAttr(detail, "meta[property=og:image]", "content"),
                firstAttr(detail, ".cover img, img.cover, .book-cover img", "src"));
        String category = firstNonBlank(firstText(detail, "meta[property=og:novel:category]"),
                firstText(detail, ".tag a", ".tags a", ".category", ".book-category"), "VIP_AUTH_REVIEW");
        String tagsJson = tagsJson(detail);
        String status = normalizeStatus(firstNonBlank(firstText(detail, "meta[property=og:novel:status]"),
                firstText(detail, ".status", ".book-status", ".book-meta", ".book-info")));
        String sourceBookId = firstNonBlank(firstRawAttr(detail, "meta[property=og:novel:book_id]", "content"), bookId(seed.url()));

        List<ParsedChapterSnapshot> chapters = List.of();
        if (!metadataOnly(rules)) {
            String catalogUrl = firstNonBlank(firstAttr(detail, "a[href*='catalog'], a[href*='chapter'], a[href*='list']", "href"),
                    seed.url());
            chapters = fetchCatalog(catalogUrl, title, rules, fetcher);
            if (chapters.isEmpty()) {
                chapters = chapterLinks(detail, title, rules.intValue(DEFAULT_MAX_CHAPTERS, "catalogRules.maxChapters"));
            }
        }

        ContentRiskGuard.RiskResult risk = ContentRiskGuard.evaluate(title, intro, "", rules.list("riskRules.blockedTerms"));
        String contentStatus = risk.reviewRequired() ? "PENDING_REVIEW" : status;
        String firstChapterId = chapters.isEmpty() ? "" : chapters.get(0).chapterId();
        String firstChapterUrl = chapters.isEmpty() ? "" : chapters.get(0).url();
        return new ParsedBookSnapshot(title, author, abs(detail, cover), intro, seed.url(), sourceBookId, seed.wordCount(),
                category, contentStatus, firstChapterId, firstChapterUrl, chapters, tagsJson);
    }

    private boolean metadataOnly(CrawlerRuleConfig rules) {
        return rules.boolValue(false, "poc.metadataOnly", "metadataOnly", "authorizedBook.metadataOnly");
    }

    private List<ParsedChapterSnapshot> fetchCatalog(String catalogUrl, String bookTitle, CrawlerRuleConfig rules,
                                                     DocumentFetcher fetcher) throws Exception {
        List<ParsedChapterSnapshot> chapters = new ArrayList<>();
        Set<String> seenPages = new LinkedHashSet<>();
        String currentUrl = catalogUrl;
        int maxPages = rules.intValue(DEFAULT_MAX_CATALOG_PAGES, "catalogRules.maxPages");
        while (StringUtils.hasText(currentUrl) && seenPages.size() < maxPages && seenPages.add(currentUrl)) {
            Document page = fetcher.fetch(currentUrl);
            chapters.addAll(chapterLinks(page, bookTitle, rules.intValue(DEFAULT_MAX_CHAPTERS, "catalogRules.maxChapters")));
            currentUrl = nextCatalogPage(page, currentUrl);
        }
        return unique(chapters);
    }

    private List<ParsedChapterSnapshot> chapterLinks(Document document, String bookTitle, int maxChapters) {
        List<ParsedChapterSnapshot> chapters = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element link : document.select("a[href]")) {
            String href = normalize(link.absUrl("href"));
            String title = cleanChapterTitle(clean(link.text()), bookTitle, chapters.size() + 1);
            if (!isChapterUrl(href) || !seen.add(href)) {
                continue;
            }
            int chapterNo = chapters.size() + 1;
            chapters.add(new ParsedChapterSnapshot(chapterId(href), StringUtils.hasText(title) ? title : "第" + chapterNo + "章",
                    href, chapterNo, true));
            if (chapters.size() >= maxChapters) {
                break;
            }
        }
        return chapters;
    }

    private List<ParsedChapterSnapshot> unique(List<ParsedChapterSnapshot> chapters) {
        List<ParsedChapterSnapshot> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParsedChapterSnapshot chapter : chapters) {
            if (seen.add(chapter.url())) {
                unique.add(new ParsedChapterSnapshot(chapter.chapterId(), chapter.title(), chapter.url(), unique.size() + 1, true));
            }
        }
        return unique;
    }

    private String nextCatalogPage(Document document, String currentUrl) {
        for (Element link : document.select("a[href]")) {
            String text = clean(link.text()).toLowerCase();
            if ((text.contains("next") || text.contains("\u4e0b\u4e00\u9875") || text.contains("\u4e0b\u9875"))
                    && !text.contains("\u4e0b\u4e00\u7ae0") && !text.contains("\u4e0b\u7ae0")) {
                String href = normalize(link.absUrl("href"));
                if (StringUtils.hasText(href) && !href.equals(currentUrl)) {
                    return href;
                }
            }
        }
        return "";
    }

    private boolean isAllowedBookUrl(String href) {
        return StringUtils.hasText(href) && href.startsWith("https://book.xbookcn.net/")
                && BOOK_ID_PATTERN.matcher(href).find();
    }

    private boolean isChapterUrl(String href) {
        if (!StringUtils.hasText(href) || !href.startsWith("https://book.xbookcn.net/")) {
            return false;
        }
        String lower = href.toLowerCase();
        return lower.contains("chapter") || lower.contains("read") || lower.matches(".*/\\d+\\.html$");
    }

    private String firstHref(Element element) {
        Element link = element.selectFirst("a[href]");
        return link == null ? "" : link.absUrl("href");
    }

    private String firstText(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String value = element.hasAttr("content") ? element.attr("content") : element.text();
                if (StringUtils.hasText(value)) {
                    return clean(value);
                }
            }
        }
        return "";
    }

    private String firstAttr(Document document, String selector, String attr) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : normalize(element.absUrl(attr));
    }

    private String firstRawAttr(Document document, String selector, String attr) {
        Element element = document.selectFirst(selector);
        return element == null ? "" : normalize(element.attr(attr));
    }

    private String tagsJson(Document document) {
        List<String> tags = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element element : document.select(".tag a, .tags a, .label a, .labels a, .book-tags a")) {
            String tag = clean(element.text());
            if (StringUtils.hasText(tag) && seen.add(tag)) {
                tags.add(tag);
            }
        }
        if (tags.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(tags.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return json.append(']').toString();
    }

    private String bookId(String url) {
        Matcher matcher = BOOK_ID_PATTERN.matcher(url == null ? "" : url);
        return matcher.find() ? matcher.group(1) : Integer.toHexString((url == null ? "" : url).hashCode());
    }

    private String chapterId(String url) {
        String normalized = normalize(url);
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1).replaceAll("\\W+", "") : Integer.toHexString(normalized.hashCode());
    }

    private String normalizeStatus(String value) {
        if (value.contains("\u5b8c\u7ed3") || value.toLowerCase().contains("complete")) {
            return "COMPLETED";
        }
        return "SERIALIZING";
    }

    private String cleanAuthor(String value) {
        return clean(value).replaceFirst("^(\\u4f5c\\u8005|author)[:\\uff1a]?\\s*", "");
    }

    private String cleanBookTitle(String value) {
        String cleaned = clean(value)
                .replaceAll("(?i)\\s*[-_|]\\s*(book\\.)?xbookcn(\\.net)?.*$", "")
                .replaceAll("\\s*[-_|]\\s*(\\u5c0f\\u8bf4|\\u5c0f\\u8bf4\\u7f51|\\u9605\\u8bfb).*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned;
    }

    private String cleanChapterTitle(String value, String bookTitle, int chapterNo) {
        String cleaned = clean(value)
                .replaceAll("(?i)\\s*[-_|]\\s*(book\\.)?xbookcn(\\.net)?.*$", "")
                .replaceAll("\\s*[-_|]\\s*(\\u5c0f\\u8bf4|\\u5c0f\\u8bf4\\u7f51|\\u9605\\u8bfb|\\u4e0b\\u4e00\\u7ae0|\\u4e0a\\u4e00\\u7ae0).*$", "")
                .replaceAll("^(?:\\u7b2c\\s*" + chapterNo + "\\s*\\u7ae0\\s*){2,}", "\u7b2c" + chapterNo + "\u7ae0 ")
                .replaceAll("^(?:chapter\\s*" + chapterNo + "\\s*){2,}", "Chapter " + chapterNo + " ")
                .replaceAll("\\s+", " ")
                .trim();
        String cleanBookTitle = cleanBookTitle(bookTitle);
        if (StringUtils.hasText(cleanBookTitle) && cleaned.startsWith(cleanBookTitle)) {
            String withoutBook = cleaned.substring(cleanBookTitle.length())
                    .replaceFirst("^[\\s:：\\-_|　]+", "")
                    .trim();
            if (StringUtils.hasText(withoutBook)) {
                cleaned = withoutBook;
            }
        }
        if (cleaned.matches("(?i)^(home|next|previous|catalog|xbookcn)$")
                || cleaned.matches("^(\\u9996\\u9875|\\u4e0b\\u4e00\\u9875|\\u4e0a\\u4e00\\u9875|\\u76ee\\u5f55)$")) {
            return "";
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : org.jsoup.parser.Parser.unescapeEntities(value, false)
                .replace('\u00a0', ' ')
                .trim();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        int hash = value.indexOf('#');
        return hash >= 0 ? value.substring(0, hash) : value;
    }

    private String abs(Document document, String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return normalize(URI.create(document.baseUri()).resolve(value).toString());
        } catch (IllegalArgumentException ex) {
            return normalize(value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
