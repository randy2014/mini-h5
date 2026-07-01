CREATE DATABASE IF NOT EXISTS mini_novel_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mini_novel_crawler;

INSERT INTO crawl_source (
  source_code,
  name,
  base_url,
  source_type,
  auth_mode,
  rule_config_json,
  enabled,
  priority,
  remark
) VALUES (
  'shuqi_public',
  '书旗公开免费章节',
  'https://www.shuqi.com',
  'PUBLIC',
  'NONE',
  JSON_OBJECT('parser', 'shuqi', 'freeOnly', true, 'verifiedBookId', '8872073'),
  1,
  20,
  '书旗免费章节链路验证源：仅采集 payStatus=0 的公开免费章节。'
) ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  base_url = VALUES(base_url),
  source_type = VALUES(source_type),
  auth_mode = VALUES(auth_mode),
  rule_config_json = VALUES(rule_config_json),
  enabled = VALUES(enabled),
  priority = VALUES(priority),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;

SET @shuqi_source_id := (SELECT id FROM crawl_source WHERE source_code = 'shuqi_public' LIMIT 1);

UPDATE crawl_rank_source
SET rank_type = 'SINGLE_BOOK_FREE',
    rank_url = 'https://www.shuqi.com/book/8872073.html',
    prefer_completed = 1,
    max_books = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗单书免费链路验证';

INSERT INTO crawl_rank_source (
  source_id,
  rank_name,
  rank_type,
  rank_url,
  prefer_completed,
  max_books,
  enabled
)
SELECT
  @shuqi_source_id,
  '书旗单书免费链路验证',
  'SINGLE_BOOK_FREE',
  'https://www.shuqi.com/book/8872073.html',
  1,
  1,
  1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id
      AND rank_name = '书旗单书免费链路验证'
  );

UPDATE crawl_schedule
SET schedule_times = '00:00,08:00,14:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 1,
    crawl_vip = 0,
    auto_merge = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND name = '书旗免费章节链路验证';

INSERT INTO crawl_schedule (
  name,
  source_id,
  credential_id,
  schedule_times,
  timezone,
  crawl_public,
  crawl_vip,
  auto_merge,
  enabled
)
SELECT
  '书旗免费章节链路验证',
  @shuqi_source_id,
  NULL,
  '00:00,08:00,14:00',
  'Asia/Shanghai',
  1,
  0,
  1,
  1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM crawl_schedule
    WHERE source_id = @shuqi_source_id
      AND name = '书旗免费章节链路验证'
  );
