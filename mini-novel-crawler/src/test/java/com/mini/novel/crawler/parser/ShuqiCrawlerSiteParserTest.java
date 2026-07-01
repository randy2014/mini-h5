package com.mini.novel.crawler.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ShuqiCrawlerSiteParserTest {
    @Test
    void decodesShiftedBase64ChapterContent() {
        String plain = "<p>第一章 测试正文</p><p>这里是完整的免费章节内容。</p>";
        String encoded = encodeLikeShuqi(plain);

        String decoded = ShuqiCrawlerSiteParser.decodeChapterContent(encoded);

        assertThat(decoded).isEqualTo("第一章 测试正文\n这里是完整的免费章节内容。");
    }

    private String encodeLikeShuqi(String plain) {
        String base64 = Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
        StringBuilder encoded = new StringBuilder(base64.length());
        for (int i = 0; i < base64.length(); i++) {
            char ch = base64.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                encoded.append(inverseShift(ch));
            } else {
                encoded.append(ch);
            }
        }
        return encoded.toString();
    }

    private char inverseShift(char decoded) {
        for (char candidate = 'A'; candidate <= 'Z'; candidate++) {
            if (shift(candidate) == decoded) {
                return candidate;
            }
        }
        for (char candidate = 'a'; candidate <= 'z'; candidate++) {
            if (shift(candidate) == decoded) {
                return candidate;
            }
        }
        return decoded;
    }

    private char shift(char ch) {
        int lower = Character.toLowerCase(ch);
        int code = (lower - 83) % 26;
        if (code == 0) {
            code = 26;
        }
        return (char) (code + (Character.isUpperCase(ch) ? 64 : 96));
    }
}
