package com.mini.novel.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.api.support.VipPublicationProgress;
import com.mini.novel.book.entity.Chapter;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.service.BookReadService;
import com.mini.novel.vip.service.VipAccessService;
import com.mini.novel.common.exception.BusinessException;
import com.mini.novel.common.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class NovelControllerChapterPaginationTest {
    @Test
    void returnsRequestedMiddlePageWithMorePagesAvailable() {
        BookReadService books = mock(BookReadService.class);
        Page<Chapter> page = new Page<>(2, 50, 124);
        page.setRecords(List.of(chapter(101L), chapter(102L)));
        when(books.listChapters(291L, 2, 50)).thenReturn(page);
        when(books.getNovel(291L)).thenReturn(novel(291L, false));
        NovelController controller = controller(books);

        Page<Chapter> result = controller.chapters(291L, 2, 50, null).data();

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
        when(books.getNovel(294L)).thenReturn(novel(294L, false));

        Page<Chapter> result = controller(books).chapters(294L, 7, 50, null).data();

        assertEquals(7, result.getPages());
        assertFalse(result.getCurrent() < result.getPages());
    }

    @Test
    void ordinaryUserCannotSeeVipDetailOrChapterTitles() {
        BookReadService books = mock(BookReadService.class);
        VipAccessService vipAccess = mock(VipAccessService.class);
        CurrentUserResolver users = mock(CurrentUserResolver.class);
        Novel vipNovel = novel(300L, true);
        when(books.getNovel(300L)).thenReturn(vipNovel);
        when(users.resolveUserId(null)).thenReturn(9L);
        when(vipAccess.hasActiveVip(9L)).thenReturn(false);
        NovelController controller = new NovelController(
                books, vipAccess, users, mock(VipPublicationProgress.class));

        BusinessException detailError = assertThrows(BusinessException.class,
                () -> controller.detail(300L, null));
        BusinessException chaptersError = assertThrows(BusinessException.class,
                () -> controller.chapters(300L, 1, 50, null));

        assertEquals(ErrorCode.VIP_REQUIRED, detailError.getCode());
        assertEquals(ErrorCode.VIP_REQUIRED, chaptersError.getCode());
        org.mockito.Mockito.verify(books, org.mockito.Mockito.never()).listChapters(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
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

    private Novel novel(long id, boolean vip) {
        Novel novel = new Novel();
        novel.setId(id);
        novel.setVipRequired(vip);
        return novel;
    }
}
