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
    private static final Pattern EXPLICIT_MINOR_PATTERN = Pattern.compile(
            "(?:\\u5341[0-7]\\u5c81|1[0-7]\\s*\\u5c81)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AGE_UNKNOWN_PATTERN = Pattern.compile(
            "(?:\\u672a\\u6210\\u5e74|\\u5e7c\\u5973|\\u5e7c\\u7ae5|\\u5c0f\\u5b66\\u751f|"
                    + "\\u521d\\u4e2d\\u751f|\\u5973\\u7ae5|\\u7537\\u7ae5|\\u5b69\\u5b50|"
                    + "\\u5c11\\u5973|\\u5c11\\u5e74|\\u5b66\\u751f|\\u6821\\u56ed|\\u9ad8\\u4e2d\\u751f|\\u841d\\u8389|"
                    + "\\u5e74\\u8f7b|minor|underage|child)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PERSON_PATTERN = Pattern.compile(
            "(?:\\u5973\\u5b69|\\u7537\\u5b69|\\u5973\\u7ae5|\\u7537\\u7ae5|\\u5b69\\u5b50|\\u5b66\\u751f|\\u5973\\u751f|"
                    + "\\u7537\\u751f|\\u5979|\\u4ed6|girl|boy|child|student)",
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
                    return unknownReview();
                }
            }
        }
        boolean hasSexual = SEXUAL_PATTERN.matcher(combined).find();
        boolean hasAgeUnknown = AGE_UNKNOWN_PATTERN.matcher(combined).find();
        boolean hasExplicitMinor = EXPLICIT_MINOR_PATTERN.matcher(combined).find();
        boolean hasPersonSignal = PERSON_PATTERN.matcher(combined).find();
        if (hasSexual || hasAgeUnknown || hasExplicitMinor || hasPersonSignal) {
            return unknownReview();
        }
        return RiskResult.ok();
    }

    private static RiskResult unknownReview() {
        return RiskResult.review("rule_code=UNKNOWN_REQUIRES_MANUAL_REVIEW;version=20260714;confidence=UNKNOWN;hint=possible_age_or_behavior_context");
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
