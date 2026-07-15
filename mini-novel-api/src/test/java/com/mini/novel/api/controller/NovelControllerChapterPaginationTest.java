package com.mini.novel.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.api.support.VipPublicationProgress;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.vip.service.VipAccessService;
import java.util.List;
import org.junit.jupiter.api.Test;

class NovelControllerChapterPaginationTest {
    @Test
    void returnsRequestedMiddlePageWithMorePagesAvailable() {
        BookReadService books = mock(BookReadService.class);
        Page<Chapter> page = new Page<>(2, 50, 124);
        page.setRecords(List.of(chapter(101L), chapter(102L)));
        when(books.listChapters(291L, 2, 50)).thenReturn(page);
        NovelController controller = controller(books);

        Page<Chapter> result = controller.chapters(291L, 2, 50).data();

        assertEquals(124, result.getTotal());
        assertEquals(3, result.getPages());
        assertEquals(2, result.getCurrent());
        assertEquals(List.of(101L, 102L), result.getRecords().stream().map(Chapter::getId).toList());
        assertTrue(result.getCurrent() < result.getPages());
        verify(books).listChapters(291L, 2, 50);
    }

    @Test
    void marksLastPartialPageAsFinished() {
        BookReadService books = mock(BookReadService.class);
        Page<Chapter> page = new Page<>(7, 50, 320);
        page.setRecords(List.of(chapter(320L)));
        when(books.listChapters(294L, 7, 50)).thenReturn(page);

        Page<Chapter> result = controller(books).chapters(294L, 7, 50).data();

        assertEquals(7, result.getPages());
        assertFalse(result.getCurrent() < result.getPages());
    }

    private NovelController controller(BookReadService books) {
        return new NovelController(books, mock(VipAccessService.class), mock(CurrentUserResolver.class),
                mock(VipPublicationProgress.class));
    }

    private Chapter chapter(long id) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        return chapter;
    }
}
