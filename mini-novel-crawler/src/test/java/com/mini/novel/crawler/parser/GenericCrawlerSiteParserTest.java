package com.mini.novel.crawler.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class GenericCrawlerSiteParserTest {
    private final GenericCrawlerSiteParser parser = new GenericCrawlerSiteParser();

    @Test
    void parsesRuleDrivenRankAndFullCatalog() throws Exception {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.ruleConfigJson = """
                {
                  "rankRules": {
                    "bookList": ".rank-item",
                    "bookUrl": "a.book@href",
                    "bookName": ".name",
                    "author": ".author",
                    "wordCount": ".words"
                  },
                  "bookRules": {
                    "name": "h1",
                    "author": ".book-author",
                    "intro": ".intro",
                    "cover": ".cover@src",
                    "wordCount": ".word-count",
                    "catalogUrl": ".catalog-link@href"
                  },
                  "catalogRules": {
                    "chapterList": ".chapter",
                    "chapterUrl": "a@href",
                    "chapterTitle": "a",
                    "maxChapters": 50
                  }
                }
                """;

        Document rank = Jsoup.parse("""
                <div class="rank-item">
                  <a class="book" href="/book/100.html"><span class="name">星河旧书</span></a>
                  <span class="author">云上人</span>
                  <span class="words">123.4万字</span>
                </div>
                """, "https://example.com/rank/");
        List<ParsedBookSeed> seeds = parser.parseBookSeeds(source, rank, "https://example.com/rank/", 10);
        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).url()).isEqualTo("https://example.com/book/100.html");
        assertThat(seeds.get(0).title()).isEqualTo("星河旧书");
        assertThat(seeds.get(0).wordCount()).isEqualTo(1234000L);

        Map<String, Document> pages = new HashMap<>();
        pages.put("https://example.com/book/100.html", Jsoup.parse("""
                <h1>星河旧书</h1>
                <span class="book-author">作者：云上人</span>
                <img class="cover" src="/cover.jpg">
                <p class="intro">一本完整测试书。</p>
                <span class="word-count">123.4万字</span>
                <a class="catalog-link" href="/book/100/catalog.html">目录</a>
                """, "https://example.com/book/100.html"));
        pages.put("https://example.com/book/100/catalog.html", Jsoup.parse(catalogHtml(30),
                "https://example.com/book/100/catalog.html"));

        ParsedBookSnapshot snapshot = parser.fetchBook(source, seeds.get(0), pages::get);
        assertThat(snapshot.title()).isEqualTo("星河旧书");
        assertThat(snapshot.author()).isEqualTo("云上人");
        assertThat(snapshot.wordCount()).isEqualTo(1234000L);
        assertThat(snapshot.chapters()).hasSize(30);
        assertThat(snapshot.chapters().get(0).title()).isEqualTo("第1章 星光");
        assertThat(snapshot.chapters().get(29).url()).isEqualTo("https://example.com/book/100/chapter/30.html");
    }

    @Test
    void supportsSoNovelStyleAliasesAndReverseCatalog() throws Exception {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.ruleConfigJson = """
                {
                  "search": {
                    "list": ".book-card",
                    "detailUrl": "a@href",
                    "name": ".title"
                  },
                  "book": {
                    "name": "h1",
                    "catalogUrl": ".toc@href"
                  },
                  "toc": {
                    "chapterList": ".item",
                    "chapterUrl": "a@href",
                    "chapterTitle": "a",
                    "reverse": true,
                    "maxChapters": 10
                  }
                }
                """;

        Document rank = Jsoup.parse("""
                <div class="book-card"><a href="/novel/1.html"><b class="title">倒序书</b></a></div>
                """, "https://source.test/");
        ParsedBookSeed seed = parser.parseBookSeeds(source, rank, "https://source.test/", 5).get(0);

        Map<String, Document> pages = Map.of(
                "https://source.test/novel/1.html", Jsoup.parse("""
                        <h1>倒序书</h1><a class="toc" href="/novel/1/toc.html">目录</a>
                        """, "https://source.test/novel/1.html"),
                "https://source.test/novel/1/toc.html", Jsoup.parse("""
                        <div class="item"><a href="/novel/1/3.html">第3章 终点</a></div>
                        <div class="item"><a href="/novel/1/2.html">第2章 中段</a></div>
                        <div class="item"><a href="/novel/1/1.html">第1章 开始</a></div>
                        """, "https://source.test/novel/1/toc.html")
        );

        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, pages::get);
        assertThat(snapshot.chapters()).extracting(ParsedChapterSnapshot::title)
                .containsExactly("第1章 开始", "第2章 中段", "第3章 终点");
        assertThat(snapshot.chapters()).extracting(ParsedChapterSnapshot::chapterNo)
                .containsExactly(1, 2, 3);
    }

    private String catalogHtml(int count) {
        StringBuilder html = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            html.append("<div class=\"chapter\"><a href=\"/book/100/chapter/")
                    .append(i)
                    .append(".html\">第")
                    .append(i)
                    .append("章 星光</a></div>");
        }
        return html.toString();
    }
}
