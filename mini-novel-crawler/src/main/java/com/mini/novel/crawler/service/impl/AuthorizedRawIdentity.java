package com.mini.novel.crawler.service.impl;

import org.springframework.util.StringUtils;

public final class AuthorizedRawIdentity {
    private AuthorizedRawIdentity() {
    }

    public static String canonicalSourceBookId(String authorizedSourceBookId, String parsedSourceBookId, String fallbackSourceBookId) {
        if (StringUtils.hasText(authorizedSourceBookId)) {
            return authorizedSourceBookId;
        }
        if (StringUtils.hasText(parsedSourceBookId)) {
            return parsedSourceBookId;
        }
        return fallbackSourceBookId;
    }
}
