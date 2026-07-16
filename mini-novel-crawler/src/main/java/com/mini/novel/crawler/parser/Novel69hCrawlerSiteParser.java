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
@Order(32)
public class Novel69hCrawlerSiteParser implements CrawlerSiteParser {
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "/erotic-novel/([a-z_]+)/article-(\\d+)\\.html(?:$|[?#])", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean supports(CrawlerSourceConfig source, String rankUrl) {
        String value = ((source == null ? "" : source.sourceCode + " " + source.baseUrl) + " " + rankUrl).toLowerCase();
        return value.contains("novel69h_authorized") || value.contains("69hnovel.com");
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
            return List.of(new ParsedBookSeed(normalizeArticleUrl(abs(document, singleBookUrl)), "", "", "", 0L, "", rankUrl));
        }

        List<ParsedBookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String selector = firstNonBlank(rules.text("rankRules.bookList"),
                ".L-main-col a[href*='/erotic-novel/'][href*='article-'], "
                        + "main a[href*='/erotic-novel/'][href*='article-'], "
                        + "a[href*='/erotic-novel/'][href*='article-']");
        for (Element link : document.select(selector)) {
            String href = normalizeArticleUrl(link.absUrl("href"));
            if (!isArticleUrl(href) || !seen.add(href)) {
                continue;
            }
            seeds.add(new ParsedBookSeed(href, simplified(cleanTitle(link.text())), "", articleId(href), 0L, "", rankUrl));
            if (seeds.size() >= maxBooks) {
                break;
            }
        }
        return seeds;
    }

    @Override
    public String nextRankPage(CrawlerSourceConfig source, Document document, String rankUrl) {
        for (Element link : document.select(".M-page a[href], .pagination a[href], a[href]")) {
            String text = clean(link.text()).toLowerCase();
            if (text.contains("next") || text.contains("\u4e0b\u4e00\u9875")
                    || text.contains("\u4e0b\u9875") || text.contains("\u4e0b\u4e00\u9801")) {
                String href = normalize(link.absUrl("href"));
                if (isRankPageUrl(href) && !href.equals(rankUrl)) {
                    return href;
                }
            }
        }
        Matcher matcher = Pattern.compile("erotic-novel-(\\d+)\\.html").matcher(rankUrl == null ? "" : rankUrl);
        if (matcher.find()) {
            int next = Integer.parseInt(matcher.group(1)) + 1;
            return "https://www.69hnovel.com/erotic-novel-" + String.format("%02d", next) + ".html";
        }
        return "https://www.69hnovel.com/erotic-novel-02.html";
    }

    @Override
    public ParsedBookSnapshot fetchBook(ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        return fetchBook(null, seed, fetcher);
    }

    @Override
    public ParsedBookSnapshot fetchBook(CrawlerSourceConfig source, ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        Document detail = fetcher.fetch(seed.url());
        String url = normalizeArticleUrl(firstNonBlank(detail.location(), seed.url()));
        String articleId = firstNonBlank(articleId(url), articleId(seed.url()), seed.intro(), Integer.toHexString(url.hashCode()));
        String title = simplified(cleanTitle(firstNonBlank(
                firstText(detail, "article h1", ".L-main-col h1", "h1"),
                titleWithoutSite(firstText(detail, "title")),
                seed.title(),
                "69hnovel-" + articleId)));
        String category = simplified(firstNonBlank(firstCategory(detail, url), ""));
        String content = simplified(extractArticleText(detail, CrawlerRuleConfig.from(source)));
        ParsedChapterSnapshot chapter = new ParsedChapterSnapshot(articleId, title, url, 1, true, content, content.length());

        return new ParsedBookSnapshot(
                title,
                "Unknown",
                firstImage(detail),
                simplified(firstDescription(detail)),
                url,
                articleId,
                content.length(),
                category,
                "PENDING_REVIEW",
                articleId,
                url,
                List.of(chapter),
                tagsJson(category));
    }

    private boolean isArticleUrl(String href) {
        return StringUtils.hasText(href)
                && href.startsWith("https://www.69hnovel.com/erotic-novel/")
                && ARTICLE_PATTERN.matcher(href).find();
    }

    private boolean isRankPageUrl(String href) {
        return StringUtils.hasText(href)
                && (href.equals("https://www.69hnovel.com/erotic-novel.html")
                || href.matches("https://www\\.69hnovel\\.com/erotic-novel-\\d+\\.html(?:[?#].*)?"));
    }

    private String articleId(String url) {
        Matcher matcher = ARTICLE_PATTERN.matcher(normalize(url));
        return matcher.find() ? matcher.group(2) : "";
    }

    private String normalizeArticleUrl(String value) {
        String url = normalize(value).replace("http://www.69hnovel.com/", "https://www.69hnovel.com/");
        Matcher matcher = ARTICLE_PATTERN.matcher(url);
        return matcher.find()
                ? "https://www.69hnovel.com/erotic-novel/" + matcher.group(1) + "/article-" + matcher.group(2) + ".html"
                : url;
    }

    private String firstCategory(Document document, String articleUrl) {
        Element article = document.selectFirst("article, .L-main-col");
        if (article != null) {
            for (Element link : article.select("a[href*='/erotic-novel/'][href$='.html']")) {
                String href = normalize(link.absUrl("href"));
                if (isRankCategoryUrl(href) && sameCategoryPath(articleUrl, href)) {
                    String text = clean(link.text()).replaceAll("\\s+\\d+$", "").trim();
                    if (StringUtils.hasText(text) && !text.contains("\u6807\u7b7e\u4e91")) {
                        return text;
                    }
                }
            }
        }
        Matcher matcher = ARTICLE_PATTERN.matcher(articleUrl == null ? "" : articleUrl);
        return matcher.find() ? categoryName(matcher.group(1)) : "";
    }

    private boolean isRankCategoryUrl(String href) {
        return href.matches("https://www\\.69hnovel\\.com/erotic-novel/[a-z_]+\\.html(?:[?#].*)?");
    }

    private boolean sameCategoryPath(String articleUrl, String categoryUrl) {
        Matcher matcher = ARTICLE_PATTERN.matcher(articleUrl == null ? "" : articleUrl);
        return matcher.find() && categoryUrl.contains("/erotic-novel/" + matcher.group(1) + ".html");
    }

    private String categoryName(String slug) {
        return switch (slug) {
            case "collection" -> "\u5408\u96c6\u7cfb\u5217";
            case "wife" -> "\u4eba\u59bb\u719f\u5973";
            case "home" -> "\u5bb6\u5ead\u4e71\u4f26";
            case "rape" -> "\u5f3a\u66b4\u8650\u5f85";
            case "school" -> "\u6821\u56ed\u5e08\u751f\u540c\u5b66";
            case "city" -> "\u90fd\u5e02\u6deb\u4e71\u751f\u6d3b";
            case "anime" -> "\u52a8\u6f2b\u6539\u7f16";
            case "cosplay" -> "\u89d2\u8272\u626e\u6f14";
            case "celebrities" -> "\u540d\u4eba\u660e\u661f";
            case "martial_arts" -> "\u6b66\u4fa0\u79d1\u5e7b";
            case "story" -> "\u6027\u7ecf\u9a8c";
            case "special" -> "\u53e6\u7c7b\u5176\u5b83";
            case "knowledge" -> "\u6027\u77e5\u8bc6";
            case "long" -> "\u957f\u7bc7\u5c0f\u8bf4";
            case "short" -> "\u77ed\u7bc7\u5c0f\u8bf4";
            default -> "";
        };
    }

    private String extractArticleText(Document document, CrawlerRuleConfig rules) {
        Document copy = document.clone();
        for (String selector : rules.list("chapterRules.removeSelectors", "chapter.removeSelectors")) {
            copy.select(selector).remove();
        }
        copy.select("script, style, iframe, noscript, .M-banner, .M-aside-nav, .L-main-col.aside, "
                + ".table-box, .iframebox, .iframe-outbox, .M-card-title, .article-page, .pagination, "
                + ".prev-next, .breadcrumb, .adsbygoogle, a[rel=nofollow]").remove();
        String contentRule = rules.text("chapterRules.content", "chapter.content", "content.selector");
        Element article = StringUtils.hasText(contentRule) ? firstElement(copy, contentRule.split("\\|\\|")) : null;
        if (article == null) {
            article = copy.selectFirst("article");
        }
        if (article == null) {
            return "";
        }
        article.select("h1, h2, h3, a[href*='wendun5.com'], a[href*='opopapp.com']").remove();
        return clean(article.wholeText()).replaceAll("\\n{3,}", "\n\n").trim();
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

    private String firstDescription(Document document) {
        return clean(firstText(document, "meta[name=description]", "meta[property=og:description]"));
    }

    private String firstImage(Document document) {
        Element image = document.selectFirst("article img[src], .L-main-col img[src], meta[property=og:image]");
        if (image == null) {
            return "";
        }
        return image.hasAttr("content") ? normalize(image.attr("content")) : normalize(image.absUrl("src"));
    }

    private String tagsJson(String category) {
        String tag = simplified(category);
        if (!StringUtils.hasText(tag)) {
            return "[]";
        }
        return "[\"" + tag.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
    }

    private String titleWithoutSite(String value) {
        return clean(value)
                .replaceAll("\\s*_?69\\u6587\\u5b66\\u7f51.*$", "")
                .replaceAll("\\s*_?69\\u6587\\u5b78\\u7db2.*$", "")
                .trim();
    }

    private String cleanTitle(String value) {
        return titleWithoutSite(value).replaceAll("\\s+", " ").trim();
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
