package com.mini.novel.crawler.parser;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.ArrayList;
import java.util.Collections;
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
@Order(1000)
public class GenericCrawlerSiteParser implements CrawlerSiteParser {
    private static final int DEFAULT_MAX_CHAPTERS_PER_BOOK = 2000;
    private static final int MAX_CHAPTERS_PER_BOOK_CAP = 5000;
    private static final int DEFAULT_MAX_CATALOG_PAGES = 1;
    private static final int MAX_CATALOG_PAGES_CAP = 20;
    private static final Pattern CHAPTER_NO_PATTERN = Pattern.compile(
            "(?:\\u7b2c\\s*)?([0-9\\u4e00\\u4e8c\\u4e09\\u56db\\u4e94\\u516d\\u4e03\\u516b\\u4e5d\\u5341\\u767e\\u5343\\u4e07]+)\\s*[\\u7ae0\\u8282\\u5377]");
    private static final Pattern WORD_COUNT_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([\\u4e07\\u5343]?)\\s*[\\u5b57]?");

    @Override
    public boolean supports(CrawlerSourceConfig source, String rankUrl) {
        return true;
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks) {
        return parseBookSeeds(null, document, rankUrl, maxBooks);
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(CrawlerSourceConfig source, Document document, String rankUrl, int maxBooks) {
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        List<ParsedBookSeed> ruleSeeds = parseRuleBookSeeds(rules, document, rankUrl, maxBooks);
        if (!ruleSeeds.isEmpty()) {
            return ruleSeeds;
        }

        Set<String> links = new LinkedHashSet<>();
        for (Element link : document.select("a[href]")) {
            String text = link.text();
            String href = normalizeUrl(link.absUrl("href"));
            if (StringUtils.hasText(href) && looksLikeBookLink(href, text)) {
                links.add(href);
            }
            if (links.size() >= maxBooks) {
                break;
            }
        }

        List<ParsedBookSeed> seeds = new ArrayList<>();
        for (String link : links) {
            seeds.add(new ParsedBookSeed(link, "", "", "", 0L, "", rankUrl));
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

        String title = firstNonBlank(
                firstRuleValue(detail, rules.text("bookRules.name", "book.name", "detail.name")),
                seed.title(),
                firstText(detail, "h1", ".book-title", ".novel-title", "meta[property=og:title]", "title"));
        String author = firstNonBlank(
                firstRuleValue(detail, rules.text("bookRules.author", "book.author", "detail.author")),
                seed.author(),
                firstText(detail, ".author", ".book-author", ".writer", "a[href*='author']"));
        String intro = firstNonBlank(
                firstRuleValue(detail, rules.text("bookRules.intro", "book.intro", "detail.intro")),
                seed.intro(),
                firstText(detail, ".intro", ".book-intro", ".summary", ".description", "meta[name=description]"));
        String categoryName = firstNonBlank(
                firstRuleValue(detail, rules.text("bookRules.categoryName", "book.categoryName", "detail.categoryName")),
                firstText(detail, "meta[property=og:novel:category]", ".category", ".book-category"));
        String bookStatus = normalizeBookStatus(firstNonBlank(
                firstRuleValue(detail, rules.text("bookRules.status", "book.status", "detail.status")),
                firstText(detail, "meta[property=og:novel:status]", ".status", ".book-status", ".book-meta", ".book-info")));
        String cover = firstNonBlank(
                firstRuleValue(detail, rules.text("bookRules.cover", "book.cover", "detail.cover")),
                firstImage(detail));
        long wordCount = firstPositive(
                parseWordCount(firstRuleValue(detail, rules.text("bookRules.wordCount", "book.wordCount", "detail.wordCount"))),
                seed.wordCount());

        List<ParsedChapterSnapshot> chapters = new ArrayList<>(ruleChapterLinks(rules, detail));
        if (chapters.isEmpty()) {
            chapters.addAll(chapterLinks(detail, maxChapters(rules)));
        }
        if (!chapters.isEmpty()) {
            chapters = uniqueAndOrder(chapters, rules);
        }

        String catalogUrl = firstNonBlank(
                firstRuleValue(detail, rules.text("bookRules.catalogUrl", "book.catalogUrl", "detail.catalogUrl")),
                firstCatalogUrl(detail));
        if (StringUtils.hasText(catalogUrl)) {
            List<ParsedChapterSnapshot> catalogChapters = fetchCatalogChapters(rules, catalogUrl, fetcher);
            if (catalogChapters.size() > chapters.size()) {
                chapters = catalogChapters;
            }
        }

        if (chapters.isEmpty()) {
            String chapterUrl = firstChapterUrl(detail);
            if (StringUtils.hasText(chapterUrl)) {
                chapters = List.of(new ParsedChapterSnapshot(stableId(chapterUrl), "", chapterUrl, 1, false));
            }
        }

        String sourceBookId = firstNonBlank(firstRuleValue(detail, rules.text("bookRules.sourceBookId", "book.sourceBookId")),
                stableId(seed.url()));
        String chapterId = chapters.isEmpty() ? "" : chapters.get(0).chapterId();
        String chapterUrl = chapters.isEmpty() ? "" : chapters.get(0).url();
        return new ParsedBookSnapshot(title, cleanAuthor(author), cover, intro, seed.url(), sourceBookId,
                wordCount, categoryName, bookStatus, chapterId, chapterUrl, chapters);
    }

    private List<ParsedBookSeed> parseRuleBookSeeds(CrawlerRuleConfig rules, Document document, String rankUrl, int maxBooks) {
        String listSelector = rules.text("rankRules.bookList", "searchRules.bookList", "search.list");
        if (!StringUtils.hasText(listSelector)) {
            return List.of();
        }
        List<ParsedBookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element item : document.select(listSelector)) {
            String url = normalizeUrl(ruleValue(item, rules.text("rankRules.bookUrl", "searchRules.bookUrl", "search.bookUrl", "search.detailUrl")));
            if (!StringUtils.hasText(url) && "a".equalsIgnoreCase(item.tagName())) {
                url = normalizeUrl(item.absUrl("href"));
            }
            if (!StringUtils.hasText(url) || !seen.add(url)) {
                continue;
            }
            seeds.add(new ParsedBookSeed(
                    url,
                    ruleValue(item, rules.text("rankRules.bookName", "searchRules.bookName", "search.name")),
                    ruleValue(item, rules.text("rankRules.author", "searchRules.author", "search.author")),
                    ruleValue(item, rules.text("rankRules.intro", "searchRules.intro", "search.intro")),
                    parseWordCount(ruleValue(item, rules.text("rankRules.wordCount", "searchRules.wordCount", "search.wordCount"))),
                    ruleValue(item, rules.text("rankRules.chapterId", "searchRules.chapterId", "search.chapterId")),
                    rankUrl));
            if (seeds.size() >= maxBooks) {
                break;
            }
        }
        return seeds;
    }

    private List<ParsedChapterSnapshot> fetchCatalogChapters(CrawlerRuleConfig rules, String catalogUrl,
                                                             DocumentFetcher fetcher) throws Exception {
        List<ParsedChapterSnapshot> chapters = new ArrayList<>();
        Set<String> visitedPages = new LinkedHashSet<>();
        String currentUrl = catalogUrl;
        int maxPages = Math.max(1, Math.min(rules.intValue(DEFAULT_MAX_CATALOG_PAGES,
                "catalogRules.maxPages", "toc.maxPages"), MAX_CATALOG_PAGES_CAP));
        while (StringUtils.hasText(currentUrl) && visitedPages.size() < maxPages && visitedPages.add(currentUrl)) {
            Document catalog = fetcher.fetch(currentUrl);
            chapters.addAll(ruleChapterLinks(rules, catalog));
            if (chapters.isEmpty()) {
                chapters.addAll(chapterLinks(catalog, maxChapters(rules)));
            }
            currentUrl = nextCatalogPageUrl(catalog, currentUrl, rules);
        }
        return chapters;
    }

    private List<ParsedChapterSnapshot> ruleChapterLinks(CrawlerRuleConfig rules, Document document) {
        String listSelector = rules.text("catalogRules.chapterList", "toc.chapterList", "catalog.list");
        if (!StringUtils.hasText(listSelector)) {
            return List.of();
        }
        List<ParsedChapterSnapshot> chapters = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int maxChapters = maxChapters(rules);
        for (Element item : document.select(listSelector)) {
            String href = normalizeUrl(ruleValue(item, rules.text("catalogRules.chapterUrl", "toc.chapterUrl", "catalog.url")));
            if (!StringUtils.hasText(href) && "a".equalsIgnoreCase(item.tagName())) {
                href = normalizeUrl(item.absUrl("href"));
            }
            if (!StringUtils.hasText(href) || !seen.add(href)) {
                continue;
            }
            String title = cleanChapterTitle(ruleValue(item, rules.text("catalogRules.chapterTitle", "toc.chapterTitle", "catalog.title")));
            if (!StringUtils.hasText(title)) {
                title = cleanChapterTitle(item.text());
            }
            int chapterNo = normalizedChapterNo(title, chapters.size() + 1);
            chapters.add(new ParsedChapterSnapshot(stableId(href),
                    StringUtils.hasText(title) ? title : "Chapter " + chapterNo,
                    href,
                    chapterNo,
                    isVipHint(title, href)));
            if (chapters.size() >= maxChapters) {
                break;
            }
        }
        return uniqueAndOrder(chapters, rules);
    }

    private List<ParsedChapterSnapshot> chapterLinks(Document detail, int maxChapters) {
        List<ParsedChapterSnapshot> chapters = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element link : detail.select("a[href]")) {
            String text = cleanChapterTitle(link.text());
            String href = normalizeUrl(link.absUrl("href"));
            if (!looksLikeChapterLink(href, text) || !seen.add(href)) {
                continue;
            }
            int chapterNo = normalizedChapterNo(text, chapters.size() + 1);
            chapters.add(new ParsedChapterSnapshot(stableId(href),
                    StringUtils.hasText(text) ? text : "Chapter " + chapterNo,
                    href,
                    chapterNo,
                    isVipHint(text, href)));
            if (chapters.size() >= maxChapters) {
                break;
            }
        }
        return chapters;
    }

