package com.mini.novel.crawler.parser;

import java.io.IOException;
import org.jsoup.nodes.Document;

@FunctionalInterface
public interface DocumentFetcher {
    Document fetch(String url) throws IOException;
}
