package com.mini.novel.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.book.entity.Novel;
import java.util.List;
import org.junit.jupiter.api.Test;

class VipBookPageVoTest {
    @Test
    void reportsMorePagesBeforeLastPage() {
        Page<Novel> page = new Page<>(2, 20, 61);
        page.setRecords(List.of(new Novel()));
        VipBookPageVo result = VipBookPageVo.from(page);
        assertEquals(61, result.getTotal());
        assertEquals(4, result.getPages());
        assertEquals(2, result.getPage());
        assertEquals(20, result.getPageSize());
        assertTrue(result.isHasMore());
    }

    @Test
    void marksExactAndPartialLastPagesFinished() {
        assertFalse(VipBookPageVo.from(new Page<Novel>(3, 20, 60)).isHasMore());
        assertFalse(VipBookPageVo.from(new Page<Novel>(4, 20, 61)).isHasMore());
    }

    @Test
    void emptyResultHasNoMorePages() {
        VipBookPageVo result = VipBookPageVo.from(new Page<>(1, 20, 0));
        assertEquals(0, result.getPages());
        assertFalse(result.isHasMore());
    }
}
