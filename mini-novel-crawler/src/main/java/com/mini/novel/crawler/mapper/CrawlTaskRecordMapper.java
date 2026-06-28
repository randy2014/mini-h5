package com.mini.novel.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mini.novel.crawler.entity.CrawlTaskRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CrawlTaskRecordMapper extends BaseMapper<CrawlTaskRecord> {
}
