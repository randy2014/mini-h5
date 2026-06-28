package com.mini.novel.crawler.parser;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.List;
import org.jsoup.nodes.Document;

public interface CrawlerSiteParser {
    boolean supports(CrawlerSourceConfig source, String rankUrl);

    List<ParsedBookSeed> parseBookSeeds(Document document, String rankUrl, int maxBooks);

    ParsedBookSnapshot fetchBook(ParsedBookSeed seed, DocumentFetcher fetcher) throws Exception;
}
