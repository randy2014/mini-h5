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
        if (StringUtils.hasText(singleBookUrl)) {
            return List.of(new ParsedBookSeed(abs(document, singleBookUrl), "", "", "", 0L, "", rankUrl));
        }

        List<ParsedBookSeed> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String selector = firstNonBlank(rules.text("rankRules.bookList"), ".post h2 a[href], .entry h2 a[href], a[href*='/post/']");
        for (Element link : document.select(selector)) {
            String href = normalize(link.absUrl("href"));
            if (!isPostUrl(href) || !seen.add(href)) {
                continue;
            }
            seeds.add(new ParsedBookSeed(href, cleanTitle(link.text()), "", "", 0L, "", rankUrl));
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
        Document detail = fetcher.fetch(seed.url());
        String title = cleanTitle(firstNonBlank(
                firstText(detail, ".post h2", ".narrowcolumn .post h2", "h2"),
                titleWithoutSite(firstText(detail, "title")),
                seed.title()));
        String sourceBookId = firstNonBlank(postId(seed.url()), postId(detail.location()), Integer.toHexString(seed.url().hashCode()));
        String url = normalize(firstNonBlank(detail.location(), seed.url()));
        String category = firstNonBlank(firstCategory(detail), "AUTHORIZED_VIP");
        String tagsJson = tagsJson(detail);
        long wordCount = estimateEntryText(detail).length();

        ParsedChapterSnapshot chapter = new ParsedChapterSnapshot(
                sourceBookId,
                StringUtils.hasText(title) ? title : "第1章",
                url,
                1,
                true);
        return new ParsedBookSnapshot(
                StringUtils.hasText(title) ? title : "h528-" + sourceBookId,
                firstNonBlank(firstAuthor(detail), "佚名"),
                firstImage(detail),
                "",
                url,
                sourceBookId,
                wordCount,
                category,
                "PENDING_REVIEW",
                chapter.chapterId(),
                chapter.url(),
                List.of(chapter),
                tagsJson);
    }

    private boolean isPostUrl(String href) {
        return StringUtils.hasText(href)
                && href.startsWith("http://www.h528.com/post/")
                && POST_ID_PATTERN.matcher(href).find();
    }

    private String postId(String url) {
        Matcher matcher = POST_ID_PATTERN.matcher(normalize(url));
        return matcher.find() ? matcher.group(1) : "";
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
        for (Element link : document.select("a[href*='/post/category/']")) {
            String text = clean(link.text());
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String firstAuthor(Document document) {
        String author = firstText(document, ".postmetadata a[rel=author]", ".author", "a[rel=author]");
        return clean(author);
    }

    private String firstImage(Document document) {
        Element image = document.selectFirst(".entry img[src], .post img[src]");
        return image == null ? "" : normalize(image.absUrl("src"));
    }

    private String tagsJson(Document document) {
        List<String> tags = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Element link : document.select("a[href*='/post/category/']")) {
            String tag = clean(link.text());
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

    private String estimateEntryText(Document document) {
        Document copy = document.clone();
        copy.select("script, style, iframe, .navigation, .sidebar, .postmetadata, .alignleft, .alignright").remove();
        Element entry = copy.selectFirst(".post .entry, .entry");
        return entry == null ? "" : clean(entry.text());
    }

    private String titleWithoutSite(String value) {
        return clean(value)
                .replaceAll("\\s*-\\s*風月文學網.*$", "")
                .replaceAll("\\s*-\\s*成人小說.*$", "")
                .trim();
    }

    private String cleanTitle(String value) {
        return titleWithoutSite(value)
                .replaceAll("\\s+", " ")
                .trim();
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
