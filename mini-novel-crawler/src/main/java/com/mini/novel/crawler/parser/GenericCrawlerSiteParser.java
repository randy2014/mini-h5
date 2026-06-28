package com.mini.novel.crawler.parser;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(1000)
public class GenericCrawlerSiteParser implements CrawlerSiteParser {
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
        String chapterUrl = firstChapterUrl(detail);
        String sourceBookId = Integer.toHexString(seed.url().hashCode());
        return new ParsedBookSnapshot(title, cleanAuthor(author), cover, intro, seed.url(), sourceBookId, 0L,
                chapterUrl.isEmpty() ? "" : Integer.toHexString(chapterUrl.hashCode()), chapterUrl);
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
