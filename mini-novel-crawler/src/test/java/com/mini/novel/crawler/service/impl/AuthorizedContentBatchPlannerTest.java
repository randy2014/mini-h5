package com.mini.novel.crawler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.mini.novel.crawler.entity.CrawlerAuthorizedBook;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizedContentBatchPlannerTest {
    @Test
    void selectsThreeNonOverlappingBatchesAndAdvancesAfterId() {
        List<CrawlerAuthorizedBook> books = books(15);
        Set<String> finished = new HashSet<>();

        var first = AuthorizedContentBatchPlanner.plan(books, finished, "", 5);
        var second = AuthorizedContentBatchPlanner.plan(books, finished,
                "continuation=afterId:" + first.afterId() + ", selectedIds=" + ids(first), 5);
        var third = AuthorizedContentBatchPlanner.plan(books, finished,
                "continuation=afterId:" + second.afterId() + ", selectedIds=" + ids(second), 5);

        assertThat(first.selectedIds()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(second.selectedIds()).containsExactly(6L, 7L, 8L, 9L, 10L);
        assertThat(third.selectedIds()).containsExactly(11L, 12L, 13L, 14L, 15L);
        assertThat(first.afterId()).isLessThan(second.afterId()).isLessThan(third.afterId());
    }

    @Test
    void skipsFinishedSourceBooks() {
        Set<String> finished = Set.of("book-1", "book-2");

        var plan = AuthorizedContentBatchPlanner.plan(books(7), finished, "", 5);

        assertThat(plan.selectedIds()).containsExactly(3L, 4L, 5L, 6L, 7L);
        assertThat(plan.afterId()).isEqualTo(7L);
    }

    @Test
    void detectsRepeatedSelection() {
        var plan = AuthorizedContentBatchPlanner.plan(books(5), Set.of(),
                "continuation=afterId:0, selectedIds=1,2,3,4,5", 5);

        assertThat(plan.duplicateSelection()).isTrue();
    }

    private static List<CrawlerAuthorizedBook> books(int count) {
        return java.util.stream.LongStream.rangeClosed(1, count)
                .mapToObj(id -> {
                    CrawlerAuthorizedBook book = new CrawlerAuthorizedBook();
                    book.id = id;
                    book.sourceBookId = "book-" + id;
                    book.bookUrl = "https://book.xbookcn.net/book-" + id;
                    book.title = "Book " + id;
                    book.author = "Author";
                    return book;
                })
                .toList();
    }

    private static String ids(AuthorizedContentBatchPlanner.BatchPlan plan) {
        return plan.selectedIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
}
