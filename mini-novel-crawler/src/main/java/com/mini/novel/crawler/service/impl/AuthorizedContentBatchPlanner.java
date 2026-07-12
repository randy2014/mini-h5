package com.mini.novel.crawler.service.impl;

import com.mini.novel.crawler.entity.CrawlerAuthorizedBook;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class AuthorizedContentBatchPlanner {
    private static final Pattern AFTER_ID = Pattern.compile("continuation=afterId:(\\d+)");
    private static final Pattern SELECTED_IDS = Pattern.compile("selectedIds=([0-9,]+)");

    private AuthorizedContentBatchPlanner() {
    }

    static BatchPlan plan(List<CrawlerAuthorizedBook> eligibleBooks,
                          Set<String> finishedSourceBookIds,
                          String previousMessage,
                          int limit) {
        long previousAfterId = continuationAfterId(previousMessage);
        Set<Long> previousSelectedIds = selectedIds(previousMessage);
        List<CrawlerAuthorizedBook> selected = new ArrayList<>();
        for (CrawlerAuthorizedBook book : eligibleBooks.stream()
                .sorted(Comparator.comparing(book -> book.id == null ? 0L : book.id))
                .toList()) {
            if (book.id == null || book.id <= previousAfterId || !StringUtils.hasText(book.bookUrl)) {
                continue;
            }
            if (StringUtils.hasText(book.sourceBookId) && finishedSourceBookIds.contains(book.sourceBookId)) {
                continue;
            }
            selected.add(book);
            if (selected.size() >= Math.max(1, Math.min(20, limit))) {
                break;
            }
        }
        long afterId = selected.stream().map(book -> book.id).max(Long::compareTo).orElse(previousAfterId);
        Set<Long> selectedIds = new LinkedHashSet<>(selected.stream().map(book -> book.id).toList());
        boolean duplicate = !selectedIds.isEmpty() && selectedIds.equals(previousSelectedIds);
        boolean advanced = selected.isEmpty() || afterId > previousAfterId;
        return new BatchPlan(selected, previousAfterId, afterId, selectedIds, duplicate, advanced);
    }

    static long continuationAfterId(String message) {
        if (!StringUtils.hasText(message)) {
            return 0L;
        }
        Matcher matcher = AFTER_ID.matcher(message);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    static Set<Long> selectedIds(String message) {
        Set<Long> ids = new LinkedHashSet<>();
        if (!StringUtils.hasText(message)) {
            return ids;
        }
        Matcher matcher = SELECTED_IDS.matcher(message);
        if (!matcher.find()) {
            return ids;
        }
        for (String value : matcher.group(1).split(",")) {
            if (StringUtils.hasText(value)) {
                ids.add(Long.parseLong(value));
            }
        }
        return ids;
    }

    record BatchPlan(List<CrawlerAuthorizedBook> selected,
                     long previousAfterId,
                     long afterId,
                     Set<Long> selectedIds,
                     boolean duplicateSelection,
                     boolean advanced) {
    }
}
