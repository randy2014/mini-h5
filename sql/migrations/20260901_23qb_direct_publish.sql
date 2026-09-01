-- 20260901 调整：23qb 公开源内容免审核、爬取后直接发布入库（恢复自动合并）
-- h528/69hnovel 授权源保持审核流（auto_merge=0，内容进 PENDING_REVIEW 队列）

UPDATE mini_novel_crawler.crawl_schedule s
JOIN mini_novel_crawler.crawl_source src ON src.id = s.source_id
SET s.auto_merge = 1, s.updated_at = NOW()
WHERE s.enabled = 1 AND src.source_type = 'PUBLIC';
