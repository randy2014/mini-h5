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
import com.mini.novel.book.entity.Category;
import com.mini.novel.book.entity.Novel;
import com.mini.novel.book.mapper.CategoryMapper;
import com.mini.novel.book.mapper.NovelMapper;
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
    @Mock private CategoryMapper categoryMapper;
    @Mock private VipPublicationProgress publicationProgress;

    private VipController controller;

    @BeforeEach
    void setUp() {
        controller = new VipController(vipPlanMapper, currentUserResolver, vipAccessService,
                novelMapper, categoryMapper, publicationProgress);
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsFilteredPaginationAndNormalizesInvalidCategoryToOther() {
        Category city = category(7L, "都市", 10);
        when(categoryMapper.selectList(any())).thenReturn(List.of(city));
        when(novelMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Novel> page = invocation.getArgument(0);
            Novel invalid = new Novel();
            invalid.setId(99L);
            invalid.setCategoryId(999L);
            page.setTotal(21);
            page.setRecords(List.of(invalid));
            return page;
        });

        Result<VipBookPageVo> response = controller.books(2, 20, "other");
        VipBookPageVo data = response.data();

        assertEquals(21, data.getTotal());
        assertEquals(2, data.getPages());
        assertEquals(2, data.getPage());
        assertFalse(data.isHasMore());
        assertNull(data.getRecords().get(0).getCategoryId());
        assertEquals("其他", data.getRecords().get(0).getCategoryName());
    }

    @Test
    void returnsAllFirstRealCategoriesInSortOrderAndOtherLast() {
        Category city = category(7L, "都市", 10);
        Category empty = category(8L, "空分类", 20);
        when(categoryMapper.selectList(any())).thenReturn(List.of(city, empty));
        when(novelMapper.selectCount(any())).thenReturn(4L, 2L, 0L, 2L);

        List<VipBookCategoryVo> categories = controller.categories().data();

        assertEquals(List.of("all", "7", "other"), categories.stream().map(VipBookCategoryVo::getKey).toList());
        assertEquals(List.of(4L, 2L, 2L), categories.stream().map(VipBookCategoryVo::getCount).toList());
        assertEquals(categories.get(0).getCount(), categories.stream().skip(1).mapToLong(VipBookCategoryVo::getCount).sum());
        assertTrue(categories.get(0).getCategoryName().equals("全部"));
        assertTrue(categories.get(2).getCategoryName().equals("其他"));
    }

    @Test
    void vipBooksQueryUsesAuthorizedVipMappingInsteadOfHardcodedSourceDomain() {
        String sql = controller.vipBooksQuery("all", java.util.Set.of()).getSqlSegment();

        assertTrue(sql.contains("AUTHORIZED_VIP"));
        assertTrue(sql.contains("novel_source_mapping"));
        assertFalse(sql.contains("book.xbookcn.net"));
    }

    private Category category(Long id, String name, int sort) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSort(sort);
        return category;
    }
}
