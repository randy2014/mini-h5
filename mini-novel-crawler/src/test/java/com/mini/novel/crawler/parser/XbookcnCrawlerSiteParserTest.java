package com.mini.novel.crawler.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class XbookcnCrawlerSiteParserTest {
    private final XbookcnCrawlerSiteParser parser = new XbookcnCrawlerSiteParser();

    @Test
    void parsesSingleAuthorizedBookAndPagedCatalog() throws Exception {
        CrawlerSourceConfig source = source("""
                {
                  "poc": {
                    "singleBookOnly": true,
                    "bookUrl": "https://book.xbookcn.net/book/100"
                  },
                  "catalogRules": {
                    "maxPages": 3,
                    "maxChapters": 20
                  },
                  "riskRules": {
                    "enabled": true
                  }
                }
                """);

        Document rank = Jsoup.parse("<html></html>", "https://book.xbookcn.net/");
        ParsedBookSeed seed = parser.parseBookSeeds(source, rank, "https://book.xbookcn.net/", 1).get(0);

        Map<String, Document> pages = Map.of(
                "https://book.xbookcn.net/book/100", Jsoup.parse("""
                        <h1>Authorized Adult Novel</h1>
                        <div class="author">Author: Writer A</div>
                        <div class="intro">A lawful adult-focused authorized work.</div>
                        <div class="category">Authorized</div>
                        <a href="/book/100/catalog-1.html">Catalog</a>
                        """, "https://book.xbookcn.net/book/100"),
                "https://book.xbookcn.net/book/100/catalog-1.html", Jsoup.parse("""
                        <a href="/book/100/chapter/1.html">Chapter 1 Opening</a>
                        <a href="/book/100/chapter/2.html">Chapter 2 Middle</a>
                        <a href="/book/100/catalog-2.html">Next</a>
                        """, "https://book.xbookcn.net/book/100/catalog-1.html"),
                "https://book.xbookcn.net/book/100/catalog-2.html", Jsoup.parse("""
                        <a href="/book/100/chapter/3.html">Chapter 3 Ending</a>
                        """, "https://book.xbookcn.net/book/100/catalog-2.html")
        );

        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, pages::get);

        assertThat(snapshot.sourceBookId()).isEqualTo("100");
        assertThat(snapshot.title()).isEqualTo("Authorized Adult Novel");
        assertThat(snapshot.author()).isEqualTo("Writer A");
        assertThat(snapshot.chapters()).hasSize(3);
        assertThat(snapshot.chapters()).extracting(ParsedChapterSnapshot::chapterNo)
                .containsExactly(1, 2, 3);
        assertThat(snapshot.chapters()).extracting(ParsedChapterSnapshot::vip)
                .containsOnly(true);
    }

    @Test
    void metadataOnlyDoesNotFetchCatalogOrChapters() throws Exception {
        CrawlerSourceConfig source = source("""
                {
                  "poc": {
                    "singleBookOnly": true,
                    "metadataOnly": true,
                    "bookUrl": "https://book.xbookcn.net/book/200"
                  },
                  "riskRules": {
                    "enabled": true
                  }
                }
                """);

        ParsedBookSeed seed = parser.parseBookSeeds(source,
                Jsoup.parse("<html></html>", "https://book.xbookcn.net/"),
                "https://book.xbookcn.net/", 1).get(0);
        ParsedBookSnapshot snapshot = parser.fetchBook(source, seed, url -> {
            if (!"https://book.xbookcn.net/book/200".equals(url)) {
                throw new AssertionError("metadata-only mode must not fetch catalog or chapter URLs: " + url);
            }
            return Jsoup.parse("""
                    <meta property="og:novel:book_id" content="x200">
                    <meta property="og:image" content="/covers/200.jpg">
                    <h1>Metadata Only Book</h1>
                    <div class="author">Author: Writer B</div>
                    <div class="intro">Only metadata is discovered.</div>
                    <div class="category">Authorized</div>
                    <div class="tags"><a>Adult</a><a>Drama</a></div>
                    <a href="/book/200/catalog.html">Catalog</a>
                    """, "https://book.xbookcn.net/book/200");
        });

        assertThat(snapshot.title()).isEqualTo("Metadata Only Book");
        assertThat(snapshot.sourceBookId()).isEqualTo("x200");
        assertThat(snapshot.coverUrl()).isEqualTo("https://book.xbookcn.net/covers/200.jpg");
        assertThat(snapshot.chapters()).isEmpty();
        assertThat(snapshot.tagsJson()).isEqualTo("[\"Adult\",\"Drama\"]");
    }

    @Test
    void riskGuardHardBlocksSexualMinorSignals() {
        ContentRiskGuard.RiskResult result = ContentRiskGuard.evaluate(
                "test", "", "\u672a\u6210\u5e74 sexual content", java.util.List.of());

        assertThat(result.blocked()).isTrue();
        assertThat(result.reviewRequired()).isTrue();
    }

    @Test
    void riskGuardMarksSingleHighRiskSignalForReview() {
        ContentRiskGuard.RiskResult result = ContentRiskGuard.evaluate(
                "test", "", "porn signal only", java.util.List.of());

        assertThat(result.blocked()).isFalse();
        assertThat(result.reviewRequired()).isTrue();
    }

    @Test
    void riskGuardDoesNotHardBlockDistantMinorSignals() {
        ContentRiskGuard.RiskResult result = ContentRiskGuard.evaluate(
                "test", "", "\u672a\u6210\u5e74" + "safe text ".repeat(30) + "sexual content", java.util.List.of());

        assertThat(result.blocked()).isFalse();
        assertThat(result.reviewRequired()).isTrue();
    }

    @Test
    void riskGuardKeepsAgeUnknownSignalsForReview() {
        ContentRiskGuard.RiskResult result = ContentRiskGuard.evaluate(
                "test", "", "\u5c11\u5973 sexual signal", java.util.List.of());

        assertThat(result.blocked()).isFalse();
        assertThat(result.reviewRequired()).isTrue();
    }

    private CrawlerSourceConfig source(String rules) {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.sourceCode = "xbookcn_authorized";
        source.baseUrl = "https://book.xbookcn.net";
        source.sourceType = "AUTHORIZED_VIP";
        source.ruleConfigJson = rules;
        return source;
    }
}