    private List<ParsedChapterSnapshot> uniqueAndOrder(List<ParsedChapterSnapshot> chapters, CrawlerRuleConfig rules) {
        if (chapters.isEmpty()) {
            return chapters;
        }
        List<ParsedChapterSnapshot> unique = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParsedChapterSnapshot chapter : chapters) {
            if (StringUtils.hasText(chapter.url()) && seen.add(chapter.url())) {
                int chapterNo = chapter.chapterNo() <= 0 ? unique.size() + 1 : chapter.chapterNo();
                unique.add(new ParsedChapterSnapshot(chapter.chapterId(), chapter.title(), chapter.url(), chapterNo, chapter.vip()));
            }
        }
        if (rules.boolValue(false, "catalogRules.reverse", "toc.reverse", "catalog.reverse")) {
            Collections.reverse(unique);
            List<ParsedChapterSnapshot> reordered = new ArrayList<>();
            for (int i = 0; i < unique.size(); i++) {
                ParsedChapterSnapshot chapter = unique.get(i);
                reordered.add(new ParsedChapterSnapshot(chapter.chapterId(), chapter.title(), chapter.url(), i + 1, chapter.vip()));
            }
            return reordered;
        }
        return unique;
    }

    private String firstChapterUrl(Document detail) {
        for (Element link : detail.select("a[href]")) {
            String text = link.text();
            String href = normalizeUrl(link.absUrl("href"));
            String lower = href.toLowerCase();
            if (StringUtils.hasText(href)
                    && (lower.contains("chapter") || lower.contains("read"))
                    && (text.contains("\u7b2c") || text.contains("\u7ae0")
                    || text.contains("\u9605\u8bfb") || text.contains("\u5f00\u59cb"))) {
                return href;
            }
        }
        return "";
    }

    private String firstCatalogUrl(Document detail) {
        for (Element link : detail.select("a[href]")) {
            String text = link.text();
            String href = normalizeUrl(link.absUrl("href"));
            String lower = href.toLowerCase();
            if (StringUtils.hasText(href)
                    && (text.contains("\u76ee\u5f55") || lower.contains("catalog") || lower.contains("chapterlist"))) {
                return href;
            }
        }
        return "";
    }

    private String nextCatalogPageUrl(Document document, String currentUrl, CrawlerRuleConfig rules) {
        String nextSelector = rules.text("catalogRules.nextPage", "toc.nextPage", "catalog.nextPage");
        if (!StringUtils.hasText(nextSelector)) {
            return "";
        }
        for (Element link : document.select(nextSelector)) {
            String href = normalizeUrl(StringUtils.hasText(link.attr("href")) ? link.absUrl("href") : link.text());
            if (StringUtils.hasText(href) && !href.equals(currentUrl)) {
                return href;
            }
        }
        return "";
    }

    private boolean looksLikeBookLink(String href, String text) {
        String lower = href.toLowerCase();
        return text != null && text.trim().length() >= 2
                && (lower.contains("book") || lower.contains("novel") || lower.contains("info"));
    }

    private boolean looksLikeChapterLink(String href, String text) {
        if (!StringUtils.hasText(href) || !StringUtils.hasText(text)) {
            return false;
        }
        String lower = href.toLowerCase();
        String normalizedText = text.trim();
        if (normalizedText.length() > 100
                || normalizedText.contains("\u767b\u5f55")
                || normalizedText.contains("\u6ce8\u518c")) {
            return false;
        }
        return lower.contains("chapter") || lower.contains("read") || lower.matches(".*/\\d+\\.html$")
                || normalizedText.contains("\u7ae0") || normalizedText.contains("\u8282")
                || normalizedText.startsWith("\u7b2c");
    }

    private String firstText(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                String text = selector.startsWith("meta") ? element.attr("content") : element.text();
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }
        return "";
    }

    private String firstImage(Document detail) {
        return detail.select("img[src]").stream()
                .map(img -> normalizeImageUrl(img.absUrl("src")))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("https://dummyimage.com/300x420/20232a/ffffff&text=Novel");
    }

    private String firstRuleValue(Element scope, String rule) {
        if (!StringUtils.hasText(rule)) {
            return "";
        }
        for (String part : rule.split("\\|\\|")) {
            String value = ruleValue(scope, part);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String ruleValue(Element scope, String rule) {
        if (!StringUtils.hasText(rule)) {
            return "";
        }
        String selector = rule.trim();
        if ("text".equalsIgnoreCase(selector)) {
            return scope.text().trim();
        }
        if ("ownText".equalsIgnoreCase(selector)) {
            return scope.ownText().trim();
        }
        if ("html".equalsIgnoreCase(selector)) {
            return scope.html().trim();
        }
        String attr = "";
        int atIndex = selector.lastIndexOf('@');
        if (atIndex > 0 && atIndex < selector.length() - 1) {
            attr = selector.substring(atIndex + 1).trim();
            selector = selector.substring(0, atIndex).trim();
        } else if (selector.startsWith("attr:")) {
            attr = selector.substring("attr:".length()).trim();
            selector = "";
        } else if (selector.startsWith("css:")) {
            selector = selector.substring("css:".length()).trim();
        }
        Element element = StringUtils.hasText(selector) ? scope.selectFirst(selector) : scope;
        if (element == null) {
            return "";
        }
        if (StringUtils.hasText(attr)) {
            if ("href".equalsIgnoreCase(attr) || "src".equalsIgnoreCase(attr)) {
                return normalizeImageUrl(element.absUrl(attr));
            }
            return element.attr(attr).trim();
        }
        if (element.tagName().equalsIgnoreCase("meta")) {
            return element.attr("content").trim();
        }
        return element.text().trim();
    }

    private int maxChapters(CrawlerRuleConfig rules) {
        return Math.max(1, Math.min(rules.intValue(DEFAULT_MAX_CHAPTERS_PER_BOOK,
                "catalogRules.maxChapters", "toc.maxChapters", "catalog.maxChapters"), MAX_CHAPTERS_PER_BOOK_CAP));
    }

    private int normalizedChapterNo(String title, int fallback) {
        int parsedNo = parseChapterNo(title);
        return parsedNo > 0 ? parsedNo : fallback;
    }

    private int parseChapterNo(String title) {
        Matcher matcher = CHAPTER_NO_PATTERN.matcher(title == null ? "" : title);
        if (!matcher.find()) {
            return 0;
        }
        String value = matcher.group(1);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return chineseNumber(value);
        }
    }

    private int chineseNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        int result = 0;
        int section = 0;
        int number = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = chineseDigit(value.charAt(i));
            if (digit >= 0) {
                number = digit;
                continue;
            }
            int unit = chineseUnit(value.charAt(i));
            if (unit == 10000) {
                section = (section + number) * unit;
                result += section;
                section = 0;
                number = 0;
            } else if (unit > 0) {
                section += (number == 0 ? 1 : number) * unit;
                number = 0;
            }
        }
        return result + section + number;
    }

    private int chineseDigit(char ch) {
        return switch (ch) {
            case '\u96f6' -> 0;
            case '\u4e00' -> 1;
            case '\u4e8c', '\u4e24' -> 2;
            case '\u4e09' -> 3;
            case '\u56db' -> 4;
            case '\u4e94' -> 5;
            case '\u516d' -> 6;
            case '\u4e03' -> 7;
            case '\u516b' -> 8;
            case '\u4e5d' -> 9;
            default -> -1;
        };
    }

    private int chineseUnit(char ch) {
        return switch (ch) {
            case '\u5341' -> 10;
            case '\u767e' -> 100;
            case '\u5343' -> 1000;
            case '\u4e07' -> 10000;
            default -> 0;
        };
    }

    private long parseWordCount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0L;
        }
        Matcher matcher = WORD_COUNT_PATTERN.matcher(text.replace(",", ""));
        if (!matcher.find()) {
            return 0L;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if ("\u4e07".equals(unit)) {
            value *= 10000D;
        } else if ("\u5343".equals(unit)) {
            value *= 1000D;
        }
        return Math.round(value);
    }

    private String normalizeBookStatus(String text) {
        if (!StringUtils.hasText(text)) {
            return "UNKNOWN";
        }
        String value = text.replaceAll("\\s+", "");
        if (value.contains("\u5b8c\u7ed3")
                || value.contains("\u5df2\u5b8c\u7ed3")
                || value.contains("\u5168\u672c")
                || value.equalsIgnoreCase("completed")
                || value.equalsIgnoreCase("finished")) {
            return "COMPLETED";
        }
        if (value.contains("\u8fde\u8f7d")
                || value.contains("\u66f4\u65b0\u4e2d")
                || value.equalsIgnoreCase("serializing")
                || value.equalsIgnoreCase("ongoing")) {
            return "SERIALIZING";
        }
        return "UNKNOWN";
    }

    private boolean isVipHint(String text, String href) {
        String value = ((text == null ? "" : text) + " " + (href == null ? "" : href)).toLowerCase();
        return value.contains("vip")
                || value.contains("\u4ed8\u8d39")
                || value.contains("\u8ba2\u9605");
    }

    private String cleanChapterTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        return title.replaceAll("\\s+", " ").trim();
    }

    private String cleanAuthor(String author) {
        if (!StringUtils.hasText(author)) {
            return "";
        }
        return author.replace("\u4f5c\u8005\uff1a", "")
                .replace("\u4f5c\u5bb6\uff1a", "")
                .replace("Author:", "")
                .trim();
    }

    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String trimmed = normalizeImageUrl(url.trim());
        int hashIndex = trimmed.indexOf('#');
        return hashIndex >= 0 ? trimmed.substring(0, hashIndex) : trimmed;
    }

    private String normalizeImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        return url.startsWith("//") ? "https:" + url : url;
    }

    private String stableId(String value) {
        return Integer.toHexString((value == null ? "" : value).hashCode());
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

    private long firstPositive(long... values) {
        for (long value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0L;
    }
}
