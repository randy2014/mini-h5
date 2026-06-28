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
@Order(10)
public class QidianCrawlerSiteParser implements CrawlerSiteParser {
    private static final Pattern MOBILE_BOOK_PATTERN = Pattern.compile(
            "\\{[^{}]*?\"bid\"\\s*:\\s*\"?(\\d+)\"?[^{}]*?\"cid\"\\s*:\\s*\"?(\\d+)\"?[^{}]*?\"bName\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"bAuth\"\\s*:\\s*\"([^\"]+)\"[^{}]*?(?:\"desc\"\\s*:\\s*\"([^\"]*)\")?[^{}]*?(?:\"cnt\"\\s*:\\s*\"([^\"]*)\")?[^{}]*?\\}");
    private static final Pattern BOOK_ID_PATTERN = Pattern.compile("(?:/info/|/book/)(\\d+)");

    @Override
    public boolean supports(CrawlerSourceConfig source, String rankUrl) {
        String value = ((source.sourceCode == null ? "" : source.sourceCode) + " " + rankUrl).toLowerCase();
        return value.contains("qidian");
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks) {
        List<ParsedBookSeed> mobileSeeds = parseMobileEmbeddedBooks(document.html(), maxBooks);
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
        List<ParsedBookSeed> seeds = new ArrayList<>();
        for (String link : links) {
            seeds.add(new ParsedBookSeed(link, "", "", "", 0L, "", rankUrl));
        }
        return seeds;
    }

    @Override
    public ParsedBookSnapshot fetchBook(ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        String sourceBookId = extractBookId(seed.url());
        if (StringUtils.hasText(seed.title())) {
            return new ParsedBookSnapshot(seed.title(), seed.author(),
                    "https://dummyimage.com/300x420/20232a/ffffff&text=Qidian",
                    seed.intro(), seed.url(), sourceBookId, seed.wordCount(), seed.chapterId(),
                    publicChapterUrl(seed.url(), seed.chapterId()));
        }

        Document detail = fetcher.fetch(seed.url());
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
        return new ParsedBookSnapshot(title, cleanAuthor(author), cover, intro, seed.url(), sourceBookId, wordCount,
                chapterId, publicChapterUrl(seed.url(), chapterId));
    }

    private List<ParsedBookSeed> parseMobileEmbeddedBooks(String html, int maxBooks) {
        List<ParsedBookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = MOBILE_BOOK_PATTERN.matcher(html);
        while (matcher.find() && seeds.size() < maxBooks) {
            String bid = matcher.group(1);
            String cid = matcher.group(2);
            if (!seen.add(bid)) {
                continue;
            }
            seeds.add(new ParsedBookSeed(
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
}
