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
@Order(1000)
public class GenericCrawlerSiteParser implements CrawlerSiteParser {
    private static final int MAX_CHAPTERS_PER_BOOK = 20;
    private static final Pattern CHAPTER_NO_PATTERN = Pattern.compile("(?:第\\s*)?([0-9一二三四五六七八九十百千万]+)\\s*[章节回]");

    @Override
    public boolean supports(CrawlerSourceConfig source, String rankUrl) {
        return true;
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks) {
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
        Document detail = fetcher.fetch(seed.url());
        String title = firstText(detail, "h1", ".book-title", ".novel-title", "meta[property=og:title]", "title");
        String author = firstText(detail, ".author", ".book-author", ".writer", "a[href*='author']");
        String intro = firstText(detail, ".intro", ".book-intro", ".summary", ".description", "meta[name=description]");
        String cover = detail.select("img[src]").stream()
                .map(img -> normalizeImageUrl(img.absUrl("src")))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("https://dummyimage.com/300x420/20232a/ffffff&text=Novel");
        List<ParsedChapterSnapshot> chapters = chapterLinks(detail);
        if (chapters.size() <= 1) {
            String catalogUrl = firstCatalogUrl(detail);
            if (StringUtils.hasText(catalogUrl) && !catalogUrl.equals(seed.url())) {
                Document catalog = fetcher.fetch(catalogUrl);
                List<ParsedChapterSnapshot> catalogChapters = chapterLinks(catalog);
                if (catalogChapters.size() > chapters.size()) {
                    chapters = catalogChapters;
                }
            }
        }
        if (chapters.isEmpty()) {
            String chapterUrl = firstChapterUrl(detail);
            if (StringUtils.hasText(chapterUrl)) {
                chapters = List.of(new ParsedChapterSnapshot(Integer.toHexString(chapterUrl.hashCode()), "", chapterUrl, 1, false));
            }
        }
        String sourceBookId = Integer.toHexString(seed.url().hashCode());
        String chapterId = chapters.isEmpty() ? "" : chapters.get(0).chapterId();
        String chapterUrl = chapters.isEmpty() ? "" : chapters.get(0).url();
        return new ParsedBookSnapshot(title, cleanAuthor(author), cover, intro, seed.url(), sourceBookId, 0L,
                chapterId, chapterUrl, chapters);
    }

    private boolean looksLikeBookLink(String href, String text) {
        String lower = href.toLowerCase();
        return text != null && text.trim().length() >= 2
                && (lower.contains("book") || lower.contains("novel") || lower.contains("info") || lower.contains("read"));
    }

    private String firstChapterUrl(Document detail) {
        for (Element link : detail.select("a[href]")) {
            String text = link.text();
            String href = normalizeUrl(link.absUrl("href"));
            String lower = href.toLowerCase();
            if (StringUtils.hasText(href)
                    && (lower.contains("chapter") || lower.contains("read"))
                    && (text.contains("第") || text.contains("章") || text.contains("阅读") || text.contains("开始"))) {
                return href;
            }
        }
        return "";
    }

    private List<ParsedChapterSnapshot> chapterLinks(Document detail) {
        List<ParsedChapterSnapshot> chapters = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element link : detail.select("a[href]")) {
            String text = cleanChapterTitle(link.text());
            String href = normalizeUrl(link.absUrl("href"));
            if (!looksLikeChapterLink(href, text) || !seen.add(href)) {
                continue;
            }
            int chapterNo = chapters.size() + 1;
            int parsedNo = parseChapterNo(text);
            if (parsedNo > 0) {
                chapterNo = parsedNo;
            }
            chapters.add(new ParsedChapterSnapshot(Integer.toHexString(href.hashCode()),
                    StringUtils.hasText(text) ? text : "章节 " + chapterNo,
                    href,
                    chapterNo,
                    isVipHint(text, href)));
            if (chapters.size() >= MAX_CHAPTERS_PER_BOOK) {
                break;
            }
        }
        return chapters;
    }

    private boolean looksLikeChapterLink(String href, String text) {
        if (!StringUtils.hasText(href) || !StringUtils.hasText(text)) {
            return false;
        }
        String lower = href.toLowerCase();
        String normalizedText = text.trim();
        if (normalizedText.length() > 80 || normalizedText.contains("登录") || normalizedText.contains("注册")) {
            return false;
        }
        return lower.contains("chapter") || lower.contains("read") || lower.matches(".*/\\d+\\.html$")
                || normalizedText.contains("章") || normalizedText.contains("节") || normalizedText.startsWith("第");
    }

    private String firstCatalogUrl(Document detail) {
        for (Element link : detail.select("a[href]")) {
            String text = link.text();
            String href = normalizeUrl(link.absUrl("href"));
            String lower = href.toLowerCase();
            if (StringUtils.hasText(href) && (text.contains("目录") || lower.contains("catalog") || lower.contains("chapterlist"))) {
                return href;
            }
        }
        return "";
    }

    private String cleanChapterTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        return title.replaceAll("\\s+", " ").trim();
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
        String digits = value
                .replace("一", "1").replace("二", "2").replace("三", "3").replace("四", "4").replace("五", "5")
                .replace("六", "6").replace("七", "7").replace("八", "8").replace("九", "9");
        if ("十".equals(value)) {
            return 10;
        }
        if (value.startsWith("十") && digits.length() > 1) {
            return 10 + Character.digit(digits.charAt(1), 10);
        }
        if (value.contains("十")) {
            String[] parts = digits.split("十", -1);
            int left = parts[0].isEmpty() ? 1 : Character.digit(parts[0].charAt(0), 10);
            int right = parts.length > 1 && !parts[1].isEmpty() ? Character.digit(parts[1].charAt(0), 10) : 0;
            return left * 10 + right;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean isVipHint(String text, String href) {
        String value = ((text == null ? "" : text) + " " + (href == null ? "" : href)).toLowerCase();
        return value.contains("vip") || value.contains("付费") || value.contains("订阅");
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

    private String cleanAuthor(String author) {
        if (!StringUtils.hasText(author)) {
            return "";
        }
        return author.replace("作者：", "").replace("作家：", "").trim();
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
}
