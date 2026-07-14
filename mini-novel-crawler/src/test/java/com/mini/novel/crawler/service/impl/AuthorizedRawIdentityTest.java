package com.mini.novel.crawler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizedRawIdentityTest {
    @Test
    void authorizedSourceBookIdOverridesParsedHash() {
        assertThat(AuthorizedRawIdentity.canonicalSourceBookId("AUTH-288", "short-hash", "url-hash"))
                .isEqualTo("AUTH-288");
    }

    @Test
    void parsedSourceBookIdIsUsedOnlyWhenAuthorizedIdMissing() {
        assertThat(AuthorizedRawIdentity.canonicalSourceBookId("", "parsed-id", "url-hash"))
                .isEqualTo("parsed-id");
    }

    @Test
    void urlHashIsLastFallback() {
        assertThat(AuthorizedRawIdentity.canonicalSourceBookId(null, " ", "url-hash"))
                .isEqualTo("url-hash");
    }
}
