package com.mini.novel.crawler.parser;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class ContentRiskGuard {
    private static final Pattern SEXUAL_PATTERN = Pattern.compile(
            "(?:\\u8272\\u60c5|\\u8272\\u60c5\\u63cf\\u5199|\\u6027\\u7231|\\u4e71\\u4f26|\\u5f3a\\u5978|"
                    + "\\u5f3a\\u66b4|\\u5f3a\\u5236\\u53d1\\u751f\\u5173\\u7cfb|\\u6deb\\u4e71|\\u88f8\\u4f53|"
                    + "\\u505a\\u7231|\\u53e3\\u4ea4|\\u9634\\u9053|\\u9634\\u830e|rape|incest|porn|sex)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MINOR_PATTERN = Pattern.compile(
            "(?:\\u672a\\u6210\\u5e74|\\u5e7c\\u5973|\\u5e7c\\u7ae5|\\u5c0f\\u5b66\\u751f|"
                    + "\\u521d\\u4e2d\\u751f|\\u5c11\\u5973|\\u5c11\\u5e74|\\u5973\\u7ae5|\\u7537\\u7ae5|"
                    + "\\u5341[0-7]\\u5c81|1[0-7]\\s*\\u5c81|minor|underage|child)",
            Pattern.CASE_INSENSITIVE);

    private ContentRiskGuard() {
    }

    public static RiskResult evaluate(String title, String intro, String content, List<String> extraBlockedTerms) {
        String combined = join(title, intro, content);
        if (!StringUtils.hasText(combined)) {
            return RiskResult.ok();
        }
        String lower = combined.toLowerCase(Locale.ROOT);
        if (extraBlockedTerms != null) {
            for (String term : extraBlockedTerms) {
                if (StringUtils.hasText(term) && lower.contains(term.toLowerCase(Locale.ROOT))) {
                    return RiskResult.blocked("Configured blocked risk term matched.");
                }
            }
        }
        boolean sexual = SEXUAL_PATTERN.matcher(combined).find();
        boolean minor = MINOR_PATTERN.matcher(combined).find();
        if (sexual && minor) {
            return RiskResult.blocked("Possible sexual minor content matched hard-block rule.");
        }
        if (sexual || minor) {
            return RiskResult.review("Possible high-risk content requires manual review.");
        }
        return RiskResult.ok();
    }

    private static String join(String title, String intro, String content) {
        return (title == null ? "" : title) + "\n"
                + (intro == null ? "" : intro) + "\n"
                + (content == null ? "" : content);
    }

    public record RiskResult(boolean blocked, boolean reviewRequired, String reason) {
        public static RiskResult ok() {
            return new RiskResult(false, false, "OK");
        }

        public static RiskResult review(String reason) {
            return new RiskResult(false, true, reason);
        }

        public static RiskResult blocked(String reason) {
            return new RiskResult(true, true, reason);
        }
    }
}
