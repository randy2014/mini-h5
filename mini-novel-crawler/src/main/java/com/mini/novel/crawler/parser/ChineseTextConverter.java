package com.mini.novel.crawler.parser;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

public final class ChineseTextConverter {
    private ChineseTextConverter() {
    }

    public static String toSimplified(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        return ZhConverterUtil.toSimple(value);
    }
}
