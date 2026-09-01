-- 20260901 调整：23qb 公开源内容免审核、爬取后直接发布入库（恢复自动合并）
-- h528/69hnovel 授权源保持审核流（auto_merge=0，内容进 PENDING_REVIEW 队列）
-- 本迁移幂等，每次部署重跑安全。

-- 1) 公开源计划恢复自动合并——幂等
UPDATE mini_novel_crawler.crawl_schedule s
JOIN mini_novel_crawler.crawl_source src ON src.id = s.source_id
SET s.auto_merge = 1, s.updated_at = NOW()
WHERE s.enabled = 1 AND src.source_type = 'PUBLIC';

-- 2) "已发布"的待审章节自动转回 CONTENT_READY——幂等纠正
--    判断依据：novel_source_mapping + chapter_source_mapping 中已存在该章节的发布映射
--    （说明该章节内容已入库，无论公开源自动合并还是授权源审核通过，都无需再审核）。
--    未发布的新章节（无映射行）保持 PENDING_REVIEW，继续走审核。
UPDATE mini_novel_crawler.crawl_chapter_raw c
JOIN mini_novel_crawler.crawl_book_raw b ON b.id = c.book_raw_id
JOIN mini_novel.novel_source_mapping m
  ON m.source_code = b.source_code AND m.source_book_id = b.source_book_id
JOIN mini_novel.chapter_source_mapping csm
  ON csm.novel_mapping_id = m.id AND csm.source_chapter_id = c.source_chapter_id
SET c.content_status = 'CONTENT_READY', c.updated_at = NOW()
WHERE c.content_status = 'PENDING_REVIEW';
