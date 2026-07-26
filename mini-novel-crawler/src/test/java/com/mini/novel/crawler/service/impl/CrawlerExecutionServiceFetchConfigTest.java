package com.mini.novel.crawler.service.impl;

import com.mini.novel.crawler.entity.CrawlerSourceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerExecutionServiceFetchConfigTest {

    @Test
    void kkxszUsesMinimalPublicHeadersAcceptedBySource() {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.sourceCode = "kkxsz_public";
        source.sourceType = "PUBLIC";

        var connection = CrawlerExecutionServiceImpl.configureFetchConnection(
                "https://www.kkxsz.com/list-8/", source);

        assertThat(connection.request().header("User-Agent")).isEqualTo("curl/8.18.0");
        assertThat(connection.request().header("Accept")).isEqualTo("*/*");
        assertThat(connection.request().header("Referer")).isNull();
        assertThat(connection.request().header("Sec-Fetch-Mode")).isNull();
        assertThat(connection.request().followRedirects()).isTrue();
    }

    @Test
    void nonKkxszSourcesKeepExistingBrowserLikeHeaders() {
        CrawlerSourceConfig source = new CrawlerSourceConfig();
        source.sourceCode = "23qb_public";
        source.sourceType = "PUBLIC";

        var connection = CrawlerExecutionServiceImpl.configureFetchConnection(
                "https://www.23qb.com/class/1_1.html", source);

        assertThat(connection.request().header("User-Agent")).contains("Chrome/126.0");
        assertThat(connection.request().header("Accept")).contains("text/html");
        assertThat(connection.request().header("Accept-Language")).isEqualTo("zh-CN,zh;q=0.9");
        assertThat(connection.request().header("Referer")).isEqualTo("https://www.23qb.com/");
        assertThat(connection.request().header("Sec-Fetch-Mode")).isEqualTo("navigate");
    }
}
