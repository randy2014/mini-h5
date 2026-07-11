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

    private CrawlerSourceConfig source(String rules) {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.sourceCode = "xbookcn_authorized";
        source.baseUrl = "https://book.xbookcn.net";
        source.sourceType = "AUTHORIZED_VIP";
        source.ruleConfigJson = rules;
        return source;
    }
}
