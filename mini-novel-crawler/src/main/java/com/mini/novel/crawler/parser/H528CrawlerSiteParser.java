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
@Order(31)
public class H528CrawlerSiteParser implements CrawlerSiteParser {
    private static final Pattern POST_ID_PATTERN = Pattern.compile("/post/(\\d+)\\.html(?:$|[?#])", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(CrawlerSourceConfig source, String rankUrl) {
        String value = ((source == null ? "" : source.sourceCode + " " + source.baseUrl) + " " + rankUrl).toLowerCase();
        return value.contains("h528_authorized") || value.contains("h528.com");
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks) {
        return parseBookSeeds(null, document, rankUrl, maxBooks);
    }

    @Override
    public List<ParsedBookSeed> parseBookSeeds(CrawlerSourceConfig source, Document document, String rankUrl, int maxBooks) {
        CrawlerRuleConfig rules = CrawlerRuleConfig.from(source);
        String singleBookUrl = rules.text("poc.bookUrl", "bookUrl");
        if (StringUtils.hasText(singleBookUrl) && !"BATCH".equalsIgnoreCase(rules.text("poc.mode", "mode"))) {
            return List.of(new ParsedBookSeed(normalizePostUrl(abs(document, singleBookUrl)), "", "", "", 0L, "", rankUrl));
        }

        List<ParsedBookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String selector = firstNonBlank(rules.text("rankRules.bookList"),
                ".post h2 a[href], h3 a[rel=bookmark][href], .post .entry-title a[href], .entry h2 a[href], h2 a[href], a[href*='/post/']");
        List<Element> links = new ArrayList<>(document.select(selector));
        if (links.isEmpty()) {
            links.addAll(document.select("h3 a[rel=bookmark][href], a[href*='/post/']"));
        }
        for (Element link : links) {
            String href = normalizePostUrl(link.absUrl("href"));
            if (!isPostUrl(href) || !seen.add(href)) {
                continue;
            }
            seeds.add(new ParsedBookSeed(href, simplified(cleanTitle(link.text())), "", postId(href), 0L, "", rankUrl));
            if (seeds.size() >= maxBooks) {
                break;
            }
        }
        return seeds;
    }

    @Override
    public String nextRankPage(CrawlerSourceConfig source, Document document, String rankUrl) {
        for (Element link : document.select(".navigation a[href], .wp-pagenavi a[href], a.nextpostslink[href], a[href]")) {
            String text = clean(link.text()).toLowerCase();
            if (link.hasClass("nextpostslink") || text.contains("next") || text.contains("\u4e0b\u4e00\u9875")
                    || text.contains("\u4e0b\u9875") || text.contains("\u4e0b\u4e00\u9801")) {
                String href = normalize(link.absUrl("href"));
                if (isRankPageUrl(href) && !href.equals(rankUrl)) {
                    return href;
                }
            }
        }
        return pageNumber(rankUrl) <= 1 ? "http://www.h528.com/page/2/" : "";
    }

    @Override
    public ParsedBookSnapshot fetchBook(ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        return fetchBook(null, seed, fetcher);
    }

    @Override
    public ParsedBookSnapshot fetchBook(CrawlerSourceConfig source, ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        Document detail = fetcher.fetch(seed.url());
        String url = normalizePostUrl(firstNonBlank(detail.location(), seed.url()));
        String postId = firstNonBlank(postId(url), postId(seed.url()), seed.intro(), Integer.toHexString(url.hashCode()));
        String title = simplified(cleanTitle(firstNonBlank(
                firstText(detail, ".post h2", ".narrowcolumn .post h2", "h2", "h1"),
                titleWithoutSite(firstText(detail, "title")),
                seed.title(),
                "h528-" + postId)));
        String author = simplified(firstNonBlank(firstAuthor(detail), "Unknown"));
        String category = simplified(firstCategory(detail));
        String content = simplified(extractEntryText(detail, CrawlerRuleConfig.from(source)));
        long wordCount = content.length();

        ParsedChapterSnapshot chapter = new ParsedChapterSnapshot(
                postId,
                title,
                url,
                1,
                true,
                content,
                content.length());
        return new ParsedBookSnapshot(
                title,
                author,
                firstImage(detail),
                simplified(firstNonBlank(firstExcerpt(detail), "")),
                url,
                postId,
                wordCount,
                category,
                "PENDING_REVIEW",
                postId,
                url,
                List.of(chapter),
                tagsJson(detail));
    }

    private boolean isPostUrl(String href) {
        return StringUtils.hasText(href)
                && href.startsWith("http://www.h528.com/post/")
                && POST_ID_PATTERN.matcher(href).find();
    }

    private boolean isRankPageUrl(String href) {
        return StringUtils.hasText(href)
                && (href.equals("http://www.h528.com/") || href.matches("http://www\\.h528\\.com/page/\\d+/?(?:[?#].*)?"));
    }

    private int pageNumber(String url) {
        Matcher matcher = Pattern.compile("/page/(\\d+)/?").matcher(url == null ? "" : url);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    private String postId(String url) {
        Matcher matcher = POST_ID_PATTERN.matcher(normalize(url));
        return matcher.find() ? matcher.group(1) : "";
    }

    private String normalizePostUrl(String value) {
        String url = normalize(value).replace("https://www.h528.com/", "http://www.h528.com/");
        Matcher matcher = POST_ID_PATTERN.matcher(url);
        return matcher.find() ? "http://www.h528.com/post/" + matcher.group(1) + ".html" : url;
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

    private String firstCategory(Document document) {
        for (Element link : categoryLinks(document)) {
            String text = clean(link.text());
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String firstAuthor(Document document) {
        return clean(firstText(document, ".postmetadata a[rel=author]", ".author", "a[rel=author]"));
    }

    private String firstExcerpt(Document document) {
        return clean(firstText(document, ".post .excerpt", ".entry .excerpt", "meta[name=description]"));
    }

    private String firstImage(Document document) {
        Element image = document.selectFirst(".entry img[src], .post img[src]");
        return image == null ? "" : normalize(image.absUrl("src"));
    }

    private String tagsJson(Document document) {
        List<String> tags = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element link : categoryLinks(document)) {
            String tag = simplified(clean(link.text()));
            if (StringUtils.hasText(tag) && seen.add(tag)) {
                tags.add(tag);
            }
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

    private List<Element> categoryLinks(Document document) {
        Element container = document.selectFirst(".post .postmetadata, article .postmetadata, .narrowcolumn .post .postmetadata");
        if (container == null) {
            return List.of();
        }
        return container.select("a[href*='/post/category/'], a[rel=category tag]");
    }

    private String extractEntryText(Document document, CrawlerRuleConfig rules) {
        Document copy = document.clone();
        for (String selector : rules.list("chapterRules.removeSelectors", "chapter.removeSelectors")) {
            copy.select(selector).remove();
        }
        copy.select("script, style, iframe, .navigation, .sidebar, .postmetadata, .alignleft, .alignright").remove();
        String contentRule = rules.text("chapterRules.content", "chapter.content", "content.selector");
        Element entry = StringUtils.hasText(contentRule) ? firstElement(copy, contentRule.split("\\|\\|")) : null;
        if (entry == null) {
            entry = copy.selectFirst(".post .entry, .entry, article");
        }
        if (entry == null) {
            return "";
        }
        return clean(entry.wholeText()).replaceAll("\\n{3,}", "\n\n").trim();
    }

    private Element firstElement(Document document, String[] selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector.trim());
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private String titleWithoutSite(String value) {
        return clean(value)
                .replaceAll("(?i)\\s*-\\s*h528.*$", "")
                .replaceAll("\\s*-\\s*\\u98a8\\u6708\\u6587\\u5b78\\u7db2.*$", "")
                .replaceAll("\\s*-\\s*\\u6210\\u4eba\\u5c0f\\u8aaa.*$", "")
                .trim();
    }

    private String cleanTitle(String value) {
        return titleWithoutSite(value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String simplified(String value) {
        return ChineseTextConverter.toSimplified(clean(value));
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
        String withoutHash = hash >= 0 ? value.substring(0, hash) : value;
        return withoutHash.replaceAll("/+$", "/");
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
