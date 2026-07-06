CREATE DATABASE IF NOT EXISTS mini_novel_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mini_novel_crawler;

SET @keep_source_id := (SELECT id FROM crawl_source WHERE source_code = '23qb_public' LIMIT 1);

UPDATE crawl_source
SET enabled = CASE WHEN id = @keep_source_id THEN 1 ELSE 0 END,
    updated_at = CURRENT_TIMESTAMP
WHERE @keep_source_id IS NOT NULL;

UPDATE crawl_rank_source
SET enabled = CASE WHEN source_id = @keep_source_id THEN 1 ELSE 0 END,
    updated_at = CURRENT_TIMESTAMP
WHERE @keep_source_id IS NOT NULL;

UPDATE crawl_schedule
SET enabled = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE @keep_source_id IS NOT NULL
  AND (source_id IS NULL OR source_id <> @keep_source_id);

UPDATE crawl_schedule
SET schedule_times = '04:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 1,
    crawl_vip = 0,
    auto_merge = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE @keep_source_id IS NOT NULL
  AND source_id = @keep_source_id;
