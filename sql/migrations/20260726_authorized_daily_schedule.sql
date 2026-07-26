USE mini_novel_crawler;

SET @now_shanghai := UTC_TIMESTAMP() + INTERVAL 8 HOUR;
SET @next_0200 := IF(
  TIME(@now_shanghai) < '02:00:00',
  TIMESTAMP(DATE(@now_shanghai), '02:00:00'),
  TIMESTAMP(DATE(@now_shanghai) + INTERVAL 1 DAY, '02:00:00')
);

SET @h528_source_id := (SELECT id FROM crawl_source WHERE source_code = 'h528_authorized' LIMIT 1);
SET @novel69h_source_id := (SELECT id FROM crawl_source WHERE source_code = 'novel69h_authorized' LIMIT 1);
SET @xbookcn_source_id := (SELECT id FROM crawl_source WHERE source_code = 'xbookcn_authorized' LIMIT 1);

UPDATE crawl_source
SET enabled = 1,
    rule_config_json = JSON_SET(
      COALESCE(rule_config_json, JSON_OBJECT()),
      '$.rankRules.maxPages', 10
    ),
    updated_at = NOW()
WHERE source_code = 'h528_authorized';

UPDATE crawl_rank_source
SET max_books = 200,
    enabled = 1,
    updated_at = NOW()
WHERE source_id = @h528_source_id
  AND rank_type = 'H528_AUTHORIZED_POSTS';

UPDATE crawl_source
SET enabled = 1,
    rule_config_json = JSON_SET(
      COALESCE(rule_config_json, JSON_OBJECT()),
      '$.rankRules.maxPages', 5
    ),
    updated_at = NOW()
WHERE source_code = 'novel69h_authorized';

UPDATE crawl_rank_source
SET max_books = 100,
    enabled = 1,
    updated_at = NOW()
WHERE source_id = @novel69h_source_id
  AND rank_type = 'CATEGORY_AUTHORIZED';

UPDATE crawl_source
SET enabled = 0,
    updated_at = NOW()
WHERE source_code = 'xbookcn_authorized';

UPDATE crawl_rank_source
SET enabled = 0,
    updated_at = NOW()
WHERE source_id = @xbookcn_source_id;

UPDATE crawl_schedule
SET source_id = @h528_source_id,
    credential_id = NULL,
    schedule_times = '02:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 0,
    crawl_vip = 1,
    auto_merge = 0,
    enabled = 1,
    next_run_at = @next_0200,
    updated_at = NOW()
WHERE name = 'h528 authorized daily review crawl'
  AND @h528_source_id IS NOT NULL;

INSERT INTO crawl_schedule (
  name,
  source_id,
  credential_id,
  schedule_times,
  timezone,
  crawl_public,
  crawl_vip,
  auto_merge,
  enabled,
  last_run_at,
  next_run_at
) SELECT
  'h528 authorized daily review crawl',
  @h528_source_id,
  NULL,
  '02:00',
  'Asia/Shanghai',
  0,
  1,
  0,
  1,
  NULL,
  @next_0200
WHERE @h528_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_schedule WHERE name = 'h528 authorized daily review crawl'
  );

UPDATE crawl_schedule
SET source_id = @novel69h_source_id,
    credential_id = NULL,
    schedule_times = '02:00',
    timezone = 'Asia/Shanghai',
    crawl_public = 0,
    crawl_vip = 1,
    auto_merge = 0,
    enabled = 1,
    next_run_at = @next_0200,
    updated_at = NOW()
WHERE name = '69hnovel authorized daily review crawl'
  AND @novel69h_source_id IS NOT NULL;

INSERT INTO crawl_schedule (
  name,
  source_id,
  credential_id,
  schedule_times,
  timezone,
  crawl_public,
  crawl_vip,
  auto_merge,
  enabled,
  last_run_at,
  next_run_at
) SELECT
  '69hnovel authorized daily review crawl',
  @novel69h_source_id,
  NULL,
  '02:00',
  'Asia/Shanghai',
  0,
  1,
  0,
  1,
  NULL,
  @next_0200
WHERE @novel69h_source_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crawl_schedule WHERE name = '69hnovel authorized daily review crawl'
  );

UPDATE crawl_schedule
SET enabled = 0,
    updated_at = NOW()
WHERE source_id = @xbookcn_source_id;
