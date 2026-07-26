package com.mini.novel.crawler.parser;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
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
@Order(34)
public class KkxszCrawlerSiteParser implements CrawlerSiteParser {
    private static final Pattern BOOK_PATTERN = Pattern.compile("/book/([A-Za-z0-9]+)\\.html(?:$|[?#])");
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("/book/([A-Za-z0-9]+)-(\\d+)\\.html(?:$|[?#])");
    private static final Pattern LIST_PATTERN = Pattern.compile("/list-(\\d+)(?:-(\\d+))?/(?:$|[?#])");
    private static final int MAX_SYNTHETIC_CHAPTERS = 5000;

    @Override
    public boolean supports(CrawlerSourceConfig source, String rankUrl) {
        String value = ((source == null ? "" : source.sourceCode + " " + source.baseUrl) + " " + rankUrl).toLowerCase();
        return value.contains("kkxsz_public") || value.contains("kkxsz.com");
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks) {
        return parseBookSeeds(null, document, rankUrl, maxBooks);
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(CrawlerSourceConfig source, Document document, String rankUrl, int maxBooks) {
        List<ParsedBookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element link : document.select(".bookList a[href*='/book/'], .list a[href*='/book/'], "
                + ".book-list a[href*='/book/'], main a[href*='/book/'], a[href*='/book/']")) {
            String href = normalizeBookUrl(link.absUrl("href"));
            if (!isBookUrl(href) || !seen.add(href)) {
                continue;
            }
            seeds.add(new ParsedBookSeed(href, clean(link.text()), "", bookId(href), 0L, "", rankUrl));
            if (seeds.size() >= maxBooks) {
                break;
            }
        }
        return seeds;
    }

    @Override
    public String nextRankPage(CrawlerSourceConfig source, Document document, String rankUrl) {
        for (Element link : document.select(".page a.next[href], .page a[href]")) {
            String text = clean(link.text());
            String href = normalize(link.absUrl("href"));
            if (StringUtils.hasText(href) && isListUrl(href)
                    && (text.contains("\u4e0b\u4e00\u9875") || text.contains("\u4e0b\u9875"))) {
                return href.equals(rankUrl) ? "" : href;
            }
        }
        return "";
    }

    @Override
    public ParsedBookSnapshot fetchBook(ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        return fetchBook(null, seed, fetcher);
    }

    @Override
    public ParsedBookSnapshot fetchBook(CrawlerSourceConfig source, ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        Document detail = fetcher.fetch(seed.url());
        String detailUrl = normalizeBookUrl(firstNonBlank(detail.location(), seed.url()));
        String sourceBookId = firstNonBlank(bookId(detailUrl), seed.intro());
        String title = clean(firstMeta(detail, "og:novel:book_name", "og:title"));
        if (!StringUtils.hasText(title)) {
            title = clean(firstText(detail, ".chapterCon .name", ".txtb .name", "h1"));
        }
        String author = clean(firstMeta(detail, "og:novel:author"));
        String intro = clean(firstMeta(detail, "og:description", "description"));
        String category = mapCategory(firstNonBlank(firstMeta(detail, "og:novel:category"), firstInfoValue(detail, "\u5206\u7c7b")));
        String status = normalizeStatus(firstNonBlank(firstMeta(detail, "og:novel:status"), firstInfoValue(detail, "\u72b6\u6001")));
        String cover = normalize(firstNonBlank(firstMeta(detail, "og:image"), firstImage(detail)));
        long wordCount = parseWordCount(firstInfoValue(detail, "\u5b57\u6570"));
        int latestChapterNo = latestChapterNo(detail);
        List<ParsedChapterSnapshot> chapters = catalogChapters(detail, sourceBookId, latestChapterNo);
        String firstChapterId = chapters.isEmpty() ? "" : chapters.get(0).chapterId();
        String firstChapterUrl = chapters.isEmpty() ? "" : chapters.get(0).url();
        return new ParsedBookSnapshot(title, author, cover, intro, detailUrl, sourceBookId, wordCount,
                category, status, firstChapterId, firstChapterUrl, chapters);
    }

    private List<ParsedChapterSnapshot> catalogChapters(Document detail, String bookId, int latestChapterNo) {
        List<ParsedChapterSnapshot> visible = new ArrayList<>();
        Set<Integer> seenNos = new LinkedHashSet<>();
        for (Element link : detail.select(".chapterList a[href], a[href*='/book/" + bookId + "-']")) {
            String href = normalize(link.absUrl("href"));
            Matcher matcher = CHAPTER_PATTERN.matcher(href);
            if (!matcher.find() || !bookId.equals(matcher.group(1))) {
                continue;
            }
            int chapterNo = Integer.parseInt(matcher.group(2));
            if (seenNos.add(chapterNo)) {
                visible.add(new ParsedChapterSnapshot(chapterId(bookId, chapterNo),
                        chapterTitle(link.text(), chapterNo), chapterUrl(bookId, chapterNo), chapterNo, false));
            }
        }
        int maxNo = Math.max(latestChapterNo, seenNos.stream().mapToInt(Integer::intValue).max().orElse(0));
        if (maxNo <= 0 || maxNo > MAX_SYNTHETIC_CHAPTERS) {
            return visible.stream().sorted((a, b) -> Integer.compare(a.chapterNo(), b.chapterNo())).toList();
        }
        List<ParsedChapterSnapshot> chapters = new ArrayList<>();
        for (int no = 1; no <= maxNo; no++) {
            final int chapterNo = no;
            String title = visible.stream()
                    .filter(chapter -> chapter.chapterNo() == chapterNo)
                    .map(ParsedChapterSnapshot::title)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("\u7b2c" + chapterNo + "\u7ae0");
            chapters.add(new ParsedChapterSnapshot(chapterId(bookId, chapterNo), title,
                    chapterUrl(bookId, chapterNo), chapterNo, false));
        }
        return chapters;
    }

