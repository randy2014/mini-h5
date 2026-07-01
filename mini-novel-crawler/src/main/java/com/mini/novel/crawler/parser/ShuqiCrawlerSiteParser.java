package com.mini.novel.crawler.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(20)
public class ShuqiCrawlerSiteParser implements CrawlerSiteParser {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final Pattern BOOK_ID_PATTERN = Pattern.compile("/book/(\\d+)\\.html|[?&]bid=(\\d+)");
    private static final int MAX_FREE_CHAPTERS_PER_BOOK = 80;
    private static final int CONTENT_TIMEOUT_MILLIS = 45000;

    private final ObjectMapper objectMapper;

    public ShuqiCrawlerSiteParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(CrawlerSourceConfig source, String rankUrl) {
        String value = ((source == null || source.sourceCode == null ? "" : source.sourceCode)
                + " " + (source == null || source.baseUrl == null ? "" : source.baseUrl)
                + " " + rankUrl).toLowerCase();
        return value.contains("shuqi");
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks) {
        Set<String> urls = new LinkedHashSet<>();
        String directBookId = extractBookId(rankUrl);
        if (StringUtils.hasText(directBookId)) {
            urls.add(bookUrl(directBookId));
        }
        for (Element link : document.select("a[href*='/book/'][href$='.html'], a[href*='/book/']")) {
            String bookId = extractBookId(link.absUrl("href"));
            if (StringUtils.hasText(bookId)) {
                urls.add(bookUrl(bookId));
            }
            if (urls.size() >= maxBooks) {
                break;
            }
        }

        List<ParsedBookSeed> seeds = new ArrayList<>();
        for (String url : urls) {
            seeds.add(new ParsedBookSeed(url, "", "", "", 0L, "", rankUrl));
        }
        return seeds;
    }

    @Override
    public ParsedBookSnapshot fetchBook(CrawlerSourceConfig source, ParsedBookSeed seed, DocumentFetcher fetcher)
            throws Exception {
        String bookId = extractBookId(seed.url());
        if (!StringUtils.hasText(bookId)) {
            throw new IllegalArgumentException("Shuqi book id not found: " + seed.url());
        }

        Document detail = fetcher.fetch(bookUrl(bookId));
        String firstChapterId = firstChapterId(fetcher.fetch(catalogUrl(bookId)));
        String readerUrl = readerUrl(bookId, firstChapterId);
        Document reader = fetcher.fetch(readerUrl);
        JsonNode pageData = extractReaderPageData(reader);
        if (pageData.isMissingNode() || pageData.isNull()) {
            throw new IllegalStateException("Shuqi reader page data not found");
        }

        String title = firstNonBlank(text(pageData, "bookName"), firstText(detail, "h1", ".book-title", "title"));
        String author = firstNonBlank(text(pageData, "authorName"), firstText(detail, ".author", ".book-author"));
        String intro = firstNonBlank(text(pageData, "intro"), text(pageData, "desc"),
                firstText(detail, ".intro", ".book-intro", "meta[name=description]"));
        String cover = normalizeImageUrl(firstNonBlank(text(pageData, "imgUrl"), text(pageData, "cover"),
                text(pageData, "bookCover"), firstImage(detail)));
        long wordCount = number(pageData, "wordCount");

        List<ParsedChapterSnapshot> chapters = freeChapters(bookId, readerUrl, pageData);
        String chapterId = chapters.isEmpty() ? firstChapterId : chapters.get(0).chapterId();
        String chapterUrl = chapters.isEmpty() ? readerUrl : chapters.get(0).url();
        return new ParsedBookSnapshot(title, cleanAuthor(author), cover, intro, bookUrl(bookId), bookId, wordCount,
                chapterId, chapterUrl, chapters);
    }

    @Override
    public ParsedBookSnapshot fetchBook(ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        return fetchBook(null, seed, fetcher);
    }

    private List<ParsedChapterSnapshot> freeChapters(String bookId, String readerUrl, JsonNode pageData)
            throws IOException {
        String freePrefix = text(pageData, "freeContUrlPrefix");
        List<JsonNode> chapterNodes = new ArrayList<>();
        collectChapterNodes(pageData.path("chapterList"), chapterNodes);

        List<ParsedChapterSnapshot> chapters = new ArrayList<>();
        int chapterNo = 0;
        for (JsonNode chapter : chapterNodes) {
            chapterNo++;
            if (!"0".equals(text(chapter, "payStatus"))) {
                continue;
            }
            String chapterId = firstNonBlank(text(chapter, "chapterId"), text(chapter, "id"));
            String suffix = text(chapter, "contUrlSuffix");
            String contentUrl = concatUrl(freePrefix, suffix);
            if (!StringUtils.hasText(chapterId) || !StringUtils.hasText(contentUrl)) {
                continue;
            }
            String content = fetchFreeContent(contentUrl, readerUrl);
            if (!StringUtils.hasText(content)) {
                continue;
            }
            chapters.add(new ParsedChapterSnapshot(
                    chapterId,
                    firstNonBlank(text(chapter, "chapterName"), text(chapter, "title"), "第" + chapterNo + "章"),
                    readerUrl(bookId, chapterId),
                    chapterNo,
                    false,
                    content,
                    (int) number(chapter, "wordCount")));
            if (chapters.size() >= MAX_FREE_CHAPTERS_PER_BOOK) {
                break;
            }
        }
        return chapters;
    }

