package com.mini.novel.crawler.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class Novel69hCrawlerSiteParserTest {
    private final Novel69hCrawlerSiteParser parser = new Novel69hCrawlerSiteParser();

    @Test
    void parsesSingleArticleAsIsolatedBookAndChapter() throws Exception {
        CrawlerSourceConfig source = source("""
                {"poc":{"singleBookOnly":true,"bookUrl":"https://www.69hnovel.com/erotic-novel/story/article-12608.html"},
                 "chapterRules":{"content":"article","removeSelectors":[".M-banner",".M-aside-nav",".table-box"]}}
                """);
        ParsedBookSeed seed = parser.parseBookSeeds(source,
                Jsoup.parse("<html></html>", "https://www.69hnovel.com/erotic-novel.html"),
                "https://www.69hnovel.com/erotic-novel.html", 1).get(0);
        Document page = Jsoup.parse("""
                <html><head><title>\u7e41\u9ad4\u6a19\u984c_69\u6587\u5b66\u7f51</title>
                <meta name="description" content="\u7c21\u4ecb"></head>
                <body><article>
                  <h1>\u7e41\u9ad4\u6a19\u984c</h1>
                  <a href="/erotic-novel/story.html">\u6027\u7d93\u9a57</a>
                  <div class="M-banner">ad</div>
                  <p>\u7b2c\u4e00\u6bb5\u6b63\u6587\u3002</p><p>\u7b2c\u4e8c\u6bb5\u6b63\u6587\u3002</p>
                  <a href="https://www.wendun5.com">external ad</a>
                </article>
                <aside><a href="/erotic-novel/wife.html">\u4eba\u59bb\u719f\u5973 2889</a></aside></body></html>
                """, "https://www.69hnovel.com/erotic-novel/story/article-12608.html");

        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, url -> page);

        assertThat(snapshot.sourceBookId()).isEqualTo("12608");
        assertThat(snapshot.title()).isEqualTo("\u7e41\u4f53\u6807\u9898");
        assertThat(snapshot.categoryName()).isEqualTo("\u6027\u7ecf\u9a8c");
        assertThat(snapshot.tagsJson()).isEqualTo("[\"\u6027\u7ecf\u9a8c\"]");
        assertThat(snapshot.bookStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(snapshot.chapters()).hasSize(1);
        assertThat(snapshot.chapters().get(0).chapterId()).isEqualTo("12608");
        assertThat(snapshot.chapters().get(0).content()).contains("\u7b2c\u4e00\u6bb5\u6b63\u6587").doesNotContain("ad");
    }

    @Test
    void discoversArticleSeedsAndKeepsStableIds() {
        CrawlerSourceConfig source = source("{}");
        Document rank = Jsoup.parse("""
                <main class="L-main-col">
                  <a href="/erotic-novel/story/article-12608.html">First</a>
                  <a href="/erotic-novel/story/article-12608.html#comments">Duplicate</a>
                  <a href="/erotic-novel/home/article-18148.html">Second</a>
                </main>
                <aside><a href="/erotic-novel/tags.html">tags</a></aside>
                """, "https://www.69hnovel.com/erotic-novel.html");

        var seeds = parser.parseBookSeeds(source, rank, "https://www.69hnovel.com/erotic-novel.html", 20);

        assertThat(seeds).hasSize(2);
        assertThat(seeds).extracting(ParsedBookSeed::url)
                .containsExactly("https://www.69hnovel.com/erotic-novel/story/article-12608.html",
                        "https://www.69hnovel.com/erotic-novel/home/article-18148.html");
        assertThat(seeds).extracting(ParsedBookSeed::intro).containsExactly("12608", "18148");
    }

    @Test
    void detectsNextRankPage() {
        CrawlerSourceConfig source = source("{}");
        Document rank = Jsoup.parse("""
                <div class="M-page"><a href="/erotic-novel-02.html">\u4e0b\u4e00\u9875</a></div>
                """, "https://www.69hnovel.com/erotic-novel.html");

        assertThat(parser.nextRankPage(source, rank, "https://www.69hnovel.com/erotic-novel.html"))
                .isEqualTo("https://www.69hnovel.com/erotic-novel-02.html");
    }

    @Test
    void infersNextRankPageWhenPaginationLinkIsMissing() {
        CrawlerSourceConfig source = source("{}");
        Document rank = Jsoup.parse("<main></main>", "https://www.69hnovel.com/erotic-novel-02.html");

        assertThat(parser.nextRankPage(source, rank, "https://www.69hnovel.com/erotic-novel-02.html"))
                .isEqualTo("https://www.69hnovel.com/erotic-novel-03.html");
    }

    @Test
    void keepsCategoryFromMainArticleInsteadOfSidebar() throws Exception {
        CrawlerSourceConfig source = source("""
                {"poc":{"singleBookOnly":true,"bookUrl":"https://www.69hnovel.com/erotic-novel/story/article-12609.html"}}
                """);
        ParsedBookSeed seed = parser.parseBookSeeds(source,
                Jsoup.parse("<html></html>", "https://www.69hnovel.com/erotic-novel.html"),
                "https://www.69hnovel.com/erotic-novel.html", 1).get(0);
        Document page = Jsoup.parse("""
                <html><body>
                <article>
                  <h1>Title</h1>
                  <a href="/erotic-novel/story.html">\u6027\u7d93\u9a57</a>
                  <p>Body text.</p>
                </article>
                <aside><a href="/erotic-novel/wife.html">\u4eba\u59bb\u719f\u5973 999</a></aside>
                </body></html>
                """, "https://www.69hnovel.com/erotic-novel/story/article-12609.html");

        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, url -> page);

        assertThat(snapshot.categoryName()).isEqualTo("\u6027\u7ecf\u9a8c");
        assertThat(snapshot.tagsJson()).isEqualTo("[\"\u6027\u7ecf\u9a8c\"]");
    }

    @Test
    void propagatesAccessFailure() {
        CrawlerSourceConfig source = source("""
                {"poc":{"singleBookOnly":true,"bookUrl":"https://www.69hnovel.com/erotic-novel/story/article-12608.html"}}
                """);
        ParsedBookSeed seed = parser.parseBookSeeds(source,
                Jsoup.parse("<html></html>", "https://www.69hnovel.com/erotic-novel.html"),
                "https://www.69hnovel.com/erotic-novel.html", 1).get(0);

        assertThatThrownBy(() -> parser.fetchBook(source, seed, url -> {
            throw new IllegalStateException("access denied");
        })).isInstanceOf(IllegalStateException.class);
    }

    private CrawlerSourceConfig source(String rules) {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.sourceCode = "novel69h_authorized";
        source.baseUrl = "https://www.69hnovel.com";
        source.sourceType = "AUTHORIZED_VIP";
        source.authMode = "NONE";
        source.ruleConfigJson = rules;
        return source;
    }
}
