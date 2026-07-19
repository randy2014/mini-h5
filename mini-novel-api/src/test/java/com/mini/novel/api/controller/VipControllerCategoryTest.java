package com.mini.novel.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mini.novel.api.model.VipBookCategoryVo;
import com.mini.novel.api.model.VipBookPageVo;
import com.mini.novel.api.support.CurrentUserResolver;
import com.mini.novel.api.support.VipPublicationProgress;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.entity.VipCategory;
import com.mini.novel.book.mapper.NovelMapper;
import com.mini.novel.book.mapper.NovelVipCategoryMappingMapper;
import com.mini.novel.book.mapper.VipCategoryMapper;
import com.mini.novel.common.result.Result;
import com.mini.novel.vip.mapper.VipPlanMapper;
import com.mini.novel.vip.service.VipAccessService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VipControllerCategoryTest {
    @Mock private VipPlanMapper vipPlanMapper;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private VipAccessService vipAccessService;
    @Mock private NovelMapper novelMapper;
    @Mock private VipCategoryMapper vipCategoryMapper;
    @Mock private NovelVipCategoryMappingMapper novelVipCategoryMappingMapper;
    @Mock private VipPublicationProgress publicationProgress;

    private VipController controller;

    @BeforeEach
    void setUp() {
        controller = new VipController(vipPlanMapper, currentUserResolver, vipAccessService,
                novelMapper, vipCategoryMapper, novelVipCategoryMappingMapper, publicationProgress);
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsFilteredPaginationAndNormalizesInvalidCategoryToOther() {
        VipCategory city = category(7L, "city", 10);
        when(vipCategoryMapper.selectList(any())).thenReturn(List.of(city));
        when(novelMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Novel> page = invocation.getArgument(0);
            Novel invalid = new Novel();
            invalid.setId(99L);
            invalid.setCategoryId(999L);
            page.setTotal(21);
            page.setRecords(List.of(invalid));
            return page;
        });

        Result<VipBookPageVo> response = controller.books(2, 20, VipController.CATEGORY_OTHER);
        VipBookPageVo data = response.data();

        assertEquals(21, data.getTotal());
        assertEquals(2, data.getPages());
        assertEquals(2, data.getPage());
        assertFalse(data.isHasMore());
        assertNull(data.getRecords().get(0).getCategoryId());
        assertEquals(VipController.CATEGORY_OTHER_NAME, data.getRecords().get(0).getCategoryName());
    }

    @Test
    void returnsAllFirstRealCategoriesInSortOrderAndOtherLast() {
        VipCategory city = category(7L, "city", 10);
        VipCategory empty = category(8L, " ", 20);
        when(vipCategoryMapper.selectList(any())).thenReturn(List.of(city, empty));
        when(novelMapper.selectCount(any())).thenReturn(4L, 2L, 2L);

        List<VipBookCategoryVo> categories = controller.categories().data();

        assertEquals(List.of("all", "7", "other"), categories.stream().map(VipBookCategoryVo::getKey).toList());
        assertEquals(List.of(4L, 2L, 2L), categories.stream().map(VipBookCategoryVo::getCount).toList());
        assertEquals(categories.get(0).getCount(), categories.stream().skip(1).mapToLong(VipBookCategoryVo::getCount).sum());
        assertTrue(categories.get(0).getCategoryName().equals(VipController.CATEGORY_ALL_NAME));
        assertTrue(categories.get(2).getCategoryName().equals(VipController.CATEGORY_OTHER_NAME));
    }

    @Test
    void vipBooksQueryUsesAuthorizedVipMappingAndApprovalAuditInsteadOfHardcodedSourceDomain() {
        String sql = controller.vipBooksQuery("all", java.util.Set.of()).getSqlSegment();

        assertTrue(sql.contains("AUTHORIZED_VIP"));
        assertTrue(sql.contains("novel_source_mapping"));
        assertTrue(sql.contains("crawler_authorized_book_audit"));
        assertFalse(sql.contains("book.xbookcn.net"));
    }

    @Test
    void vipBooksQueryFiltersByIndependentVipCategoryMapping() {
        String sql = controller.vipBooksQuery("7", java.util.Set.of(7L)).getSqlSegment();

        assertTrue(sql.contains("novel_vip_category_mapping"));
        assertFalse(sql.contains("novel.category_id"));
        assertFalse(sql.contains(" FROM category "));
    }

    private VipCategory category(Long id, String name, int sort) {
        VipCategory category = new VipCategory();
        category.setId(id);
        category.setName(name);
        category.setSort(sort);
        category.setEnabled(true);
        return category;
    }
}