    private String fetchFreeContent(String url, String referer) throws IOException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String body = Jsoup.connect(url)
                        .ignoreContentType(true)
                        .userAgent(USER_AGENT)
                        .header("Accept", "application/json,text/plain,*/*")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("Referer", referer)
                        .timeout(CONTENT_TIMEOUT_MILLIS)
                        .execute()
                        .body();
                JsonNode response = objectMapper.readTree(body);
                if (!"200".equals(text(response, "state"))) {
                    return "";
                }
                return decodeChapterContent(text(response, "ChapterContent"));
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

    private JsonNode extractReaderPageData(Document document) {
        for (Element element : document.select("i.page-data")) {
            String json = Parser.unescapeEntities(element.html(), false).trim();
            if (!StringUtils.hasText(json)) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(json);
                if (node.has("chapterList") && StringUtils.hasText(text(node, "freeContUrlPrefix"))) {
                    return node;
                }
            } catch (Exception ignored) {
                // Ignore non-reader page-data blocks.
            }
        }
        return MissingNode.getInstance();
    }

    private void collectChapterNodes(JsonNode node, List<JsonNode> chapters) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject() && (node.has("chapterId") || node.has("id"))
                && (node.has("chapterName") || node.has("title"))) {
            chapters.add(node);
            return;
        }
        if (node.isObject() || node.isArray()) {
            for (JsonNode child : node) {
                collectChapterNodes(child, chapters);
            }
        }
    }

    static String decodeChapterContent(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return "";
        }
        String shifted = shiftLetters(encoded);
        String normalized = shifted.replaceAll("\\s+", "");
        int padding = normalized.length() % 4;
        if (padding > 0) {
            normalized = normalized + "=".repeat(4 - padding);
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
            return cleanDecodedContent(decoded);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static String shiftLetters(String value) {
        StringBuilder shifted = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                int lower = Character.toLowerCase(ch);
                int code = (lower - 83) % 26;
                if (code == 0) {
                    code = 26;
                }
                shifted.append((char) (code + (Character.isUpperCase(ch) ? 64 : 96)));
            } else {
                shifted.append(ch);
            }
        }
        return shifted.toString();
    }

    private static String cleanDecodedContent(String decoded) {
        if (!StringUtils.hasText(decoded)) {
            return "";
        }
        String html = decoded.replaceAll("(?i)<br\\s*/?>", "\n");
        Document document = Jsoup.parse(html);
        List<String> paragraphs = document.select("p").eachText().stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        String text = paragraphs.isEmpty() ? document.text() : String.join("\n", paragraphs);
        return text.replace('\u00A0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String firstChapterId(Document catalog) {
        for (Element link : catalog.select("a[href*='/reader']")) {
            String chapterId = queryValue(link.absUrl("href"), "cid");
            if (StringUtils.hasText(chapterId)) {
                return chapterId;
            }
        }
        return "";
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
                .orElse("https://dummyimage.com/300x420/20232a/ffffff&text=Shuqi");
    }

    private String cleanAuthor(String author) {
        if (!StringUtils.hasText(author)) {
            return "";
        }
        return author.replace("作者：", "").replace("作者:", "").trim();
    }

    private String extractBookId(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        Matcher matcher = BOOK_ID_PATTERN.matcher(url);
        if (!matcher.find()) {
            return "";
        }
        return StringUtils.hasText(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
    }

    private String queryValue(String url, String name) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        Matcher matcher = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&#]+)").matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String bookUrl(String bookId) {
        return "https://www.shuqi.com/book/" + bookId + ".html";
    }

    private String catalogUrl(String bookId) {
        return "https://www.shuqi.com/chapter?bid=" + bookId;
    }

    private String readerUrl(String bookId, String chapterId) {
        return StringUtils.hasText(chapterId)
                ? "https://www.shuqi.com/reader?bid=" + bookId + "&cid=" + chapterId
                : "https://www.shuqi.com/reader?bid=" + bookId;
    }

    private String concatUrl(String prefix, String suffix) {
        String value = firstNonBlank(prefix) + Parser.unescapeEntities(firstNonBlank(suffix), false);
        return value.startsWith("//") ? "https:" + value : value;
    }

    private String normalizeImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        return url.startsWith("//") ? "https:" + url : url;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private long number(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        String text = value.asText("").replaceAll("[^0-9]", "");
        return StringUtils.hasText(text) ? Long.parseLong(text) : 0L;
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
}
