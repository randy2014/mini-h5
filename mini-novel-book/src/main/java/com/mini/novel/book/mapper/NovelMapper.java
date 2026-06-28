package com.mini.novel.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.book.entity.Novel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NovelMapper extends BaseMapper<Novel> {
}