    private int latestChapterNo(Document detail) {
        String latestUrl = firstMeta(detail, "og:novel:latest_chapter_url");
        Matcher urlMatcher = CHAPTER_PATTERN.matcher(normalize(latestUrl));
        if (urlMatcher.find()) {
            return Integer.parseInt(urlMatcher.group(2));
        }
        Matcher titleMatcher = Pattern.compile("\u7b2c\\s*(\\d+)\\s*\u7ae0").matcher(firstMeta(detail, "og:novel:latest_chapter_name"));
        return titleMatcher.find() ? Integer.parseInt(titleMatcher.group(1)) : 0;
    }

    private String firstMeta(Document document, String... names) {
        for (String name : names) {
            Element element = document.selectFirst("meta[property=" + name + "], meta[name=" + name + "]");
            if (element != null && StringUtils.hasText(element.attr("content"))) {
                return element.attr("content").trim();
            }
        }
        return "";
    }

    private String firstInfoValue(Document document, String label) {
        for (Element item : document.select(".txtb li, .bookInfo li, .info li")) {
            String text = clean(item.text());
            if (text.startsWith(label + "\uff1a") || text.startsWith(label + ":")) {
                return text.substring(text.indexOf(text.contains("\uff1a") ? "\uff1a" : ":") + 1).trim();
            }
        }
        return "";
    }

    private String firstText(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            if (element != null && StringUtils.hasText(element.text())) {
                return element.text();
            }
        }
        return "";
    }

    private String firstImage(Document document) {
        Element image = document.selectFirst(".pic img[src], img[src*='/bookimg/']");
        return image == null ? "" : image.absUrl("src");
    }

    private boolean isBookUrl(String href) {
        return BOOK_PATTERN.matcher(normalize(href)).find();
    }

    private boolean isListUrl(String href) {
        return LIST_PATTERN.matcher(normalize(href)).find();
    }

    private String bookId(String url) {
        Matcher matcher = BOOK_PATTERN.matcher(normalize(url));
        return matcher.find() ? matcher.group(1) : "";
    }

    private String normalizeBookUrl(String value) {
        String url = normalize(value).replace("http://www.kkxsz.com/", "https://www.kkxsz.com/");
        Matcher matcher = BOOK_PATTERN.matcher(url);
        return matcher.find() ? "https://www.kkxsz.com/book/" + matcher.group(1) + ".html" : url;
    }

    private String chapterUrl(String bookId, int chapterNo) {
        return "https://www.kkxsz.com/book/" + bookId + "-" + chapterNo + ".html";
    }

    private String chapterId(String bookId, int chapterNo) {
        return bookId + "-" + chapterNo;
    }

    private String chapterTitle(String value, int chapterNo) {
        String title = clean(value);
        return StringUtils.hasText(title) ? title : "\u7b2c" + chapterNo + "\u7ae0";
    }

    private String mapCategory(String sourceCategory) {
        String value = clean(sourceCategory);
        if (value.contains("\u90fd\u5e02")) return "\u90fd\u5e02\u5c0f\u8bf4";
        if (value.contains("\u6e38\u620f")) return "\u6e38\u620f\u7ade\u6280";
        if (value.contains("\u79d1\u5e7b")) return "\u79d1\u5e7b\u7a7a\u95f4";
        if (value.contains("\u60ac\u7591")) return "\u60ac\u7591\u60ca\u609a";
        if (value.contains("\u8a00\u60c5")) return "\u8a00\u60c5\u5c0f\u8bf4";
        if (value.contains("\u4ed9\u4fa0") || value.contains("\u6b66\u4fa0")) return "\u4fee\u771f\u6b66\u4fa0";
        if (value.contains("\u5386\u53f2") || value.contains("\u519b\u4e8b")) return "\u5386\u53f2\u519b\u4e8b";
        if (value.contains("\u7384\u5e7b") || value.contains("\u5947\u5e7b") || value.contains("\u539f\u751f\u5e7b\u60f3")) return "\u7384\u5e7b\u9b54\u6cd5";
        return StringUtils.hasText(value) ? value : "Unknown";
    }

    private String normalizeStatus(String value) {
        String text = clean(value);
        if (text.contains("\u5b8c\u672c") || text.contains("\u5b8c\u7ed3")) {
            return "COMPLETED";
        }
        if (text.contains("\u8fde\u8f7d")) {
            return "SERIALIZING";
        }
        return "UNKNOWN";
    }

    private long parseWordCount(String value) {
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)(\u4e07)?").matcher(clean(value));
        if (!matcher.find()) {
            return 0L;
        }
        double number = Double.parseDouble(matcher.group(1));
        if (StringUtils.hasText(matcher.group(2))) {
            number *= 10000D;
        }
        return Math.round(number);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String url = value.trim();
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        int hash = url.indexOf('#');
        return hash >= 0 ? url.substring(0, hash) : url;
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
