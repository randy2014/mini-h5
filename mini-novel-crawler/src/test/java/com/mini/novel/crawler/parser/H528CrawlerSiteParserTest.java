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
        CrawlerSourceConfig source = source("{}");
        Document home = Jsoup.parse("""
                <a href="/u/1.html">ad</a>
                <div class="post"><h2><a href="/post/28936.html">First Story</a></h2></div>
                <div class="post"><h2><a href="/post/28935.html#more">Second Story</a></h2></div>
                <a href="/page/2">2</a>
                """, "http://www.h528.com/");

        var seeds = parser.parseBookSeeds(source, home, "http://www.h528.com/", 10);

        assertThat(seeds).hasSize(2);
        assertThat(seeds).extracting(ParsedBookSeed::url)
                .containsExactly("http://www.h528.com/post/28936.html", "http://www.h528.com/post/28935.html");
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
