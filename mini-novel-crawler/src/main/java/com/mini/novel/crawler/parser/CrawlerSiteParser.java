package com.mini.novel.crawler.parser;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.List;
import org.jsoup.nodes.Document;

public interface CrawlerSiteParser {
    boolean supports(CrawlerSourceConfig source, String rankUrl);

    List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks);

    ParsedBookSnapshot fetchBook(ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception;

    default List<ParsedBookSeed> parseBookSeeds(CrawlerSourceConfig source, Document document, String rankUrl, int maxBooks) {
        return parseBookSeeds(document, rankUrl, maxBooks);
    }

    default ParsedBookSnapshot fetchBook(CrawlerSourceConfig source, ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception {
        return fetchBook(seed, fetcher);
    }
}
