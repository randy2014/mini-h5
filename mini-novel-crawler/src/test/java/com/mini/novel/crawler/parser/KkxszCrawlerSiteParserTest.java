package com.mini.novel.crawler.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class KkxszCrawlerSiteParserTest {
    private final KkxszCrawlerSiteParser parser = new KkxszCrawlerSiteParser();

    @Test
    void discoversBooksFromCategoryPageAndNextPage() {
        CrawlerSourceConfig source = source();
        var document = Jsoup.parse("""
                <html><body>
                <div class="bookList">
                  <a href="/book/gaedib.html">我不是精灵王</a>
                  <a href="/book/gaedib.html#dup">重复</a>
                  <a href="/book/dgj0i.html">护国狱神</a>
                </div>
                <div class="page"><a class="next" href="/list-4-2/">下一页</a></div>
                </body></html>
                """, "https://www.kkxsz.com/list-4/");

        var seeds = parser.parseBookSeeds(source, document, "https://www.kkxsz.com/list-4/", 20);

        assertThat(seeds).hasSize(2);
        assertThat(seeds).extracting(ParsedBookSeed::url)
                .containsExactly("https://www.kkxsz.com/book/gaedib.html",
                        "https://www.kkxsz.com/book/dgj0i.html");
        assertThat(parser.nextRankPage(source, document, "https://www.kkxsz.com/list-4/"))
                .isEqualTo("https://www.kkxsz.com/list-4-2/");
    }

    @Test
    void buildsCompleteCatalogFromLatestChapterNumber() throws Exception {
        CrawlerSourceConfig source = source();
        var detail = Jsoup.parse("""
                <html><head>
                  <meta property="og:novel:book_name" content="我不是精灵王" />
                  <meta property="og:novel:author" content="彦飞雪" />
                  <meta property="og:description" content="简介" />
                  <meta property="og:image" content="http://www.kkxsz.com/bookimg/236958.jpg" />
                  <meta property="og:novel:category" content="原生幻想" />
                  <meta property="og:novel:status" content="全本" />
                  <meta property="og:novel:latest_chapter_url" content="http://www.kkxsz.com/book/gaedib-6.html" />
                </head><body>
                  <div class="txtb"><li>字数：10.5万</li></div>
                  <div class="chapterList">
                    <a href="/book/gaedib-6.html">第6章 最新章</a>
                    <a href="/book/gaedib-1.html">第1章 开始</a>
                    <a href="/book/gaedib-2.html">第2章 继续</a>
                  </div>
                </body></html>
                """, "https://www.kkxsz.com/book/gaedib.html");

        ParsedBookSnapshot snapshot = parser.fetchBook(source,
                new ParsedBookSeed("https://www.kkxsz.com/book/gaedib.html", "", "", "", 0L, "", ""),
                url -> detail);

        assertThat(snapshot.sourceBookId()).isEqualTo("gaedib");
        assertThat(snapshot.categoryName()).isEqualTo("玄幻魔法");
        assertThat(snapshot.bookStatus()).isEqualTo("COMPLETED");
        assertThat(snapshot.wordCount()).isEqualTo(105000L);
        assertThat(snapshot.chapters()).hasSize(6);
        assertThat(snapshot.chapters()).extracting(ParsedChapterSnapshot::chapterId)
                .containsExactly("gaedib-1", "gaedib-2", "gaedib-3", "gaedib-4", "gaedib-5", "gaedib-6");
        assertThat(snapshot.chapters().get(2).title()).isEqualTo("第3章");
        assertThat(snapshot.chapters().get(5).title()).isEqualTo("第6章 最新章");
    }

    @Test
    void keepsChapterContentSelectorInSourceRules() throws Exception {
        CrawlerSourceConfig source = source();
        var detail = Jsoup.parse("""
                <html><head>
                  <meta property="og:novel:book_name" content="书名" />
                  <meta property="og:novel:latest_chapter_url" content="https://www.kkxsz.com/book/abc-1.html" />
                </head><body><div class="chapterList"><a href="/book/abc-1.html">第1章 标题</a></div></body></html>
                """, "https://www.kkxsz.com/book/abc.html");
        var chapter = Jsoup.parse("""
                <html><body>
                  <h1 class="title">第1章 标题</h1>
                  <div class="content" id="content">
                    <p>第一段正文。</p><p>第二段正文。</p>
                  </div>
                </body></html>
                """, "https://www.kkxsz.com/book/abc-1.html");

        Map<String, org.jsoup.nodes.Document> pages = Map.of(
                "https://www.kkxsz.com/book/abc.html", detail,
                "https://www.kkxsz.com/book/abc-1.html", chapter);

        ParsedBookSnapshot snapshot = parser.fetchBook(source,
                new ParsedBookSeed("https://www.kkxsz.com/book/abc.html", "", "", "", 0L, "", ""),
                pages::get);

        assertThat(snapshot.chapters()).hasSize(1);
        assertThat(snapshot.chapters().get(0).url()).isEqualTo("https://www.kkxsz.com/book/abc-1.html");
    }

    private CrawlerSourceConfig source() {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.sourceCode = "kkxsz_public";
        source.baseUrl = "https://www.kkxsz.com";
        source.sourceType = "PUBLIC";
        source.authMode = "NONE";
        source.ruleConfigJson = "{}";
        return source;
    }
}
