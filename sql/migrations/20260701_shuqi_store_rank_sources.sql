CREATE DATABASE IF NOT EXISTS mini_novel_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mini_novel_crawler;

SET @shuqi_source_id := (SELECT id FROM crawl_source WHERE source_code = 'shuqi_public' LIMIT 1);

UPDATE crawl_rank_source
SET enabled = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗单书免费链路验证';

UPDATE crawl_schedule
SET name = '书旗榜单免费章节采集',
    schedule_times = '00:00,08:00,14:00',
    crawl_public = 1,
    crawl_vip = 0,
    auto_merge = 1,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND name IN ('书旗免费章节链路验证', '书旗榜单免费章节采集');

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
  '书旗榜单免费章节采集',
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
      AND name = '书旗榜单免费章节采集'
  );

UPDATE crawl_rank_source
SET rank_type = 'STORE_ALL',
    rank_url = 'https://www.shuqi.com/store?sz=0&fc=0&wd=10&tm=0&st=0',
    prefer_completed = 0,
    max_books = 20,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗书库综合前20';

INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @shuqi_source_id, '书旗书库综合前20', 'STORE_ALL',
       'https://www.shuqi.com/store?sz=0&fc=0&wd=10&tm=0&st=0', 0, 20, 1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id AND rank_name = '书旗书库综合前20'
  );

UPDATE crawl_rank_source
SET rank_type = 'STORE_MALE',
    rank_url = 'https://www.shuqi.com/store?sz=1&fc=0&wd=10&tm=0&st=0',
    prefer_completed = 0,
    max_books = 20,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗男频前20';

INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @shuqi_source_id, '书旗男频前20', 'STORE_MALE',
       'https://www.shuqi.com/store?sz=1&fc=0&wd=10&tm=0&st=0', 0, 20, 1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id AND rank_name = '书旗男频前20'
  );

UPDATE crawl_rank_source
SET rank_type = 'STORE_FEMALE',
    rank_url = 'https://www.shuqi.com/store?sz=2&fc=0&wd=10&tm=0&st=0',
    prefer_completed = 0,
    max_books = 20,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗女频前20';

INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @shuqi_source_id, '书旗女频前20', 'STORE_FEMALE',
       'https://www.shuqi.com/store?sz=2&fc=0&wd=10&tm=0&st=0', 0, 20, 1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id AND rank_name = '书旗女频前20'
  );

UPDATE crawl_rank_source
SET rank_type = 'STORE_COMPLETED',
    rank_url = 'https://www.shuqi.com/store?sz=0&fc=0&wd=10&tm=0&st=2',
    prefer_completed = 1,
    max_books = 20,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = @shuqi_source_id
  AND rank_name = '书旗完结前20';

INSERT INTO crawl_rank_source (source_id, rank_name, rank_type, rank_url, prefer_completed, max_books, enabled)
SELECT @shuqi_source_id, '书旗完结前20', 'STORE_COMPLETED',
       'https://www.shuqi.com/store?sz=0&fc=0&wd=10&tm=0&st=2', 1, 20, 1
WHERE @shuqi_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_rank_source
    WHERE source_id = @shuqi_source_id AND rank_name = '书旗完结前20'
  );
