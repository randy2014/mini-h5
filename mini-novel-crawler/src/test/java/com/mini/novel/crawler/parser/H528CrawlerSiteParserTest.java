package com.mini.novel.crawler.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class H528CrawlerSiteParserTest {
    private final H528CrawlerSiteParser parser = new H528CrawlerSiteParser();

    @Test
    void parsesSingleAuthorizedPostAsOneQuarantinedVipChapter() throws Exception {
        CrawlerSourceConfig source = source("""
                {"poc":{"singleBookOnly":true,"bookUrl":"http://www.h528.com/post/28936.html"}}
                """);
        ParsedBookSeed seed = parser.parseBookSeeds(source,
                Jsoup.parse("<html></html>", "http://www.h528.com/"),
                "http://www.h528.com/", 1).get(0);
        Document page = Jsoup.parse("""
                <html><head><title>Sample Adult Story - 風月文學網 &#8211; 成人小說 情色文學</title></head>
                <body><div class="narrowcolumn"><div class="post">
                  <h2>Sample Adult Story</h2>
                  <div class="entry"><p>正文段落一。</p><p>正文段落二。</p></div>
                  <p class="postmetadata"><a href="/post/category/city">都市生活</a></p>
                </div><div class="sidebar"><a href="/post/1.html">noise</a></div></div></body></html>
                """, "http://www.h528.com/post/28936.html");

        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, url -> page);

        assertThat(snapshot.sourceBookId()).isEqualTo("28936");
        assertThat(snapshot.title()).isEqualTo("Sample Adult Story");
        assertThat(snapshot.categoryName()).isEqualTo("都市生活");
        assertThat(snapshot.bookStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(snapshot.chapters()).hasSize(1);
        assertThat(snapshot.chapters().get(0).chapterId()).isEqualTo("28936");
        assertThat(snapshot.chapters().get(0).title()).isEqualTo("Sample Adult Story");
        assertThat(snapshot.chapters().get(0).vip()).isTrue();
    }

    @Test
    void discoversPostSeedsFromHomeButSkipsAdsAndNonPostLinks() {
        CrawlerSourceConfig source = source("""
                {"poc":{"mode":"BATCH"}}
                """);
        Document home = Jsoup.parse("""
                <a href="/u/1.html">ad</a>
                <div class="post"><h2><a href="/post/28936.html">繁體標題</a></h2></div>
                <div class="post"><h2><a href="/post/28935.html#more">第二篇</a></h2></div>
                <div class="post"><h2><a href="https://www.h528.com/post/28935.html">duplicate</a></h2></div>
                <a href="/page/2">2</a>
                """, "http://www.h528.com/");

        var seeds = parser.parseBookSeeds(source, home, "http://www.h528.com/", 10);

        assertThat(seeds).hasSize(2);
        assertThat(seeds).extracting(ParsedBookSeed::url)
                .containsExactly("http://www.h528.com/post/28936.html", "http://www.h528.com/post/28935.html");
        assertThat(seeds.get(0).title()).isEqualTo("繁体标题");
    }

    @Test
    void convertsTraditionalTitleCategoryAuthorAndBodyToSimplified() throws Exception {
        CrawlerSourceConfig source = source("""
                {"poc":{"singleBookOnly":true,"bookUrl":"http://www.h528.com/post/30000.html"}}
                """);
        ParsedBookSeed seed = parser.parseBookSeeds(source,
                Jsoup.parse("<html></html>", "http://www.h528.com/"),
                "http://www.h528.com/", 1).get(0);
        Document page = Jsoup.parse("""
                <html><head><title>臺灣風雲 - h528</title></head><body>
                <div class="post"><h2>臺灣風雲</h2>
                  <div class="entry"><p>這是一段繁體正文，後續應轉為簡體。</p></div>
                  <p class="postmetadata"><a rel="author">張三</a><a href="/post/category/test">都市傳奇</a></p>
                </div></body></html>
                """, "http://www.h528.com/post/30000.html");

        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, url -> page);

        assertThat(snapshot.sourceBookId()).isEqualTo("30000");
        assertThat(snapshot.chapterId()).isEqualTo("30000");
        assertThat(snapshot.title()).isEqualTo("台湾风云");
        assertThat(snapshot.author()).isEqualTo("张三");
        assertThat(snapshot.categoryName()).isEqualTo("都市传奇");
        assertThat(snapshot.chapters().get(0).content()).contains("繁体正文").contains("简体");
    }

    @Test
    void findsNextHomePageFromNavigation() {
        CrawlerSourceConfig source = source("""
                {"poc":{"mode":"BATCH"}}
                """);
        Document home = Jsoup.parse("""
                <div class="navigation"><a href="/page/2/">下一页</a></div>
                """, "http://www.h528.com/");

        assertThat(parser.nextRankPage(source, home, "http://www.h528.com/"))
                .isEqualTo("http://www.h528.com/page/2/");
    }

    @Test
    void keepsSourceIdentityOnFailureByFailingTheFetch() {
        CrawlerSourceConfig source = source("""
                {"poc":{"singleBookOnly":true,"bookUrl":"http://www.h528.com/post/28936.html"}}
                """);
        ParsedBookSeed seed = parser.parseBookSeeds(source,
                Jsoup.parse("<html></html>", "http://www.h528.com/"),
                "http://www.h528.com/", 1).get(0);

        assertThatThrownBy(() -> parser.fetchBook(source, seed, url -> {
            throw new IllegalStateException("access denied");
        })).isInstanceOf(IllegalStateException.class);
    }

    private CrawlerSourceConfig source(String rules) {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.sourceCode = "h528_authorized";
        source.baseUrl = "http://www.h528.com";
        source.sourceType = "AUTHORIZED_VIP";
        source.authMode = "NONE";
        source.ruleConfigJson = rules;
        return source;
    }
}
