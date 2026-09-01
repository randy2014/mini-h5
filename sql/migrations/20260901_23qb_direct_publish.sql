-- 20260901 调整：23qb 公开源内容免审核、爬取后直接发布入库（恢复自动合并）
-- h528/69hnovel 授权源保持审核流（auto_merge=0，内容进 PENDING_REVIEW 队列）
-- 本迁移幂等，每次部署重跑安全。

-- 1) 公开源计划恢复自动合并——幂等
UPDATE mini_novel_crawler.crawl_schedule s
JOIN mini_novel_crawler.crawl_source src ON src.id = s.source_id
SET s.auto_merge = 1, s.updated_at = NOW()
WHERE s.enabled = 1 AND src.source_type = 'PUBLIC';

-- 2) 公开源"已发布书"的待审章节自动转回 CONTENT_READY——幂等纠正
--    场景：早期迁移把已入库的 CONTENT_READY 章节翻成 PENDING_REVIEW，
--    若该书已存在 CONTENT_READY 发布映射，说明内容已入库，无需再审核。
--    授权源（PUBLIC 之外）不受影响，保持人工审核。
UPDATE mini_novel_crawler.crawl_chapter_raw c
JOIN mini_novel_crawler.crawl_book_raw b ON b.id = c.book_raw_id
JOIN mini_novel_crawler.crawl_source src ON src.source_code = b.source_code
JOIN mini_novel.novel_source_mapping m
  ON m.source_code = b.source_code AND m.source_book_id = b.source_book_id
SET c.content_status = 'CONTENT_READY', c.updated_at = NOW()
WHERE src.source_type = 'PUBLIC'
  AND c.content_status = 'PENDING_REVIEW'
  AND m.content_status = 'CONTENT_READY';
